package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import com.valoser.futacha.shared.compat.CompatImagePhash
import com.valoser.futacha.shared.compat.ScrollAnchor
import com.valoser.futacha.shared.compat.compatInlineLinks
import com.valoser.futacha.shared.compat.toCompatPlainText
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.media.FutabaMediaKind
import com.valoser.futacha.shared.media.FUTABA_COMPAT_IMAGE_EXTENSIONS
import com.valoser.futacha.shared.media.FUTABA_COMPAT_VIDEO_EXTENSIONS
import com.valoser.futacha.shared.media.FUTABA_COMPAT_MEDIA_EXTENSION_PATTERN
import com.valoser.futacha.shared.media.classifyFutabaMedia
import com.valoser.futacha.shared.media.mediaFileExtension
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import com.valoser.futacha.shared.network.readBoundedHttpResponseBytes
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.request
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import coil3.request.ImageRequest
import com.valoser.futacha.shared.ui.image.FutabaExtensionFallbackPolicy
import com.valoser.futacha.shared.ui.image.futabaExtensionFallbackPolicy

/** Match the legacy adapter's max-edge/aspect-ratio thumbnail sizing. */
internal fun compatThreadThumbnailBounds(
    maxSize: Int,
    sourceWidth: Int?,
    sourceHeight: Int?,
    keepStableFrame: Boolean = false
): Pair<Int, Int> {
    val edge = maxSize.coerceAtLeast(1)
    // あぷ小 has no dimensions in the thread HTML.  Resizing its container
    // after Coil decodes the source makes the following text jump sideways.
    // Keep the reference client's configured square slot and fit the bitmap
    // inside it instead.
    if (keepStableFrame) return edge to edge
    val width = sourceWidth?.takeIf { it > 0 } ?: return edge to edge
    val height = sourceHeight?.takeIf { it > 0 } ?: return edge to edge
    return if (width >= height) {
        edge to (edge.toLong() * height / width).toInt().coerceAtLeast(1)
    } else {
        (edge.toLong() * width / height).toInt().coerceAtLeast(1) to edge
    }
}

private val compatApuSmallFileRegex = Regex(
    "(?:fu\\d+|f\\d+)\\.(?:$FUTABA_COMPAT_MEDIA_EXTENSION_PATTERN)$",
    RegexOption.IGNORE_CASE
)
private val compatApuSmallBareFileRegex = Regex(
    "(?:^|[^A-Za-z0-9])((?:fu\\d+|f\\d+)\\.(?:$FUTABA_COMPAT_MEDIA_EXTENSION_PATTERN))(?![A-Za-z0-9])",
    RegexOption.IGNORE_CASE
)
private val compatHtmlBreakRegex = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
private val compatBoundedHtmlTagRegex = Regex("<[^>]{0,500}>", RegexOption.IGNORE_CASE)
private val compatEncodedGreaterThanRegex = Regex("&#x?3e;|&gt;", RegexOption.IGNORE_CASE)
private val compatApuSmallUrlNormalizer = Regex(
    "^(https?://dec\\.2chan\\.net)/(up2?|up)/+(src|thumb)/+(.+)$",
    RegexOption.IGNORE_CASE
)

internal fun normalizeCompatApuSmallMediaUrl(url: String): String {
    val trimmed = url.trim()
    val match = compatApuSmallUrlNormalizer.matchEntire(trimmed) ?: return trimmed
    return "${match.groupValues[1]}/${match.groupValues[2].lowercase()}/" +
        "${match.groupValues[3].lowercase()}/${match.groupValues[4]}"
}

/** True for the legacy あぷ小 / up uploader URLs used by the APK. */
internal fun isCompatApuSmallMediaUrl(url: String): Boolean {
    val clean = normalizeCompatApuSmallMediaUrl(url).substringBefore('?').substringBefore('#')
    return ("/up2/src/" in clean || "/up/src/" in clean) &&
        compatApuSmallFileRegex.matches(clean.substringAfterLast('/'))
}

/** Recognise an あぷ小 source by file name even after an archive rewrites its host/path. */
internal fun compatApuSmallMediaFileName(url: String?): String? {
    val fileName = url
        ?.substringBefore('?')
        ?.substringBefore('#')
        ?.substringAfterLast('/')
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: return null
    return fileName.takeIf(compatApuSmallFileRegex::matches)?.lowercase()
}

private fun compatApuSmallSourceUrl(fileName: String): String {
    val directory = if (fileName.startsWith("fu", ignoreCase = true)) "up2" else "up"
    return "https://dec.2chan.net/$directory/src/$fileName"
}

internal fun compatMediaFileIdentity(url: String?): String? = url
    ?.substringBefore('?')
    ?.substringBefore('#')
    ?.substringAfterLast('/')
    ?.trim()
    ?.lowercase()
    ?.takeIf(String::isNotBlank)

/**
 * Build the thumbnail URL used by the legacy viewer for an up/up2 source.
 *
 * The uploader publishes thumbnails as `<source-stem>s.jpg` below `thumb/`,
 * regardless of the source extension.  Keeping the source extension out of
 * this function is intentional: WEBM/MP4 and image uploads share the same
 * thumbnail naming scheme in the reference APK.
 */
internal fun compatApuSmallThumbnailUrl(url: String): String? {
    val normalized = normalizeCompatApuSmallMediaUrl(url)
    if (!isCompatApuSmallMediaUrl(normalized)) return null
    val clean = normalized.substringBefore('?').substringBefore('#')
    val slash = clean.lastIndexOf('/')
    if (slash < 0 || !clean.substring(0, slash).endsWith("/src", ignoreCase = true)) return null
    val fileName = clean.substring(slash + 1)
    val dot = fileName.lastIndexOf('.')
    if (dot <= 0) return null
    val thumbnailName = fileName.substring(0, dot) + "s.jpg"
    return clean.substring(0, slash)
        .replace("/src", "/thumb", ignoreCase = true)
        .let { "$it/$thumbnailName" }
}

