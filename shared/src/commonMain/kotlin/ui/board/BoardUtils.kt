package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.util.resolveThreadTitle
import io.ktor.http.Url
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import kotlin.time.ExperimentalTime

/**
 * Format a timestamp as a human-readable date/time string
 */
@OptIn(ExperimentalTime::class)
fun formatLastVisited(epochMillis: Long): String {
    val instant = Instant.fromEpochMilliseconds(epochMillis)
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val monthValue = localDateTime.month.ordinal + 1
    return "${localDateTime.year}/${monthValue.toString().padStart(2, '0')}/${localDateTime.day.toString().padStart(2, '0')} " +
            "${localDateTime.hour.toString().padStart(2, '0')}:${localDateTime.minute.toString().padStart(2, '0')}"
}

/**
 * Build a ThreadHistoryEntry from a ThreadPage, updating existing entry if present
 */
@OptIn(ExperimentalTime::class)
fun buildHistoryEntryFromPage(
    page: ThreadPage,
    history: List<ThreadHistoryEntry>,
    threadId: String,
    threadTitle: String?,
    board: BoardSummary,
    overrideThreadUrl: String? = null
): ThreadHistoryEntry {
    val existingEntry = history.firstOrNull {
        isSameHistoryContext(it, threadId, board)
    }
    val firstPost = page.posts.firstOrNull()
    val candidateTitle = resolveThreadTitle(firstPost, threadTitle, existingEntry?.title)
    val resolvedImageUrl = existingEntry?.titleImageUrl?.takeIf { it.isNotBlank() }
        ?: page.posts.firstOrNull()?.thumbnailUrl.orEmpty()
    val resolvedBoardUrl = when {
        overrideThreadUrl.isNullOrBlank() -> board.url
        else -> overrideThreadUrl
    }
    val timestamp = Clock.System.now().toEpochMilliseconds()
    return existingEntry?.copy(
        title = candidateTitle,
        titleImageUrl = resolvedImageUrl,
        boardId = board.id,
        boardName = board.name,
        boardUrl = resolvedBoardUrl,
        replyCount = page.posts.size,
        lastVisitedEpochMillis = timestamp
    ) ?: ThreadHistoryEntry(
        threadId = threadId,
        boardId = board.id,
        title = candidateTitle,
        titleImageUrl = resolvedImageUrl,
        boardName = board.name,
        boardUrl = resolvedBoardUrl,
        lastVisitedEpochMillis = timestamp,
        replyCount = page.posts.size
    )
}

private fun isSameHistoryContext(entry: ThreadHistoryEntry, threadId: String, board: BoardSummary): Boolean {
    if (entry.threadId != threadId) return false
    if (entry.boardId.isNotBlank() && board.id.isNotBlank()) {
        return entry.boardId == board.id
    }
    return normalizeBoardUrlForIdentity(entry.boardUrl) == normalizeBoardUrlForIdentity(board.url)
}

private fun normalizeBoardUrlForIdentity(value: String): String {
    return value
        .trim()
        .substringBefore('?')
        .trimEnd('/')
        .lowercase()
}

/**
 * Resolve the effective board URL from a thread URL override or fallback board URL
 */
fun resolveEffectiveBoardUrl(threadUrlOverride: String?, fallbackBoardUrl: String): String {
    if (threadUrlOverride.isNullOrBlank()) return fallbackBoardUrl
    return runCatching {
        val url = Url(threadUrlOverride)
        val segments = url.encodedPath.split('/').filter { it.isNotBlank() }
        val boardSegments = segments.takeWhile { it.lowercase() != "res" }
        if (boardSegments.isEmpty()) return@runCatching fallbackBoardUrl
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
    }.getOrElse { fallbackBoardUrl }
}
