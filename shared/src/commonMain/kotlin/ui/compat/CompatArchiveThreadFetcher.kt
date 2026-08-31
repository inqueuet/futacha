package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.media.FUTABA_COMPAT_MEDIA_EXTENSION_PATTERN
import com.valoser.futacha.shared.network.buildInqueuetArchiveThreadUrlFromUrl
import com.valoser.futacha.shared.network.readBoundedHttpResponseBytes
import com.valoser.futacha.shared.parser.ThreadHtmlParserCore
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.TextEncoding
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.coroutines.withContext

private const val COMPAT_ARCHIVE_MAX_HTML_BYTES = 20 * 1024 * 1024

private data class CompatArchiveResponse(
    val body: String,
    val contentPath: String?,
    val redirectPath: String?
)

private val ftbucketContentLinkRegex = Regex(
    """(?i)href\s*=\s*['\"]([^'\"]*cont/[^'\"]+/index\.htm(?:[?#][^'\"]*)?)['\"]"""
)
private val ftbucketMetaRedirectRegex = Regex(
    """(?i)(?:url|URL)\s*=\s*([^\s;\"']+)"""
)
private val compatArchiveApuViewSuffixRegex = Regex(
    """(?i)((?:fu|f)\d+\.(?:$FUTABA_COMPAT_MEDIA_EXTENSION_PATTERN))\s*\[見る](?=\s*(?:</a\s*>)|$)"""
)

internal fun normalizeCompatArchiveApuViewLabelHtml(messageHtml: String): String =
    compatArchiveApuViewSuffixRegex.replace(messageHtml) { match ->
        match.groupValues[1]
    }

/**
 * Some archive HTML adds a viewer-only `[見る]` suffix to an あぷ／あぷ小
 * filename. The reference app presents only the filename. Restrict the trim
 * to a media filename at the end of an anchor label (or text line) so normal
 * prose and unrelated links containing the same word are untouched.
 */
internal fun normalizeCompatArchiveApuViewLabels(page: ThreadPage): ThreadPage = page.copy(
    posts = page.posts.map { post ->
        post.copy(
            messageHtml = normalizeCompatArchiveApuViewLabelHtml(post.messageHtml)
        )
    }
)

/**
 * Archive candidates are intentionally ordered from the self-hosted archive to
 * the public mirrors.  A mirror is only contacted when the live response is
 * incomplete or unavailable.
 */
internal fun buildCompatArchiveThreadCandidates(sourceUrl: String): List<String> = buildList {
    buildInqueuetArchiveThreadUrlFromUrl(sourceUrl)?.let(::add)
    add(buildCompatFtbucketUrl(sourceUrl))
    buildCompatForestUrl(sourceUrl)?.let(::add)
    buildCompatFutapoUrl(sourceUrl)?.let(::add)
}.distinct()

/**
 * Fetches an archive URL and parses the resulting Futaba-compatible HTML.
 * FTBucket needs one extra request: the public endpoint first returns a
 * download page and then points at cont/.../index.htm.
 */
