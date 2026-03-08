package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry

private const val HISTORY_ENTRY_DELIMITER = "::"

internal fun mergeAppStateHistoryEntry(
    existing: ThreadHistoryEntry,
    incoming: ThreadHistoryEntry
): ThreadHistoryEntry {
    val keepExistingReadState = existing.lastVisitedEpochMillis >= incoming.lastVisitedEpochMillis
    val mergedLastVisited = maxOf(existing.lastVisitedEpochMillis, incoming.lastVisitedEpochMillis)
    val mergedReplyCount = maxOf(existing.replyCount, incoming.replyCount)

    return incoming.copy(
        boardId = incoming.boardId.ifBlank { existing.boardId },
        title = incoming.title.ifBlank { existing.title },
        titleImageUrl = incoming.titleImageUrl.ifBlank { existing.titleImageUrl },
        boardName = incoming.boardName.ifBlank { existing.boardName },
        boardUrl = incoming.boardUrl.ifBlank { existing.boardUrl },
        lastVisitedEpochMillis = mergedLastVisited,
        replyCount = mergedReplyCount,
        lastReadItemIndex = if (keepExistingReadState) {
            existing.lastReadItemIndex
        } else {
            incoming.lastReadItemIndex
        },
        lastReadItemOffset = if (keepExistingReadState) {
            existing.lastReadItemOffset
        } else {
            incoming.lastReadItemOffset
        },
        hasAutoSave = existing.hasAutoSave || incoming.hasAutoSave
    )
}

internal fun historyEntryIdentity(entry: ThreadHistoryEntry): String {
    return historyEntryIdentity(
        threadId = entry.threadId,
        boardId = entry.boardId,
        boardUrl = entry.boardUrl
    )
}

internal fun historyEntryIdentity(threadId: String, boardId: String, boardUrl: String): String {
    val normalizedThreadId = threadId.trim()
    if (normalizedThreadId.isBlank()) return ""
    val normalizedBoardId = boardId.trim()
    if (normalizedBoardId.isNotBlank()) {
        return "$normalizedBoardId$HISTORY_ENTRY_DELIMITER$normalizedThreadId"
    }
    val normalizedBoardUrl = normalizeHistoryBoardUrlForIdentity(boardUrl)
    if (normalizedBoardUrl.isNotBlank()) {
        return "$normalizedBoardUrl$HISTORY_ENTRY_DELIMITER$normalizedThreadId"
    }
    return normalizedThreadId
}

internal fun matchesHistoryEntryIdentity(
    entry: ThreadHistoryEntry,
    threadId: String,
    boardId: String,
    boardUrl: String
): Boolean {
    if (entry.threadId != threadId) return false
    val normalizedBoardId = boardId.trim()
    val entryBoardId = entry.boardId.trim()
    if (normalizedBoardId.isNotBlank() && entryBoardId.isNotBlank()) {
        return entryBoardId == normalizedBoardId
    }
    val normalizedBoardUrl = normalizeHistoryBoardUrlForIdentity(boardUrl)
    val entryBoardUrl = normalizeHistoryBoardUrlForIdentity(entry.boardUrl)
    if (normalizedBoardUrl.isNotBlank() && entryBoardUrl.isNotBlank()) {
        return entryBoardUrl == normalizedBoardUrl
    }
    return normalizedBoardId.isBlank() && entryBoardId.isBlank() &&
        normalizedBoardUrl.isBlank() && entryBoardUrl.isBlank()
}

internal fun normalizeHistoryBoardUrlForIdentity(boardUrl: String): String {
    val normalized = boardUrl
        .trim()
        .substringBefore('?')
        .trimEnd('/')
        .lowercase()
    if (normalized.isBlank()) return ""
    return normalized.substringBefore("/res/")
}
