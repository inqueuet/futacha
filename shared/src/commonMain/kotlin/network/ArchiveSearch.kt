package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.model.BoardSummary
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

data class ArchiveSearchScope(val server: String, val board: String)

data class ArchiveSearchItem(
    val threadId: String,
    val server: String,
    val board: String,
    val title: String? = null,
    val htmlUrl: String,
    val thumbUrl: String? = null,
    val replyCount: Int = 0,
    val status: String? = null,
    val totalBytes: Long? = null,
    val savedAt: Long? = null,
    val createdAt: Long? = null,
    val finalizedAt: Long? = null,
    val uploadedAt: Long? = null
)

private const val FUTABA_HOST_SUFFIX = ".2chan.net"
private const val INQUEUET_HOST_SUFFIX = ".inqueuet.com"
private const val DEFAULT_ARCHIVE_SEARCH_SERVER = "may"
private const val DEFAULT_ARCHIVE_SEARCH_LIMIT = 20
private const val MAX_ARCHIVE_SEARCH_LIMIT = 100
private const val ARCHIVE_THUMBNAIL_HEAD_MAX_LINES = 80
private const val ARCHIVE_THUMBNAIL_HEAD_MAX_BYTES = 96 * 1024
private const val ARCHIVE_THUMBNAIL_READ_BUFFER_BYTES = 8 * 1024
private const val ARCHIVE_THUMBNAIL_MAX_ZERO_READ_RETRIES = 80
private const val ARCHIVE_THUMBNAIL_ZERO_READ_BACKOFF_MILLIS = 25L
private const val ARCHIVE_THUMBNAIL_RESPONSE_TIMEOUT_MILLIS = 10_000L
private const val ARCHIVE_SEARCH_PROBE_CONCURRENCY = 4
private const val ARCHIVE_SEARCH_RESPONSE_MAX_BYTES = 2 * 1024 * 1024
private const val ARCHIVE_SEARCH_QUERY_MAX_CHARS = 512
private const val ARCHIVE_RESULT_URL_MAX_CHARS = 8 * 1024
private const val ARCHIVE_RESULT_ID_MAX_CHARS = 256
private const val ARCHIVE_RESULT_TITLE_MAX_CHARS = 512
private const val ARCHIVE_RESULT_STATUS_MAX_CHARS = 64
private val ARCHIVE_THREAD_RES_ID_REGEX = Regex("""/res/(\d+)\.html?""")
private val ARCHIVE_THREAD_ID_REGEX = Regex("""\d{1,20}""")
private val ARCHIVE_SERVER_REGEX = Regex("""[A-Za-z0-9-]{1,63}""")
private val ARCHIVE_BOARD_REGEX = Regex("""[A-Za-z0-9_-]{1,80}""")
private val ARCHIVE_THUMB_IMG_REGEX = Regex(
    """<img\b[^>]{0,700}\bsrc\s*=\s*['"]([^'"]*/thumb/[^'"]+)['"][^>]{0,700}>""",
    RegexOption.IGNORE_CASE
)

private data class ArchiveThreadProbe(
    val statusCode: Int?,
    val thumbnailUrl: String?
)

@Serializable
private data class InqueuetArchiveSearchResponse(
    val q: String? = null,
    val server: String? = null,
    val board: String? = null,
    val limit: Int? = null,
    val count: Int? = null,
    val results: List<InqueuetArchiveSearchResult> = emptyList()
)

@Serializable
private data class InqueuetArchiveSearchResult(
    val id: String? = null,
    val server: String? = null,
    val board: String? = null,
    @SerialName("thread_no")
    val threadNo: String? = null,
    val threadId: String? = null,
    @SerialName("reply_count")
    val replyCount: Int? = null,
    @SerialName("replyCount")
    val replyCountLegacy: Int? = null,
    val status: String? = null,
    @SerialName("total_bytes")
    val totalBytes: Long? = null,
    @SerialName("saved_at")
    val savedAt: Long? = null,
    val title: String? = null,
    @SerialName("archive_url")
    val archiveUrl: String? = null,
    val htmlUrl: String? = null,
    val thumbUrl: String? = null,
    @SerialName("thumb_url")
    val thumbUrlSnake: String? = null,
    val thumbnailUrl: String? = null,
    @SerialName("thumbnail_url")
    val thumbnailUrlSnake: String? = null
)

