package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.ui.board.RegisteredThreadNavigation

internal fun resolveHistoryEntrySelection(
    entry: ThreadHistoryEntry,
    boards: List<BoardSummary>
): FutachaThreadSelection? {
    val entryBoardUrlKey = entry.boardUrl
        .trim()
        .substringBefore('?')
        .trimEnd('/')
        .lowercase()
    val targetBoard = boards.firstOrNull { entry.boardId.isNotBlank() && it.id == entry.boardId }
        ?: boards.firstOrNull {
            it.url.trim().substringBefore('?').trimEnd('/').lowercase() == entryBoardUrlKey
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
                Regex("""/res/\d+\.html?""", RegexOption.IGNORE_CASE).containsMatchIn(url)
            }
        )
    }
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