/** Resolve cached post bodies that contain uploader filenames without anchors. */
internal fun compatApuSmallSourceUrlsFromMessage(messageHtml: String): List<String> {
    // Cached compatibility posts may contain only the filename.  Do not
    // resolve filenames copied into `>` quote lines: they belong to the
    // quoted post and must not become a second gallery item for this post.
    val plainMessage = messageHtml
        .replace(compatHtmlBreakRegex, "\n")
        .replace(compatBoundedHtmlTagRegex, "")
        .replace(compatEncodedGreaterThanRegex, ">")
    val unquotedMessage = plainMessage.lineSequence()
        .filterNot { it.trimStart().startsWith(">") }
        .joinToString("\n")
    return compatApuSmallBareFileRegex.findAll(unquotedMessage)
        .mapNotNull { it.groupValues.getOrNull(1) }
        .distinct()
        .map(::compatApuSmallSourceUrl)
        .toList()
}

/** Backward-compatible single-source helper for the primary attachment slot. */
internal fun compatApuSmallSourceUrlFromMessage(messageHtml: String): String? =
    compatApuSmallSourceUrlsFromMessage(messageHtml).firstOrNull()

private fun compatApuFileAppearsOnlyInQuote(messageHtml: String, fileName: String): Boolean {
    if (fileName.isBlank()) return false
    val normalizedFileName = fileName.lowercase()
    val lines = messageHtml.toCompatPlainText().lineSequence().map(String::lowercase).toList()
    val quoted = lines.any { it.trimStart().startsWith(">") && normalizedFileName in it }
    val unquoted = lines.any { !it.trimStart().startsWith(">") && normalizedFileName in it }
    return quoted && !unquoted
}

/** Return all visible あぷ小 links, including bare `fu123.jpg` filenames. */
internal fun compatInlineApuSmallMediaUrls(messageHtml: String): List<String> =
    buildList {
        val plainMessage = messageHtml.toCompatPlainText()
        compatInlineLinks(messageHtml)
            .asSequence()
            // A quoted `>fu123.jpg` is part of the referenced post, not an
            // attachment belonging to the current post.  compatInlineLinks
            // intentionally reports every tappable link, so apply the same
            // quote exclusion here before creating gallery media.
            .filter { link ->
                val lineStart = plainMessage.lastIndexOf('\n', (link.start - 1).coerceAtLeast(0)) + 1
                !plainMessage.substring(lineStart, link.start.coerceIn(lineStart, plainMessage.length))
                    .trimStart()
                    .startsWith(">")
            }
            .map { it.url }
            .filter(::isCompatApuSmallMediaUrl)
            .forEach(::add)
        // Keep a parser/cache fallback for HTML variants whose visible text
        // is empty after sanitisation but still contains a bare fu filename.
        compatApuSmallSourceUrlsFromMessage(messageHtml).forEach(::add)
    }.distinct()

/**
 * Return only the inline uploader previews that the current compatibility
 * preference allows to be fetched and displayed in a post row.
 *
 * The original upload remains a link/viewer target even when this returns an
 * empty list.  This distinction is important: 「表示しない」 disables the
 * generated thumbnail, not the user's ability to open the fu… media.
 */
internal fun compatVisibleInlineApuSmallMediaUrls(
    messageHtml: String,
    upsThumbnailMethod: String?,
    wifiConnected: Boolean
): List<String> = compatInlineApuSmallMediaUrls(messageHtml)
    .takeIf { compatApuSmallThumbEnabled(upsThumbnailMethod, wifiConnected) }
    .orEmpty()

internal fun normalizeCompatPostMedia(post: CompatPostSnapshot): CompatPostSnapshot {
    val normalizedImageUrl = post.imageUrl?.let(::normalizeCompatApuSmallMediaUrl)
    val normalizedThumbnailUrl = post.thumbnailUrl?.let(::normalizeCompatApuSmallMediaUrl)
    val normalizedPost = if (
        normalizedImageUrl != post.imageUrl || normalizedThumbnailUrl != post.thumbnailUrl
    ) {
        post.copy(imageUrl = normalizedImageUrl, thumbnailUrl = normalizedThumbnailUrl)
    } else post
    if (normalizedPost.imageUrl != null) {
        val apuFileName = compatApuSmallMediaFileName(normalizedPost.imageUrl)
        if (apuFileName != null) {
            // Archive pages rewrite uploader links to their own host.  File
            // identity is stable across that rewrite, so canonicalise it before
            // quote filtering and duplicate suppression.
            if (compatApuFileAppearsOnlyInQuote(normalizedPost.messageHtml, apuFileName)) {
                return normalizedPost.copy(imageUrl = null, thumbnailUrl = null)
            }
            val sourceUrl = if (isCompatApuSmallMediaUrl(normalizedPost.imageUrl)) {
                normalizedPost.imageUrl
            } else {
                compatApuSmallSourceUrl(apuFileName)
            }
            val thumbnailUrl = if (classifyFutabaMedia(sourceUrl) == FutabaMediaKind.VIDEO) {
                normalizedPost.thumbnailUrl ?: compatApuSmallThumbnailUrl(sourceUrl)
            } else {
                // Images are decoded directly into the configured thumbnail
                // bounds, matching 1.apk and avoiding a missing /thumb request.
                null
            }
            return normalizedPost.copy(imageUrl = sourceUrl, thumbnailUrl = thumbnailUrl)
        }
        return normalizedPost
    }
    if (normalizedPost.thumbnailUrl != null) return normalizedPost

    // Cached snapshots created by an older build may have lost the structured
    // attachment fields while retaining the original absolute anchor in the
    // body. Recover every supported image/video URL before rendering the row;
    // otherwise only the older JPG-shaped records appear in the viewer.
    val plainMessage = normalizedPost.messageHtml.toCompatPlainText()
    val bodyMediaUrl = compatInlineLinks(normalizedPost.messageHtml)
        .asSequence()
        .filter { link ->
            val lineStart = plainMessage.lastIndexOf('\n', (link.start - 1).coerceAtLeast(0)) + 1
            !plainMessage
                .substring(lineStart, link.start.coerceIn(lineStart, plainMessage.length))
                .trimStart()
                .startsWith(">")
        }
        .map { it.url }
        .firstOrNull {
            classifyFutabaMedia(it) != FutabaMediaKind.UNSUPPORTED &&
                !isCompatApuSmallMediaUrl(it)
        }
    if (bodyMediaUrl != null) {
        return normalizedPost.copy(
            imageUrl = bodyMediaUrl,
            thumbnailUrl = normalizedPost.thumbnailUrl
        )
    }
    val sourceUrl = compatApuSmallSourceUrlFromMessage(normalizedPost.messageHtml) ?: return normalizedPost
    return normalizedPost.copy(
        imageUrl = sourceUrl,
        thumbnailUrl = compatApuSmallThumbnailUrl(sourceUrl)
            .takeIf { classifyFutabaMedia(sourceUrl) == FutabaMediaKind.VIDEO }
    )
}

