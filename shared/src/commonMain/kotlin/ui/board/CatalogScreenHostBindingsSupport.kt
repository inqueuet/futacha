package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.DrawerState
import androidx.compose.material3.SnackbarHostState
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogDisplayStyle
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.ImageData
import kotlinx.coroutines.CoroutineScope

internal data class CatalogScreenScaffoldBindings(
    val history: List<ThreadHistoryEntry>,
    val onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit,
    val historyDrawerCallbacks: CatalogHistoryDrawerCallbacks,
    val drawerState: DrawerState,
    val isDrawerOpen: Boolean,
    val coroutineScope: CoroutineScope,
    val snackbarHostState: SnackbarHostState,
    val board: BoardSummary?,
    val catalogMode: CatalogMode,
    val searchQuery: String,
    val isSearchActive: Boolean,
    val topBarCallbacks: CatalogTopBarCallbacks,
    val catalogNavEntries: List<CatalogNavEntryConfig>,
    val navigationCallbacks: CatalogNavigationCallbacks,
    val uiState: CatalogUiState,
    val watchWords: List<String>,
    val catalogNgWords: List<String>,
    val catalogNgFilteringEnabled: Boolean,
    val debouncedSearchQuery: String,
    val activeRepository: BoardRepository,
    val onThreadSelected: (CatalogItem) -> Unit,
    val performRefresh: () -> Unit,
    val isRefreshing: Boolean,
    val catalogDisplayStyle: CatalogDisplayStyle,
    val catalogGridColumns: Int,
    val catalogGridState: LazyGridState,
    val catalogListState: LazyListState
)

internal data class CatalogScreenOverlayHostBindings(
    val overlayState: CatalogOverlayState,
    val overlayBindings: CatalogScreenOverlayBindingsBundle,
    val createThreadDraft: CreateThreadDraft,
    val setCreateThreadDraft: (CreateThreadDraft) -> Unit,
    val createThreadImage: ImageData?,
    val setCreateThreadImage: (ImageData?) -> Unit,
    val setCreateThreadDialogVisible: (Boolean) -> Unit,
    val board: BoardSummary?,
    val archiveSearchQuery: String,
    val searchQuery: String,
    val catalogMode: CatalogMode,
    val catalogDisplayStyle: CatalogDisplayStyle,
    val catalogGridColumns: Int,
    val pastSearchRuntimeState: CatalogPastSearchRuntimeState,
    val watchWords: List<String>,
    val catalogNgWords: List<String>,
    val catalogNgFilteringEnabled: Boolean,
    val isPrivacyFilterEnabled: Boolean,
    val createThreadBindings: CatalogCreateThreadBindings,
    val preferencesState: ScreenPreferencesState,
    val preferencesCallbacks: ScreenPreferencesCallbacks,
    val history: List<ThreadHistoryEntry>,
    val fileSystem: FileSystem?,
    val autoSavedThreadRepository: SavedThreadRepository?,
    val cookieRepository: CookieRepository?
)

internal data class CatalogScreenHostBindingsResult(
    val scaffoldBindings: CatalogScreenScaffoldBindings,
    val overlayHostBindings: CatalogScreenOverlayHostBindings
)

internal data class CatalogScreenHostBindingsInputs(
    val history: List<ThreadHistoryEntry>,
    val onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit,
    val historyDrawerCallbacks: CatalogHistoryDrawerCallbacks,
    val drawerState: DrawerState,
    val isDrawerOpen: Boolean,
    val coroutineScope: CoroutineScope,
    val snackbarHostState: SnackbarHostState,
    val board: BoardSummary?,
    val catalogMode: CatalogMode,
    val searchQuery: String,
    val isSearchActive: Boolean,
    val runtimeBindings: CatalogScreenRuntimeBindingsBundle,
    val preferencesState: ScreenPreferencesState,
    val uiState: CatalogUiState,
    val watchWords: List<String>,
    val catalogNgWords: List<String>,
    val catalogNgFilteringEnabled: Boolean,
    val debouncedSearchQuery: String,
    val activeRepository: BoardRepository,
    val onThreadSelected: (CatalogItem) -> Unit,
    val performRefresh: () -> Unit,
    val isRefreshing: Boolean,
    val catalogDisplayStyle: CatalogDisplayStyle,
    val catalogGridColumns: Int,
    val catalogGridState: LazyGridState,
    val catalogListState: LazyListState,
    val overlayState: CatalogOverlayState,
    val overlayBindings: CatalogScreenOverlayBindingsBundle,
    val createThreadDraft: CreateThreadDraft,
    val setCreateThreadDraft: (CreateThreadDraft) -> Unit,
    val createThreadImage: ImageData?,
    val setCreateThreadImage: (ImageData?) -> Unit,
    val setCreateThreadDialogVisible: (Boolean) -> Unit,
    val archiveSearchQuery: String,
    val pastSearchRuntimeState: CatalogPastSearchRuntimeState,
    val isPrivacyFilterEnabled: Boolean,
    val createThreadBindings: CatalogCreateThreadBindings,
    val preferencesCallbacks: ScreenPreferencesCallbacks,
    val fileSystem: FileSystem?,
    val autoSavedThreadRepository: SavedThreadRepository?,
    val cookieRepository: CookieRepository?
)