fun extractArchiveSearchScope(board: BoardSummary?): ArchiveSearchScope? {
    return extractArchiveSearchScope(board?.url)
}

fun extractArchiveSearchScope(boardUrl: String?): ArchiveSearchScope? {
    if (boardUrl.isNullOrBlank()) return null
    return runCatching {
        if (!boardUrl.contains("://")) return null
        val normalizedUrl = resolveBaseUrlFromThreadUrl(boardUrl) ?: boardUrl
        val baseUrl = BoardUrlResolver.resolveBoardBaseUrl(normalizedUrl)
        val parsed = Url(baseUrl)
        if (parsed.host.isBlank()) return null
        val server = parsed.host.substringBefore('.', parsed.host).ifBlank { return null }
        val boardSlug = BoardUrlResolver.resolveBoardSlug(normalizedUrl).ifBlank { return null }
        ArchiveSearchScope(server = server, board = boardSlug)
    }.getOrNull()
}

fun buildInqueuetArchiveUrl(sourceUrl: String): String? {
    if (sourceUrl.isBlank() || !sourceUrl.contains("://")) return null
    return runCatching {
        val url = Url(sourceUrl.trim())
        if (url.protocol.name !in setOf("http", "https")) return null
        val server = resolveInqueuetArchiveServer(url.host) ?: return null
        val encodedPath = url.encodedPath.takeIf { it.isNotBlank() } ?: return null
        "https://$server.inqueuet.com$encodedPath"
    }.getOrNull()
}

fun buildInqueuetArchiveThreadUrlFromUrl(sourceUrl: String): String? {
    val archiveUrl = buildInqueuetArchiveUrl(sourceUrl) ?: return null
    return if (ARCHIVE_THREAD_RES_ID_REGEX.containsMatchIn(archiveUrl)) archiveUrl else null
}

fun isInqueuetArchiveUrl(url: String): Boolean {
    if (url.isBlank() || !url.contains("://")) return false
    return runCatching {
        val parsed = Url(url.trim())
        if (parsed.protocol.name !in setOf("http", "https")) return false
        val host = parsed.host.trim().lowercase().trim('.')
        host == "inqueuet.com" || host.endsWith(INQUEUET_HOST_SUFFIX)
    }.getOrDefault(false)
}

fun buildInqueuetArchiveThreadUrl(
    boardUrl: String,
    threadId: String
): String? {
    val sourceThreadUrl = runCatching {
        BoardUrlResolver.resolveThreadUrl(boardUrl, threadId)
    }.getOrNull() ?: return null
    return buildInqueuetArchiveUrl(sourceThreadUrl)
}

fun buildDirectArchiveSearchItems(
    query: String,
    scope: ArchiveSearchScope?,
    archiveBaseUrl: String? = null
): List<ArchiveSearchItem> {
    val normalized = query.trim()
    if (normalized.isBlank()) return emptyList()
    if (!ARCHIVE_THREAD_ID_REGEX.matches(normalized)) return emptyList()
    val board = scope?.board ?: return emptyList()
    val base = archiveBaseUrl?.trim()?.trimEnd('/')
        ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
       ?: ("https://" + scope.server + ".inqueuet.com")
    val scopedThreadUrl = "$base/$board/res/$normalized.htm"
    return listOf(
        ArchiveSearchItem(
            threadId = normalized,
            server = scope.server,
            board = scope.board,
            title = "No.$normalized",
            htmlUrl = scopedThreadUrl
        )
    )
}

