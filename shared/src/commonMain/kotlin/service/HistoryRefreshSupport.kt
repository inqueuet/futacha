package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.network.NetworkException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.Url
import kotlin.time.Clock

internal data class HistoryRefreshWindowSelection(
    val entries: List<ThreadHistoryEntry>,
    val nextCursor: Int
)

internal fun buildHistoryBoardKey(
    entry: ThreadHistoryEntry,
    boardById: Map<String, BoardSummary>,
    boardByBaseUrl: Map<String, BoardSummary>
): String {
    val board = resolveHistoryBoardForEntry(entry, boardById, boardByBaseUrl)
    return entry.boardId
        .takeIf { it.isNotBlank() }
        ?: board?.id?.takeIf { it.isNotBlank() }
        ?: entry.boardUrl.ifBlank { board?.url.orEmpty() }
}

internal fun resolveHistoryBoardForEntry(
    entry: ThreadHistoryEntry,
    boardById: Map<String, BoardSummary>,
    boardByBaseUrl: Map<String, BoardSummary>
): BoardSummary? {
    entry.boardId.takeIf { it.isNotBlank() }?.let { boardId ->
        boardById[boardId]?.let { return it }
    }
    val key = normalizeHistoryBoardKey(entry.boardUrl) ?: return null
    return boardByBaseUrl[key]
}

internal fun normalizeHistoryBoardKey(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val resolved = runCatching { BoardUrlResolver.resolveBoardBaseUrl(url) }.getOrDefault(url)
    return resolved.trimEnd('/').lowercase().ifBlank { null }
}

internal fun resolveArchiveBaseUrl(
    threadUrl: String,
    fallbackBoardUrl: String?
): String? {
    if (threadUrl.isBlank()) return fallbackBoardUrl?.takeIf { it.isNotBlank() }
    if (!threadUrl.contains("://")) {
        return fallbackBoardUrl?.takeIf { it.isNotBlank() }
    }
    return runCatching {
        val url = Url(threadUrl)
        val segments = url.encodedPath.split('/').filter { it.isNotBlank() }
        val boardSegments = segments.takeWhile { it.lowercase() != "res" }
        if (boardSegments.isEmpty()) {
            fallbackBoardUrl?.takeIf { it.isNotBlank() }
        } else {
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
        }
    }.getOrElse { fallbackBoardUrl?.takeIf { it.isNotBlank() } }
}

internal fun normalizeHistoryArchiveQuery(raw: String, maxLength: Int): String {
    if (maxLength <= 0) return ""
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val builder = StringBuilder(minOf(trimmed.length, maxLength))
    var previousWasWhitespace = false
    for (ch in trimmed) {
        val isWhitespace = ch.isWhitespace()
        if (isWhitespace) {
            if (!previousWasWhitespace && builder.isNotEmpty()) {
                builder.append(' ')
            }
        } else {
            builder.append(ch)
            if (builder.length >= maxLength) break
        }
        previousWasWhitespace = isWhitespace
    }
    return builder.toString().trim()
}

internal fun isHistoryRefreshAbortSignal(t: Throwable, reasonPrefix: String): Boolean {
    return t is NetworkException && t.message?.startsWith(reasonPrefix) == true
}

internal fun isHistoryRefreshNotFound(t: Throwable): Boolean {
    val status = (t as? ClientRequestException)?.response?.status?.value
        ?: (t as? NetworkException)?.statusCode
    return status == 404 || status == 410
}

internal fun buildHistoryRefreshError(
    totalThreads: Int,
    details: List<HistoryRefresher.ErrorDetail>
): HistoryRefresher.RefreshError {
    val stageCounts = linkedMapOf<String, Int>()
    details.forEach { detail ->
        stageCounts[detail.stage] = (stageCounts[detail.stage] ?: 0) + 1
    }
    return HistoryRefresher.RefreshError(
        errorCount = details.size,
        totalThreads = totalThreads,
        timestamp = Clock.System.now().toEpochMilliseconds(),
        errors = details.take(10),
        stageCounts = stageCounts
    )
}

internal fun selectHistoryRefreshWindow(
    history: List<ThreadHistoryEntry>,
    maxThreadsPerRun: Int?,
    cursor: Int
): HistoryRefreshWindowSelection {
    if (history.isEmpty()) {
        return HistoryRefreshWindowSelection(entries = emptyList(), nextCursor = 0)
    }

    val limit = maxThreadsPerRun?.coerceAtLeast(1) ?: history.size
    if (limit >= history.size) {
        return HistoryRefreshWindowSelection(entries = history, nextCursor = cursor)
    }

    val size = history.size
    val start = ((cursor % size) + size) % size
    val endExclusive = start + limit
    val nextCursor = endExclusive % size
    val entries = if (endExclusive <= size) {
        history.subList(start, endExclusive)
    } else {
        buildList(limit) {
            addAll(history.subList(start, size))
            addAll(history.subList(0, endExclusive - size))
        }
    }
    return HistoryRefreshWindowSelection(entries = entries, nextCursor = nextCursor)
}
