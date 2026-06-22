package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.model.BoardSummary
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

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
private val ARCHIVE_THREAD_RES_ID_REGEX = Regex("""/res/(\d+)\.html?""")

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
    val thumbUrl: String? = null
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
        val host = Url(url.trim()).host.trim().lowercase().trim('.')
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
    scope: ArchiveSearchScope?
): List<ArchiveSearchItem> {
    val normalized = query.trim()
    if (normalized.isBlank()) return emptyList()
    if (!normalized.all { it.isDigit() }) return emptyList()
    val scopedThreadUrl = scope?.let {
        "https://${it.server}.inqueuet.com/${it.board}/res/$normalized.htm"
    } ?: return emptyList()
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
    limit: Int = DEFAULT_ARCHIVE_SEARCH_LIMIT
): List<ArchiveSearchItem> {
    val normalized = query.trim()
    require(normalized.isNotBlank()) { "q required" }

    val directItems = buildDirectArchiveSearchItems(normalized, scope)
    if (directItems.isNotEmpty()) {
        return directItems
    }

    val safeLimit = limit.coerceIn(1, MAX_ARCHIVE_SEARCH_LIMIT)
    val hostServer = scope?.server
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: DEFAULT_ARCHIVE_SEARCH_SERVER
    val response = httpClient.get("https://$hostServer.inqueuet.com/search") {
        parameter("q", normalized)
        scope?.server?.trim()?.takeIf { it.isNotBlank() }?.let { parameter("server", it) }
        scope?.board?.trim()?.takeIf { it.isNotBlank() }?.let { parameter("board", it) }
        parameter("limit", safeLimit)
    }
    val body = response.bodyAsText()
    if (!response.status.isSuccess()) {
        val detail = if (body.isBlank()) "" else ": ${body.take(120)}"
        throw NetworkException("過去ログ検索に失敗しました (HTTP ${response.status.value}$detail)", response.status.value)
    }

    val decoded = archiveSearchJson.decodeFromString<InqueuetArchiveSearchResponse>(body)
    return decoded.results.mapNotNull { result ->
        result.toArchiveSearchItem(scope)
    }
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
    fallbackScope: ArchiveSearchScope?
): ArchiveSearchItem? {
    val resolvedThreadId = threadNo?.trim()?.takeIf { it.isNotBlank() }
        ?: threadId?.trim()?.takeIf { it.isNotBlank() }
        ?: id?.split('/')?.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }
        ?: archiveUrl?.let { ARCHIVE_THREAD_RES_ID_REGEX.find(it)?.groupValues?.getOrNull(1) }
        ?: htmlUrl?.let { ARCHIVE_THREAD_RES_ID_REGEX.find(it)?.groupValues?.getOrNull(1) }
        ?: return null
    val resolvedServer = server?.trim()?.takeIf { it.isNotBlank() }
        ?: id?.split('/')?.getOrNull(0)?.trim()?.takeIf { it.isNotBlank() }
        ?: fallbackScope?.server.orEmpty()
    val resolvedBoard = board?.trim()?.takeIf { it.isNotBlank() }
        ?: id?.split('/')?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        ?: fallbackScope?.board.orEmpty()
    val resolvedUrl = archiveUrl?.trim()?.takeIf { it.isNotBlank() }
        ?: htmlUrl?.trim()?.takeIf { it.isNotBlank() }
        ?: buildArchiveThreadUrlOrNull(resolvedServer, resolvedBoard, resolvedThreadId)
        ?: return null
    return ArchiveSearchItem(
        threadId = resolvedThreadId,
        server = resolvedServer,
        board = resolvedBoard,
        title = title,
        htmlUrl = resolvedUrl,
        thumbUrl = thumbUrl,
        replyCount = replyCount ?: replyCountLegacy ?: 0,
        status = status,
        totalBytes = totalBytes,
        savedAt = savedAt,
        uploadedAt = savedAt
    )
}

private fun buildArchiveThreadUrlOrNull(
    server: String,
    board: String,
    threadId: String
): String? {
    if (server.isBlank() || board.isBlank() || threadId.isBlank()) return null
    return "https://$server.inqueuet.com/$board/res/$threadId.htm"
}
