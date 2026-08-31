package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.util.hasEpochIntervalElapsed

internal const val HISTORY_SCROLL_OFFSET_WRITE_THRESHOLD_PX = 96
internal const val HISTORY_SCROLL_PERSIST_MIN_INTERVAL_MS = 15_000L
internal const val HISTORY_SCROLL_VISITED_UPDATE_INTERVAL_MS = 60_000L

internal fun shouldSkipHistoryScrollUpdate(
    existingEntry: ThreadHistoryEntry?,
    index: Int,
    offset: Int,
    nowMillis: Long,
    forcePersist: Boolean = false,
    postId: String? = existingEntry?.lastReadPostId
): Boolean {
    existingEntry ?: return false

    if (existingEntry.lastReadPostId != postId) return false

    if (
        existingEntry.lastReadItemIndex == index &&
        absoluteIntDistance(existingEntry.lastReadItemOffset, offset) < HISTORY_SCROLL_OFFSET_WRITE_THRESHOLD_PX.toLong()
    ) {
        return true
    }

    if (forcePersist) {
        return false
    }

    val indexDelta = absoluteIntDistance(existingEntry.lastReadItemIndex, index)
    val offsetDelta = absoluteIntDistance(existingEntry.lastReadItemOffset, offset)
    return !hasEpochIntervalElapsed(
        nowMillis,
        existingEntry.lastVisitedEpochMillis,
        HISTORY_SCROLL_PERSIST_MIN_INTERVAL_MS
    ) &&
        indexDelta <= 2L &&
        offsetDelta < (HISTORY_SCROLL_OFFSET_WRITE_THRESHOLD_PX * 8L)
}

internal fun applyHistoryScrollUpdate(
    entry: ThreadHistoryEntry,
    index: Int,
    offset: Int,
    postId: String?,
    nowMillis: Long
): ThreadHistoryEntry {
    val shouldUpdateVisitedAt =
        entry.lastReadItemIndex != index ||
            absoluteIntDistance(entry.lastReadItemOffset, offset) >= HISTORY_SCROLL_OFFSET_WRITE_THRESHOLD_PX.toLong() ||
            hasEpochIntervalElapsed(
                nowMillis,
                entry.lastVisitedEpochMillis,
                HISTORY_SCROLL_VISITED_UPDATE_INTERVAL_MS
            )

    return entry.copy(
        lastReadItemIndex = index,
        lastReadItemOffset = offset,
        lastReadPostId = postId,
        lastVisitedEpochMillis = if (shouldUpdateVisitedAt) {
            nowMillis
        } else {
            entry.lastVisitedEpochMillis
        }
    )
}

private fun absoluteIntDistance(left: Int, right: Int): Long {
    val difference = left.toLong() - right.toLong()
    return if (difference < 0L) -difference else difference
}

internal fun buildNewHistoryScrollEntry(
    threadId: String,
    index: Int,
    offset: Int,
    postId: String?,
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
        lastReadItemOffset = offset,
        lastReadPostId = postId
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
