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

internal data class CatalogScreenHostBindings(
    val scaffold: CatalogScreenScaffoldBindings,
    val overlay: CatalogScreenOverlayHostBindings
)

internal fun buildCatalogScreenHostBindings(
    history: List<ThreadHistoryEntry>,
    onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit,
    historyDrawerCallbacks: CatalogHistoryDrawerCallbacks,
    drawerState: DrawerState,
    isDrawerOpen: Boolean,
    coroutineScope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    board: BoardSummary?,
    catalogMode: CatalogMode,
    searchQuery: String,
    isSearchActive: Boolean,
    topBarCallbacks: CatalogTopBarCallbacks,
    catalogNavEntries: List<CatalogNavEntryConfig>,
    navigationCallbacks: CatalogNavigationCallbacks,
    uiState: CatalogUiState,
    watchWords: List<String>,
    catalogNgWords: List<String>,
    catalogNgFilteringEnabled: Boolean,
    debouncedSearchQuery: String,
    activeRepository: BoardRepository,
    onThreadSelected: (CatalogItem) -> Unit,
    performRefresh: () -> Unit,
    isRefreshing: Boolean,
    catalogDisplayStyle: CatalogDisplayStyle,
    catalogGridColumns: Int,
    catalogGridState: LazyGridState,
    catalogListState: LazyListState,
    overlayState: CatalogOverlayState,
    overlayBindings: CatalogScreenOverlayBindingsBundle,
    createThreadDraft: CreateThreadDraft,
    setCreateThreadDraft: (CreateThreadDraft) -> Unit,
    createThreadImage: ImageData?,
    setCreateThreadImage: (ImageData?) -> Unit,
    setCreateThreadDialogVisible: (Boolean) -> Unit,
    archiveSearchQuery: String,
    pastSearchRuntimeState: CatalogPastSearchRuntimeState,
    isPrivacyFilterEnabled: Boolean,
    createThreadBindings: CatalogCreateThreadBindings,
    preferencesState: ScreenPreferencesState,
    preferencesCallbacks: ScreenPreferencesCallbacks,
    fileSystem: FileSystem?,
    autoSavedThreadRepository: SavedThreadRepository?,
    cookieRepository: CookieRepository?
): CatalogScreenHostBindings {
    return CatalogScreenHostBindings(
        scaffold = CatalogScreenScaffoldBindings(
            history = history,
            onHistoryEntryDismissed = onHistoryEntryDismissed,
            historyDrawerCallbacks = historyDrawerCallbacks,
            drawerState = drawerState,
            isDrawerOpen = isDrawerOpen,
            coroutineScope = coroutineScope,
            snackbarHostState = snackbarHostState,
            board = board,
            catalogMode = catalogMode,
            searchQuery = searchQuery,
            isSearchActive = isSearchActive,
            topBarCallbacks = topBarCallbacks,
            catalogNavEntries = catalogNavEntries,
            navigationCallbacks = navigationCallbacks,
            uiState = uiState,
            watchWords = watchWords,
            catalogNgWords = catalogNgWords,
            catalogNgFilteringEnabled = catalogNgFilteringEnabled,
            debouncedSearchQuery = debouncedSearchQuery,
            activeRepository = activeRepository,
            onThreadSelected = onThreadSelected,
            performRefresh = performRefresh,
            isRefreshing = isRefreshing,
            catalogDisplayStyle = catalogDisplayStyle,
            catalogGridColumns = catalogGridColumns,
            catalogGridState = catalogGridState,
            catalogListState = catalogListState
        ),
        overlay = CatalogScreenOverlayHostBindings(
            overlayState = overlayState,
            overlayBindings = overlayBindings,
            createThreadDraft = createThreadDraft,
            setCreateThreadDraft = setCreateThreadDraft,
            createThreadImage = createThreadImage,
            setCreateThreadImage = setCreateThreadImage,
            setCreateThreadDialogVisible = setCreateThreadDialogVisible,
            board = board,
            archiveSearchQuery = archiveSearchQuery,
            searchQuery = searchQuery,
            catalogMode = catalogMode,
            catalogDisplayStyle = catalogDisplayStyle,
            catalogGridColumns = catalogGridColumns,
            pastSearchRuntimeState = pastSearchRuntimeState,
            watchWords = watchWords,
            catalogNgWords = catalogNgWords,
            catalogNgFilteringEnabled = catalogNgFilteringEnabled,
            isPrivacyFilterEnabled = isPrivacyFilterEnabled,
            createThreadBindings = createThreadBindings,
            preferencesState = preferencesState,
            preferencesCallbacks = preferencesCallbacks,
            history = history,
            fileSystem = fileSystem,
            autoSavedThreadRepository = autoSavedThreadRepository,
            cookieRepository = cookieRepository
        )
    )
}
