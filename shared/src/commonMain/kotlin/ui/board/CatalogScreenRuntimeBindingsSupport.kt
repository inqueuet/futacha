package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.DrawerState
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.network.ArchiveSearchScope
import com.valoser.futacha.shared.repo.BoardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.serialization.json.Json

internal data class CatalogScreenRuntimeBindingsBundle(
    val executionBindings: CatalogExecutionBindings,
    val initialLoadBindings: CatalogInitialLoadBindings,
    val createThreadBindings: CatalogCreateThreadBindings,
    val historyDrawerCallbacks: CatalogHistoryDrawerCallbacks,
    val topBarCallbacks: CatalogTopBarCallbacks,
    val navigationCallbacks: CatalogNavigationCallbacks
)

internal fun buildCatalogScreenRuntimeBindingsBundle(
    coroutineScope: CoroutineScope,
    drawerState: DrawerState,
    currentBoard: () -> BoardSummary?,
    currentCatalogMode: () -> CatalogMode,
    currentCatalogLoadGeneration: () -> Long,
    setCatalogLoadGeneration: (Long) -> Unit,
    currentCatalogLoadJob: () -> Job?,
    setCatalogLoadJob: (Job?) -> Unit,
    currentIsRefreshing: () -> Boolean,
    setIsRefreshing: (Boolean) -> Unit,
    setCatalogUiState: (CatalogUiState) -> Unit,
    setLastCatalogItems: (List<CatalogItem>) -> Unit,
    loadCatalogItems: suspend (BoardSummary, CatalogMode) -> List<CatalogItem>,
    activeRepository: BoardRepository,
    currentCreateThreadDraft: () -> CreateThreadDraft,
    currentCreateThreadImage: () -> com.valoser.futacha.shared.util.ImageData?,
    setCreateThreadDraft: (CreateThreadDraft) -> Unit,
    setCreateThreadImage: (com.valoser.futacha.shared.util.ImageData?) -> Unit,
    setShowCreateThreadDialog: (Boolean) -> Unit,
    updateLastUsedDeleteKey: (String) -> Unit,
    showSnackbar: suspend (String) -> Unit,
    currentIsHistoryRefreshing: () -> Boolean,
    setIsHistoryRefreshing: (Boolean) -> Unit,
    onHistoryRefresh: suspend () -> Unit,
    currentPastSearchRuntimeState: () -> CatalogPastSearchRuntimeState,
    setPastSearchRuntimeState: (CatalogPastSearchRuntimeState) -> Unit,
    httpClient: io.ktor.client.HttpClient?,
    archiveSearchJson: Json,
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    onBack: () -> Unit,
    onHistoryCleared: () -> Unit,
    onShowGlobalSettings: () -> Unit,
    setSearchQuery: (String) -> Unit,
    setSearchActive: (Boolean) -> Unit,
    persistCatalogMode: (CatalogMode) -> Unit,
    lastUsedDeleteKey: String,
    currentCreateThreadPassword: () -> String,
    setCreateThreadPassword: (String) -> Unit,
    scrollCatalogToTop: () -> Unit,
    setShowPastThreadSearchDialog: (Boolean) -> Unit,
    setShowModeDialog: (Boolean) -> Unit,
    setShowSettingsMenu: (Boolean) -> Unit
): CatalogScreenRuntimeBindingsBundle {
    val executionBindings = buildCatalogExecutionBindings(
        coroutineScope = coroutineScope,
        currentIsHistoryRefreshing = currentIsHistoryRefreshing,
        setIsHistoryRefreshing = setIsHistoryRefreshing,
        onHistoryRefresh = onHistoryRefresh,
        showSnackbar = showSnackbar,
        currentBoard = currentBoard,
        currentCatalogMode = currentCatalogMode,
        currentIsRefreshing = currentIsRefreshing,
        currentCatalogLoadGeneration = currentCatalogLoadGeneration,
        setCatalogLoadGeneration = setCatalogLoadGeneration,
        setIsRefreshing = setIsRefreshing,
        currentCatalogLoadJob = currentCatalogLoadJob,
        setCatalogLoadJob = setCatalogLoadJob,
        setCatalogUiState = setCatalogUiState,
        setLastCatalogItems = setLastCatalogItems,
        loadCatalogItems = loadCatalogItems,
        currentPastSearchRuntimeState = currentPastSearchRuntimeState,
        setPastSearchRuntimeState = setPastSearchRuntimeState,
        httpClient = httpClient,
        archiveSearchJson = archiveSearchJson
    )
    return CatalogScreenRuntimeBindingsBundle(
        executionBindings = executionBindings,
        initialLoadBindings = buildCatalogInitialLoadBindings(
            coroutineScope = coroutineScope,
            currentBoard = currentBoard,
            currentCatalogMode = currentCatalogMode,
            currentCatalogLoadGeneration = currentCatalogLoadGeneration,
            setCatalogLoadGeneration = setCatalogLoadGeneration,
            currentCatalogLoadJob = currentCatalogLoadJob,
            setCatalogLoadJob = setCatalogLoadJob,
            setIsRefreshing = setIsRefreshing,
            setCatalogUiState = setCatalogUiState,
            setLastCatalogItems = setLastCatalogItems,
            loadCatalogItems = loadCatalogItems
        ),
        createThreadBindings = buildCatalogCreateThreadBindings(
            coroutineScope = coroutineScope,
            activeRepository = activeRepository,
            currentBoard = currentBoard,
            currentDraft = currentCreateThreadDraft,
            currentImage = currentCreateThreadImage,
            setCreateThreadDraft = setCreateThreadDraft,
            setCreateThreadImage = setCreateThreadImage,
            setShowCreateThreadDialog = setShowCreateThreadDialog,
            updateLastUsedDeleteKey = updateLastUsedDeleteKey,
            showSnackbar = showSnackbar,
            performRefresh = executionBindings.performRefresh
        ),
        historyDrawerCallbacks = buildCatalogHistoryDrawerCallbacks(
            coroutineScope = coroutineScope,
            drawerState = drawerState,
            onHistoryEntrySelected = onHistoryEntrySelected,
            onBack = onBack,
            onRefreshClick = executionBindings.handleHistoryRefresh,
            onHistoryCleared = onHistoryCleared,
            showSnackbar = showSnackbar,
            onShowGlobalSettings = onShowGlobalSettings
        ),
        topBarCallbacks = buildCatalogTopBarCallbacks(
            coroutineScope = coroutineScope,
            drawerState = drawerState,
            setSearchQuery = setSearchQuery,
            setSearchActive = setSearchActive,
            persistCatalogMode = persistCatalogMode,
            onShowGlobalSettings = onShowGlobalSettings,
            showSnackbar = showSnackbar
        ),
        navigationCallbacks = buildCatalogNavigationCallbacks(
            lastUsedDeleteKey = lastUsedDeleteKey,
            currentCreateThreadPassword = currentCreateThreadPassword,
            setCreateThreadPassword = setCreateThreadPassword,
            setShowCreateThreadDialog = setShowCreateThreadDialog,
            scrollCatalogToTop = scrollCatalogToTop,
            performRefresh = executionBindings.performRefresh,
            setShowPastThreadSearchDialog = setShowPastThreadSearchDialog,
            setShowModeDialog = setShowModeDialog,
            setShowSettingsMenu = setShowSettingsMenu
        )
    )
}
