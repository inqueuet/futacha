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

internal data class ThreadScreenMenuInteractionInputs(
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
    val onTogglePrivacy: () -> Unit
)

internal data class ThreadScreenSearchInteractionInputs(
    val currentSearchIndex: () -> Int,
    val setCurrentSearchIndex: (Int) -> Unit,
    val currentSearchMatches: () -> List<ThreadSearchMatch>,
    val onScrollToSearchMatchPostIndex: (Int?) -> Unit,
    val onCloseDrawerAfterHistorySelection: () -> Unit,
    val onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit
)

internal data class ThreadScreenRefreshInteractionInputs(
    val currentFirstVisibleItemIndex: () -> Int,
    val currentFirstVisibleItemOffset: () -> Int,
    val onStartRefreshFromPull: (Int, Int) -> Unit
)

internal data class ThreadScreenHistoryDrawerInputs(
    val onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit,
    val onBoardClick: () -> Unit,
    val onHistoryRefreshClick: () -> Unit,
    val onHistoryBatchDeleteClick: () -> Unit,
    val onHistorySettingsClick: () -> Unit
)

internal fun buildThreadScreenInteractionBindingsBundle(
    menuInputs: ThreadScreenMenuInteractionInputs,
    searchInputs: ThreadScreenSearchInteractionInputs,
    postActionInputs: ThreadScreenPostActionInputs,
    refreshInputs: ThreadScreenRefreshInteractionInputs,
    historyDrawerInputs: ThreadScreenHistoryDrawerInputs
): ThreadScreenInteractionBindingsBundle {
    val historySelectionHandler = buildThreadScreenHistorySelectionHandler(
        onCloseDrawer = searchInputs.onCloseDrawerAfterHistorySelection,
        onHistoryEntrySelected = searchInputs.onHistoryEntrySelected
    )
    return ThreadScreenInteractionBindingsBundle(
        menuEntryHandler = buildThreadScreenMenuEntryHandler(
            isRefreshing = menuInputs.isRefreshing,
            onOpenReplyDialog = menuInputs.onOpenReplyDialog,
            onScrollTop = menuInputs.onScrollTop,
            onScrollBottom = menuInputs.onScrollBottom,
            onShowRefreshBusyMessage = menuInputs.onShowRefreshBusyMessage,
            onStartRefresh = menuInputs.onStartRefreshFromMenu,
            onOpenGallery = menuInputs.onOpenGallery,
            onDelegateToSaveHandler = menuInputs.onDelegateToSaveHandler,
            onShowFilterSheet = menuInputs.onShowFilterSheet,
            onShowSettingsSheet = menuInputs.onShowSettingsSheet,
            onClearNgHeaderPrefill = menuInputs.onClearNgHeaderPrefill,
            onShowNgManagement = menuInputs.onShowNgManagement,
            onOpenExternalApp = menuInputs.onOpenExternalApp,
            onShowReadAloudControls = menuInputs.onShowReadAloudControls,
            onTogglePrivacy = menuInputs.onTogglePrivacy
        ),
        searchNavigationCallbacks = buildThreadScreenSearchNavigationCallbacks(
            currentIndex = searchInputs.currentSearchIndex,
            setCurrentIndex = searchInputs.setCurrentSearchIndex,
            matches = searchInputs.currentSearchMatches,
            onScrollToPostIndex = searchInputs.onScrollToSearchMatchPostIndex
        ),
        historySelectionHandler = historySelectionHandler,
        postActionHandlers = buildThreadScreenPostActionHandlers(postActionInputs),
        refreshHandler = buildThreadScreenRefreshHandler(
            isRefreshing = menuInputs.isRefreshing,
            currentFirstVisibleItemIndex = refreshInputs.currentFirstVisibleItemIndex,
            currentFirstVisibleItemOffset = refreshInputs.currentFirstVisibleItemOffset,
            onStartRefresh = refreshInputs.onStartRefreshFromPull
        ),
        historyDrawerCallbacks = buildThreadScreenHistoryDrawerCallbacks(
            onHistoryEntryDismissed = historyDrawerInputs.onHistoryEntryDismissed,
            onHistoryEntrySelected = historySelectionHandler,
            onBoardClick = historyDrawerInputs.onBoardClick,
            onRefreshClick = historyDrawerInputs.onHistoryRefreshClick,
            onBatchDeleteClick = historyDrawerInputs.onHistoryBatchDeleteClick,
            onSettingsClick = historyDrawerInputs.onHistorySettingsClick
        )
    )
}
