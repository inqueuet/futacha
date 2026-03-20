package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.ui.board.resolveRegisteredThreadNavigation

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
                    selectSavedThread(currentNavigationState(), selection)
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
            val state = currentNavigationState()
            setNavigationState(
                clearFutachaThreadSelection(
                    state = state,
                    clearBoardSelection = state.isSavedThreadsVisible
                )
            )
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