internal fun normalizeCompatThreadSnapshot(snapshot: CompatThreadSnapshot): CompatThreadSnapshot =
    snapshot.copy(posts = snapshot.posts.map(::normalizeCompatPostMedia))

internal fun compatMediaIdentity(post: CompatPostSnapshot): String =
    post.mediaKey ?: post.postNo

internal enum class CompatGalleryTapAction {
    OPEN_VIEWER,
    SELECT_MEDIA
}

/**
 * The reference APK saves immediately while save mode is active. Toshiaki compatibility
 * mode deliberately extends that interaction to selection so images and videos can be
 * exported together as a ZIP or folder.
 */
internal fun compatGalleryTapAction(saveMode: Boolean): CompatGalleryTapAction =
    if (saveMode) CompatGalleryTapAction.SELECT_MEDIA else CompatGalleryTapAction.OPEN_VIEWER

internal enum class CompatGalleryBatchSaveFormat {
    ZIP,
    FOLDER
}

internal fun buildCompatGalleryBatchSaveMessage(
    format: CompatGalleryBatchSaveFormat,
    succeeded: Int,
    failed: Int
): String = buildString {
    append(
        when (format) {
            CompatGalleryBatchSaveFormat.ZIP -> "ZIPに"
            CompatGalleryBatchSaveFormat.FOLDER -> "フォルダに"
        }
    )
    append(succeeded)
    append("件を保存しました")
    if (failed > 0) {
        append("\n")
        append(failed)
        append("件失敗しました")
    }
}

internal fun compatBatchMediaUrls(posts: List<CompatPostSnapshot>): List<String> = posts
    .mapNotNull { it.imageUrl ?: it.thumbnailUrl }
    .filter { classifyFutabaMedia(it) != FutabaMediaKind.UNSUPPORTED }
    .distinct()

internal fun compatBatchOutputFileNames(urls: List<String>): Map<String, String> {
    val counts = mutableMapOf<String, Int>()
    return buildMap {
        urls.distinct().forEachIndexed { index, url ->
            val rawName = url.substringBefore('#').substringBefore('?').substringAfterLast('/')
                .ifBlank { "media_${index + 1}.bin" }
            val stem = rawName.substringBeforeLast('.', rawName)
            val extension = rawName.substringAfterLast('.', "")
                .let { if (it.isBlank()) "" else ".$it" }
            val collisionIndex = counts.getOrElse(rawName) { 0 }
            counts[rawName] = collisionIndex + 1
            put(url, if (collisionIndex == 0) rawName else "$stem($collisionIndex)$extension")
        }
    }
}

internal fun compatGalleryOverflowLabels(): List<String> =
    listOf("表示オプション", "設定", "ヘルプ")

internal fun compatViewerTopOverflowLabels(): List<String> =
    listOf("表示オプション", "ツールバー編集", "設定", "ヘルプ")

/** Exact item arrays used by sample/1.apk's titleless context menus. */
internal fun compatCatalogContextLabels(): List<String> = listOf(
    "NGスレッドに登録",
    "NGスレッドとNGワードに登録",
    "NG画像に登録",
    "delを送信する",
    "delとNGスレッドに登録",
    "delとNGスレッドとNGワードに登録",
    "タブに追加する"
)

internal fun compatGalleryContextBaseLabels(): List<String> = listOf(
    "元レスに移動する",
    "画像を保存する",
    "サムネイルを再読み込みする",
    "NG画像に登録",
    "リンクURLをコピー",
    "ブラウザーで開く",
    "URLを共有",
    "画像を共有"
)

internal fun compatThreadImageContextBaseLabels(): List<String> =
    compatGalleryContextBaseLabels().drop(1)

internal fun compatViewerQuickMenuLabels(): List<String> =
    listOf("保存", "共有", "検索")

internal fun compatDrawerTabContextLabels(): List<String> = listOf(
    "お気に入り",
    "削除する",
    "下のスレを全て削除する",
    "他のスレを全て削除する",
    "落ちたスレを削除する",
    "全て削除する"
)

internal fun compatCanonicalMediaUrl(value: String): String =
    value.substringBefore('#').substringBefore('?').trimEnd('/')

/** Pure matching rule shared by thread-link routing and viewer regression tests. */
internal fun compatViewerPostMatchesMediaUrl(
    post: CompatPostSnapshot,
    url: String
): Boolean {
    val requested = compatCanonicalMediaUrl(url)
    return listOfNotNull(
        post.imageUrl,
        post.thumbnailUrl,
        resolveCompatViewerMediaUrl(post)
    ).any { candidate -> compatCanonicalMediaUrl(candidate) == requested }
}

/**
 * Expand a post's explicit inline あぷ小 references into viewer media items.
 * The normal post model intentionally has one primary attachment; these
 * synthetic items let the gallery and pager include a second `fu…` image
 * without changing the displayed reply identity.
 */
