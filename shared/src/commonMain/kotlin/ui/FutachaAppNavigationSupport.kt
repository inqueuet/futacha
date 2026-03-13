package com.valoser.futacha.shared.ui

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.ui.board.RegisteredThreadNavigation
import com.valoser.futacha.shared.ui.board.resolveRegisteredThreadNavigation
import kotlinx.coroutines.CancellationException

internal data class FutachaNavigationCallbacks(
    val onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    val onSavedThreadSelected: (SavedThread) -> Unit,
    val onCatalogThreadSelected: (
        threadId: String,
        title: String?,
        replies: Int?,
        thumbnailUrl: String?,
        threadUrl: String?
    ) -> Unit,
    val onSavedThreadsDismissed: () -> Unit,
    val onBoardSelectionCleared: () -> Unit,
    val onThreadDismissed: () -> Unit,
    val onRegisteredThreadUrlClick: (String) -> Boolean
)

internal data class FutachaThreadSelection(
    val boardId: String,
    val threadId: String,
    val threadTitle: String?,
    val threadReplies: Int?,
    val threadThumbnailUrl: String?,
    val threadUrl: String?,
    val isSavedThreadsVisible: Boolean = false
)

internal data class FutachaNavigationState(
    val selectedBoardId: String? = null,
    val selectedThreadId: String? = null,
    val selectedThreadTitle: String? = null,
    val selectedThreadReplies: Int? = null,
    val selectedThreadThumbnailUrl: String? = null,
    val selectedThreadUrl: String? = null,
    val isSavedThreadsVisible: Boolean = false
) {
    companion object {
        val Saver: Saver<FutachaNavigationState, Any> = listSaver(
            save = { state ->
                listOf(
                    state.selectedBoardId,
                    state.selectedThreadId,
                    state.selectedThreadTitle,
                    state.selectedThreadReplies,
                    state.selectedThreadThumbnailUrl,
                    state.selectedThreadUrl,
                    state.isSavedThreadsVisible
                )
            },
            restore = { restored ->
                FutachaNavigationState(
                    selectedBoardId = restored.getOrNull(0) as String?,
                    selectedThreadId = restored.getOrNull(1) as String?,
                    selectedThreadTitle = restored.getOrNull(2) as String?,
                    selectedThreadReplies = restored.getOrNull(3) as Int?,
                    selectedThreadThumbnailUrl = restored.getOrNull(4) as String?,
                    selectedThreadUrl = restored.getOrNull(5) as String?,
                    isSavedThreadsVisible = restored.getOrNull(6) as? Boolean ?: false
                )
            }
        )
    }
}

internal sealed interface FutachaDestination {
    data object SavedThreads : FutachaDestination
    data object BoardManagement : FutachaDestination
    data class MissingBoard(val missingBoardId: String) : FutachaDestination
    data class Catalog(val board: BoardSummary) : FutachaDestination
    data class Thread(val board: BoardSummary, val threadId: String) : FutachaDestination
}

internal fun clearFutachaThreadSelection(
    state: FutachaNavigationState,
    clearBoardSelection: Boolean = false
): FutachaNavigationState {
    return state.copy(
        selectedBoardId = if (clearBoardSelection) null else state.selectedBoardId,
        selectedThreadId = null,
        selectedThreadTitle = null,
        selectedThreadReplies = null,
        selectedThreadThumbnailUrl = null,
        selectedThreadUrl = null
    )
}

internal fun selectFutachaBoard(
    state: FutachaNavigationState,
    boardId: String
): FutachaNavigationState {
    return clearFutachaThreadSelection(
        state = state.copy(
            selectedBoardId = boardId,
            isSavedThreadsVisible = false
        ),
        clearBoardSelection = false
    )
}

internal fun applyFutachaThreadSelection(
    state: FutachaNavigationState,
    selection: FutachaThreadSelection
): FutachaNavigationState {
    return state.copy(
        selectedBoardId = selection.boardId,
        selectedThreadId = selection.threadId,
        selectedThreadTitle = selection.threadTitle,
        selectedThreadReplies = selection.threadReplies,
        selectedThreadThumbnailUrl = selection.threadThumbnailUrl,
        selectedThreadUrl = selection.threadUrl,
        isSavedThreadsVisible = selection.isSavedThreadsVisible
    )
}

internal fun selectCatalogThread(
    state: FutachaNavigationState,
    selection: FutachaThreadSelection
): FutachaNavigationState {
    return applyFutachaThreadSelection(
        state = state.copy(isSavedThreadsVisible = false),
        selection = selection.copy(isSavedThreadsVisible = false)
    )
}

