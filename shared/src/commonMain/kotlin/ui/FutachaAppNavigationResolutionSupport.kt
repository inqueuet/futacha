package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.ui.board.RegisteredThreadNavigation

private val FUTACHA_NAVIGATION_THREAD_URL_REGEX = Regex("""/res/\d+\.html?""", RegexOption.IGNORE_CASE)

internal fun resolveHistoryEntrySelection(
    entry: ThreadHistoryEntry,
    boards: List<BoardSummary>
): FutachaThreadSelection? {
    val entryBoardUrlKey = historyBoardLookupKey(entry.boardUrl)
    val targetBoard = boards.firstOrNull { entry.boardId.isNotBlank() && it.id == entry.boardId }
        ?: entryBoardUrlKey?.let { key ->
            boards.firstOrNull { historyBoardLookupKey(it.url) == key }
        }
        ?: boards.firstOrNull { it.name == entry.boardName }

    return targetBoard?.let { board ->
        FutachaThreadSelection(
            boardId = board.id,
            boardName = board.name,
            threadId = entry.threadId,
            threadTitle = entry.title,
            threadReplies = entry.replyCount,
            threadThumbnailUrl = entry.titleImageUrl,
            threadUrl = entry.boardUrl.takeIf { url ->
                FUTACHA_NAVIGATION_THREAD_URL_REGEX.containsMatchIn(url)
            }
        )
    }
}

/**
 * Board identity used to match a history entry with a registered board. Both sides go
 * through the same normalization: thread URLs (`/b/res/1.htm`) and board pages
 * (`/b/futaba.php`) reduce to the board directory, and http/https are treated alike.
 */
internal fun historyBoardLookupKey(url: String): String? {
    val trimmed = url.trim().substringBefore('#').substringBefore('?')
    if (trimmed.isBlank()) return null
    val boardBase = runCatching { BoardUrlResolver.resolveBoardBaseUrl(trimmed) }.getOrNull()
        ?: return null
    return boardBase
        .lowercase()
        .substringAfter("://")
        .trimEnd('/')
        .takeIf { it.isNotBlank() }
}

internal fun resolveSavedThreadSelection(
    thread: SavedThread,
    boards: List<BoardSummary>
): FutachaThreadSelection? {
    val targetBoard = boards.firstOrNull { it.id == thread.boardId }
        ?: boards.firstOrNull { it.name == thread.boardName }
    val fallbackBoardId = targetBoard?.id
        ?: thread.boardId.trim().ifBlank { thread.boardName.trim() }
    if (fallbackBoardId.isBlank()) {
        return null
    }
    val fallbackBoardName = targetBoard?.name
        ?: thread.boardName.trim().ifBlank { fallbackBoardId }

    return FutachaThreadSelection(
        boardId = fallbackBoardId,
        boardName = fallbackBoardName,
        threadId = thread.threadId,
        threadTitle = thread.title,
        threadReplies = thread.postCount,
        threadThumbnailUrl = null,
        threadUrl = null,
        isSavedThreadsVisible = true
    )
}

internal fun shouldApplyRegisteredThreadNavigation(
    currentBoardId: String?,
    currentThreadId: String?,
    currentThreadUrl: String?,
    target: RegisteredThreadNavigation
): Boolean {
    return !(currentBoardId == target.board.id &&
        currentThreadId == target.threadId &&
        currentThreadUrl == target.threadUrl)
}

internal fun isSelectedBoardStillMissing(
    selectedBoardId: String?,
    missingBoardId: String,
    boards: List<BoardSummary>
): Boolean {
    return selectedBoardId == missingBoardId && boards.none { it.id == missingBoardId }
}

internal fun resolveMissingBoardRecoveryState(
    state: FutachaNavigationState,
    missingBoardId: String,
    boards: List<BoardSummary>
): FutachaNavigationState? {
    if (!isSelectedBoardStillMissing(state.selectedBoardId, missingBoardId, boards)) {
        return null
    }
    return clearFutachaThreadSelection(
        state = state,
        clearBoardSelection = true
    )
}