private fun expandCompatInlineApuMedia(post: CompatPostSnapshot): List<CompatPostSnapshot> {
    val normalized = normalizeCompatPostMedia(post)
    val primaryMediaIdentities = setOfNotNull(
        compatMediaFileIdentity(normalized.imageUrl),
        compatMediaFileIdentity(normalized.thumbnailUrl)
    )
    val inlineUrls = compatInlineApuSmallMediaUrls(normalized.messageHtml)
        .filterNot { compatMediaFileIdentity(it) in primaryMediaIdentities }
    return buildList {
        add(normalized)
        inlineUrls.forEachIndexed { index, url ->
            add(
                normalized.copy(
                    imageUrl = url,
                    thumbnailUrl = compatApuSmallThumbnailUrl(url)
                        .takeIf { classifyFutabaMedia(url) == FutabaMediaKind.VIDEO },
                    thumbnailWidth = null,
                    thumbnailHeight = null,
                    mediaKey = "${normalized.postNo}::apu::$index"
                )
            )
        }
    }
}

internal fun compatMediaPostsWithInlineApu(posts: List<CompatPostSnapshot>): List<CompatPostSnapshot> =
    posts.flatMap(::expandCompatInlineApuMedia)

internal fun compatApuSmallThumbEnabled(method: String?, wifiConnected: Boolean): Boolean {
    return when (method?.trim()?.lowercase()) {
        null, "" -> true
        "none", "利用しない", "表示しない" -> false
        "wifi", "wi-fi回線のみ", "wi-fi回線のみ先読み" -> wifiConnected
        else -> true // load / preload / 表示する / 表示する(先読み)
    }
}

internal fun compatPostHasVisibleMedia(
    post: CompatPostSnapshot,
    upsThumbnailMethod: String? = null,
    wifiConnected: Boolean = false
): Boolean {
    if (post.mediaKey != null) return true
    val original = post.imageUrl ?: post.thumbnailUrl ?: return false
    return !isCompatApuSmallMediaUrl(original) ||
        compatApuSmallThumbEnabled(upsThumbnailMethod, wifiConnected)
}

/**
 * Whether a post belongs in the image/video sequence of the viewer.
 *
 * The あぷ小 preference controls network thumbnail loading in the thread
 * row, not whether the original upload can be opened.  Keeping those two
 * decisions separate prevents a bare `fu....jpg` link from falling through
 * to the external browser when thumbnail loading is disabled.
 */
internal fun compatPostHasViewerMedia(post: CompatPostSnapshot): Boolean =
    post.mediaKey != null || post.imageUrl != null || post.thumbnailUrl != null

/**
 * Return the exact media sequence used by the compatibility viewer.
 *
 * The thread rows and the viewer apply the あぷ小 and image-NG filters.  Keeping
 * that filtering in one pure function is important because the index passed to
 * the pager must refer to this sequence, not to the unfiltered thread posts.
 */
internal fun compatViewerMediaPosts(
    posts: List<CompatPostSnapshot>,
    hiddenImages: Set<String> = emptySet(),
    hiddenPostNos: Set<String> = emptySet(),
    upsThumbnailMethod: String? = null,
    wifiConnected: Boolean = false
): List<CompatPostSnapshot> = compatMediaPostsWithInlineApu(posts).filter { post ->
    compatPostHasViewerMedia(post) &&
        post.postNo !in hiddenPostNos &&
        post.imageUrl !in hiddenImages &&
        post.thumbnailUrl !in hiddenImages
}

/**
 * Resolve thread-image pHash rules for the gallery/viewer.  The list screen
 * and the thread rows already use the same rule, but the gallery has to do
 * the asynchronous image fetch itself because it is a separate host.
 */
internal suspend fun compatImagePhashHiddenPostNos(
    httpClient: HttpClient?,
    posts: List<CompatPostSnapshot>,
    rules: List<com.valoser.futacha.shared.compat.CompatNgRule>,
    threshold: Int
): Set<String> {
    val client = httpClient ?: return emptySet()
    if (rules.isEmpty()) return emptySet()
    val candidates = withContext(AppDispatchers.parsing) {
        posts
            .map(::normalizeCompatPostMedia)
            .mapNotNull { post ->
                (post.imageUrl ?: post.thumbnailUrl)?.let { url -> post.postNo to url }
            }
            .distinctBy { it.second }
            .take(256)
    }
    val hidden = mutableSetOf<String>()
    withTimeoutOrNull(COMPAT_PHASH_BATCH_TIMEOUT_MILLIS) {
        candidates.forEach { (postNo, url) ->
            val phash = withTimeoutOrNull(COMPAT_PHASH_REQUEST_TIMEOUT_MILLIS) {
                fetchCompatImagePhash(client, url).getOrNull()
            } ?: return@forEach
            if (rules.any { rule -> CompatImagePhash.isSimilar(phash, rule.normalizedValue, threshold) }) {
                hidden += postNo
            }
        }
    }
    return hidden
}

/** Resolve a viewer launch by post identity before falling back to its old index. */
internal fun compatViewerInitialPage(
    posts: List<CompatPostSnapshot>,
    requestedPostNo: String?,
    fallbackIndex: Int
): Int {
    if (posts.isEmpty()) return 0
    val identityIndex = requestedPostNo
        ?.let { identity -> posts.indexOfFirst { compatMediaIdentity(it) == identity } }
        ?.takeIf { it >= 0 }
    return (identityIndex ?: fallbackIndex).coerceIn(0, posts.lastIndex)
}

/**
 * A viewer toolbar command has an absolute destination in both reference APKs.
 * It must not be resolved through the caller-sensitive system Back behavior.
 */
internal sealed interface CompatViewerNavigationTarget {
    data class SourcePost(val anchor: ScrollAnchor) : CompatViewerNavigationTarget
    data class Gallery(val index: Int, val mediaIdentity: String?) : CompatViewerNavigationTarget
}

internal fun compatViewerNavigationTarget(
    actionKey: String,
    posts: List<CompatPostSnapshot>,
    currentPage: Int,
    snapshotRevision: Long
): CompatViewerNavigationTarget? {
    val page = currentPage.coerceIn(0, posts.lastIndex.coerceAtLeast(0))
    val post = posts.getOrNull(page)
    return when (actionKey) {
        "back" -> post?.let {
            CompatViewerNavigationTarget.SourcePost(
                ScrollAnchor(
                    postNo = it.postNo,
                    fallbackIndex = it.position.coerceAtLeast(0),
                    snapshotRevision = snapshotRevision
                )
            )
        }
        "gallery" -> CompatViewerNavigationTarget.Gallery(
            index = page,
            mediaIdentity = post?.let(::compatMediaIdentity)
        )
        else -> null
    }
}

