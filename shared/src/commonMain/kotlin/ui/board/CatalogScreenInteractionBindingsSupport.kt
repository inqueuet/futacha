package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.DrawerState
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogDisplayStyle
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.CatalogPageContent
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

internal data class CatalogScreenMutationInputs(
    val coroutineScope: CoroutineScope,
    val stateStore: AppStateStore?,
    val currentBoardId: () -> String?,
    val setCatalogMode: (CatalogMode) -> Unit,
    val currentCatalogNgWords: () -> List<String>,
    val currentWatchWords: () -> List<String>,
    val currentCatalogNgFilteringEnabled: () -> Boolean,
    val setCatalogNgFilteringEnabled: (Boolean) -> Unit,
    val onFallbackCatalogNgWordsChanged: (List<String>) -> Unit,
    val onFallbackWatchWordsChanged: (List<String>) -> Unit,
    val showSnackbar: suspend (String) -> Unit,
    val setLocalCatalogDisplayStyle: (CatalogDisplayStyle) -> Unit,
    val setLocalCatalogGridColumns: (Int) -> Unit,
    val currentCatalogDisplayStyle: () -> CatalogDisplayStyle,
    val catalogGridState: LazyGridState,
    val catalogListState: LazyListState
)

internal data class CatalogScreenRuntimeInputs(
    val coroutineScope: CoroutineScope,
    val drawerState: DrawerState,
    val currentBoard: () -> BoardSummary?,
    val stateStore: AppStateStore?,
    val currentCatalogMode: () -> CatalogMode,
    val currentCatalogLoadGeneration: () -> Long,
    val setCatalogLoadGeneration: (Long) -> Unit,
    val currentCatalogLoadJob: () -> Job?,
    val setCatalogLoadJob: (Job?) -> Unit,
    val currentIsRefreshing: () -> Boolean,
    val setIsRefreshing: (Boolean) -> Unit,
    val setCatalogUiState: (CatalogUiState) -> Unit,
    val setLastCatalogItems: (List<CatalogItem>) -> Unit,
    val loadCatalogItems: suspend (BoardSummary, CatalogMode) -> CatalogPageContent,
    val activeRepository: BoardRepository,
    val currentCreateThreadDraft: () -> CreateThreadDraft,
    val currentCreateThreadImage: () -> ImageData?,
    val setCreateThreadDraft: (CreateThreadDraft) -> Unit,
    val setCreateThreadImage: (ImageData?) -> Unit,
    val setShowCreateThreadDialog: (Boolean) -> Unit,
    val updateLastUsedDeleteKey: (String) -> Unit,
    val showSnackbar: suspend (String) -> Unit,
    val currentIsHistoryRefreshing: () -> Boolean,
    val setIsHistoryRefreshing: (Boolean) -> Unit,
    val onHistoryRefresh: suspend () -> Unit,
    val currentPastSearchRuntimeState: () -> CatalogPastSearchRuntimeState,
    val setPastSearchRuntimeState: (CatalogPastSearchRuntimeState) -> Unit,
    val httpClient: HttpClient?,
    val archiveSearchJson: Json,
    val onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    val onBack: () -> Unit,
    val onHistoryCleared: () -> Unit,
    val setShowGlobalSettings: (Boolean) -> Unit,
    val setSearchQuery: (String) -> Unit,
    val setSearchActive: (Boolean) -> Unit,
    val lastUsedDeleteKey: String,
    val currentCreateThreadPassword: () -> String,
    val setCreateThreadPassword: (String) -> Unit,
    val setShowPastThreadSearchDialog: (Boolean) -> Unit,
    val setShowModeDialog: (Boolean) -> Unit,
    val setShowSettingsMenu: (Boolean) -> Unit
)

internal data class CatalogScreenOverlayInputs(
    val currentBoard: () -> BoardSummary?,
    val currentCatalogMode: () -> CatalogMode,
    val currentPastSearchRuntimeState: () -> CatalogPastSearchRuntimeState,
    val setPastSearchRuntimeState: (CatalogPastSearchRuntimeState) -> Unit,
    val setShowGlobalSettings: (Boolean) -> Unit,
    val setShowPastThreadSearchDialog: (Boolean) -> Unit,
    val setShowPastSearchSheetVisible: (Boolean) -> Unit,
    val setShowModeDialog: (Boolean) -> Unit,
    val setShowDisplayStyleDialog: (Boolean) -> Unit,
    val setShowSettingsMenu: (Boolean) -> Unit,
    val setNgManagementVisible: (Boolean) -> Unit,
    val setWatchWordsVisible: (Boolean) -> Unit,
    val setCookieManagementVisible: (Boolean) -> Unit,
    val currentArchiveSearchScope: () -> ArchiveSearchScope?,
    val setLastArchiveSearchScope: (ArchiveSearchScope?) -> Unit,
    val setArchiveSearchQuery: (String) -> Unit,
    val currentArchiveSearchQuery: () -> String,
    val currentLastArchiveSearchScope: () -> ArchiveSearchScope?,
    val onThreadSelected: (CatalogItem) -> Unit,
    val urlLauncher: (String) -> Unit,
    val stateStore: AppStateStore?,
    val isPrivacyFilterEnabled: () -> Boolean,
    val coroutineScope: CoroutineScope,
    val cookieRepository: CookieRepository?
)

