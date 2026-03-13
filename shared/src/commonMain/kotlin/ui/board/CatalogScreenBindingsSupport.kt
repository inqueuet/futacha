package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.DrawerState
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.CatalogNavEntryId
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.network.ArchiveSearchItem
import com.valoser.futacha.shared.state.AppStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun buildCatalogMockMenuMessage(action: CatalogMenuAction): String {
    return "${action.label} はモックでのみ動作です"
}

internal data class CatalogHistoryDrawerCallbacks(
    val onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    val onBoardClick: () -> Unit,
    val onRefreshClick: () -> Unit,
    val onBatchDeleteClick: () -> Unit,
    val onSettingsClick: () -> Unit
)

internal fun buildCatalogHistoryDrawerCallbacks(
    coroutineScope: CoroutineScope,
    drawerState: DrawerState,
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    onBack: () -> Unit,
    onRefreshClick: () -> Unit,
    onHistoryCleared: () -> Unit,
    showSnackbar: suspend (String) -> Unit,
    onShowGlobalSettings: () -> Unit
): CatalogHistoryDrawerCallbacks {
    return CatalogHistoryDrawerCallbacks(
        onHistoryEntrySelected = { entry ->
            coroutineScope.launch { drawerState.close() }
            onHistoryEntrySelected(entry)
        },
        onBoardClick = {
            coroutineScope.launch {
                drawerState.close()
                onBack()
            }
        },
        onRefreshClick = onRefreshClick,
        onBatchDeleteClick = {
            coroutineScope.launch {
                onHistoryCleared()
                showSnackbar("履歴を一括削除しました")
                drawerState.close()
            }
        },
        onSettingsClick = onShowGlobalSettings
    )
}

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
        onSearchQueryChange = setSearchQuery,
        onSearchActiveChange = { active ->
            setSearchActive(active)
            if (!active) {
                setSearchQuery("")
            }
        },
        onNavigationClick = {
            coroutineScope.launch { drawerState.open() }
        },
        onModeSelected = persistCatalogMode,
        onMenuAction = { action ->
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
    setShowPastThreadSearchDialog: (Boolean) -> Unit,
    setShowModeDialog: (Boolean) -> Unit,
    setShowSettingsMenu: (Boolean) -> Unit
): CatalogNavigationCallbacks {
    return CatalogNavigationCallbacks(
        onNavigate = { destination ->
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
                CatalogNavEntryId.PastThreadSearch -> setShowPastThreadSearchDialog(true)
                CatalogNavEntryId.Mode -> setShowModeDialog(true)
                CatalogNavEntryId.Settings -> setShowSettingsMenu(true)
            }
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
                coroutineScope.launch {
                    stateStore?.setPrivacyFilterEnabled(!isPrivacyFilterEnabled())
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
