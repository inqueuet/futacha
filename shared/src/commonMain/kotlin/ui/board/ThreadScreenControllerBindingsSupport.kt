package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repo.BoardRepository
import kotlinx.coroutines.CoroutineScope

internal data class ThreadScreenControllerBindingsBundle(
    val actionExecutionBindings: ThreadScreenActionExecutionBindingsBundle,
    val interactionBindings: ThreadScreenInteractionBindingsBundle
)

internal data class ThreadScreenControllerActionInputs(
    val coroutineScope: CoroutineScope,
    val actionStateBindings: ThreadScreenActionStateBindings,
    val actionDependencies: ThreadScreenActionDependencies,
    val historyRefreshStateBindings: ThreadScreenHistoryRefreshStateBindings,
    val onHistoryRefresh: suspend () -> Unit,
    val showHistoryRefreshMessage: suspend (String) -> Unit,
    val readAloudStateBindings: ThreadScreenReadAloudStateBindings,
    val readAloudCallbacks: ThreadScreenReadAloudCallbacks,
    val readAloudDependencies: ThreadScreenReadAloudDependencies
)

internal data class ThreadScreenControllerInteractionInputs(
    val isRefreshing: () -> Boolean,
    val onOpenReplyDialog: () -> Unit,
    val onScrollTop: () -> Unit,
    val onScrollBottom: () -> Unit,
    val onShowRefreshBusyMessage: () -> Unit,
    val onStartRefreshFromMenu: () -> Unit,
    val onOpenGallery: () -> Unit,
    val onDelegateToSaveHandler: () -> Unit,
    val onShowFilterSheet: () -> Unit,
    val onShowSettingsSheet: () -> Unit,
    val onClearNgHeaderPrefill: () -> Unit,
    val onShowNgManagement: () -> Unit,
    val onOpenExternalApp: () -> Unit,
    val onShowReadAloudControls: () -> Unit,
    val onTogglePrivacy: () -> Unit,
    val currentSearchIndex: () -> Int,
    val setCurrentSearchIndex: (Int) -> Unit,
    val currentSearchMatches: () -> List<ThreadSearchMatch>,
    val onScrollToSearchMatchPostIndex: (Int?) -> Unit,
    val onCloseDrawerAfterHistorySelection: () -> Unit,
    val onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    val currentOverlayState: () -> ThreadPostOverlayState,
    val setOverlayState: (ThreadPostOverlayState) -> Unit,
    val lastUsedDeleteKey: String,
    val currentSaidaneLabel: (Post) -> String?,
    val isSelfPost: (Post) -> Boolean,
    val onSaidaneLabelUpdated: (Post, String) -> Unit,
    val repository: BoardRepository,
    val effectiveBoardUrl: String,
    val threadId: String,
    val currentFirstVisibleItemIndex: () -> Int,
    val currentFirstVisibleItemOffset: () -> Int,
    val onStartRefreshFromPull: (Int, Int) -> Unit,
    val onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit,
    val onBoardClick: () -> Unit,
    val onHistoryBatchDeleteClick: () -> Unit,
    val onHistorySettingsClick: () -> Unit
)