internal fun selectCatalogThread(
    state: FutachaNavigationState,
    threadId: String,
    title: String?,
    replies: Int?,
    thumbnailUrl: String?,
    threadUrl: String?
): FutachaNavigationState {
    return state.copy(
        selectedThreadId = threadId,
        selectedThreadTitle = title,
        selectedThreadReplies = replies,
        selectedThreadThumbnailUrl = thumbnailUrl,
        selectedThreadUrl = threadUrl,
        isSavedThreadsVisible = false
    )
}

internal fun applyRegisteredThreadNavigation(
    state: FutachaNavigationState,
    target: RegisteredThreadNavigation
): FutachaNavigationState {
    return state.copy(
        selectedBoardId = target.board.id,
        selectedThreadId = target.threadId,
        selectedThreadTitle = null,
        selectedThreadReplies = null,
        selectedThreadThumbnailUrl = null,
        selectedThreadUrl = target.threadUrl,
        isSavedThreadsVisible = false
    )
}

internal fun dismissSavedThreads(state: FutachaNavigationState): FutachaNavigationState {
    return state.copy(isSavedThreadsVisible = false)
}

internal fun resolveFutachaDestination(
    navigationState: FutachaNavigationState,
    boards: List<BoardSummary>
): FutachaDestination {
    if (navigationState.selectedBoardId == null) {
        return if (navigationState.isSavedThreadsVisible) {
            FutachaDestination.SavedThreads
        } else {
            FutachaDestination.BoardManagement
        }
    }
    val selectedBoard = boards.firstOrNull { it.id == navigationState.selectedBoardId }
    if (selectedBoard == null) {
        return FutachaDestination.MissingBoard(navigationState.selectedBoardId)
    }
    return if (navigationState.selectedThreadId == null) {
        FutachaDestination.Catalog(selectedBoard)
    } else {
        FutachaDestination.Thread(
            board = selectedBoard,
            threadId = navigationState.selectedThreadId
        )
    }
}

internal fun buildFutachaNavigationCallbacks(
    currentBoards: () -> List<BoardSummary>,
    currentNavigationState: () -> FutachaNavigationState,
    setNavigationState: (FutachaNavigationState) -> Unit
): FutachaNavigationCallbacks {
    return FutachaNavigationCallbacks(
        onHistoryEntrySelected = { entry ->
            resolveHistoryEntrySelection(entry, currentBoards())?.let { selection ->
                setNavigationState(
                    applyFutachaThreadSelection(currentNavigationState(), selection)
                )
            }
        },
        onSavedThreadSelected = { thread ->
            resolveSavedThreadSelection(thread, currentBoards())?.let { selection ->
                setNavigationState(
                    selectCatalogThread(currentNavigationState(), selection)
                )
            }
        },
        onCatalogThreadSelected = { threadId, title, replies, thumbnailUrl, threadUrl ->
            setNavigationState(
                selectCatalogThread(
                    state = currentNavigationState(),
                    threadId = threadId,
                    title = title,
                    replies = replies,
                    thumbnailUrl = thumbnailUrl,
                    threadUrl = threadUrl
                )
            )
        },
        onSavedThreadsDismissed = {
            setNavigationState(dismissSavedThreads(currentNavigationState()))
        },
        onBoardSelectionCleared = {
            setNavigationState(
                clearFutachaThreadSelection(
                    state = currentNavigationState(),
                    clearBoardSelection = true
                )
            )
        },
        onThreadDismissed = {
            setNavigationState(clearFutachaThreadSelection(currentNavigationState()))
        },
        onRegisteredThreadUrlClick = { url ->
            val target = resolveRegisteredThreadNavigation(url, currentBoards())
            if (target == null) {
                false
            } else {
                val navigationState = currentNavigationState()
                if (
                    shouldApplyRegisteredThreadNavigation(
                        currentBoardId = navigationState.selectedBoardId,
                        currentThreadId = navigationState.selectedThreadId,
                        currentThreadUrl = navigationState.selectedThreadUrl,
                        target = target
                    )
                ) {
                    setNavigationState(applyRegisteredThreadNavigation(navigationState, target))
                }
                true
            }
        }
    )
}

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

    return targetBoard?.let { board ->
        FutachaThreadSelection(
            boardId = board.id,
            threadId = thread.threadId,
            threadTitle = thread.title,
            threadReplies = thread.postCount,
            threadThumbnailUrl = null,
            threadUrl = null,
            isSavedThreadsVisible = false
        )
    }
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
