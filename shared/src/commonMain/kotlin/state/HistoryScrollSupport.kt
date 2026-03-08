package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlin.math.abs

internal const val HISTORY_SCROLL_OFFSET_WRITE_THRESHOLD_PX = 24
internal const val HISTORY_SCROLL_PERSIST_MIN_INTERVAL_MS = 2_000L
internal const val HISTORY_SCROLL_VISITED_UPDATE_INTERVAL_MS = 15_000L

internal fun shouldSkipHistoryScrollUpdate(
    existingEntry: ThreadHistoryEntry?,
    index: Int,
    offset: Int,
    nowMillis: Long
): Boolean {
    existingEntry ?: return false

    if (
        existingEntry.lastReadItemIndex == index &&
        abs(existingEntry.lastReadItemOffset - offset) < HISTORY_SCROLL_OFFSET_WRITE_THRESHOLD_PX
    ) {
        return true
    }

    val indexDelta = abs(existingEntry.lastReadItemIndex - index)
    val offsetDelta = abs(existingEntry.lastReadItemOffset - offset)
    return nowMillis - existingEntry.lastVisitedEpochMillis < HISTORY_SCROLL_PERSIST_MIN_INTERVAL_MS &&
        indexDelta <= 2 &&
        offsetDelta < (HISTORY_SCROLL_OFFSET_WRITE_THRESHOLD_PX * 8)
}

internal fun applyHistoryScrollUpdate(
    entry: ThreadHistoryEntry,
    index: Int,
    offset: Int,
    nowMillis: Long
): ThreadHistoryEntry {
    val shouldUpdateVisitedAt =
        entry.lastReadItemIndex != index ||
            abs(entry.lastReadItemOffset - offset) >= HISTORY_SCROLL_OFFSET_WRITE_THRESHOLD_PX ||
            nowMillis - entry.lastVisitedEpochMillis >= HISTORY_SCROLL_VISITED_UPDATE_INTERVAL_MS

    return entry.copy(
        lastReadItemIndex = index,
        lastReadItemOffset = offset,
        lastVisitedEpochMillis = if (shouldUpdateVisitedAt) {
            nowMillis
        } else {
            entry.lastVisitedEpochMillis
        }
    )
}

internal fun buildNewHistoryScrollEntry(
    threadId: String,
    index: Int,
    offset: Int,
    boardId: String,
    title: String,
    titleImageUrl: String,
    boardName: String,
    boardUrl: String,
    replyCount: Int,
    nowMillis: Long
): ThreadHistoryEntry {
    return ThreadHistoryEntry(
        threadId = threadId,
        boardId = boardId,
        title = title,
        titleImageUrl = titleImageUrl,
        boardName = boardName,
        boardUrl = boardUrl,
        lastVisitedEpochMillis = nowMillis,
        replyCount = replyCount,
        lastReadItemIndex = index,
        lastReadItemOffset = offset
    )
}

internal fun buildHistoryScrollJobKey(threadId: String, boardId: String, boardUrl: String): String {
    val normalizedBoardId = boardId.trim()
    if (normalizedBoardId.isNotBlank()) {
        return "$normalizedBoardId::${threadId.trim()}"
    }
    val normalizedBoardUrl = boardUrl.trimEnd('/')
    return if (normalizedBoardUrl.isNotBlank()) {
        "$normalizedBoardUrl::${threadId.trim()}"
    } else {
        threadId.trim()
    }
}