internal fun buildThreadScreenControllerBindingsBundle(
    actionInputs: ThreadScreenControllerActionInputs,
    interactionInputs: ThreadScreenControllerInteractionInputs
): ThreadScreenControllerBindingsBundle {
    val actionExecutionBindings = buildThreadScreenActionExecutionBindingsBundle(
        coroutineScope = actionInputs.coroutineScope,
        actionStateBindings = actionInputs.actionStateBindings,
        actionDependencies = actionInputs.actionDependencies,
        historyRefreshStateBindings = actionInputs.historyRefreshStateBindings,
        onHistoryRefresh = actionInputs.onHistoryRefresh,
        showHistoryRefreshMessage = actionInputs.showHistoryRefreshMessage,
        readAloudStateBindings = actionInputs.readAloudStateBindings,
        readAloudCallbacks = actionInputs.readAloudCallbacks,
        readAloudDependencies = actionInputs.readAloudDependencies
    )
    return ThreadScreenControllerBindingsBundle(
        actionExecutionBindings = actionExecutionBindings,
        interactionBindings = buildThreadScreenInteractionBindingsBundle(
            menuInputs = ThreadScreenMenuInteractionInputs(
                isRefreshing = interactionInputs.isRefreshing,
                onOpenReplyDialog = interactionInputs.onOpenReplyDialog,
                onScrollTop = interactionInputs.onScrollTop,
                onScrollBottom = interactionInputs.onScrollBottom,
                onShowRefreshBusyMessage = interactionInputs.onShowRefreshBusyMessage,
                onStartRefreshFromMenu = interactionInputs.onStartRefreshFromMenu,
                onOpenGallery = interactionInputs.onOpenGallery,
                onDelegateToSaveHandler = interactionInputs.onDelegateToSaveHandler,
                onShowFilterSheet = interactionInputs.onShowFilterSheet,
                onShowSettingsSheet = interactionInputs.onShowSettingsSheet,
                onClearNgHeaderPrefill = interactionInputs.onClearNgHeaderPrefill,
                onShowNgManagement = interactionInputs.onShowNgManagement,
                onOpenExternalApp = interactionInputs.onOpenExternalApp,
                onShowReadAloudControls = interactionInputs.onShowReadAloudControls,
                onTogglePrivacy = interactionInputs.onTogglePrivacy
            ),
            searchInputs = ThreadScreenSearchInteractionInputs(
                currentSearchIndex = interactionInputs.currentSearchIndex,
                setCurrentSearchIndex = interactionInputs.setCurrentSearchIndex,
                currentSearchMatches = interactionInputs.currentSearchMatches,
                onScrollToSearchMatchPostIndex = interactionInputs.onScrollToSearchMatchPostIndex,
                onCloseDrawerAfterHistorySelection = interactionInputs.onCloseDrawerAfterHistorySelection,
                onHistoryEntrySelected = interactionInputs.onHistoryEntrySelected
            ),
            postActionInputs = ThreadScreenPostActionInputs(
                currentOverlayState = interactionInputs.currentOverlayState,
                setOverlayState = interactionInputs.setOverlayState,
                lastUsedDeleteKey = interactionInputs.lastUsedDeleteKey,
                currentSaidaneLabel = interactionInputs.currentSaidaneLabel,
                isSelfPost = interactionInputs.isSelfPost,
                onShowOptionalMessage = actionInputs.readAloudCallbacks.showOptionalMessage,
                onSaidaneLabelUpdated = interactionInputs.onSaidaneLabelUpdated,
                launchUnitAction = { successMessage, failurePrefix, onSuccess, block ->
                    actionExecutionBindings.actionBindings.launch(
                        successMessage = successMessage,
                        failurePrefix = failurePrefix,
                        onSuccess = { _: Unit -> onSuccess() }
                    ) {
                        performThreadAction(block)
                    }
                },
                voteSaidane = { post ->
                    interactionInputs.repository.voteSaidane(
                        interactionInputs.effectiveBoardUrl,
                        interactionInputs.threadId,
                        post.id
                    )
                },
                requestDeletion = { post ->
                    interactionInputs.repository.requestDeletion(
                        interactionInputs.effectiveBoardUrl,
                        interactionInputs.threadId,
                        post.id,
                        DEFAULT_DEL_REASON_CODE
                    )
                }
            ),
            refreshInputs = ThreadScreenRefreshInteractionInputs(
                currentFirstVisibleItemIndex = interactionInputs.currentFirstVisibleItemIndex,
                currentFirstVisibleItemOffset = interactionInputs.currentFirstVisibleItemOffset,
                onStartRefreshFromPull = interactionInputs.onStartRefreshFromPull
            ),
            historyDrawerInputs = ThreadScreenHistoryDrawerInputs(
                onHistoryEntryDismissed = interactionInputs.onHistoryEntryDismissed,
                onBoardClick = interactionInputs.onBoardClick,
                onHistoryRefreshClick = actionExecutionBindings.historyRefreshBindings.handleHistoryRefresh,
                onHistoryBatchDeleteClick = interactionInputs.onHistoryBatchDeleteClick,
                onHistorySettingsClick = interactionInputs.onHistorySettingsClick
            )
        )
    )
}