internal suspend fun fetchCompatArchiveThreadPage(
    httpClient: HttpClient,
    archiveUrl: String
): ThreadPage {
    var currentUrl = archiveUrl
    repeat(3) {
        // Reading a large archive response can allocate tens of megabytes. Keep both the
        // network/body read and the decode/regex work away from the Compose Main dispatcher.
        val archiveResponse = withContext(AppDispatchers.io) {
            val response = httpClient.get(currentUrl)
            check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
            val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            require(contentLength == null || contentLength <= COMPAT_ARCHIVE_MAX_HTML_BYTES) {
                "アーカイブ本文が大きすぎます"
            }
            val bytes = readBoundedHttpResponseBytes(response, COMPAT_ARCHIVE_MAX_HTML_BYTES)
            val contentType = response.headers[HttpHeaders.ContentType]
            withContext(AppDispatchers.parsing) {
                val body = TextEncoding.decodeToString(bytes, contentType)
                CompatArchiveResponse(
                    body = body,
                    contentPath = ftbucketContentLinkRegex.find(body)?.groupValues?.getOrNull(1),
                    redirectPath = ftbucketMetaRedirectRegex.find(body)?.groupValues?.getOrNull(1)
                )
            }
        }

        val contentPath = archiveResponse.contentPath
        if (contentPath != null) {
            currentUrl = resolveCompatArchiveRelativeUrl(currentUrl, contentPath)
            return@repeat
        }

        val redirectPath = archiveResponse.redirectPath
        if (redirectPath != null && redirectPath != currentUrl) {
            currentUrl = resolveCompatArchiveRelativeUrl(currentUrl, redirectPath)
            return@repeat
        }

        val parserBaseUrl = currentUrl.substringBeforeLast('/').trimEnd('/')
        val parserBody = withContext(AppDispatchers.parsing) {
            if (currentUrl.contains("ftbucket", ignoreCase = true)) {
                // The archived page keeps the original Futaba canonical link, but
                // its relative img/thumb files live beside the FTBucket capture.
                // Remove that canonical override so media stays in the archive.
                archiveResponse.body.replace(
                    Regex("""(?is)<link\b[^>]{0,1000}\brel\s*=\s*['\"]canonical['\"][^>]{0,1000}>"""),
                    ""
                )
            } else {
                archiveResponse.body
            }
        }
        val page = withContext(AppDispatchers.parsing) {
            normalizeCompatArchiveApuViewLabels(
                ThreadHtmlParserCore.parseThread(parserBody, parserBaseUrl)
            )
        }
        require(page.posts.isNotEmpty()) { "アーカイブ本文にレスがありません" }
        return page
    }
    error("アーカイブ本文のリンクを解決できませんでした")
}

/**
 * Merges a live/cache page with archive pages by post ID.  The live copy wins
 * for duplicate IDs, while archive-only responses fill gaps after a truncated
 * cache or a dead-thread response.  This keeps edits/deletion flags from an
 * active board response authoritative.
 */
internal fun mergeCompatThreadPages(
    primary: ThreadPage,
    supplements: List<ThreadPage>
): ThreadPage {
    if (supplements.isEmpty()) return primary

    val mergedById = LinkedHashMap<String, Post>()
    supplements.asSequence().flatMap { it.posts.asSequence() }.forEach { post ->
        if (!mergedById.containsKey(post.id)) mergedById[post.id] = post
    }
    primary.posts.forEach { post -> mergedById[post.id] = post }

    val mergedPosts = mergedById.values.sortedWith(
        compareBy<Post> { it.order ?: Int.MAX_VALUE }
            .thenBy { it.id.toLongOrNull() ?: Long.MAX_VALUE }
    )
    val archiveWasComplete = supplements.any { !it.isTruncated }
    return primary.copy(
        boardTitle = primary.boardTitle ?: supplements.firstNotNullOfOrNull { it.boardTitle },
        expiresAtLabel = primary.expiresAtLabel ?: supplements.firstNotNullOfOrNull { it.expiresAtLabel },
        deletedNotice = primary.deletedNotice ?: supplements.firstNotNullOfOrNull { it.deletedNotice },
        posts = mergedPosts,
        isTruncated = if (archiveWasComplete) false else primary.isTruncated,
        truncationReason = if (archiveWasComplete) null else primary.truncationReason
    )
}

internal fun resolveCompatArchiveRelativeUrl(baseUrl: String, rawPath: String): String {
    val base = Url(baseUrl)
    require(base.protocol.name == "http" || base.protocol.name == "https") {
        "アーカイブURLの形式が不正です"
    }
    if (rawPath.startsWith("http://") || rawPath.startsWith("https://")) {
        val target = Url(rawPath)
        require(
            target.protocol == base.protocol &&
                target.host.equals(base.host, ignoreCase = true) &&
                target.port == base.port
        ) {
            "アーカイブ応答が別の接続先を指しています"
        }
        return rawPath
    }
    val origin = buildString {
        append(base.protocol.name)
        append("://")
        append(base.host)
        if (base.port != base.protocol.defaultPort) append(":").append(base.port)
    }
    if (rawPath.startsWith("/")) return origin + rawPath
    val directory = base.encodedPath.substringBeforeLast('/', "").trim('/')
    return origin + "/" + listOf(directory, rawPath).filter(String::isNotBlank).joinToString("/")
}