/**
 * Compatibility chrome prefers the small thumbnail, but current Futaba content can expose
 * only a source URL (notably bare fu/f uploader names resolved by the thread parser).
 */
internal fun resolveCompatPostPreviewUrl(
    post: CompatPostSnapshot,
    upsThumbnailMethod: String? = null,
    wifiConnected: Boolean = false
): String? {
    val original = post.imageUrl ?: post.thumbnailUrl ?: return null
    if (isCompatApuSmallMediaUrl(original)) {
        // Callers outside a thread screen (legacy save/share previews and
        // compatibility tests) have no per-thread setting; preserve their
        // historical source-image fallback.
        if (upsThumbnailMethod == null && classifyFutabaMedia(original) == FutabaMediaKind.VIDEO) {
            return post.thumbnailUrl ?: compatApuSmallThumbnailUrl(original) ?: original
        }
        if (!compatApuSmallThumbEnabled(upsThumbnailMethod, wifiConnected)) return null
        return if (classifyFutabaMedia(original) == FutabaMediaKind.VIDEO) {
            post.thumbnailUrl ?: compatApuSmallThumbnailUrl(original) ?: original
        } else {
            // Use one stable source request.  Successful memory/disk entries
            // are reused immediately when a tab or gallery is reopened.
            post.imageUrl ?: original
        }
    }
    return post.thumbnailUrl ?: post.imageUrl
}

internal fun resolveCompatCatalogPreviewUrl(
    item: CatalogItem,
    lowQuality: Boolean = false
): String? = if (lowQuality) {
    item.thumbnailUrl
        ?.replace("/thumb/", "/cat/")
        ?: item.fullImageUrl
} else {
    item.thumbnailUrl ?: item.fullImageUrl
}

/**
 * Applies the final 1.apk catalog quality policy.
 *
 * old.apk only recognized a network whose legacy type name was `MOBILE`.
 * 1.apk deliberately changed this to Android's metered/unmetered decision, so
 * Ethernet and a user-marked unmetered connection retain normal thumbnails.
 */
internal fun shouldUseCompatCatalogLowQuality(
    alwaysLowQuality: Boolean,
    meteredOnlyLowQuality: Boolean,
    isUnmeteredConnection: Boolean
): Boolean = alwaysLowQuality || (meteredOnlyLowQuality && !isUnmeteredConnection)

/**
 * Applies 1.apk's Viewer preload values (`usually`, `wifi`, `none`).
 *
 * Early compatibility builds persisted the visible Japanese labels instead,
 * so keep accepting those labels while every new write uses the APK raw value.
 * Unknown or missing values retain the reference default (`usually`).
 */
internal fun shouldPreloadCompatViewer(
    storedMode: String?,
    isUnmeteredConnection: Boolean
): Boolean = when (storedMode?.trim()?.lowercase()) {
    "none", "off", "利用しない" -> false
    "wifi", "wi-fi回線のみ" -> isUnmeteredConnection
    else -> true
}

/**
 * Candidate order used by the legacy catalog card.
 *
 * sample/1.apk passes the catalog's thumbnail (`strSrc`) to the image loader
 * even when the catalog parser also knows a source-image URL.  Loading the
 * source image first is both slower and can fail for media whose extension is
 * only valid on the source endpoint.  Keep the thumbnail first and retry the
 * source only when the thumbnail cannot be decoded.
 */
internal fun compatCatalogPreviewCandidates(
    item: CatalogItem,
    lowQuality: Boolean = false
): List<String> = buildList {
    if (lowQuality) {
        resolveCompatCatalogPreviewUrl(item, lowQuality = true)
            ?.takeIf { it.isNotBlank() }
            ?.let(::add)
    }
    item.thumbnailUrl?.takeIf { it.isNotBlank() }?.let(::add)
    item.fullImageUrl
        ?.takeIf { it.isNotBlank() && it != item.thumbnailUrl }
        ?.let(::add)

    // The tutorial board is intentionally backed by the checked-in HTML
    // fixture. Its URLs use an example.com host, so a network image loader
    // can never resolve them. The Android host supplies a matching packaged
    // drawable as the last candidate so real boards still use their
    // server-provided thumbnail first.
    if (item.thumbnailUrl.orEmpty().contains("example.com", ignoreCase = true) ||
        item.fullImageUrl.orEmpty().contains("example.com", ignoreCase = true)
    ) {
        add("android.resource://com.valoser.futacha/drawable/compat_fixture_catalog_thumb")
    }
}.distinct()

/** Viewer/save/share actions must use the original media whenever one exists. */
internal fun resolveCompatViewerMediaUrl(post: CompatPostSnapshot): String? =
    post.imageUrl ?: post.thumbnailUrl

internal fun isCompatVideoMediaUrl(url: String): Boolean =
    classifyFutabaMedia(url) == FutabaMediaKind.VIDEO

internal fun isCompatImageMediaUrl(url: String): Boolean =
    classifyFutabaMedia(url) == FutabaMediaKind.IMAGE

internal fun compatMediaExtension(url: String): String = mediaFileExtension(url)

internal fun compatSupportedImageExtensions(): Set<String> = FUTABA_COMPAT_IMAGE_EXTENSIONS

internal fun compatSupportedVideoExtensions(): Set<String> = FUTABA_COMPAT_VIDEO_EXTENSIONS

/**
 * Image requests in compatibility mode must be tolerant of old Futaba
 * mirrors that publish a valid image under a stale extension.  Do not include
 * video fallbacks here: a broken image thumbnail must never turn into an
 * automatically loaded video.
 */
