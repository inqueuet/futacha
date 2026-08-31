package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.DrawerState
import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.analytics.analyticsCountBucket
import com.valoser.futacha.shared.analytics.analyticsSessionContextId
import com.valoser.futacha.shared.analytics.analyticsTextHasUrl
import com.valoser.futacha.shared.analytics.analyticsTextLengthBucket
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.CatalogNavEntryId
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.network.ArchiveSearchItem
import com.valoser.futacha.shared.state.AppStateStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

internal fun buildCatalogMockMenuMessage(action: CatalogMenuAction): String {
    return "${action.label} はモックでのみ動作です"
}

internal data class CatalogHistoryDrawerCallbacks(
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

internal fun buildCatalogHistoryDrawerCallbacks(
    coroutineScope: CoroutineScope,
    drawerState: DrawerState,
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    onBack: () -> Unit,
    onRefreshClick: () -> Unit,
    onHistoryExport: suspend () -> String,
    onHistoryExportThenClear: suspend () -> String = { "" },
    onHistoryExportSelected: suspend (List<ThreadHistoryEntry>) -> String = { "" },
    onHistoryLoadImportPreview: suspend () -> com.valoser.futacha.shared.ui.FutachaHistoryArchivePreview? = { null },
    onHistoryImport: suspend () -> String,
    onHistoryImportSelected: suspend (Set<String>) -> String = { "" },
    onHistoryCleared: () -> Unit,
    showSnackbar: suspend (String) -> Unit,
    onShowGlobalSettings: () -> Unit
): CatalogHistoryDrawerCallbacks {
    return CatalogHistoryDrawerCallbacks(
        onHistoryEntrySelected = { entry ->
            AnalyticsTracker.event(
                "history_entry_selected",
                historyEntryTouchContext(entry, source = "catalog")
            )
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                drawerState.close()
                onHistoryEntrySelected(entry)
            }
        },
        onBoardClick = {
            AnalyticsTracker.event("history_drawer_action", mapOf("action" to "back_to_boards"))
            coroutineScope.launch {
                drawerState.close()
                onBack()
            }
        },
        onRefreshClick = {
            AnalyticsTracker.event("history_drawer_action", mapOf("action" to "refresh"))
            onRefreshClick()
        },
        onExportClick = {
            AnalyticsTracker.event("history_drawer_action", mapOf("action" to "export"))
            coroutineScope.launch {
                try {
                    val message = onHistoryExport()
                    if (message.isNotBlank()) {
                        showSnackbar(message)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    showSnackbar(buildBoardManagementHistoryArchiveFailureMessage("エクスポート", e))
                }
            }
        },
        onExportThenClearClick = {
            AnalyticsTracker.event("history_drawer_action", mapOf("action" to "export_then_clear"))
            coroutineScope.launch {
                try {
                    val message = onHistoryExportThenClear()
                    if (message.isNotBlank()) {
                        showSnackbar(message)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    showSnackbar(buildBoardManagementHistoryArchiveFailureMessage("エクスポート後の削除", e))
                }
            }
        },
        onExportSelectedClick = { selectedEntries ->
            AnalyticsTracker.event(
                "history_drawer_action",
                mapOf(
                    "action" to "export_selected",
                    "selection_count_bucket" to analyticsCountBucket(selectedEntries.size)
                )
            )
            coroutineScope.launch {
                try {
                    val message = onHistoryExportSelected(selectedEntries)
                    if (message.isNotBlank()) {
                        showSnackbar(message)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    showSnackbar(buildBoardManagementHistoryArchiveFailureMessage("エクスポート", e))
                }
            }
        },
        onLoadImportPreview = {
            AnalyticsTracker.event("history_drawer_action", mapOf("action" to "import_preview"))
            onHistoryLoadImportPreview()
        },
        onImportClick = {
            AnalyticsTracker.event("history_drawer_action", mapOf("action" to "import"))
            coroutineScope.launch {
                try {
                    val message = onHistoryImport()
                    if (message.isNotBlank()) {
                        showSnackbar(message)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    showSnackbar(buildBoardManagementHistoryArchiveFailureMessage("インポート", e))
                }
            }
        },
        onImportSelectedClick = { selectedSnapshotIds ->
            AnalyticsTracker.event(
                "history_drawer_action",
                mapOf(
                    "action" to "import_selected",
                    "selection_count_bucket" to analyticsCountBucket(selectedSnapshotIds.size)
                )
            )
            coroutineScope.launch {
                try {
                    val message = onHistoryImportSelected(selectedSnapshotIds)
                    if (message.isNotBlank()) {
                        showSnackbar(message)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    showSnackbar(buildBoardManagementHistoryArchiveFailureMessage("インポート", e))
                }
            }
        },
        onBatchDeleteClick = {
            AnalyticsTracker.event("history_drawer_action", mapOf("action" to "clear"))
            coroutineScope.launch {
                onHistoryCleared()
                showSnackbar("履歴を一括削除しました")
                drawerState.close()
            }
        },
        onSettingsClick = {
            AnalyticsTracker.event("history_drawer_action", mapOf("action" to "settings"))
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                drawerState.close()
                onShowGlobalSettings()
            }
        }
    )
}

private fun historyEntryTouchContext(
    entry: ThreadHistoryEntry,
    source: String
): Map<String, String> = mapOf(
    "source" to source,
    "board_context" to analyticsSessionContextId("board", entry.boardId, entry.boardUrl),
    "thread_context" to analyticsSessionContextId("thread", entry.boardUrl, entry.threadId),
    "title_length_bucket" to analyticsTextLengthBucket(entry.title),
    "reply_count_bucket" to analyticsCountBucket(entry.replyCount)
)

internal data class CatalogTopBarCallbacks(
    val onSearchQueryChange: (String) -> Unit,
    val onSearchActiveChange: (Boolean) -> Unit,
    val onNavigationClick: () -> Unit,
    val onModeSelected: (CatalogMode) -> Unit,
    val onMenuAction: (CatalogMenuAction) -> Unit
)

internal fun buildCatalogTopBarCallbacks(
    coroutineScope: CoroutineScope,
    drawerState: DrawerState,
    setSearchQuery: (String) -> Unit,
    setSearchActive: (Boolean) -> Unit,
    persistCatalogMode: (CatalogMode) -> Unit,
    onShowGlobalSettings: () -> Unit,
    showSnackbar: suspend (String) -> Unit
): CatalogTopBarCallbacks {
    return CatalogTopBarCallbacks(
        onSearchQueryChange = { query ->
            AnalyticsTracker.event(
                "catalog_search_query_changed",
                mapOf(
                    "query_length_bucket" to analyticsTextLengthBucket(query),
                    "query_has_url" to analyticsTextHasUrl(query)
                )
            )
            setSearchQuery(query)
        },
        onSearchActiveChange = { active ->
            AnalyticsTracker.event(
                "catalog_search_state",
                mapOf("state" to if (active) "active" else "inactive")
            )
            setSearchActive(active)
            if (!active) {
                setSearchQuery("")
            }
        },
        onNavigationClick = {
            AnalyticsTracker.event("history_drawer_opened", mapOf("source" to "catalog"))
            coroutineScope.launch { drawerState.open() }
        },
        onModeSelected = { mode ->
            AnalyticsTracker.event("catalog_mode_selected", mapOf("mode" to mode.name.lowercase()))
            persistCatalogMode(mode)
        },
        onMenuAction = { action ->
            AnalyticsTracker.event("catalog_top_menu_action", mapOf("action" to action.name.lowercase()))
            if (action == CatalogMenuAction.Settings) {
                onShowGlobalSettings()
            } else {
                coroutineScope.launch {
                    showSnackbar(buildCatalogMockMenuMessage(action))
                }
            }
        }
    )
}

internal data class CatalogNavigationCallbacks(
    val onNavigate: (CatalogNavEntryId) -> Unit
)

internal fun buildCatalogNavigationCallbacks(
    lastUsedDeleteKey: String,
    currentCreateThreadPassword: () -> String,
    setCreateThreadPassword: (String) -> Unit,
    setShowCreateThreadDialog: (Boolean) -> Unit,
    scrollCatalogToTop: () -> Unit,
    performRefresh: () -> Unit,
    isPastThreadSearchNoticeHidden: () -> Boolean,
    setShowPastThreadSearchNoticeDialog: (Boolean) -> Unit,
    setShowPastThreadSearchDialog: (Boolean) -> Unit,
    setShowModeDialog: (Boolean) -> Unit,
    setShowSettingsMenu: (Boolean) -> Unit
): CatalogNavigationCallbacks {
    return CatalogNavigationCallbacks(
        onNavigate = { destination ->
            AnalyticsTracker.event("catalog_nav_action", mapOf("action" to destination.name.lowercase()))
            when (destination) {
                CatalogNavEntryId.CreateThread -> {
                    setCreateThreadPassword(
                        resolveCreateThreadDialogOpenPassword(
                            currentPassword = currentCreateThreadPassword(),
                            lastUsedDeleteKey = lastUsedDeleteKey
                        )
                    )
                    setShowCreateThreadDialog(true)
                }
                CatalogNavEntryId.ScrollToTop -> scrollCatalogToTop()
                CatalogNavEntryId.RefreshCatalog -> performRefresh()
                CatalogNavEntryId.PastThreadSearch -> {
                    if (isPastThreadSearchNoticeHidden()) {
                        setShowPastThreadSearchDialog(true)
                    } else {
                        setShowPastThreadSearchNoticeDialog(true)
                    }
                }
                CatalogNavEntryId.Mode -> setShowModeDialog(true)
                CatalogNavEntryId.Settings -> setShowSettingsMenu(true)
            }
        }
    )
}

internal data class CatalogPastThreadSearchNoticeCallbacks(
    val onDismiss: () -> Unit,
    val onContinue: (doNotShowAgain: Boolean) -> Unit
)

internal fun buildCatalogPastThreadSearchNoticeCallbacks(
    stateStore: AppStateStore?,
    coroutineScope: CoroutineScope,
    setShowPastThreadSearchNoticeDialog: (Boolean) -> Unit,
    setShowPastThreadSearchDialog: (Boolean) -> Unit
): CatalogPastThreadSearchNoticeCallbacks {
    return CatalogPastThreadSearchNoticeCallbacks(
        onDismiss = { setShowPastThreadSearchNoticeDialog(false) },
        onContinue = { doNotShowAgain ->
            setShowPastThreadSearchNoticeDialog(false)
            if (doNotShowAgain) {
                coroutineScope.launch {
                    stateStore?.setPastThreadSearchNoticeHidden(true)
                }
            }
            setShowPastThreadSearchDialog(true)
        }
    )
}

internal data class CatalogSettingsMenuCallbacks(
    val onDismiss: () -> Unit,
    val onAction: (CatalogSettingsMenuItem) -> Unit
)

internal fun buildCatalogSettingsMenuCallbacks(
    board: () -> BoardSummary?,
    catalogMode: () -> CatalogMode,
    urlLauncher: (String) -> Unit,
    stateStore: AppStateStore?,
    isPrivacyFilterEnabled: () -> Boolean,
    coroutineScope: CoroutineScope,
    scrollCatalogToTop: () -> Unit,
    setShowDisplayStyleDialog: (Boolean) -> Unit,
    setIsNgManagementVisible: (Boolean) -> Unit,
    setIsWatchWordsVisible: (Boolean) -> Unit,
    setShowSettingsMenu: (Boolean) -> Unit
): CatalogSettingsMenuCallbacks {
    return CatalogSettingsMenuCallbacks(
        onDismiss = { setShowSettingsMenu(false) },
        onAction = { menuItem ->
            AnalyticsTracker.event("catalog_settings_action", mapOf("action" to menuItem.name.lowercase()))
            val resolvedAction = resolveCatalogSettingsActionState(menuItem)
            if (resolvedAction.scrollToTop) {
                scrollCatalogToTop()
            }
            if (resolvedAction.showDisplayStyleDialog) {
                setShowDisplayStyleDialog(true)
            }
            if (resolvedAction.showNgManagement) {
                setIsNgManagementVisible(true)
            }
            if (resolvedAction.showWatchWords) {
                setIsWatchWordsVisible(true)
            }
            if (resolvedAction.openExternalApp) {
                board()?.let { currentBoard ->
                    val catalogUrl = buildCatalogExternalAppUrl(currentBoard.url, catalogMode())
                    urlLauncher(catalogUrl)
                }
            }
            if (resolvedAction.togglePrivacy) {
                val enabled = !isPrivacyFilterEnabled()
                AnalyticsTracker.event(
                    "preference_changed",
                    mapOf(
                        "preference" to "privacy_filter",
                        "value" to if (enabled) "enabled" else "disabled"
                    )
                )
                coroutineScope.launch {
                    stateStore?.setPrivacyFilterEnabled(enabled)
                }
            }
            if (resolvedAction.closeSettingsMenu) {
                setShowSettingsMenu(false)
            }
        }
    )
}

internal data class CatalogPastThreadSearchResultCallbacks(
    val onDismiss: () -> Unit,
    val onRetry: () -> Unit,
    val onItemSelected: (ArchiveSearchItem) -> Unit
)

internal fun buildCatalogPastThreadSearchResultCallbacks(
    currentPastSearchGeneration: () -> Long,
    currentPastSearchJob: () -> kotlinx.coroutines.Job?,
    setPastSearchGeneration: (Long) -> Unit,
    setPastSearchJob: (kotlinx.coroutines.Job?) -> Unit,
    setIsPastSearchSheetVisible: (Boolean) -> Unit,
    runPastThreadSearch: (String, com.valoser.futacha.shared.network.ArchiveSearchScope?) -> Boolean,
    currentArchiveSearchQuery: () -> String,
    currentLastArchiveSearchScope: () -> com.valoser.futacha.shared.network.ArchiveSearchScope?,
    onThreadSelected: (com.valoser.futacha.shared.model.CatalogItem) -> Unit
): CatalogPastThreadSearchResultCallbacks {
    return CatalogPastThreadSearchResultCallbacks(
        onDismiss = {
            val sheetState = dismissPastThreadSearchSheet(currentPastSearchGeneration())
            setPastSearchGeneration(sheetState.nextGeneration)
            currentPastSearchJob()?.cancel()
            if (sheetState.shouldClearRunningJob) {
                setPastSearchJob(null)
            }
            setIsPastSearchSheetVisible(sheetState.shouldShowSheet)
        },
        onRetry = {
            runPastThreadSearch(currentArchiveSearchQuery(), currentLastArchiveSearchScope())
        },
        onItemSelected = { item ->
            val selectionState = selectPastThreadSearchItem(currentPastSearchGeneration(), item)
            setPastSearchGeneration(selectionState.sheetState.nextGeneration)
            currentPastSearchJob()?.cancel()
            if (selectionState.sheetState.shouldClearRunningJob) {
                setPastSearchJob(null)
            }
            setIsPastSearchSheetVisible(selectionState.sheetState.shouldShowSheet)
            onThreadSelected(selectionState.selectedCatalogItem)
        }
    )
}
