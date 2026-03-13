package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadMenuEntryId

internal data class ThreadScreenInteractionBindingsBundle(
    val menuEntryHandler: (ThreadMenuEntryId) -> Unit,
    val searchNavigationCallbacks: ThreadSearchNavigationCallbacks,
    val historySelectionHandler: (ThreadHistoryEntry) -> Unit,
    val postActionHandlers: ThreadPostActionHandlers,
    val refreshHandler: () -> Unit,
    val historyDrawerCallbacks: ThreadHistoryDrawerCallbacks
)

internal fun buildThreadScreenInteractionBindingsBundle(
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
    onShowOptionalMessage: (String?) -> Unit,
    onSaidaneLabelUpdated: (Post, String) -> Unit,
    launchUnitAction: (
        successMessage: String,
        failurePrefix: String,
        onSuccess: () -> Unit,
        block: suspend () -> Unit
    ) -> Unit,
    voteSaidane: suspend (Post) -> Unit,
    requestDeletion: suspend (Post) -> Unit,
    currentFirstVisibleItemIndex: () -> Int,
    currentFirstVisibleItemOffset: () -> Int,
    onStartRefreshFromPull: (Int, Int) -> Unit,
    onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit,
    onBoardClick: () -> Unit,
    onHistoryRefreshClick: () -> Unit,
    onHistoryBatchDeleteClick: () -> Unit,
    onHistorySettingsClick: () -> Unit
): ThreadScreenInteractionBindingsBundle {
    return ThreadScreenInteractionBindingsBundle(
        menuEntryHandler = buildThreadScreenMenuEntryHandler(
            isRefreshing = isRefreshing,
            onOpenReplyDialog = onOpenReplyDialog,
            onScrollTop = onScrollTop,
            onScrollBottom = onScrollBottom,
            onShowRefreshBusyMessage = onShowRefreshBusyMessage,
            onStartRefresh = onStartRefreshFromMenu,
            onOpenGallery = onOpenGallery,
            onDelegateToSaveHandler = onDelegateToSaveHandler,
            onShowFilterSheet = onShowFilterSheet,
            onShowSettingsSheet = onShowSettingsSheet,
            onClearNgHeaderPrefill = onClearNgHeaderPrefill,
            onShowNgManagement = onShowNgManagement,
            onOpenExternalApp = onOpenExternalApp,
            onShowReadAloudControls = onShowReadAloudControls,
            onTogglePrivacy = onTogglePrivacy
        ),
        searchNavigationCallbacks = buildThreadScreenSearchNavigationCallbacks(
            currentIndex = currentSearchIndex,
            setCurrentIndex = setCurrentSearchIndex,
            matches = currentSearchMatches,
            onScrollToPostIndex = onScrollToSearchMatchPostIndex
        ),
        historySelectionHandler = buildThreadScreenHistorySelectionHandler(
            onCloseDrawer = onCloseDrawerAfterHistorySelection,
            onHistoryEntrySelected = onHistoryEntrySelected
        ),
        postActionHandlers = buildThreadScreenPostActionHandlers(
            currentOverlayState = currentOverlayState,
            setOverlayState = setOverlayState,
            lastUsedDeleteKey = lastUsedDeleteKey,
            currentSaidaneLabel = currentSaidaneLabel,
            isSelfPost = isSelfPost,
            onShowOptionalMessage = onShowOptionalMessage,
            onSaidaneLabelUpdated = onSaidaneLabelUpdated,
            launchUnitAction = launchUnitAction,
            voteSaidane = voteSaidane,
            requestDeletion = requestDeletion
        ),
        refreshHandler = buildThreadScreenRefreshHandler(
            isRefreshing = isRefreshing,
            currentFirstVisibleItemIndex = currentFirstVisibleItemIndex,
            currentFirstVisibleItemOffset = currentFirstVisibleItemOffset,
            onStartRefresh = onStartRefreshFromPull
        ),
        historyDrawerCallbacks = buildThreadScreenHistoryDrawerCallbacks(
            onHistoryEntryDismissed = onHistoryEntryDismissed,
            onHistoryEntrySelected = buildThreadScreenHistorySelectionHandler(
                onCloseDrawer = onCloseDrawerAfterHistorySelection,
                onHistoryEntrySelected = onHistoryEntrySelected
            ),
            onBoardClick = onBoardClick,
            onRefreshClick = onHistoryRefreshClick,
            onBatchDeleteClick = onHistoryBatchDeleteClick,
            onSettingsClick = onHistorySettingsClick
        )
    )
}