suspend fun searchInqueuetArchiveThreads(
    httpClient: HttpClient,
    archiveSearchJson: Json,
    query: String,
    scope: ArchiveSearchScope?,
    limit: Int = DEFAULT_ARCHIVE_SEARCH_LIMIT,
    archiveBaseUrl: String? = null
): List<ArchiveSearchItem> {
    val normalized = query.trim()
    require(normalized.isNotBlank()) { "q required" }
    require(normalized.length <= ARCHIVE_SEARCH_QUERY_MAX_CHARS) {
        "q exceeds $ARCHIVE_SEARCH_QUERY_MAX_CHARS characters"
    }

    val directItems = buildDirectArchiveSearchItems(normalized, scope, archiveBaseUrl)
    if (directItems.isNotEmpty()) {
        return enrichAvailableArchiveSearchItems(httpClient, directItems)
    }

    val safeLimit = limit.coerceIn(1, MAX_ARCHIVE_SEARCH_LIMIT)
    val hostServer = scope?.server
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: DEFAULT_ARCHIVE_SEARCH_SERVER
    val searchBase = archiveBaseUrl?.trim()?.trimEnd('/')
        ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        ?: "https://$hostServer.inqueuet.com"
    val response = httpClient.get("$searchBase/search") {
        parameter("q", normalized)
        scope?.server?.trim()?.takeIf { it.isNotBlank() }?.let { parameter("server", it) }
        scope?.board?.trim()?.takeIf { it.isNotBlank() }?.let { parameter("board", it) }
        parameter("limit", safeLimit)
    }
    val body = readBoundedHttpResponseText(response, ARCHIVE_SEARCH_RESPONSE_MAX_BYTES)
    if (!response.status.isSuccess()) {
        val detail = if (body.isBlank()) "" else ": ${body.take(120)}"
        throw NetworkException("過去ログ検索に失敗しました (HTTP ${response.status.value}$detail)", response.status.value)
    }

    val decoded = archiveSearchJson.decodeFromString<InqueuetArchiveSearchResponse>(body)
    // The remote endpoint is not trusted to honor the requested limit. Bound
    // the decoded collection before launching availability probes so a large
    // response cannot create thousands of suspended coroutines at once.
    val items = decoded.results.asSequence()
        .take(safeLimit)
        .mapNotNull { result -> result.toArchiveSearchItem(scope, searchBase) }
        .toList()
    return enrichAvailableArchiveSearchItems(httpClient, items)
}

private fun resolveInqueuetArchiveServer(host: String): String? {
    val normalizedHost = host.trim().lowercase().trim('.')
    val server = when {
        normalizedHost.endsWith(FUTABA_HOST_SUFFIX) ->
            normalizedHost.removeSuffix(FUTABA_HOST_SUFFIX)
        normalizedHost.endsWith(INQUEUET_HOST_SUFFIX) ->
            normalizedHost.removeSuffix(INQUEUET_HOST_SUFFIX)
        else -> null
    }?.takeIf { it.isNotBlank() && !it.contains('.') }
    return server
}

private fun resolveBaseUrlFromThreadUrl(threadUrl: String): String? {
    return runCatching {
        val url = Url(threadUrl)
        val segments = url.encodedPath.split('/').filter { it.isNotBlank() }
        val boardSegments = segments.takeWhile { it.lowercase() != "res" }
        if (boardSegments.isEmpty()) return null
        val path = "/" + boardSegments.joinToString("/")
        buildString {
            append(url.protocol.name)
            append("://")
            append(url.host)
            if (url.port != url.protocol.defaultPort) {
                append(":${url.port}")
            }
            append(path.trimEnd('/'))
        }
    }.getOrNull()
}

