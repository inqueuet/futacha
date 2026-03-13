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
import androidx.compose.ui.graphics.Color
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
import com.valoser.futacha.shared.network.extractArchiveSearchScope
import com.valoser.futacha.shared.network.fetchArchiveSearchResults
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.rememberUrlLauncher
import com.valoser.futacha.shared.util.SaveDirectorySelection
import io.ktor.client.HttpClient
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun CatalogScreen(
    board: BoardSummary?,
    history: List<ThreadHistoryEntry>,
    onBack: () -> Unit,
    onThreadSelected: (CatalogItem) -> Unit,
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit = {},
    onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit = {},
    onHistoryEntryUpdated: (ThreadHistoryEntry) -> Unit = {},
    onHistoryRefresh: suspend () -> Unit = {},
    onHistoryCleared: () -> Unit = {},
    repository: BoardRepository? = null,
    stateStore: com.valoser.futacha.shared.state.AppStateStore? = null,
    autoSavedThreadRepository: SavedThreadRepository? = null,
    cookieRepository: CookieRepository? = null,
    appVersion: String,
    isBackgroundRefreshEnabled: Boolean = false,
    onBackgroundRefreshChanged: (Boolean) -> Unit = {},
    isLightweightModeEnabled: Boolean = false,
    onLightweightModeChanged: (Boolean) -> Unit = {},
    fileSystem: com.valoser.futacha.shared.util.FileSystem? = null,
    manualSaveDirectory: String = DEFAULT_MANUAL_SAVE_ROOT,
    manualSaveLocation: com.valoser.futacha.shared.model.SaveLocation? = null,
    resolvedManualSaveDirectory: String? = null,
    onManualSaveDirectoryChanged: (String) -> Unit = {},
    attachmentPickerPreference: AttachmentPickerPreference = AttachmentPickerPreference.MEDIA,
    saveDirectorySelection: SaveDirectorySelection = SaveDirectorySelection.MANUAL_INPUT,
    onAttachmentPickerPreferenceChanged: (AttachmentPickerPreference) -> Unit = {},
    onSaveDirectorySelectionChanged: (SaveDirectorySelection) -> Unit = {},
    onOpenSaveDirectoryPicker: (() -> Unit)? = null,
    preferredFileManagerPackage: String? = null,
    preferredFileManagerLabel: String? = null,
    onFileManagerSelected: ((packageName: String, label: String) -> Unit)? = null,
    onClearPreferredFileManager: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    threadMenuEntries: List<ThreadMenuEntryConfig> = defaultThreadMenuEntries(),
    onThreadMenuEntriesChanged: (List<ThreadMenuEntryConfig>) -> Unit = {},
    catalogNavEntries: List<CatalogNavEntryConfig> = defaultCatalogNavEntries(),
    onCatalogNavEntriesChanged: (List<CatalogNavEntryConfig>) -> Unit = {},
    httpClient: HttpClient? = null
) {
    val activeRepository = remember(repository) {
        repository ?: FakeBoardRepository()
    }
    val archiveSearchScope = remember(board?.url) { extractArchiveSearchScope(board) }
    val coreBindings = rememberCatalogScreenCoreBindingsBundle(
        stateStore = stateStore,
        boardId = board?.id,
        initialArchiveSearchScope = archiveSearchScope
    )
    val runtimeObjects = coreBindings.runtimeObjects
    val snackbarHostState = runtimeObjects.snackbarHostState
    val coroutineScope = runtimeObjects.coroutineScope
    val drawerState = runtimeObjects.drawerState
    val isDrawerOpen = runtimeObjects.isDrawerOpen
    val persistentBindings = coreBindings.persistentBindings
    val mutableStateBundle = coreBindings.mutableStateBundle
    val uiState = mutableStateBundle.uiState
    var catalogMode by mutableStateBundle.catalogMode
    var isRefreshing by mutableStateBundle.isRefreshing
    var catalogLoadJob by mutableStateBundle.catalogLoadJob
    var catalogLoadGeneration by mutableStateBundle.catalogLoadGeneration
    var isHistoryRefreshing by mutableStateBundle.isHistoryRefreshing
    var isSearchActive by mutableStateBundle.isSearchActive
    var searchQuery by mutableStateBundle.searchQuery
    var debouncedSearchQuery by mutableStateBundle.debouncedSearchQuery
    var overlayState by mutableStateBundle.overlayState
    var createThreadDraft by mutableStateBundle.createThreadDraft
    var createThreadImage by mutableStateBundle.createThreadImage
    var archiveSearchQuery by mutableStateBundle.archiveSearchQuery
    var catalogNgFilteringEnabled by mutableStateBundle.catalogNgFilteringEnabled
    val catalogNgWords = persistentBindings.catalogNgWords
    val watchWords = persistentBindings.watchWords
    val lastUsedDeleteKey = persistentBindings.lastUsedDeleteKey
    val updateLastUsedDeleteKey = persistentBindings.updateLastUsedDeleteKey
    val archiveSearchJson = runtimeObjects.archiveSearchJson
    var pastSearchRuntimeState by mutableStateBundle.pastSearchRuntimeState
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
    LaunchedEffect(searchQuery) {
        val normalized = resolveCatalogDebouncedSearchQuery(searchQuery)
        if (normalized.isEmpty()) {
            debouncedSearchQuery = ""
            return@LaunchedEffect
        }
        delay(200L)
        debouncedSearchQuery = normalized
    }
    var lastCatalogItems by mutableStateBundle.lastCatalogItems
    suspend fun loadCatalogItems(currentBoard: BoardSummary, mode: CatalogMode): List<CatalogItem> {
        return loadCatalogItemsForMode(
            boardUrl = currentBoard.url,
            mode = mode,
            fetchCatalog = activeRepository::getCatalog
        )
    }
    val isPrivacyFilterEnabled = persistentBindings.isPrivacyFilterEnabled
    var localCatalogDisplayStyle by mutableStateBundle.localCatalogDisplayStyle
    val catalogDisplayStyle = if (stateStore != null) {
        persistentBindings.persistentDisplayStyle
    } else {
        localCatalogDisplayStyle
    }
    var localCatalogGridColumns by mutableStateBundle.localCatalogGridColumns
    val catalogGridColumns = if (stateStore != null) {
        persistentBindings.persistentGridColumns
    } else {
        localCatalogGridColumns
    }
    val interactionBindings = buildCatalogScreenInteractionBindingsBundle(
        coroutineScope = coroutineScope,
        drawerState = drawerState,
        currentBoard = { board },
        currentBoardId = { board?.id },
        currentCatalogMode = { catalogMode },
        setCatalogMode = { catalogMode = it },
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
        currentCatalogNgWords = { catalogNgWords },
        currentWatchWords = { watchWords },
        currentCatalogNgFilteringEnabled = { catalogNgFilteringEnabled },
        setCatalogNgFilteringEnabled = { catalogNgFilteringEnabled = it },
        onFallbackCatalogNgWordsChanged = persistentBindings.onFallbackCatalogNgWordsChanged,
        onFallbackWatchWordsChanged = persistentBindings.onFallbackWatchWordsChanged,
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
        catalogListState = catalogListState,
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
        urlLauncher = rememberUrlLauncher(),
        stateStore = stateStore,
        isPrivacyFilterEnabled = { isPrivacyFilterEnabled },
        cookieRepository = cookieRepository
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

    CatalogScreenScaffold(
        history = history,
        onHistoryEntryDismissed = onHistoryEntryDismissed,
        historyDrawerCallbacks = historyDrawerCallbacks,
        drawerState = drawerState,
        isDrawerOpen = isDrawerOpen,
        coroutineScope = coroutineScope,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        board = board,
        catalogMode = catalogMode,
        searchQuery = searchQuery,
        isSearchActive = isSearchActive,
        topBarCallbacks = runtimeBindings.topBarCallbacks,
        catalogNavEntries = catalogNavEntries,
        navigationCallbacks = runtimeBindings.navigationCallbacks,
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
        catalogListState = catalogListState
    )

    val overlayBindings = interactionBindings.overlayBindings
    CatalogScreenOverlayHost(
        overlayState = overlayState,
        overlayBindings = overlayBindings,
        createThreadDraft = createThreadDraft,
        setCreateThreadDraft = { createThreadDraft = it },
        createThreadImage = createThreadImage,
        setCreateThreadImage = { createThreadImage = it },
        setCreateThreadDialogVisible = { isVisible ->
            overlayState = setCatalogCreateThreadDialogVisible(overlayState, isVisible)
        },
        attachmentPickerPreference = attachmentPickerPreference,
        preferredFileManagerPackage = preferredFileManagerPackage,
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
        appVersion = appVersion,
        isBackgroundRefreshEnabled = isBackgroundRefreshEnabled,
        onBackgroundRefreshChanged = onBackgroundRefreshChanged,
        isLightweightModeEnabled = isLightweightModeEnabled,
        onLightweightModeChanged = onLightweightModeChanged,
        manualSaveDirectory = manualSaveDirectory,
        resolvedManualSaveDirectory = resolvedManualSaveDirectory,
        onManualSaveDirectoryChanged = onManualSaveDirectoryChanged,
        saveDirectorySelection = saveDirectorySelection,
        onSaveDirectorySelectionChanged = onSaveDirectorySelectionChanged,
        onOpenSaveDirectoryPicker = onOpenSaveDirectoryPicker,
        history = history,
        fileSystem = fileSystem,
        autoSavedThreadRepository = autoSavedThreadRepository,
        threadMenuEntries = threadMenuEntries,
        onThreadMenuEntriesChanged = onThreadMenuEntriesChanged,
        catalogNavEntries = catalogNavEntries,
        onCatalogNavEntriesChanged = onCatalogNavEntriesChanged,
        preferredFileManagerLabel = preferredFileManagerLabel,
        onFileManagerSelected = onFileManagerSelected,
        onClearPreferredFileManager = onClearPreferredFileManager,
        cookieRepository = cookieRepository
    )
}


/**
 * Platform-specific image picker button
 */
@Composable
expect fun ImagePickerButton(
    onImageSelected: (com.valoser.futacha.shared.util.ImageData) -> Unit,
    preference: AttachmentPickerPreference = AttachmentPickerPreference.MEDIA,
    preferredFileManagerPackage: String? = null
)

/**
 * Platform-specific directory picker launcher
 */
@Composable
expect fun rememberDirectoryPickerLauncher(
    onDirectorySelected: (com.valoser.futacha.shared.model.SaveLocation) -> Unit,
    preferredFileManagerPackage: String? = null
): () -> Unit

// Futaba color scheme (for ThreadScreen)

private val FutabaBackground = Color(0xFFFFFFEE)
private val FutabaSurface = Color(0xFFF0E0D6)
private val FutabaSurfaceVariant = Color(0xFFE9CCCC)
private val FutabaLabelSurface = Color(0xFFEEAA88)
private val FutabaText = Color(0xFF800000)
private val FutabaTextDim = Color(0xCC800000)
private val FutabaAccentRed = Color(0xFFCC1105)
private val FutabaNameGreen = Color(0xFF117743)
private val FutabaQuoteGreen = Color(0xFF789922)

@Composable
internal fun rememberFutabaThreadColorScheme(
    base: ColorScheme = MaterialTheme.colorScheme
): ColorScheme {
    return remember(base) {
        base.copy(
            primary = FutabaSurface,
            onPrimary = FutabaText,
            primaryContainer = FutabaLabelSurface,
            onPrimaryContainer = FutabaText,
            inversePrimary = FutabaAccentRed,
            secondary = FutabaNameGreen,
            onSecondary = FutabaBackground,
            secondaryContainer = FutabaSurface,
            onSecondaryContainer = FutabaNameGreen,
            tertiary = FutabaAccentRed,
            onTertiary = FutabaBackground,
            tertiaryContainer = FutabaSurfaceVariant,
            onTertiaryContainer = FutabaText,
            background = FutabaBackground,
            onBackground = FutabaText,
            surface = FutabaSurface,
            onSurface = FutabaText,
            surfaceVariant = FutabaSurfaceVariant,
            onSurfaceVariant = FutabaTextDim,
            surfaceTint = FutabaSurface,
            error = FutabaAccentRed,
            onError = FutabaBackground,
            errorContainer = FutabaSurfaceVariant,
            onErrorContainer = FutabaText,
            outline = FutabaText,
            outlineVariant = FutabaTextDim
        )
    }
}
