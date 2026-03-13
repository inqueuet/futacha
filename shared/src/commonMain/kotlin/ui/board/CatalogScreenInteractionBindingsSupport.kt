package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.DrawerState
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogDisplayStyle
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.network.ArchiveSearchScope
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.ImageData
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.serialization.json.Json

internal data class CatalogScreenInteractionBindingsBundle(
    val mutationBindings: CatalogScreenMutationBindings,
    val runtimeBindings: CatalogScreenRuntimeBindingsBundle,
    val overlayBindings: CatalogScreenOverlayBindingsBundle
)

internal fun buildCatalogScreenInteractionBindingsBundle(
    coroutineScope: CoroutineScope,
    drawerState: DrawerState,
    currentBoard: () -> BoardSummary?,
    currentBoardId: () -> String?,
    currentCatalogMode: () -> CatalogMode,
    setCatalogMode: (CatalogMode) -> Unit,
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
    currentCreateThreadImage: () -> ImageData?,
    setCreateThreadDraft: (CreateThreadDraft) -> Unit,
    setCreateThreadImage: (ImageData?) -> Unit,
    setShowCreateThreadDialog: (Boolean) -> Unit,
    updateLastUsedDeleteKey: (String) -> Unit,
    showSnackbar: suspend (String) -> Unit,
    currentIsHistoryRefreshing: () -> Boolean,
    setIsHistoryRefreshing: (Boolean) -> Unit,
    onHistoryRefresh: suspend () -> Unit,
    currentPastSearchRuntimeState: () -> CatalogPastSearchRuntimeState,
    setPastSearchRuntimeState: (CatalogPastSearchRuntimeState) -> Unit,
    httpClient: HttpClient?,
    archiveSearchJson: Json,
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    onBack: () -> Unit,
    onHistoryCleared: () -> Unit,
    setShowGlobalSettings: (Boolean) -> Unit,
    setSearchQuery: (String) -> Unit,
    setSearchActive: (Boolean) -> Unit,
    lastUsedDeleteKey: String,
    currentCreateThreadPassword: () -> String,
    setCreateThreadPassword: (String) -> Unit,
    currentCatalogNgWords: () -> List<String>,
    currentWatchWords: () -> List<String>,
    currentCatalogNgFilteringEnabled: () -> Boolean,
    setCatalogNgFilteringEnabled: (Boolean) -> Unit,
    onFallbackCatalogNgWordsChanged: (List<String>) -> Unit,
    onFallbackWatchWordsChanged: (List<String>) -> Unit,
    setLocalCatalogDisplayStyle: (CatalogDisplayStyle) -> Unit,
    setLocalCatalogGridColumns: (Int) -> Unit,
    currentCatalogDisplayStyle: () -> CatalogDisplayStyle,
    catalogGridState: LazyGridState,
    catalogListState: LazyListState,
    setShowPastThreadSearchDialog: (Boolean) -> Unit,
    setShowPastSearchSheetVisible: (Boolean) -> Unit,
    setShowModeDialog: (Boolean) -> Unit,
    setShowDisplayStyleDialog: (Boolean) -> Unit,
    setShowSettingsMenu: (Boolean) -> Unit,
    setNgManagementVisible: (Boolean) -> Unit,
    setWatchWordsVisible: (Boolean) -> Unit,
    setCookieManagementVisible: (Boolean) -> Unit,
    currentArchiveSearchScope: () -> ArchiveSearchScope?,
    setLastArchiveSearchScope: (ArchiveSearchScope?) -> Unit,
    setArchiveSearchQuery: (String) -> Unit,
    currentArchiveSearchQuery: () -> String,
    currentLastArchiveSearchScope: () -> ArchiveSearchScope?,
    onThreadSelected: (CatalogItem) -> Unit,
    urlLauncher: (String) -> Unit,
    stateStore: AppStateStore?,
    isPrivacyFilterEnabled: () -> Boolean,
    cookieRepository: CookieRepository?
): CatalogScreenInteractionBindingsBundle {
    val mutationBindings = buildCatalogScreenMutationBindings(
        coroutineScope = coroutineScope,
        stateStore = stateStore,
        currentBoardId = currentBoardId,
        setCatalogMode = setCatalogMode,
        currentCatalogNgWords = currentCatalogNgWords,
        currentWatchWords = currentWatchWords,
        currentCatalogNgFilteringEnabled = currentCatalogNgFilteringEnabled,
        setCatalogNgFilteringEnabled = setCatalogNgFilteringEnabled,
        onFallbackCatalogNgWordsChanged = onFallbackCatalogNgWordsChanged,
        onFallbackWatchWordsChanged = onFallbackWatchWordsChanged,
        showSnackbar = showSnackbar,
        setLocalCatalogDisplayStyle = setLocalCatalogDisplayStyle,
        setLocalCatalogGridColumns = setLocalCatalogGridColumns,
        currentCatalogDisplayStyle = currentCatalogDisplayStyle,
        catalogGridState = catalogGridState,
        catalogListState = catalogListState
    )
    val runtimeBindings = buildCatalogScreenRuntimeBindingsBundle(
        coroutineScope = coroutineScope,
        drawerState = drawerState,
        currentBoard = currentBoard,
        currentCatalogMode = currentCatalogMode,
        currentCatalogLoadGeneration = currentCatalogLoadGeneration,
        setCatalogLoadGeneration = setCatalogLoadGeneration,
        currentCatalogLoadJob = currentCatalogLoadJob,
        setCatalogLoadJob = setCatalogLoadJob,
        currentIsRefreshing = currentIsRefreshing,
        setIsRefreshing = setIsRefreshing,
        setCatalogUiState = setCatalogUiState,
        setLastCatalogItems = setLastCatalogItems,
        loadCatalogItems = loadCatalogItems,
        activeRepository = activeRepository,
        currentCreateThreadDraft = currentCreateThreadDraft,
        currentCreateThreadImage = currentCreateThreadImage,
        setCreateThreadDraft = setCreateThreadDraft,
        setCreateThreadImage = setCreateThreadImage,
        setShowCreateThreadDialog = setShowCreateThreadDialog,
        updateLastUsedDeleteKey = updateLastUsedDeleteKey,
        showSnackbar = showSnackbar,
        currentIsHistoryRefreshing = currentIsHistoryRefreshing,
        setIsHistoryRefreshing = setIsHistoryRefreshing,
        onHistoryRefresh = onHistoryRefresh,
        currentPastSearchRuntimeState = currentPastSearchRuntimeState,
        setPastSearchRuntimeState = setPastSearchRuntimeState,
        httpClient = httpClient,
        archiveSearchJson = archiveSearchJson,
        onHistoryEntrySelected = onHistoryEntrySelected,
        onBack = onBack,
        onHistoryCleared = onHistoryCleared,
        onShowGlobalSettings = { setShowGlobalSettings(true) },
        setSearchQuery = setSearchQuery,
        setSearchActive = setSearchActive,
        persistCatalogMode = mutationBindings.persistCatalogMode,
        lastUsedDeleteKey = lastUsedDeleteKey,
        currentCreateThreadPassword = currentCreateThreadPassword,
        setCreateThreadPassword = setCreateThreadPassword,
        scrollCatalogToTop = mutationBindings.scrollCatalogToTop,
        setShowPastThreadSearchDialog = setShowPastThreadSearchDialog,
        setShowModeDialog = setShowModeDialog,
        setShowSettingsMenu = setShowSettingsMenu
    )
    val overlayBindings = buildCatalogScreenOverlayBindingsBundle(
        persistCatalogMode = mutationBindings.persistCatalogMode,
        updateCatalogDisplayStyle = mutationBindings.updateCatalogDisplayStyle,
        updateCatalogGridColumns = mutationBindings.updateCatalogGridColumns,
        currentArchiveSearchScope = currentArchiveSearchScope,
        setLastArchiveSearchScope = setLastArchiveSearchScope,
        setArchiveSearchQuery = setArchiveSearchQuery,
        setShowPastThreadSearchDialog = setShowPastThreadSearchDialog,
        setIsPastSearchSheetVisible = setShowPastSearchSheetVisible,
        runPastThreadSearch = runtimeBindings.executionBindings.runPastThreadSearch,
        currentPastSearchGeneration = { currentPastSearchRuntimeState().generation },
        currentPastSearchJob = { currentPastSearchRuntimeState().job },
        setPastSearchGeneration = { generation ->
            setPastSearchRuntimeState(currentPastSearchRuntimeState().copy(generation = generation))
        },
        setPastSearchJob = { job ->
            setPastSearchRuntimeState(currentPastSearchRuntimeState().copy(job = job))
        },
        currentArchiveSearchQuery = currentArchiveSearchQuery,
        currentLastArchiveSearchScope = currentLastArchiveSearchScope,
        onThreadSelected = onThreadSelected,
        board = currentBoard,
        catalogMode = currentCatalogMode,
        urlLauncher = urlLauncher,
        stateStore = stateStore,
        isPrivacyFilterEnabled = isPrivacyFilterEnabled,
        coroutineScope = coroutineScope,
        scrollCatalogToTop = mutationBindings.scrollCatalogToTop,
        setShowModeDialog = setShowModeDialog,
        setShowDisplayStyleDialog = setShowDisplayStyleDialog,
        setIsNgManagementVisible = setNgManagementVisible,
        setIsWatchWordsVisible = setWatchWordsVisible,
        setShowSettingsMenu = setShowSettingsMenu,
        cookieRepository = cookieRepository,
        setIsGlobalSettingsVisible = setShowGlobalSettings,
        setIsCookieManagementVisible = setCookieManagementVisible,
        onAddNgWord = mutationBindings.addCatalogNgWordEntry,
        onRemoveNgWord = mutationBindings.removeCatalogNgWordEntry,
        onToggleNgFiltering = mutationBindings.handleCatalogNgFilteringToggle,
        onAddWatchWord = mutationBindings.addWatchWordEntry,
        onRemoveWatchWord = mutationBindings.removeWatchWordEntry
    )
    return CatalogScreenInteractionBindingsBundle(
        mutationBindings = mutationBindings,
        runtimeBindings = runtimeBindings,
        overlayBindings = overlayBindings
    )
}
