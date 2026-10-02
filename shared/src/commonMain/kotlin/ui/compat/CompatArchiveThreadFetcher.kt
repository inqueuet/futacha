package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.media.normalizeFutabaArchiveApuViewLabelHtml
import com.valoser.futacha.shared.network.buildInqueuetArchiveThreadUrlFromUrl
import com.valoser.futacha.shared.network.readBoundedHttpResponseBytes
import com.valoser.futacha.shared.parser.ThreadHtmlParserCore
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.TextEncoding
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
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
internal fun normalizeCompatArchiveApuViewLabelHtml(messageHtml: String): String =
    normalizeFutabaArchiveApuViewLabelHtml(messageHtml)

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
            // Streamed: get() buffered the whole (up to tens of MiB) page before the
            // bounded reader could apply its size limit and read timeout.
            val (bytes, contentType) = httpClient.prepareGet(currentUrl).execute { response ->
                check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
                val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                require(contentLength == null || contentLength <= COMPAT_ARCHIVE_MAX_HTML_BYTES) {
                    "アーカイブ本文が大きすぎます"
                }
                readBoundedHttpResponseBytes(response, COMPAT_ARCHIVE_MAX_HTML_BYTES) to
                    response.headers[HttpHeaders.ContentType]
            }
            withContext(AppDispatchers.parsing) {
                val body = TextEncoding.decodeToString(bytes, contentType)
                CompatArchiveResponse(
                    body = body,
                    contentPath = if (Url(currentUrl).host == "dev2.ftbucket.info" &&
                        !Url(currentUrl).encodedPath.contains("/cont/")) {
                        ftbucketContentLinkRegex.find(body)?.groupValues?.getOrNull(1)
                    } else null,
                    redirectPath = extractArchiveMetaRefresh(body)
                )
            }
        }

        val contentPath = archiveResponse.contentPath
        if (contentPath != null) {
            currentUrl = resolveCompatArchiveRelativeUrl(currentUrl, contentPath)
            return@repeat
        }

        // Compare the resolved target: a page that refreshes to itself through a
        // relative path ("./", "index.htm") is content, not a redirect.
        val redirectUrl = archiveResponse.redirectPath
            ?.let { resolveCompatArchiveRelativeUrl(currentUrl, it) }
        if (redirectUrl != null && !isSameCompatArchiveUrl(redirectUrl, currentUrl)) {
            currentUrl = redirectUrl
            return@repeat
        }

        val parserBaseUrl = currentUrl.substringBeforeLast('/').trimEnd('/')
        val parserBody = withContext(AppDispatchers.parsing) {
            if (Url(currentUrl).host in setOf("dev2.ftbucket.info", "kako.futakuro.com")) {
                // The archived page keeps the original Futaba canonical link, but
                // its relative img/thumb files live beside the FTBucket capture.
                // Remove that canonical override so media stays in the archive.
                archiveResponse.body.replace(
                    Regex("""(?is)<link\b[^>]{0,1000}\brel\s*=\s*['\"]canonical['\"][^>]{0,1000}>"""),
                    ""
                )
            } else if (Url(currentUrl).host == "futabaforest.net") {
                normalizeForestThreadHtml(archiveResponse.body)
            } else {
                archiveResponse.body
            }
        }
        val page = withContext(AppDispatchers.parsing) {
            normalizeCompatArchiveMedia(normalizeCompatArchiveApuViewLabels(
                ThreadHtmlParserCore.parseThread(parserBody, parserBaseUrl)
            ))
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

private fun isSameCompatArchiveUrl(first: String, second: String): Boolean {
    val a = runCatching { Url(first) }.getOrNull() ?: return first == second
    val b = runCatching { Url(second) }.getOrNull() ?: return first == second
    return a.protocol == b.protocol &&
        a.host.equals(b.host, ignoreCase = true) &&
        a.port == b.port &&
        normalizeCompatArchiveDotSegments(a.encodedPath) == normalizeCompatArchiveDotSegments(b.encodedPath) &&
        a.encodedQuery == b.encodedQuery
}

private fun normalizeCompatArchiveDotSegments(path: String): String {
    var normalized = path.ifEmpty { "/" }
    while (normalized.contains("/./")) normalized = normalized.replace("/./", "/")
    if (normalized.endsWith("/.")) normalized = normalized.dropLast(1)
    return normalized
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
