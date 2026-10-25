package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadStatus
import com.valoser.futacha.shared.network.BoardUrlResolver

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
        lastReadPostId = if (keepExistingReadState) {
            existing.lastReadPostId
        } else {
            incoming.lastReadPostId
        },
        threadStatus = incoming.threadStatus ?: existing.threadStatus,
        lastViewedSnapshot = listOfNotNull(existing.lastViewedSnapshot, incoming.lastViewedSnapshot)
            .maxByOrNull { it.viewedAtEpochMillis },
        hasAutoSave = existing.hasAutoSave || incoming.hasAutoSave,
        isAutoRefreshDisabled = existing.isAutoRefreshDisabled ||
            incoming.isAutoRefreshDisabled,
        // A background refresh writes from the row it read when it started, so a post
        // made meanwhile must not be taken back (L88): the latest own post wins.
        hasSelfPost = existing.hasSelfPost || incoming.hasSelfPost,
        lastSelfPostEpochMillis = listOfNotNull(
            existing.lastSelfPostEpochMillis,
            incoming.lastSelfPostEpochMillis
        ).maxOrNull()
    )
}

/**
 * Merges an entry read from a history archive into the entry already known (L89). The archive is a copy from some
 * earlier time or another device, so unlike a fresh board response it must not take back what is known here: a
 * thread's status only moves forward (落ち・隔離・削除・管理者削除 do not come undone), so every mark either side
 * knows is kept, and the later confirmation that the thread was alive is kept. Everything else merges as
 * [mergeAppStateHistoryEntry] does; an entry new to the device keeps the archive's values as they are.
 */
internal fun mergeImportedHistoryEntry(
    existing: ThreadHistoryEntry,
    imported: ThreadHistoryEntry
): ThreadHistoryEntry = mergeAppStateHistoryEntry(existing, imported).copy(
    threadStatus = mergeKnownThreadStatus(existing.threadStatus, imported.threadStatus),
    lastConfirmedAliveEpochMillis = listOfNotNull(
        existing.lastConfirmedAliveEpochMillis,
        imported.lastConfirmedAliveEpochMillis
    ).maxOrNull()
)

private fun mergeKnownThreadStatus(first: ThreadStatus?, second: ThreadStatus?): ThreadStatus? = when {
    first == null -> second
    second == null -> first
    else -> ThreadStatus(
        isDead = first.isDead || second.isDead,
        isIsolated = first.isIsolated || second.isIsolated,
        isDeleted = first.isDeleted || second.isDeleted,
        isAdminDeleted = first.isAdminDeleted || second.isAdminDeleted
    )
}

/**
 * Merges network/visit metadata without importing a stale reading point.
 * Scroll updates use updateHistoryScrollPosition* and are intentionally kept
 * out of this merge path.
 */
internal fun mergeAppStateHistoryMetadataEntry(
    existing: ThreadHistoryEntry,
    incoming: ThreadHistoryEntry
): ThreadHistoryEntry = mergeAppStateHistoryEntry(existing, incoming).copy(
    lastReadItemIndex = existing.lastReadItemIndex,
    lastReadItemOffset = existing.lastReadItemOffset,
    lastReadPostId = existing.lastReadPostId
)

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
    val candidate = boardUrl.trim()
    if (candidate.isBlank()) return ""
    return runCatching {
        BoardUrlResolver.resolveBoardBaseUrl(candidate)
    }.getOrDefault(candidate)
        .substringBefore('?')
        .trimEnd('/')
        .lowercase()
}