internal fun ImageRequest.Builder.compatImageFallbackPolicy(): ImageRequest.Builder =
    futabaExtensionFallbackPolicy(
        FutabaExtensionFallbackPolicy(
            maxAttempts = 5,
            allowVideoFallback = false,
            preferStaticCandidates = true,
            maxVideoAttempts = 0
        )
    )

/**
 * 1.apk removed the legacy WebM→same-stem-MP4 switch because the guessed MP4
 * normally does not exist and can prevent an otherwise valid WebM from playing.
 * Keep the old argument for backup/source compatibility, but deliberately make
 * it inert and always play the URL that the post actually supplied.
 */
internal fun compatVideoPlaybackCandidates(
    url: String,
    @Suppress("UNUSED_PARAMETER")
    switchWebmToMp4: Boolean
): List<String> = listOf(url)

internal data class CompatRemoteMediaInfo(
    val contentType: String?,
    val contentLengthBytes: Long?
)

private const val COMPAT_EXIF_HEADER_LIMIT_BYTES = 1_048_576
private const val COMPAT_MEDIA_INFO_TIMEOUT_MILLIS = 8_000L
private const val COMPAT_APNG_SCAN_LIMIT_BYTES = 1_048_576
private val compatApngScanSemaphore = Semaphore(2)

/**
 * App-scope cache for the APNG badge lookup performed by the compatibility
 * gallery. Both positive and negative results are retained: a static PNG must
 * not trigger another Range request every time the gallery is reopened (#73).
 */
internal class CompatApngMarkerCache(
    private val scope: CoroutineScope,
    private val maxEntries: Int = 1_024
) {
    private val mutex = Mutex()
    private val values = LinkedHashMap<String, Boolean>()
    private val inFlight = mutableMapOf<String, Deferred<Result<Boolean>>>()

    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    suspend fun get(url: String): Boolean? = mutex.withLock {
        takeCached(normalizeKey(url))
    }

    suspend fun getOrLoad(
        url: String,
        loader: suspend () -> Result<Boolean>
    ): Result<Boolean> {
        val key = normalizeKey(url)
        var created = false
        val request = mutex.withLock {
            takeCached(key)?.let { return Result.success(it) }
            inFlight[key] ?: scope.async(start = CoroutineStart.LAZY) {
                try {
                    loader().also { result ->
                        mutex.withLock {
                            if (result.isSuccess) putCached(key, result.getOrThrow())
                        }
                    }
                } finally {
                    // Cancellation must not leave a completed/cancelled request in
                    // the de-duplication map and permanently poison this URL.
                    withContext(NonCancellable) {
                        mutex.withLock { inFlight.remove(key) }
                    }
                }
            }.also {
                inFlight[key] = it
                created = true
            }
        }
        if (created) request.start()
        return request.await()
    }

    suspend fun invalidate(url: String) {
        mutex.withLock { values.remove(normalizeKey(url)) }
    }

    internal suspend fun cachedEntryCount(): Int = mutex.withLock { values.size }

    private fun takeCached(key: String): Boolean? {
        val value = values.remove(key) ?: return null
        values[key] = value
        return value
    }

    private fun putCached(key: String, value: Boolean) {
        values.remove(key)
        values[key] = value
        while (values.size > maxEntries) {
            values.remove(values.keys.first())
        }
    }

    private fun normalizeKey(url: String): String =
        normalizeCompatApuSmallMediaUrl(url.trim()).substringBefore('#')
}

/**
 * Detect the APNG animation-control chunk without decoding the image. The scan stops at
 * the first image-data/end chunk and never examines more than the bounded prefix fetched
 * by [fetchCompatApngMarker], matching 1.apk's one-megabyte safety boundary.
 */
internal fun isCompatApngHeader(bytes: ByteArray): Boolean {
    val pngSignature = byteArrayOf(
        0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    )
    if (bytes.size < pngSignature.size || !bytes.copyOfRange(0, 8).contentEquals(pngSignature)) {
        return false
    }
    var offset = 8
    while (offset + 8 <= bytes.size && offset < COMPAT_APNG_SCAN_LIMIT_BYTES) {
        val length = ((bytes[offset].toLong() and 0xffL) shl 24) or
            ((bytes[offset + 1].toLong() and 0xffL) shl 16) or
            ((bytes[offset + 2].toLong() and 0xffL) shl 8) or
            (bytes[offset + 3].toLong() and 0xffL)
        if (length > Int.MAX_VALUE) return false
        val type = bytes.copyOfRange(offset + 4, offset + 8).decodeToString()
        if (type == "acTL") return true
        if (type == "IDAT" || type == "IEND") return false
        val next = offset.toLong() + 12L + length
        if (next > bytes.size || next > COMPAT_APNG_SCAN_LIMIT_BYTES) return false
        offset = next.toInt()
    }
    return false
}

internal suspend fun fetchCompatApngMarker(
    httpClient: HttpClient,
    url: String
): Result<Boolean> = runSuspendCatchingPreservingCancellation {
    compatApngScanSemaphore.withPermit {
        val bytes = withTimeout(COMPAT_MEDIA_INFO_TIMEOUT_MILLIS) {
            withContext(AppDispatchers.io) {
                val response = httpClient.get(url) {
                    headers.append(HttpHeaders.Range, "bytes=0-${COMPAT_APNG_SCAN_LIMIT_BYTES - 1}")
                }
                check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
                readBoundedHttpResponseBytes(
                    response = response,
                    maxBytes = COMPAT_APNG_SCAN_LIMIT_BYTES,
                    totalTimeoutMillis = COMPAT_MEDIA_INFO_TIMEOUT_MILLIS
                )
            }
        }
        withContext(AppDispatchers.parsing) { isCompatApngHeader(bytes) }
    }
}

/**
 * Read the small JPEG header needed by the viewer's information dialog.
 * Servers which support Range return only the first megabyte; servers which
 * ignore it are still bounded before the body is parsed.
 */