internal fun buildCatalogScreenHostBindings(
    inputs: CatalogScreenHostBindingsInputs
): CatalogScreenHostBindingsResult {
    val scaffoldBindings = CatalogScreenScaffoldBindings(
        history = inputs.history,
        onHistoryEntryDismissed = inputs.onHistoryEntryDismissed,
        historyDrawerCallbacks = inputs.historyDrawerCallbacks,
        drawerState = inputs.drawerState,
        isDrawerOpen = inputs.isDrawerOpen,
        coroutineScope = inputs.coroutineScope,
        snackbarHostState = inputs.snackbarHostState,
        board = inputs.board,
        catalogMode = inputs.catalogMode,
        searchQuery = inputs.searchQuery,
        isSearchActive = inputs.isSearchActive,
        topBarCallbacks = inputs.runtimeBindings.topBarCallbacks,
        catalogNavEntries = inputs.preferencesState.catalogNavEntries,
        navigationCallbacks = inputs.runtimeBindings.navigationCallbacks,
        uiState = inputs.uiState,
        watchWords = inputs.watchWords,
        catalogNgWords = inputs.catalogNgWords,
        catalogNgFilteringEnabled = inputs.catalogNgFilteringEnabled,
        debouncedSearchQuery = inputs.debouncedSearchQuery,
        activeRepository = inputs.activeRepository,
        onThreadSelected = inputs.onThreadSelected,
        performRefresh = inputs.performRefresh,
        isRefreshing = inputs.isRefreshing,
        catalogDisplayStyle = inputs.catalogDisplayStyle,
        catalogGridColumns = inputs.catalogGridColumns,
        catalogGridState = inputs.catalogGridState,
        catalogListState = inputs.catalogListState
    )
    val overlayHostBindings = CatalogScreenOverlayHostBindings(
        overlayState = inputs.overlayState,
        overlayBindings = inputs.overlayBindings,
        createThreadDraft = inputs.createThreadDraft,
        setCreateThreadDraft = inputs.setCreateThreadDraft,
        createThreadImage = inputs.createThreadImage,
        setCreateThreadImage = inputs.setCreateThreadImage,
        setCreateThreadDialogVisible = inputs.setCreateThreadDialogVisible,
        board = inputs.board,
        archiveSearchQuery = inputs.archiveSearchQuery,
        searchQuery = inputs.searchQuery,
        catalogMode = inputs.catalogMode,
        catalogDisplayStyle = inputs.catalogDisplayStyle,
        catalogGridColumns = inputs.catalogGridColumns,
        pastSearchRuntimeState = inputs.pastSearchRuntimeState,
        watchWords = inputs.watchWords,
        catalogNgWords = inputs.catalogNgWords,
        catalogNgFilteringEnabled = inputs.catalogNgFilteringEnabled,
        isPrivacyFilterEnabled = inputs.isPrivacyFilterEnabled,
        createThreadBindings = inputs.createThreadBindings,
        preferencesState = inputs.preferencesState,
        preferencesCallbacks = inputs.preferencesCallbacks,
        history = inputs.history,
        fileSystem = inputs.fileSystem,
        autoSavedThreadRepository = inputs.autoSavedThreadRepository,
        cookieRepository = inputs.cookieRepository
    )
    return CatalogScreenHostBindingsResult(
        scaffoldBindings = scaffoldBindings,
        overlayHostBindings = overlayHostBindings
    )
}
