package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.analytics.analyticsCountBucket
import com.valoser.futacha.shared.analytics.analyticsSessionContextId
import com.valoser.futacha.shared.analytics.analyticsTextHasUrl
import com.valoser.futacha.shared.analytics.analyticsTextLengthBucket
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import com.valoser.futacha.shared.model.ThreadMenuEntryId

internal data class ThreadHistoryDrawerCallbacks(
    val onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit,
    val onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    val onBoardClick: () -> Unit,
    val onRefreshClick: () -> Unit,
    val onBatchDeleteClick: () -> Unit,
    val onExportClick: () -> Unit = {},
    val onExportThenClearClick: () -> Unit = {},
    val onExportSelectedClick: (List<ThreadHistoryEntry>) -> Unit = {},
    val onLoadImportPreview: suspend () -> com.valoser.futacha.shared.ui.FutachaHistoryArchivePreview? = { null },
    val onImportClick: () -> Unit = {},
    val onImportSelectedClick: (Set<String>) -> Unit = {},
    val onSettingsClick: () -> Unit
)

internal fun buildThreadScreenHistoryDrawerCallbacks(
    onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit,
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    onBoardClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onBatchDeleteClick: () -> Unit,
    onExportClick: () -> Unit = {},
    onExportThenClearClick: () -> Unit = {},
    onExportSelectedClick: (List<ThreadHistoryEntry>) -> Unit = {},
    onLoadImportPreview: suspend () -> com.valoser.futacha.shared.ui.FutachaHistoryArchivePreview? = { null },
    onImportClick: () -> Unit = {},
    onImportSelectedClick: (Set<String>) -> Unit = {},
    onSettingsClick: () -> Unit
): ThreadHistoryDrawerCallbacks {
    return ThreadHistoryDrawerCallbacks(
        onHistoryEntryDismissed = onHistoryEntryDismissed,
        onHistoryEntrySelected = { entry ->
            AnalyticsTracker.event(
                "history_entry_selected",
                mapOf(
                    "source" to "thread",
                    "board_context" to analyticsSessionContextId("board", entry.boardId, entry.boardUrl),
                    "thread_context" to analyticsSessionContextId("thread", entry.boardUrl, entry.threadId),
                    "title_length_bucket" to analyticsTextLengthBucket(entry.title),
                    "reply_count_bucket" to analyticsCountBucket(entry.replyCount)
                )
            )
            onHistoryEntrySelected(entry)
        },
        onBoardClick = {
            AnalyticsTracker.event("history_drawer_action", mapOf("action" to "back_to_boards"))
            onBoardClick()
        },
        onRefreshClick = {
            AnalyticsTracker.event("history_drawer_action", mapOf("action" to "refresh"))
            onRefreshClick()
        },
        onBatchDeleteClick = {
            AnalyticsTracker.event("history_drawer_action", mapOf("action" to "clear"))
            onBatchDeleteClick()
        },
        onExportClick = {
            AnalyticsTracker.event("history_drawer_action", mapOf("action" to "export"))
            onExportClick()
        },
        onExportThenClearClick = {
            AnalyticsTracker.event("history_drawer_action", mapOf("action" to "export_then_clear"))
            onExportThenClearClick()
        },
        onExportSelectedClick = { entries ->
            AnalyticsTracker.event(
                "history_drawer_action",
                mapOf("action" to "export_selected", "selection_count_bucket" to analyticsCountBucket(entries.size))
            )
            onExportSelectedClick(entries)
        },
        onLoadImportPreview = {
            AnalyticsTracker.event("history_drawer_action", mapOf("action" to "import_preview"))
            onLoadImportPreview()
        },
        onImportClick = {
            AnalyticsTracker.event("history_drawer_action", mapOf("action" to "import"))
            onImportClick()
        },
        onImportSelectedClick = { snapshotIds ->
            AnalyticsTracker.event(
                "history_drawer_action",
                mapOf("action" to "import_selected", "selection_count_bucket" to analyticsCountBucket(snapshotIds.size))
            )
            onImportSelectedClick(snapshotIds)
        },
        onSettingsClick = {
            AnalyticsTracker.event("history_drawer_action", mapOf("action" to "settings"))
            onSettingsClick()
        }
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
    currentSearchQuery: () -> String = { "" },
    analyticsContext: Map<String, String> = emptyMap(),
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
            AnalyticsTracker.event(
                "thread_search_submitted",
                analyticsContext + mapOf(
                    "query_length_bucket" to analyticsTextLengthBucket(currentSearchQuery()),
                    "query_has_url" to analyticsTextHasUrl(currentSearchQuery()),
                    "match_count_bucket" to analyticsCountBucket(matches().size)
                )
            )
            applyNavigation(
                focusThreadSearchMatch(
                    currentIndex = currentIndex(),
                    matches = matches()
                )
            )
        },
        onSearchPrev = {
            AnalyticsTracker.event(
                "thread_search_navigation",
                analyticsContext + mapOf(
                    "direction" to "previous",
                    "match_count_bucket" to analyticsCountBucket(matches().size)
                )
            )
            applyNavigation(
                moveToPreviousThreadSearchMatch(
                    currentIndex = currentIndex(),
                    matches = matches()
                )
            )
        },
        onSearchNext = {
            AnalyticsTracker.event(
                "thread_search_navigation",
                analyticsContext + mapOf(
                    "direction" to "next",
                    "match_count_bucket" to analyticsCountBucket(matches().size)
                )
            )
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
    coroutineScope: CoroutineScope,
    onCloseDrawer: suspend () -> Unit,
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit
): (ThreadHistoryEntry) -> Unit {
    return { entry ->
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            onCloseDrawer()
            onHistoryEntrySelected(entry)
        }
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
    analyticsContext: Map<String, String> = emptyMap(),
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
        AnalyticsTracker.event(
            "thread_menu_action",
            analyticsContext + mapOf("action" to entryId.name.lowercase())
        )
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