internal suspend fun fetchCompatExifSummary(
    httpClient: HttpClient,
    url: String
): Result<String> = runSuspendCatchingPreservingCancellation {
    val responseBytes = withTimeout(COMPAT_MEDIA_INFO_TIMEOUT_MILLIS) {
        withContext(AppDispatchers.io) {
            val response = httpClient.get(url) {
                headers.append(HttpHeaders.Range, "bytes=0-${COMPAT_EXIF_HEADER_LIMIT_BYTES - 1}")
            }
            check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
            val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            require(contentLength == null || contentLength <= COMPAT_EXIF_HEADER_LIMIT_BYTES) {
                "画像ヘッダーが大きすぎます"
            }
            readBoundedHttpResponseBytes(
                response = response,
                maxBytes = COMPAT_EXIF_HEADER_LIMIT_BYTES,
                totalTimeoutMillis = COMPAT_MEDIA_INFO_TIMEOUT_MILLIS
            )
        }
    }
    require(responseBytes.size <= COMPAT_EXIF_HEADER_LIMIT_BYTES) { "画像ヘッダーが大きすぎます" }
    withContext(AppDispatchers.parsing) { parseCompatExifSummary(responseBytes) }
}

internal fun parseCompatExifSummary(bytes: ByteArray): String {
    if (bytes.size < 4 || (bytes[0].toInt() and 0xFF) != 0xFF || (bytes[1].toInt() and 0xFF) != 0xD8) {
        return "なし"
    }
    var offset = 2
    while (offset + 4 <= bytes.size) {
        if ((bytes[offset].toInt() and 0xFF) != 0xFF) {
            offset++
            continue
        }
        while (offset < bytes.size && (bytes[offset].toInt() and 0xFF) == 0xFF) offset++
        if (offset >= bytes.size) break
        val marker = bytes[offset].toInt() and 0xFF
        offset++
        if (marker == 0xD9 || marker == 0xDA) break
        if (marker in 0xD0..0xD7) continue
        if (offset + 2 > bytes.size) break
        val segmentLength = unsignedShort(bytes, offset)
        if (segmentLength < 2 || offset + segmentLength > bytes.size) break
        if (marker == 0xE1 && segmentLength >= 8 &&
            bytes.copyOfRange(offset + 2, offset + 8).contentEquals(byteArrayOf(0x45, 0x78, 0x69, 0x66, 0, 0))
        ) {
            return parseCompatExifTiff(bytes, offset + 8, offset + segmentLength)
        }
        offset += segmentLength
    }
    return "なし"
}

private fun parseCompatExifTiff(bytes: ByteArray, start: Int, end: Int): String {
    if (start + 8 > end) return "なし"
    val littleEndian = when {
        bytes[start].toInt() == 'I'.code && bytes[start + 1].toInt() == 'I'.code -> true
        bytes[start].toInt() == 'M'.code && bytes[start + 1].toInt() == 'M'.code -> false
        else -> return "なし"
    }
    if (readShort(bytes, start + 2, littleEndian) != 42) return "なし"
    val firstIfd = readInt(bytes, start + 4, littleEndian).toLong()
    if (firstIfd !in 8L until (end - start).toLong()) return "なし"
    val values = linkedMapOf<Int, String>()
    val visitedIfds = mutableSetOf<Int>()
    parseCompatExifIfd(
        bytes = bytes,
        start = start,
        end = end,
        relativeOffset = firstIfd.toInt(),
        littleEndian = littleEndian,
        values = values,
        visitedIfds = visitedIfds
    )
    // Camera fields such as FNumber and ExposureTime normally live in the
    // Exif sub-IFD, referenced by tag 0x8769 from the primary IFD.
    values[0x8769]?.toIntOrNull()?.let { exifIfdOffset ->
        parseCompatExifIfd(
            bytes = bytes,
            start = start,
            end = end,
            relativeOffset = exifIfdOffset,
            littleEndian = littleEndian,
            values = values,
            visitedIfds = visitedIfds
        )
    }
    val labels = listOf(
        0x0132 to "撮影日時",
        0x9003 to "撮影日時",
        0x829D to "絞り値",
        0x829A to "露出時間",
        0x8827 to "ISO 感度",
        0x920A to "焦点距離",
        0x010F to "メーカー",
        0x0110 to "モデル",
        0x0112 to "向き"
    )
    return labels.asSequence()
        .mapNotNull { (tag, label) ->
            values[tag]?.let { value ->
                when (tag) {
                    0x829D -> "絞り値: f/${formatCompatExifDecimal(value)}"
                    0x829A -> "露出時間: ${formatCompatExifExposure(value)} 秒"
                    0x920A -> "焦点距離: ${value.toDoubleOrNull()?.toInt() ?: value} mm"
                    else -> "$label: $value"
                }
            }
        }
        .distinct()
        .take(8)
        .joinToString("\n")
        .ifBlank { "なし" }
}

private fun parseCompatExifIfd(
    bytes: ByteArray,
    start: Int,
    end: Int,
    relativeOffset: Int,
    littleEndian: Boolean,
    values: MutableMap<Int, String>,
    visitedIfds: MutableSet<Int>
) {
    if (relativeOffset < 0 || relativeOffset >= end - start || !visitedIfds.add(relativeOffset)) return
    val entryCountOffset = start + relativeOffset
    if (entryCountOffset + 2 > end) return
    val entryCount = readShort(bytes, entryCountOffset, littleEndian)
    repeat(entryCount) { index ->
        val entry = entryCountOffset + 2 + index * 12
        if (entry + 12 > end) return@repeat
        val tag = readShort(bytes, entry, littleEndian)
        val type = readShort(bytes, entry + 2, littleEndian)
        val count = readInt(bytes, entry + 4, littleEndian).toLong()
        val byteCount = exifTypeSize(type)?.let { it * count } ?: return@repeat
        if (count <= 0 || byteCount > Int.MAX_VALUE) return@repeat
        val valueOffset = if (byteCount <= 4L) entry + 8 else {
            val relative = readInt(bytes, entry + 8, littleEndian).toLong()
            if (relative < 0 || relative >= (end - start).toLong()) return@repeat
            start + relative.toInt()
        }
        if (
            valueOffset < start || valueOffset > end ||
            byteCount > (end - valueOffset).toLong()
        ) return@repeat
        val valueEnd = valueOffset + byteCount.toInt()
        val value = when (type) {
            2 -> bytes.copyOfRange(valueOffset, valueEnd)
                .decodeToString()
                .trimEnd('\u0000', ' ', '\n', '\r')
                .takeIf(String::isNotBlank)
            3 -> readShort(bytes, valueOffset, littleEndian).toString()
            4 -> readInt(bytes, valueOffset, littleEndian).toString()
            5 -> {
                val numerator = readInt(bytes, valueOffset, littleEndian).toLong()
                val denominator = readInt(bytes, valueOffset + 4, littleEndian).toLong()
                if (denominator != 0L) "${numerator.toDouble() / denominator.toDouble()}" else null
            }
            else -> null
        }
        if (value != null) values[tag] = value
    }
}

