package com.valoser.futacha.shared.ui.board

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.Image
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.network.ArchiveSearchItem
import com.valoser.futacha.shared.network.ArchiveSearchScope
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.network.fetchArchiveSearchResults
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.Logger
import io.ktor.client.HttpClient
import kotlinx.coroutines.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.abs
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class, FlowPreview::class)
@Composable
fun CatalogScreen(
    board: BoardSummary?,
    screenContract: ScreenContract,
    onBack: () -> Unit,
    onThreadSelected: (CatalogItem) -> Unit,
    dependencies: CatalogScreenDependencies = CatalogScreenDependencies(),
    modifier: Modifier = Modifier
) {
    CatalogScreenContent(
        args = buildCatalogScreenContentArgsFromContract(
            board = board,
            screenContract = screenContract,
            onBack = onBack,
            onThreadSelected = onThreadSelected,
            dependencies = dependencies,
            modifier = modifier
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class, FlowPreview::class)
@Composable
fun CatalogScreen(
    board: BoardSummary?,
    history: List<ThreadHistoryEntry>,
    onBack: () -> Unit,
    onThreadSelected: (CatalogItem) -> Unit,
    historyCallbacks: ScreenHistoryCallbacks = ScreenHistoryCallbacks(),
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit = historyCallbacks.onHistoryEntrySelected,
    onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit = historyCallbacks.onHistoryEntryDismissed,
    onHistoryEntryUpdated: (ThreadHistoryEntry) -> Unit = historyCallbacks.onHistoryEntryUpdated,
    onHistoryRefresh: suspend () -> Unit = historyCallbacks.onHistoryRefresh,
    onHistoryCleared: () -> Unit = historyCallbacks.onHistoryCleared,
    dependencies: CatalogScreenDependencies = CatalogScreenDependencies(),
    repository: BoardRepository? = dependencies.repository,
    stateStore: com.valoser.futacha.shared.state.AppStateStore? = dependencies.stateStore,
    autoSavedThreadRepository: SavedThreadRepository? = dependencies.autoSavedThreadRepository,
    cookieRepository: CookieRepository? = dependencies.cookieRepository,
    preferencesState: ScreenPreferencesState,
    preferencesCallbacks: ScreenPreferencesCallbacks = ScreenPreferencesCallbacks(),
    fileSystem: com.valoser.futacha.shared.util.FileSystem? = dependencies.fileSystem,
    modifier: Modifier = Modifier,
    httpClient: HttpClient? = dependencies.httpClient
) {
    CatalogScreenContent(
        args = buildCatalogScreenContentArgs(
            board = board,
            history = history,
            onBack = onBack,
            onThreadSelected = onThreadSelected,
            historyCallbacks = historyCallbacks,
            onHistoryEntrySelected = onHistoryEntrySelected,
            onHistoryEntryDismissed = onHistoryEntryDismissed,
            onHistoryEntryUpdated = onHistoryEntryUpdated,
            onHistoryRefresh = onHistoryRefresh,
            onHistoryCleared = onHistoryCleared,
            dependencies = dependencies,
            repository = repository,
            stateStore = stateStore,
            autoSavedThreadRepository = autoSavedThreadRepository,
            cookieRepository = cookieRepository,
            preferencesState = preferencesState,
            preferencesCallbacks = preferencesCallbacks,
            fileSystem = fileSystem,
            modifier = modifier,
            httpClient = httpClient
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class, FlowPreview::class)
@Composable
private fun CatalogScreenContent(
    args: CatalogScreenContentArgs
) {
    val preparedSetup = rememberCatalogScreenPreparedSetupBundle(args)
    val context = preparedSetup.context
    val board = context.board
    val history = context.history
    val onBack = context.onBack
    val onThreadSelected = context.onThreadSelected
    val onHistoryEntrySelected = context.onHistoryEntrySelected
    val onHistoryEntryDismissed = context.onHistoryEntryDismissed
    val onHistoryEntryUpdated = context.onHistoryEntryUpdated
    val onHistoryRefresh = context.onHistoryRefresh
    val onHistoryCleared = context.onHistoryCleared
    val repository = context.repository
    val stateStore = context.stateStore
    val autoSavedThreadRepository = context.autoSavedThreadRepository
    val cookieRepository = context.cookieRepository
    val fileSystem = context.fileSystem
    val preferencesState = context.preferencesState
    val preferencesCallbacks = context.preferencesCallbacks
    val httpClient = context.httpClient
    val modifier = context.modifier
    val activeRepository = preparedSetup.activeRepository
    val archiveSearchScope = preparedSetup.archiveSearchScope
    val runtimeObjects = preparedSetup.runtimeObjects
    val snackbarHostState = runtimeObjects.snackbarHostState
    val coroutineScope = runtimeObjects.coroutineScope
    val drawerState = runtimeObjects.drawerState
    val isDrawerOpen = runtimeObjects.isDrawerOpen
    val persistentBindings = preparedSetup.persistentBindings
    val mutableStateRefs = preparedSetup.mutableStateRefs
    val uiRuntimeStateRefs = mutableStateRefs.uiRuntime
    val searchOverlayStateRefs = mutableStateRefs.searchOverlay
    val draftDisplayStateRefs = mutableStateRefs.draftDisplay
    val uiState = uiRuntimeStateRefs.uiState
    var catalogMode by uiRuntimeStateRefs.catalogMode
    var isRefreshing by uiRuntimeStateRefs.isRefreshing
    var catalogLoadJob by uiRuntimeStateRefs.catalogLoadJob
    var catalogLoadGeneration by uiRuntimeStateRefs.catalogLoadGeneration
    var isHistoryRefreshing by uiRuntimeStateRefs.isHistoryRefreshing
    var isSearchActive by searchOverlayStateRefs.isSearchActive
    var searchQuery by searchOverlayStateRefs.searchQuery
    var debouncedSearchQuery by searchOverlayStateRefs.debouncedSearchQuery
    var overlayState by searchOverlayStateRefs.overlayState
    var createThreadDraft by draftDisplayStateRefs.createThreadDraft
    var createThreadImage by draftDisplayStateRefs.createThreadImage
    var archiveSearchQuery by searchOverlayStateRefs.archiveSearchQuery
    var catalogNgFilteringEnabled by searchOverlayStateRefs.catalogNgFilteringEnabled
    val catalogNgWords = persistentBindings.catalogNgWords
    val watchWords = persistentBindings.watchWords
    val lastUsedDeleteKey = persistentBindings.lastUsedDeleteKey
    val updateLastUsedDeleteKey = persistentBindings.updateLastUsedDeleteKey
    val archiveSearchJson = runtimeObjects.archiveSearchJson
    var pastSearchRuntimeState by searchOverlayStateRefs.pastSearchRuntimeState
    val catalogGridState = runtimeObjects.catalogGridState
    val catalogListState = runtimeObjects.catalogListState
    LaunchedEffect(board?.id, persistentBindings.persistedCatalogModes) {
        resolveCatalogModeSyncValue(
            boardId = board?.id,
            persistedCatalogModes = persistentBindings.persistedCatalogModes
        )?.let { catalogMode = it }
    }
    LaunchedEffect(board?.url) {
        pastSearchRuntimeState.job?.cancel()
        val resetState = resolveCatalogPastSearchResetState(
            scope = archiveSearchScope,
            overlayState = overlayState
        )
        pastSearchRuntimeState = resetState.runtimeState
        overlayState = resetState.overlayState
    }
    LaunchedEffect(searchOverlayStateRefs.searchQuery) {
        snapshotFlow { searchOverlayStateRefs.searchQuery.value }
            .map(::resolveCatalogDebouncedSearchQuery)
            .distinctUntilChanged()
            .debounce(::resolveCatalogSearchDebounceMillis)
            .collect { normalized ->
                debouncedSearchQuery = normalized
            }
    }
    var lastCatalogItems by uiRuntimeStateRefs.lastCatalogItems
    suspend fun loadCatalogItems(currentBoard: BoardSummary, mode: CatalogMode): CatalogPageContent {
        return loadCatalogItemsForMode(
            boardUrl = currentBoard.url,
            mode = mode,
            fetchCatalog = activeRepository::getCatalogPage
        )
    }
    val isPrivacyFilterEnabled = persistentBindings.isPrivacyFilterEnabled
    var localCatalogDisplayStyle by draftDisplayStateRefs.localCatalogDisplayStyle
    var localCatalogGridColumns by draftDisplayStateRefs.localCatalogGridColumns
    val displaySettings = remember(
        stateStore,
        persistentBindings.persistentDisplayStyle,
        localCatalogDisplayStyle,
        persistentBindings.persistentGridColumns,
        localCatalogGridColumns
    ) {
        resolveCatalogScreenDisplaySettings(
            hasStateStore = stateStore != null,
            persistentDisplayStyle = persistentBindings.persistentDisplayStyle,
            localDisplayStyle = localCatalogDisplayStyle,
            persistentGridColumns = persistentBindings.persistentGridColumns,
            localGridColumns = localCatalogGridColumns
        )
    }
    val catalogDisplayStyle = displaySettings.displayStyle
    val catalogGridColumns = displaySettings.gridColumns
    val interactionBindings = buildCatalogScreenInteractionBindingsBundle(
        mutationInputs = CatalogScreenMutationInputs(
            coroutineScope = coroutineScope,
            stateStore = stateStore,
            currentBoardId = { board?.id },
            setCatalogMode = { catalogMode = it },
            currentCatalogNgWords = { catalogNgWords },
            currentWatchWords = { watchWords },
            currentCatalogNgFilteringEnabled = { catalogNgFilteringEnabled },
            setCatalogNgFilteringEnabled = { catalogNgFilteringEnabled = it },
            onFallbackCatalogNgWordsChanged = persistentBindings.onFallbackCatalogNgWordsChanged,
            onFallbackWatchWordsChanged = persistentBindings.onFallbackWatchWordsChanged,
            showSnackbar = snackbarHostState::showSnackbar,
            setLocalCatalogDisplayStyle = { localCatalogDisplayStyle = it },
            setLocalCatalogGridColumns = { localCatalogGridColumns = it },
            currentCatalogDisplayStyle = {
                if (stateStore != null) {
                    persistentBindings.persistentDisplayStyle
                } else {
                    localCatalogDisplayStyle
                }
            },
            catalogGridState = catalogGridState,
            catalogListState = catalogListState
        ),
        runtimeInputs = CatalogScreenRuntimeInputs(
            coroutineScope = coroutineScope,
            drawerState = drawerState,
            currentBoard = { board },
            stateStore = stateStore,
            currentCatalogMode = { catalogMode },
            currentCatalogLoadGeneration = { catalogLoadGeneration },
            setCatalogLoadGeneration = { catalogLoadGeneration = it },
            currentCatalogLoadJob = { catalogLoadJob },
            setCatalogLoadJob = { catalogLoadJob = it },
            currentIsRefreshing = { isRefreshing },
            setIsRefreshing = { isRefreshing = it },
            setCatalogUiState = { uiState.value = it },
            setLastCatalogItems = { lastCatalogItems = it },
            loadCatalogItems = ::loadCatalogItems,
            activeRepository = activeRepository,
            currentCreateThreadDraft = { createThreadDraft },
            currentCreateThreadImage = { createThreadImage },
            setCreateThreadDraft = { createThreadDraft = it },
            setCreateThreadImage = { createThreadImage = it },
            setShowCreateThreadDialog = { isVisible ->
                overlayState = setCatalogCreateThreadDialogVisible(overlayState, isVisible)
            },
            updateLastUsedDeleteKey = updateLastUsedDeleteKey,
            showSnackbar = snackbarHostState::showSnackbar,
            currentIsHistoryRefreshing = { isHistoryRefreshing },
            setIsHistoryRefreshing = { isHistoryRefreshing = it },
            onHistoryRefresh = onHistoryRefresh,
            currentPastSearchRuntimeState = { pastSearchRuntimeState },
            setPastSearchRuntimeState = { pastSearchRuntimeState = it },
            httpClient = httpClient,
            archiveSearchJson = archiveSearchJson,
            onHistoryEntrySelected = onHistoryEntrySelected,
            onBack = onBack,
            onHistoryCleared = onHistoryCleared,
            setShowGlobalSettings = { isVisible ->
                overlayState = setCatalogGlobalSettingsVisible(overlayState, isVisible)
            },
            setSearchQuery = { searchQuery = it },
            setSearchActive = { isSearchActive = it },
            lastUsedDeleteKey = lastUsedDeleteKey,
            currentCreateThreadPassword = { createThreadDraft.password },
            setCreateThreadPassword = {
                createThreadDraft = updateCreateThreadDraftPassword(createThreadDraft, it)
            },
            setShowPastThreadSearchDialog = { isVisible ->
                overlayState = setCatalogPastThreadSearchDialogVisible(overlayState, isVisible)
            },
            setShowModeDialog = { isVisible ->
                overlayState = setCatalogModeDialogVisible(overlayState, isVisible)
            },
            setShowSettingsMenu = { isVisible ->
                overlayState = setCatalogSettingsMenuVisible(overlayState, isVisible)
            }
        ),
        overlayInputs = CatalogScreenOverlayInputs(
            currentBoard = { board },
            currentCatalogMode = { catalogMode },
            currentPastSearchRuntimeState = { pastSearchRuntimeState },
            setPastSearchRuntimeState = { pastSearchRuntimeState = it },
            setShowGlobalSettings = { isVisible ->
                overlayState = setCatalogGlobalSettingsVisible(overlayState, isVisible)
            },
            setShowPastThreadSearchDialog = { isVisible ->
                overlayState = setCatalogPastThreadSearchDialogVisible(overlayState, isVisible)
            },
            setShowPastSearchSheetVisible = { isVisible ->
                overlayState = setCatalogPastSearchSheetVisible(overlayState, isVisible)
            },
            setShowModeDialog = { isVisible ->
                overlayState = setCatalogModeDialogVisible(overlayState, isVisible)
            },
            setShowDisplayStyleDialog = { isVisible ->
                overlayState = setCatalogDisplayStyleDialogVisible(overlayState, isVisible)
            },
            setShowSettingsMenu = { isVisible ->
                overlayState = setCatalogSettingsMenuVisible(overlayState, isVisible)
            },
            setNgManagementVisible = { isVisible ->
                overlayState = setCatalogNgManagementVisible(overlayState, isVisible)
            },
            setWatchWordsVisible = { isVisible ->
                overlayState = setCatalogWatchWordsVisible(overlayState, isVisible)
            },
            setCookieManagementVisible = { isVisible ->
                overlayState = setCatalogCookieManagementVisible(overlayState, isVisible)
            },
            currentArchiveSearchScope = { archiveSearchScope },
            setLastArchiveSearchScope = {
                pastSearchRuntimeState = pastSearchRuntimeState.copy(lastArchiveSearchScope = it)
            },
            setArchiveSearchQuery = { archiveSearchQuery = it },
            currentArchiveSearchQuery = { archiveSearchQuery },
            currentLastArchiveSearchScope = { pastSearchRuntimeState.lastArchiveSearchScope },
            onThreadSelected = onThreadSelected,
            urlLauncher = preparedSetup.urlLauncher,
            stateStore = stateStore,
            isPrivacyFilterEnabled = { isPrivacyFilterEnabled },
            coroutineScope = coroutineScope,
            cookieRepository = cookieRepository
        )
    )
    val mutationBindings = interactionBindings.mutationBindings
    val runtimeBindings = interactionBindings.runtimeBindings
    val performRefresh = runtimeBindings.executionBindings.performRefresh
    val initialLoadBindings = runtimeBindings.initialLoadBindings
    val createThreadBindings = runtimeBindings.createThreadBindings
    val historyDrawerCallbacks = runtimeBindings.historyDrawerCallbacks
    val lifecycleBindings = rememberCatalogScreenLifecycleBindings(
        coroutineScope,
        drawerState,
        isDrawerOpen,
        isSearchActive,
        onBack = onBack,
        onInitialLoad = initialLoadBindings.loadInitialCatalog,
        currentCatalogLoadJob = { catalogLoadJob },
        setCatalogLoadJob = { catalogLoadJob = it },
        currentPastSearchRuntimeState = { pastSearchRuntimeState },
        setPastSearchRuntimeState = { pastSearchRuntimeState = it },
        setSearchActive = { isSearchActive = it },
        setSearchQuery = { searchQuery = it }
    )
    DisposableEffect(Unit) {
        onDispose {
            lifecycleBindings.onDispose()
        }
    }
    PlatformBackHandler(enabled = lifecycleBindings.backAction == CatalogBackAction.CloseDrawer) {
        lifecycleBindings.onCloseDrawerBack()
    }
    PlatformBackHandler(enabled = lifecycleBindings.backAction == CatalogBackAction.ExitSearch) {
        lifecycleBindings.onExitSearchBack()
    }
    PlatformBackHandler(enabled = lifecycleBindings.backAction == CatalogBackAction.NavigateBack) {
        lifecycleBindings.onNavigateBack()
    }

    LaunchedEffect(board?.url, catalogMode) {
        lifecycleBindings.onInitialLoad()
    }
    val overlayBindings = interactionBindings.overlayBindings
    val (scaffoldBindings, overlayHostBindings) = buildCatalogScreenHostBindings(
        CatalogScreenHostBindingsInputs(
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
            runtimeBindings = runtimeBindings,
            preferencesState = preferencesState,
            uiState = uiState.value,
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
            catalogListState = catalogListState,
            overlayState = overlayState,
            overlayBindings = overlayBindings,
            createThreadDraft = createThreadDraft,
            setCreateThreadDraft = { createThreadDraft = it },
            createThreadImage = createThreadImage,
            setCreateThreadImage = { createThreadImage = it },
            setCreateThreadDialogVisible = { isVisible ->
                overlayState = setCatalogCreateThreadDialogVisible(overlayState, isVisible)
            },
            archiveSearchQuery = archiveSearchQuery,
            pastSearchRuntimeState = pastSearchRuntimeState,
            isPrivacyFilterEnabled = isPrivacyFilterEnabled,
            createThreadBindings = createThreadBindings,
            preferencesCallbacks = preferencesCallbacks,
            fileSystem = fileSystem,
            autoSavedThreadRepository = autoSavedThreadRepository,
            cookieRepository = cookieRepository
        )
    )

    CatalogScreenScaffold(bindings = scaffoldBindings, modifier = modifier)

    CatalogScreenOverlayHost(bindings = overlayHostBindings)
}