internal fun buildCatalogScreenInteractionBindingsBundle(
    mutationInputs: CatalogScreenMutationInputs,
    runtimeInputs: CatalogScreenRuntimeInputs,
    overlayInputs: CatalogScreenOverlayInputs
): CatalogScreenInteractionBindingsBundle {
    val mutationBindings = buildCatalogScreenMutationBindings(
        coroutineScope = mutationInputs.coroutineScope,
        stateStore = mutationInputs.stateStore,
        currentBoardId = mutationInputs.currentBoardId,
        setCatalogMode = mutationInputs.setCatalogMode,
        currentCatalogNgWords = mutationInputs.currentCatalogNgWords,
        currentWatchWords = mutationInputs.currentWatchWords,
        currentCatalogNgFilteringEnabled = mutationInputs.currentCatalogNgFilteringEnabled,
        setCatalogNgFilteringEnabled = mutationInputs.setCatalogNgFilteringEnabled,
        onFallbackCatalogNgWordsChanged = mutationInputs.onFallbackCatalogNgWordsChanged,
        onFallbackWatchWordsChanged = mutationInputs.onFallbackWatchWordsChanged,
        showSnackbar = mutationInputs.showSnackbar,
        setLocalCatalogDisplayStyle = mutationInputs.setLocalCatalogDisplayStyle,
        setLocalCatalogGridColumns = mutationInputs.setLocalCatalogGridColumns,
        currentCatalogDisplayStyle = mutationInputs.currentCatalogDisplayStyle,
        catalogGridState = mutationInputs.catalogGridState,
        catalogListState = mutationInputs.catalogListState
    )
    val runtimeBindings = buildCatalogScreenRuntimeBindingsBundle(
        coroutineScope = runtimeInputs.coroutineScope,
        drawerState = runtimeInputs.drawerState,
        currentBoard = runtimeInputs.currentBoard,
        stateStore = runtimeInputs.stateStore,
        currentCatalogMode = runtimeInputs.currentCatalogMode,
        currentCatalogLoadGeneration = runtimeInputs.currentCatalogLoadGeneration,
        setCatalogLoadGeneration = runtimeInputs.setCatalogLoadGeneration,
        currentCatalogLoadJob = runtimeInputs.currentCatalogLoadJob,
        setCatalogLoadJob = runtimeInputs.setCatalogLoadJob,
        currentIsRefreshing = runtimeInputs.currentIsRefreshing,
        setIsRefreshing = runtimeInputs.setIsRefreshing,
        setCatalogUiState = runtimeInputs.setCatalogUiState,
        setLastCatalogItems = runtimeInputs.setLastCatalogItems,
        loadCatalogItems = runtimeInputs.loadCatalogItems,
        activeRepository = runtimeInputs.activeRepository,
        currentCreateThreadDraft = runtimeInputs.currentCreateThreadDraft,
        currentCreateThreadImage = runtimeInputs.currentCreateThreadImage,
        setCreateThreadDraft = runtimeInputs.setCreateThreadDraft,
        setCreateThreadImage = runtimeInputs.setCreateThreadImage,
        setShowCreateThreadDialog = runtimeInputs.setShowCreateThreadDialog,
        updateLastUsedDeleteKey = runtimeInputs.updateLastUsedDeleteKey,
        showSnackbar = runtimeInputs.showSnackbar,
        currentIsHistoryRefreshing = runtimeInputs.currentIsHistoryRefreshing,
        setIsHistoryRefreshing = runtimeInputs.setIsHistoryRefreshing,
        onHistoryRefresh = runtimeInputs.onHistoryRefresh,
        currentPastSearchRuntimeState = runtimeInputs.currentPastSearchRuntimeState,
        setPastSearchRuntimeState = runtimeInputs.setPastSearchRuntimeState,
        httpClient = runtimeInputs.httpClient,
        archiveSearchJson = runtimeInputs.archiveSearchJson,
        onHistoryEntrySelected = runtimeInputs.onHistoryEntrySelected,
        onBack = runtimeInputs.onBack,
        onHistoryCleared = runtimeInputs.onHistoryCleared,
        onShowGlobalSettings = { runtimeInputs.setShowGlobalSettings(true) },
        setSearchQuery = runtimeInputs.setSearchQuery,
        setSearchActive = runtimeInputs.setSearchActive,
        persistCatalogMode = mutationBindings.persistCatalogMode,
        lastUsedDeleteKey = runtimeInputs.lastUsedDeleteKey,
        currentCreateThreadPassword = runtimeInputs.currentCreateThreadPassword,
        setCreateThreadPassword = runtimeInputs.setCreateThreadPassword,
        scrollCatalogToTop = mutationBindings.scrollCatalogToTop,
        setShowPastThreadSearchDialog = runtimeInputs.setShowPastThreadSearchDialog,
        setShowModeDialog = runtimeInputs.setShowModeDialog,
        setShowSettingsMenu = runtimeInputs.setShowSettingsMenu
    )
    val overlayBindings = buildCatalogScreenOverlayBindingsBundle(
        dialogInputs = CatalogScreenOverlayDialogInputs(
            persistCatalogMode = mutationBindings.persistCatalogMode,
            updateCatalogDisplayStyle = mutationBindings.updateCatalogDisplayStyle,
            updateCatalogGridColumns = mutationBindings.updateCatalogGridColumns,
            setShowModeDialog = overlayInputs.setShowModeDialog,
            setShowDisplayStyleDialog = overlayInputs.setShowDisplayStyleDialog
        ),
        searchInputs = CatalogScreenOverlaySearchInputs(
            currentArchiveSearchScope = overlayInputs.currentArchiveSearchScope,
            setLastArchiveSearchScope = overlayInputs.setLastArchiveSearchScope,
            setArchiveSearchQuery = overlayInputs.setArchiveSearchQuery,
            setShowPastThreadSearchDialog = overlayInputs.setShowPastThreadSearchDialog,
            setIsPastSearchSheetVisible = overlayInputs.setShowPastSearchSheetVisible,
            runPastThreadSearch = runtimeBindings.executionBindings.runPastThreadSearch,
            currentPastSearchGeneration = { overlayInputs.currentPastSearchRuntimeState().generation },
            currentPastSearchJob = { overlayInputs.currentPastSearchRuntimeState().job },
            setPastSearchGeneration = { generation ->
                overlayInputs.setPastSearchRuntimeState(
                    overlayInputs.currentPastSearchRuntimeState().copy(generation = generation)
                )
            },
            setPastSearchJob = { job ->
                overlayInputs.setPastSearchRuntimeState(
                    overlayInputs.currentPastSearchRuntimeState().copy(job = job)
                )
            },
            currentArchiveSearchQuery = overlayInputs.currentArchiveSearchQuery,
            currentLastArchiveSearchScope = overlayInputs.currentLastArchiveSearchScope,
            onThreadSelected = overlayInputs.onThreadSelected
        ),
        settingsInputs = CatalogScreenOverlaySettingsInputs(
            board = overlayInputs.currentBoard,
            catalogMode = overlayInputs.currentCatalogMode,
            urlLauncher = overlayInputs.urlLauncher,
            stateStore = overlayInputs.stateStore,
            isPrivacyFilterEnabled = overlayInputs.isPrivacyFilterEnabled,
            coroutineScope = overlayInputs.coroutineScope,
            scrollCatalogToTop = mutationBindings.scrollCatalogToTop,
            setIsNgManagementVisible = overlayInputs.setNgManagementVisible,
            setIsWatchWordsVisible = overlayInputs.setWatchWordsVisible,
            setShowSettingsMenu = overlayInputs.setShowSettingsMenu,
            cookieRepository = overlayInputs.cookieRepository,
            setIsGlobalSettingsVisible = overlayInputs.setShowGlobalSettings,
            setIsCookieManagementVisible = overlayInputs.setCookieManagementVisible,
            onAddNgWord = mutationBindings.addCatalogNgWordEntry,
            onRemoveNgWord = mutationBindings.removeCatalogNgWordEntry,
            onToggleNgFiltering = mutationBindings.handleCatalogNgFilteringToggle,
            onAddWatchWord = mutationBindings.addWatchWordEntry,
            onRemoveWatchWord = mutationBindings.removeWatchWordEntry
        )
    )
    return CatalogScreenInteractionBindingsBundle(
        mutationBindings = mutationBindings,
        runtimeBindings = runtimeBindings,
        overlayBindings = overlayBindings
    )
}
