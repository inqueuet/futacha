package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadMenuEntryId

internal data class ThreadHistoryDrawerCallbacks(
    val onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit,
    val onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    val onBoardClick: () -> Unit,
    val onRefreshClick: () -> Unit,
    val onBatchDeleteClick: () -> Unit,
    val onSettingsClick: () -> Unit
)

internal fun buildThreadScreenHistoryDrawerCallbacks(
    onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit,
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    onBoardClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onBatchDeleteClick: () -> Unit,
    onSettingsClick: () -> Unit
): ThreadHistoryDrawerCallbacks {
    return ThreadHistoryDrawerCallbacks(
        onHistoryEntryDismissed = onHistoryEntryDismissed,
        onHistoryEntrySelected = onHistoryEntrySelected,
        onBoardClick = onBoardClick,
        onRefreshClick = onRefreshClick,
        onBatchDeleteClick = onBatchDeleteClick,
        onSettingsClick = onSettingsClick
    )
}

internal data class ThreadTopBarCallbacks(
    val onSearchQueryChange: (String) -> Unit,
    val onSearchPrev: () -> Unit,
    val onSearchNext: () -> Unit,
    val onSearchSubmit: () -> Unit,
    val onSearchClose: () -> Unit,
    val onBack: () -> Unit,
    val onOpenHistory: () -> Unit,
    val onSearch: () -> Unit,
    val onMenuSettings: () -> Unit
)

internal fun buildThreadScreenTopBarCallbacks(
    onSearchQueryChange: (String) -> Unit,
    onSearchPrev: () -> Unit,
    onSearchNext: () -> Unit,
    onSearchSubmit: () -> Unit,
    onSearchClose: () -> Unit,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onSearch: () -> Unit,
    onMenuSettings: () -> Unit
): ThreadTopBarCallbacks {
    return ThreadTopBarCallbacks(
        onSearchQueryChange = onSearchQueryChange,
        onSearchPrev = onSearchPrev,
        onSearchNext = onSearchNext,
        onSearchSubmit = onSearchSubmit,
        onSearchClose = onSearchClose,
        onBack = onBack,
        onOpenHistory = onOpenHistory,
        onSearch = onSearch,
        onMenuSettings = onMenuSettings
    )
}

internal data class ThreadSearchNavigationCallbacks(
    val onSearchSubmit: () -> Unit,
    val onSearchPrev: () -> Unit,
    val onSearchNext: () -> Unit
)

internal fun buildThreadScreenSearchNavigationCallbacks(
    currentIndex: () -> Int,
    setCurrentIndex: (Int) -> Unit,
    matches: () -> List<ThreadSearchMatch>,
    onScrollToPostIndex: (Int?) -> Unit
): ThreadSearchNavigationCallbacks {
    fun applyNavigation(navigationState: ThreadSearchNavigationState) {
        setCurrentIndex(navigationState.nextIndex)
        if (navigationState.shouldScroll) {
            onScrollToPostIndex(navigationState.targetPostIndex)
        }
    }
    return ThreadSearchNavigationCallbacks(
        onSearchSubmit = {
            applyNavigation(
                focusThreadSearchMatch(
                    currentIndex = currentIndex(),
                    matches = matches()
                )
            )
        },
        onSearchPrev = {
            applyNavigation(
                moveToPreviousThreadSearchMatch(
                    currentIndex = currentIndex(),
                    matches = matches()
                )
            )
        },
        onSearchNext = {
            applyNavigation(
                moveToNextThreadSearchMatch(
                    currentIndex = currentIndex(),
                    matches = matches()
                )
            )
        }
    )
}

internal data class ThreadActionBarCallbacks(
    val onAction: (ThreadMenuEntryId) -> Unit
)

internal fun buildThreadScreenActionBarCallbacks(
    onAction: (ThreadMenuEntryId) -> Unit
): ThreadActionBarCallbacks {
    return ThreadActionBarCallbacks(onAction = onAction)
}

internal fun buildThreadScreenHistorySelectionHandler(
    onCloseDrawer: () -> Unit,
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit
): (ThreadHistoryEntry) -> Unit {
    return { entry ->
        onCloseDrawer()
        onHistoryEntrySelected(entry)
    }
}

internal fun buildThreadScreenRefreshHandler(
    isRefreshing: () -> Boolean,
    currentFirstVisibleItemIndex: () -> Int,
    currentFirstVisibleItemOffset: () -> Int,
    onStartRefresh: (Int, Int) -> Unit
): () -> Unit {
    return refresh@{
        when (resolveThreadRefreshAvailability(isRefreshing())) {
            ThreadRefreshAvailability.Busy -> return@refresh
            ThreadRefreshAvailability.Ready -> {
                onStartRefresh(
                    currentFirstVisibleItemIndex(),
                    currentFirstVisibleItemOffset()
                )
            }
        }
    }
}

internal fun buildThreadScreenMenuEntryHandler(
    isRefreshing: () -> Boolean,
    onOpenReplyDialog: () -> Unit,
    onScrollTop: () -> Unit,
    onScrollBottom: () -> Unit,
    onShowRefreshBusyMessage: () -> Unit,
    onStartRefresh: () -> Unit,
    onOpenGallery: () -> Unit,
    onDelegateToSaveHandler: () -> Unit,
    onShowFilterSheet: () -> Unit,
    onShowSettingsSheet: () -> Unit,
    onClearNgHeaderPrefill: () -> Unit,
    onShowNgManagement: () -> Unit,
    onOpenExternalApp: () -> Unit,
    onShowReadAloudControls: () -> Unit,
    onTogglePrivacy: () -> Unit
): (ThreadMenuEntryId) -> Unit {
    return { entryId ->
        val actionState = resolveThreadMenuActionState(
            entryId = entryId,
            isRefreshing = isRefreshing()
        )
        if (actionState.applyReplyDeleteKeyAutofill || actionState.showReplyDialog) {
            onOpenReplyDialog()
        }
        when (actionState.scrollTarget) {
            ThreadScrollTarget.Top -> onScrollTop()
            ThreadScrollTarget.Bottom -> onScrollBottom()
            null -> Unit
        }
        if (actionState.showRefreshBusyMessage) {
            onShowRefreshBusyMessage()
        }
        if (actionState.startRefresh) {
            onStartRefresh()
        }
        if (actionState.showGallery) {
            onOpenGallery()
        }
        if (actionState.delegateToSaveHandler) {
            onDelegateToSaveHandler()
        }
        if (actionState.showFilterSheet) {
            onShowFilterSheet()
        }
        if (actionState.showSettingsSheet) {
            onShowSettingsSheet()
        }
        if (actionState.clearNgHeaderPrefill) {
            onClearNgHeaderPrefill()
        }
        if (actionState.showNgManagement) {
            onShowNgManagement()
        }
        if (actionState.openExternalApp) {
            onOpenExternalApp()
        }
        if (actionState.showReadAloudControls) {
            onShowReadAloudControls()
        }
        if (actionState.togglePrivacy) {
            onTogglePrivacy()
        }
    }
}
