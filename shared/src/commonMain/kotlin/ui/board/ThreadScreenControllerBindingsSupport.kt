package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repo.BoardRepository
import kotlinx.coroutines.CoroutineScope

internal data class ThreadScreenControllerBindingsBundle(
    val actionExecutionBindings: ThreadScreenActionExecutionBindingsBundle,
    val interactionBindings: ThreadScreenInteractionBindingsBundle
)

internal fun buildThreadScreenControllerBindingsBundle(
    coroutineScope: CoroutineScope,
    currentActionInProgress: () -> Boolean,
    setActionInProgress: (Boolean) -> Unit,
    currentLastBusyNoticeAtMillis: () -> Long,
    setLastBusyNoticeAtMillis: (Long) -> Unit,
    busyNoticeIntervalMillis: Long,
    showMessage: (String) -> Unit,
    showOptionalMessage: (String?) -> Unit,
    onActionDebugLog: (String) -> Unit,
    onActionInfoLog: (String) -> Unit,
    onActionErrorLog: (String, Throwable) -> Unit,
    currentIsHistoryRefreshing: () -> Boolean,
    setIsHistoryRefreshing: (Boolean) -> Unit,
    onHistoryRefresh: suspend () -> Unit,
    showHistoryRefreshMessage: suspend (String) -> Unit,
    currentReadAloudState: () -> ThreadReadAloudRuntimeState,
    setReadAloudState: (ThreadReadAloudRuntimeState) -> Unit,
    scrollToReadAloudPostIndex: suspend (Int) -> Unit,
    speakReadAloudText: suspend (String) -> Unit,
    cancelActiveReadAloud: () -> Unit,
    currentReadAloudSegments: () -> List<ReadAloudSegment>,
    isRefreshing: () -> Boolean,
    onOpenReplyDialog: () -> Unit,
    onScrollTop: () -> Unit,
    onScrollBottom: () -> Unit,
    onShowRefreshBusyMessage: () -> Unit,
    onStartRefreshFromMenu: () -> Unit,
    onOpenGallery: () -> Unit,
    onDelegateToSaveHandler: () -> Unit,
    onShowFilterSheet: () -> Unit,
    onShowSettingsSheet: () -> Unit,
    onClearNgHeaderPrefill: () -> Unit,
    onShowNgManagement: () -> Unit,
    onOpenExternalApp: () -> Unit,
    onShowReadAloudControls: () -> Unit,
    onTogglePrivacy: () -> Unit,
    currentSearchIndex: () -> Int,
    setCurrentSearchIndex: (Int) -> Unit,
    currentSearchMatches: () -> List<ThreadSearchMatch>,
    onScrollToSearchMatchPostIndex: (Int?) -> Unit,
    onCloseDrawerAfterHistorySelection: () -> Unit,
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    currentOverlayState: () -> ThreadPostOverlayState,
    setOverlayState: (ThreadPostOverlayState) -> Unit,
    lastUsedDeleteKey: String,
    currentSaidaneLabel: (Post) -> String?,
    isSelfPost: (Post) -> Boolean,
    onSaidaneLabelUpdated: (Post, String) -> Unit,
    repository: BoardRepository,
    effectiveBoardUrl: String,
    threadId: String,
    currentFirstVisibleItemIndex: () -> Int,
    currentFirstVisibleItemOffset: () -> Int,
    onStartRefreshFromPull: (Int, Int) -> Unit,
    onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit,
    onBoardClick: () -> Unit,
    onHistoryBatchDeleteClick: () -> Unit,
    onHistorySettingsClick: () -> Unit
): ThreadScreenControllerBindingsBundle {
    val actionExecutionBindings = buildThreadScreenActionExecutionBindingsBundle(
        coroutineScope = coroutineScope,
        actionStateBindings = ThreadScreenActionStateBindings(
            currentActionInProgress = currentActionInProgress,
            setActionInProgress = setActionInProgress,
            currentLastBusyNoticeAtMillis = currentLastBusyNoticeAtMillis,
            setLastBusyNoticeAtMillis = setLastBusyNoticeAtMillis
        ),
        actionDependencies = ThreadScreenActionDependencies(
            busyNoticeIntervalMillis = busyNoticeIntervalMillis,
            showMessage = showMessage,
            onDebugLog = onActionDebugLog,
            onInfoLog = onActionInfoLog,
            onErrorLog = onActionErrorLog
        ),
        historyRefreshStateBindings = ThreadScreenHistoryRefreshStateBindings(
            currentIsHistoryRefreshing = currentIsHistoryRefreshing,
            setIsHistoryRefreshing = setIsHistoryRefreshing
        ),
        onHistoryRefresh = onHistoryRefresh,
        showHistoryRefreshMessage = showHistoryRefreshMessage,
        readAloudStateBindings = ThreadScreenReadAloudStateBindings(
            currentState = currentReadAloudState,
            setState = setReadAloudState
        ),
        readAloudCallbacks = ThreadScreenReadAloudCallbacks(
            showMessage = showMessage,
            showOptionalMessage = showOptionalMessage,
            scrollToPostIndex = scrollToReadAloudPostIndex,
            speakText = speakReadAloudText,
            cancelActiveReadAloud = cancelActiveReadAloud
        ),
        readAloudDependencies = ThreadScreenReadAloudDependencies(
            currentSegments = currentReadAloudSegments
        )
    )
    return ThreadScreenControllerBindingsBundle(
        actionExecutionBindings = actionExecutionBindings,
        interactionBindings = buildThreadScreenInteractionBindingsBundle(
            isRefreshing = isRefreshing,
            onOpenReplyDialog = onOpenReplyDialog,
            onScrollTop = onScrollTop,
            onScrollBottom = onScrollBottom,
            onShowRefreshBusyMessage = onShowRefreshBusyMessage,
            onStartRefreshFromMenu = onStartRefreshFromMenu,
            onOpenGallery = onOpenGallery,
            onDelegateToSaveHandler = onDelegateToSaveHandler,
            onShowFilterSheet = onShowFilterSheet,
            onShowSettingsSheet = onShowSettingsSheet,
            onClearNgHeaderPrefill = onClearNgHeaderPrefill,
            onShowNgManagement = onShowNgManagement,
            onOpenExternalApp = onOpenExternalApp,
            onShowReadAloudControls = onShowReadAloudControls,
            onTogglePrivacy = onTogglePrivacy,
            currentSearchIndex = currentSearchIndex,
            setCurrentSearchIndex = setCurrentSearchIndex,
            currentSearchMatches = currentSearchMatches,
            onScrollToSearchMatchPostIndex = onScrollToSearchMatchPostIndex,
            onCloseDrawerAfterHistorySelection = onCloseDrawerAfterHistorySelection,
            onHistoryEntrySelected = onHistoryEntrySelected,
            currentOverlayState = currentOverlayState,
            setOverlayState = setOverlayState,
            lastUsedDeleteKey = lastUsedDeleteKey,
            currentSaidaneLabel = currentSaidaneLabel,
            isSelfPost = isSelfPost,
            onShowOptionalMessage = showOptionalMessage,
            onSaidaneLabelUpdated = onSaidaneLabelUpdated,
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
                repository.voteSaidane(effectiveBoardUrl, threadId, post.id)
            },
            requestDeletion = { post ->
                repository.requestDeletion(
                    effectiveBoardUrl,
                    threadId,
                    post.id,
                    DEFAULT_DEL_REASON_CODE
                )
            },
            currentFirstVisibleItemIndex = currentFirstVisibleItemIndex,
            currentFirstVisibleItemOffset = currentFirstVisibleItemOffset,
            onStartRefreshFromPull = onStartRefreshFromPull,
            onHistoryEntryDismissed = onHistoryEntryDismissed,
            onBoardClick = onBoardClick,
            onHistoryRefreshClick = actionExecutionBindings.historyRefreshBindings.handleHistoryRefresh,
            onHistoryBatchDeleteClick = onHistoryBatchDeleteClick,
            onHistorySettingsClick = onHistorySettingsClick
        )
    )
}