private fun InqueuetArchiveSearchResult.toArchiveSearchItem(
    fallbackScope: ArchiveSearchScope?,
    archiveBaseUrl: String
): ArchiveSearchItem? {
    val boundedIdParts = id
        ?.takeIf { it.length <= ARCHIVE_RESULT_ID_MAX_CHARS }
        ?.split('/')
    val resolvedThreadId = sequenceOf(
        threadNo,
        threadId,
        boundedIdParts?.getOrNull(2),
        archiveUrl?.takeIf { it.length <= ARCHIVE_RESULT_URL_MAX_CHARS }
            ?.let { ARCHIVE_THREAD_RES_ID_REGEX.find(it)?.groupValues?.getOrNull(1) },
        htmlUrl?.takeIf { it.length <= ARCHIVE_RESULT_URL_MAX_CHARS }
            ?.let { ARCHIVE_THREAD_RES_ID_REGEX.find(it)?.groupValues?.getOrNull(1) }
    ).mapNotNull { candidate ->
        candidate?.trim()?.takeIf(ARCHIVE_THREAD_ID_REGEX::matches)
    }.firstOrNull()
        ?: return null
    val resolvedServer = sequenceOf(server, boundedIdParts?.getOrNull(0), fallbackScope?.server)
        .mapNotNull { candidate -> candidate?.trim()?.takeIf(ARCHIVE_SERVER_REGEX::matches) }
        .firstOrNull()
        ?: return null
    val resolvedBoard = sequenceOf(board, boundedIdParts?.getOrNull(1), fallbackScope?.board)
        .mapNotNull { candidate -> candidate?.trim()?.takeIf(ARCHIVE_BOARD_REGEX::matches) }
        .firstOrNull()
        ?: return null
    val rawResolvedUrl = sequenceOf(archiveUrl, htmlUrl)
        .mapNotNull { candidate ->
            candidate
                ?.takeIf { it.length <= ARCHIVE_RESULT_URL_MAX_CHARS }
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
        .firstOrNull()
        ?: buildArchiveThreadUrlOrNull(resolvedServer, resolvedBoard, resolvedThreadId, archiveBaseUrl)
        ?: return null
    val resolvedUrl = normalizeArchivePageUrl(
        candidate = rawResolvedUrl,
        expectedThreadId = resolvedThreadId,
        archiveBaseUrl = archiveBaseUrl
    ) ?: return null
    val resolvedThumbUrl = listOf(thumbUrl, thumbUrlSnake, thumbnailUrl, thumbnailUrlSnake)
        .firstNotNullOfOrNull { candidate ->
            candidate
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { normalizeArchiveResourceUrl(it, resolvedUrl) }
    return ArchiveSearchItem(
        threadId = resolvedThreadId,
        server = resolvedServer,
        board = resolvedBoard,
        title = title?.take(ARCHIVE_RESULT_TITLE_MAX_CHARS),
        htmlUrl = resolvedUrl,
        thumbUrl = resolvedThumbUrl,
        replyCount = (replyCount ?: replyCountLegacy ?: 0).coerceAtLeast(0),
        status = status?.take(ARCHIVE_RESULT_STATUS_MAX_CHARS),
        totalBytes = totalBytes?.coerceAtLeast(0L),
        savedAt = savedAt,
        uploadedAt = savedAt
    )
}

private fun buildArchiveThreadUrlOrNull(
    server: String,
    board: String,
    threadId: String,
    archiveBaseUrl: String = "https://$server.inqueuet.com"
): String? {
    if (server.isBlank() || board.isBlank() || threadId.isBlank()) return null
    val base = archiveBaseUrl.trim().trimEnd('/')
        .takeIf { it.startsWith("http://") || it.startsWith("https://") }
        ?: "https://$server.inqueuet.com"
    return "$base/$board/res/$threadId.htm"
}

private fun normalizeArchivePageUrl(
    candidate: String,
    expectedThreadId: String,
    archiveBaseUrl: String
): String? {
    if (candidate.length > ARCHIVE_RESULT_URL_MAX_CHARS) return null
    return runCatching {
        val parsed = Url(candidate)
        if (parsed.protocol.name !in setOf("http", "https")) return null
        val host = parsed.host.trim().lowercase().trim('.')
        val baseHost = Url(archiveBaseUrl).host.trim().lowercase().trim('.')
        val isAllowedHost = host == baseHost ||
            host == "inqueuet.com" ||
            host.endsWith(INQUEUET_HOST_SUFFIX)
        if (!isAllowedHost) return null
        val urlThreadId = ARCHIVE_THREAD_RES_ID_REGEX
            .find(parsed.encodedPath)
            ?.groupValues
            ?.getOrNull(1)
        if (urlThreadId != expectedThreadId) return null
        parsed.toString()
    }.getOrNull()
}

private fun normalizeArchiveResourceUrl(
    resourceUrl: String,
    pageUrl: String
): String? {
    if (resourceUrl.length > ARCHIVE_RESULT_URL_MAX_CHARS) return null
    val absolute = when {
        resourceUrl.startsWith("http://") || resourceUrl.startsWith("https://") -> resourceUrl
        resourceUrl.startsWith("//") -> "https:$resourceUrl"
        else -> runCatching {
            val page = Url(pageUrl)
            val origin = buildString {
                append(page.protocol.name)
                append("://")
                append(page.host)
                if (page.port != page.protocol.defaultPort) {
                    append(":${page.port}")
                }
            }
            if (resourceUrl.startsWith("/")) {
                "$origin$resourceUrl"
            } else {
                val basePath = page.encodedPath.substringBeforeLast('/', "")
                "$origin$basePath/$resourceUrl"
            }
        }.getOrNull()
    } ?: return null
    return runCatching {
        val resource = Url(absolute)
        val page = Url(pageUrl)
        if (resource.protocol.name !in setOf("http", "https")) return null
        val host = resource.host.trim().lowercase().trim('.')
        val pageHost = page.host.trim().lowercase().trim('.')
        if (host != pageHost && host != "inqueuet.com" && !host.endsWith(INQUEUET_HOST_SUFFIX)) {
            return null
        }
        resource.toString()
    }.getOrNull()
}

private suspend fun enrichAvailableArchiveSearchItems(
    httpClient: HttpClient,
    items: List<ArchiveSearchItem>
): List<ArchiveSearchItem> = coroutineScope {
    val semaphore = Semaphore(ARCHIVE_SEARCH_PROBE_CONCURRENCY)
    items.map { item ->
        async {
            semaphore.withPermit {
                val probe = fetchArchiveThreadProbe(httpClient, item.htmlUrl)
                if (isMissingArchiveThreadStatus(probe.statusCode)) {
                    null
                } else {
                    val resolvedThumbnail = item.thumbUrl?.takeIf { it.isNotBlank() }
                        ?: probe.thumbnailUrl
                    if (resolvedThumbnail.isNullOrBlank()) {
                        item
                    } else {
                        item.copy(thumbUrl = resolvedThumbnail)
                    }
                }
            }
        }
    }.awaitAll().filterNotNull()
}

private fun isMissingArchiveThreadStatus(statusCode: Int?): Boolean {
    return statusCode == 404 || statusCode == 410
}

private suspend fun fetchArchiveThreadProbe(
    httpClient: HttpClient,
    threadUrl: String
): ArchiveThreadProbe {
    if (threadUrl.isBlank()) return ArchiveThreadProbe(statusCode = null, thumbnailUrl = null)
    var response: HttpResponse? = null
    return try {
        response = httpClient.get(threadUrl) {
            headers[HttpHeaders.Referrer] = threadUrl.substringBeforeLast('/', threadUrl)
        }
        val statusCode = response.status.value
        if (!response.status.isSuccess()) {
            return ArchiveThreadProbe(statusCode = statusCode, thumbnailUrl = null)
        }
        val headHtml = readHttpBoardApiResponseHeadAsString(
            response = response,
            maxLines = ARCHIVE_THUMBNAIL_HEAD_MAX_LINES,
            maxBytes = ARCHIVE_THUMBNAIL_HEAD_MAX_BYTES,
            responseReadBufferBytes = ARCHIVE_THUMBNAIL_READ_BUFFER_BYTES,
            maxZeroReadRetries = ARCHIVE_THUMBNAIL_MAX_ZERO_READ_RETRIES,
            zeroReadBackoffMillis = ARCHIVE_THUMBNAIL_ZERO_READ_BACKOFF_MILLIS,
            responseTotalTimeoutMillis = ARCHIVE_THUMBNAIL_RESPONSE_TIMEOUT_MILLIS
        )
        val thumbnailUrl = ARCHIVE_THUMB_IMG_REGEX.find(headHtml)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { normalizeArchiveResourceUrl(it, threadUrl) }
        ArchiveThreadProbe(statusCode = statusCode, thumbnailUrl = thumbnailUrl)
    } catch (e: CancellationException) {
        throw e
    } catch (e: ResponseException) {
        ArchiveThreadProbe(statusCode = e.response.status.value, thumbnailUrl = null)
    } catch (e: NetworkException) {
        ArchiveThreadProbe(statusCode = e.statusCode, thumbnailUrl = null)
    } catch (_: Throwable) {
        ArchiveThreadProbe(statusCode = null, thumbnailUrl = null)
    } finally {
        response?.let { runCatching { it.bodyAsChannel().cancel(null) } }
    }
}