private fun formatCompatExifDecimal(raw: String): String =
    raw.toDoubleOrNull()?.let { value ->
        if (value % 1.0 == 0.0) value.toInt().toString()
        else value.toString().trimEnd('0').trimEnd('.')
    } ?: raw

private fun formatCompatExifExposure(raw: String): String =
    raw.toDoubleOrNull()?.let { value ->
        when {
            value >= 1.0 -> value.toInt().toString()
            value > 0.0 -> "1/${(1.0 / value).toInt()}"
            else -> raw
        }
    } ?: raw

private fun exifTypeSize(type: Int): Long? = when (type) {
    1, 2, 7 -> 1L
    3 -> 2L
    4, 9 -> 4L
    5, 10 -> 8L
    else -> null
}

private fun unsignedShort(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

private fun readShort(bytes: ByteArray, offset: Int, littleEndian: Boolean): Int {
    val first = bytes[offset].toInt() and 0xFF
    val second = bytes[offset + 1].toInt() and 0xFF
    return if (littleEndian) first or (second shl 8) else (first shl 8) or second
}

private fun readInt(bytes: ByteArray, offset: Int, littleEndian: Boolean): Int {
    val b0 = bytes[offset].toInt() and 0xFF
    val b1 = bytes[offset + 1].toInt() and 0xFF
    val b2 = bytes[offset + 2].toInt() and 0xFF
    val b3 = bytes[offset + 3].toInt() and 0xFF
    return if (littleEndian) {
        b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    } else {
        (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }
}

internal suspend fun fetchCompatRemoteMediaInfo(
    httpClient: HttpClient,
    url: String
): Result<CompatRemoteMediaInfo> = runSuspendCatchingPreservingCancellation {
    val response = httpClient.request(url) { method = HttpMethod.Head }
    check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
    CompatRemoteMediaInfo(
        contentType = response.headers[HttpHeaders.ContentType]?.substringBefore(';')?.trim(),
        contentLengthBytes = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.takeIf { it >= 0L }
    )
}

private const val COMPAT_PHASH_MAX_IMAGE_BYTES = 16L * 1024L * 1024L
private const val COMPAT_PHASH_CACHE_MAX_ENTRIES = 512
private const val COMPAT_PHASH_REQUEST_TIMEOUT_MILLIS = 3_000L
private const val COMPAT_PHASH_BATCH_TIMEOUT_MILLIS = 15_000L
private val compatPhashCacheMutex = Mutex()
private val compatPhashCache = LinkedHashMap<String, String>()
private val compatPhashRequestLocks = mutableMapOf<String, CompatPhashRequestLock>()

private class CompatPhashRequestLock(
    val mutex: Mutex = Mutex(),
    var holders: Int = 0
)

internal suspend fun fetchCompatImagePhash(
    httpClient: HttpClient,
    url: String
): Result<String> = runSuspendCatchingPreservingCancellation {
    compatPhashCacheMutex.withLock { compatPhashCache[url] }
        ?.let { return@runSuspendCatchingPreservingCancellation it }
    val requestLock = compatPhashCacheMutex.withLock {
        compatPhashRequestLocks.getOrPut(url) { CompatPhashRequestLock() }.also { it.holders += 1 }
    }
    try {
        requestLock.mutex.withLock {
            val cached = compatPhashCacheMutex.withLock { compatPhashCache[url] }
            if (cached != null) {
                cached
            } else {
                val bytes = withTimeout(COMPAT_MEDIA_INFO_TIMEOUT_MILLIS) {
                    withContext(AppDispatchers.io) {
                        val response = httpClient.get(url)
                        check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
                        val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                        require(contentLength == null || contentLength <= COMPAT_PHASH_MAX_IMAGE_BYTES) {
                            "画像が大きすぎます"
                        }
                        readBoundedHttpResponseBytes(
                            response = response,
                            maxBytes = COMPAT_PHASH_MAX_IMAGE_BYTES.toInt(),
                            totalTimeoutMillis = COMPAT_MEDIA_INFO_TIMEOUT_MILLIS
                        )
                    }
                }
                require(bytes.size.toLong() <= COMPAT_PHASH_MAX_IMAGE_BYTES) { "画像が大きすぎます" }
                val phash = requireNotNull(computeCompatImagePhashFromBytes(bytes)) { "画像をデコードできませんでした" }
                compatPhashCacheMutex.withLock {
                    compatPhashCache[url] = phash
                    while (compatPhashCache.size > COMPAT_PHASH_CACHE_MAX_ENTRIES) {
                        val iterator = compatPhashCache.entries.iterator()
                        if (iterator.hasNext()) {
                            iterator.next()
                            iterator.remove()
                        }
                    }
                }
                phash
            }
        }
    } finally {
        compatPhashCacheMutex.withLock {
            requestLock.holders -= 1
            if (requestLock.holders <= 0 && compatPhashRequestLocks[url] === requestLock) {
                compatPhashRequestLocks.remove(url)
            }
        }
    }
}

internal fun formatCompatMediaByteSize(bytes: Long?): String = when {
    bytes == null -> "不明"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB ($bytes bytes)"
    else -> {
        val unit = 1024L * 1024L
        val whole = bytes / unit
        val decimal = (bytes % unit) * 10L / unit
        "$whole.$decimal MB ($bytes bytes)"
    }
}
