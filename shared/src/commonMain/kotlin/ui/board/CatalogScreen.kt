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
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.rememberUrlLauncher
import com.valoser.futacha.shared.util.SaveDirectorySelection
import io.ktor.client.HttpClient
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime

sealed interface CatalogUiState {
    data object Loading : CatalogUiState
    data class Success(val items: List<CatalogItem>) : CatalogUiState
    data class Error(val message: String = "カタログを読み込めませんでした") : CatalogUiState
}

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
    val uiState: MutableState<CatalogUiState> =
        remember { mutableStateOf(CatalogUiState.Loading) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val isDrawerOpen by remember {
        derivedStateOf {
            drawerState.currentValue == DrawerValue.Open ||
                drawerState.targetValue == DrawerValue.Open
        }
    }
    // ボードごとにモードを覚える。キーなしだと画面を離れるたびにデフォルトへ戻ってしまう。
    val catalogModeMapState = stateStore?.catalogModes?.collectAsState(initial = emptyMap())
    var catalogMode by rememberSaveable(board?.id) {
        mutableStateOf(catalogModeMapState?.value?.get(board?.id.orEmpty()) ?: CatalogMode.default)
    }
    var isRefreshing by remember { mutableStateOf(false) }
    var catalogLoadJob by remember { mutableStateOf<Job?>(null) }
    var catalogLoadGeneration by remember { mutableStateOf(0L) }
    var isHistoryRefreshing by remember { mutableStateOf(false) }
    var isSearchActive by rememberSaveable(board?.id) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(board?.id) { mutableStateOf("") }
    var showModeDialog by remember { mutableStateOf(false) }
    var showDisplayStyleDialog by remember { mutableStateOf(false) }
    var showCreateThreadDialog by remember { mutableStateOf(false) }
    var createThreadName by rememberSaveable(board?.id) { mutableStateOf("") }
    var createThreadEmail by rememberSaveable(board?.id) { mutableStateOf("") }
    var createThreadTitle by rememberSaveable(board?.id) { mutableStateOf("") }
    var createThreadComment by rememberSaveable(board?.id) { mutableStateOf("") }
    var createThreadPassword by rememberSaveable(board?.id) { mutableStateOf("") }
    var createThreadImage by remember(board?.id) { mutableStateOf<com.valoser.futacha.shared.util.ImageData?>(null) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showPastThreadSearchDialog by remember { mutableStateOf(false) }
    var isGlobalSettingsVisible by remember { mutableStateOf(false) }
    var isCookieManagementVisible by remember { mutableStateOf(false) }
    var isNgManagementVisible by remember { mutableStateOf(false) }
    var isWatchWordsVisible by remember { mutableStateOf(false) }
    var archiveSearchQuery by rememberSaveable(board?.id) { mutableStateOf("") }
    var catalogNgFilteringEnabled by rememberSaveable(board?.id) { mutableStateOf(true) }
    val fallbackCatalogNgWordsState = rememberSaveable(board?.id) { mutableStateOf<List<String>>(emptyList()) }
    val catalogNgWordsState = stateStore?.catalogNgWords?.collectAsState(initial = fallbackCatalogNgWordsState.value)
    val catalogNgWords = catalogNgWordsState?.value ?: fallbackCatalogNgWordsState.value
    val fallbackWatchWordsState = rememberSaveable(board?.id) { mutableStateOf<List<String>>(emptyList()) }
    val watchWordsState = stateStore?.watchWords?.collectAsState(initial = fallbackWatchWordsState.value)
    val watchWords = watchWordsState?.value ?: fallbackWatchWordsState.value
    val lastUsedDeleteKeyState = stateStore?.lastUsedDeleteKey?.collectAsState(initial = "")
    var fallbackDeleteKey by rememberSaveable { mutableStateOf("") }
    val lastUsedDeleteKey = lastUsedDeleteKeyState?.value ?: fallbackDeleteKey
    val updateLastUsedDeleteKey: (String) -> Unit = { value ->
        val sanitized = value.trim().take(8)
        val store = stateStore
        if (store != null) {
            coroutineScope.launch { store.setLastUsedDeleteKey(sanitized) }
        } else {
            fallbackDeleteKey = sanitized
        }
    }
    val archiveSearchScope = remember(board?.url) { extractArchiveSearchScope(board) }
    val archiveSearchJson = remember {
        Json {
            ignoreUnknownKeys = true
        }
    }
    var isPastSearchSheetVisible by remember { mutableStateOf(false) }
    var pastSearchState by remember { mutableStateOf<ArchiveSearchState>(ArchiveSearchState.Idle) }
    var pastSearchJob by remember { mutableStateOf<Job?>(null) }
    var pastSearchGeneration by remember { mutableStateOf(0L) }
    var lastArchiveSearchScope by remember { mutableStateOf<ArchiveSearchScope?>(archiveSearchScope) }
    val showNgMessage: (String) -> Unit = { message ->
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }
    DisposableEffect(Unit) {
        onDispose {
            catalogLoadJob?.cancel()
            catalogLoadJob = null
            pastSearchJob?.cancel()
            pastSearchJob = null
        }
    }
    LaunchedEffect(board?.id, catalogModeMapState?.value) {
        val boardId = board?.id ?: return@LaunchedEffect
        val persisted = catalogModeMapState?.value?.get(boardId) ?: return@LaunchedEffect
        catalogMode = persisted
    }
    LaunchedEffect(board?.url) {
        pastSearchGeneration += 1L
        pastSearchJob?.cancel()
        pastSearchJob = null
        isPastSearchSheetVisible = false
        pastSearchState = ArchiveSearchState.Idle
        lastArchiveSearchScope = archiveSearchScope
    }
    var lastCatalogItems by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    val persistCatalogMode: (CatalogMode) -> Unit = { mode ->
        catalogMode = mode
        val boardId = board?.id
        val store = stateStore
        if (boardId != null && store != null) {
            coroutineScope.launch {
                store.setCatalogMode(boardId, mode)
            }
        }
    }
    suspend fun handleWatchWordMatches(
        catalog: List<CatalogItem>,
        filters: List<String> = watchWords
    ) {
        val normalizedFilters = filters
            .mapNotNull { it.trim().takeIf { trimmed -> trimmed.isNotBlank() }?.lowercase() }
            .distinct()
        if (normalizedFilters.isEmpty()) return
        val currentBoard = board ?: return
        val currentStateStore = stateStore ?: return

        val timestamp = Clock.System.now().toEpochMilliseconds()
        val matchedEntries = withContext(AppDispatchers.parsing) {
            catalog.mapNotNull { item ->
                val titleText = item.title?.lowercase().orEmpty()
                if (titleText.isEmpty()) return@mapNotNull null
                if (!normalizedFilters.any { titleText.contains(it) }) return@mapNotNull null
                ThreadHistoryEntry(
                    threadId = item.id,
                    boardId = currentBoard.id,
                    title = item.title?.takeIf { it.isNotBlank() } ?: "無題",
                    titleImageUrl = item.thumbnailUrl ?: "",
                    boardName = currentBoard.name,
                    boardUrl = currentBoard.url,
                    lastVisitedEpochMillis = timestamp,
                    replyCount = item.replyCount
                )
            }
        }
        if (matchedEntries.isEmpty()) return

        try {
            currentStateStore.prependOrReplaceHistoryEntries(matchedEntries)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(CATALOG_SCREEN_TAG, "Failed to append watch-word matched threads to history", e)
        }
    }
    val persistCatalogNgWords: (List<String>) -> Unit = { updated ->
        if (stateStore != null) {
            coroutineScope.launch {
                stateStore.setCatalogNgWords(updated)
            }
        } else {
            fallbackCatalogNgWordsState.value = updated
        }
    }
    val addCatalogNgWordEntry: (String) -> Unit = { value ->
        val trimmed = value.trim()
        when {
            trimmed.isEmpty() -> showNgMessage("NGワードに含める文字を入力してください")
            catalogNgWords.any { it.equals(trimmed, ignoreCase = true) } -> showNgMessage("そのNGワードはすでに登録されています")
            else -> {
                persistCatalogNgWords(catalogNgWords + trimmed)
                showNgMessage("NGワードを追加しました")
            }
        }
    }
    val removeCatalogNgWordEntry: (String) -> Unit = { entry ->
        persistCatalogNgWords(catalogNgWords.filterNot { it == entry })
        showNgMessage("NGワードを削除しました")
    }
    val toggleCatalogNgFiltering: () -> Unit = {
        catalogNgFilteringEnabled = !catalogNgFilteringEnabled
        showNgMessage(if (catalogNgFilteringEnabled) "NG表示を有効にしました" else "NG表示を無効にしました")
    }
    val persistWatchWords: (List<String>) -> Unit = { updated ->
        if (stateStore != null) {
            coroutineScope.launch {
                stateStore.setWatchWords(updated)
            }
        } else {
            fallbackWatchWordsState.value = updated
        }
    }
    val addWatchWordEntry: (String) -> Unit = { value ->
        val trimmed = value.trim()
        when {
            trimmed.isEmpty() -> showNgMessage("監視ワードを入力してください")
            watchWords.any { it.equals(trimmed, ignoreCase = true) } -> showNgMessage("そのワードはすでに登録されています")
            else -> {
                persistWatchWords(watchWords + trimmed)
                showNgMessage("監視ワードを追加しました")
                coroutineScope.launch {
                    handleWatchWordMatches(lastCatalogItems, watchWords + trimmed)
                }
            }
        }
    }
    val removeWatchWordEntry: (String) -> Unit = { entry ->
        persistWatchWords(watchWords.filterNot { it == entry })
        showNgMessage("監視ワードを削除しました")
    }
    val isPrivacyFilterEnabled by stateStore?.isPrivacyFilterEnabled?.collectAsState(initial = false)
        ?: remember { mutableStateOf(false) }
    val catalogGridState = rememberLazyGridState()
    val catalogListState = rememberLazyListState()
    val persistentDisplayStyleState = stateStore?.catalogDisplayStyle?.collectAsState(initial = CatalogDisplayStyle.Grid)
    var localCatalogDisplayStyle by rememberSaveable { mutableStateOf(CatalogDisplayStyle.Grid) }
    val catalogDisplayStyle = persistentDisplayStyleState?.value ?: localCatalogDisplayStyle
    val persistentGridColumnsState = stateStore?.catalogGridColumns?.collectAsState(initial = DEFAULT_CATALOG_GRID_COLUMNS)
    var localCatalogGridColumns by rememberSaveable { mutableStateOf(DEFAULT_CATALOG_GRID_COLUMNS) }
    val catalogGridColumns = persistentGridColumnsState?.value ?: localCatalogGridColumns
    val updateCatalogDisplayStyle: (CatalogDisplayStyle) -> Unit = { style ->
        if (stateStore != null) {
            coroutineScope.launch {
                stateStore.setCatalogDisplayStyle(style)
            }
        } else {
            localCatalogDisplayStyle = style
        }
    }
    val updateCatalogGridColumns: (Int) -> Unit = { columns ->
        val clamped = columns.coerceIn(MIN_CATALOG_GRID_COLUMNS, MAX_CATALOG_GRID_COLUMNS)
        if (stateStore != null) {
            coroutineScope.launch {
                stateStore.setCatalogGridColumns(clamped)
            }
        } else {
            localCatalogGridColumns = clamped
        }
    }
    val handleHistoryRefresh: () -> Unit = handleHistoryRefresh@{
        if (isHistoryRefreshing) return@handleHistoryRefresh
        isHistoryRefreshing = true
        coroutineScope.launch {
            try {
                onHistoryRefresh()
                snackbarHostState.showSnackbar("履歴を更新しました")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("履歴の更新に失敗しました: ${e.message ?: "不明なエラー"}")
            } finally {
                isHistoryRefreshing = false
            }
        }
    }

    val performRefresh: () -> Unit = refresh@{
        val currentBoard = board ?: return@refresh
        if (isRefreshing) return@refresh
        val requestGeneration = catalogLoadGeneration + 1L
        catalogLoadGeneration = requestGeneration
        isRefreshing = true
        catalogLoadJob?.cancel()
        catalogLoadJob = coroutineScope.launch {
            val runningJob = coroutineContext[Job]
            try {
                val catalog = activeRepository.getCatalog(currentBoard.url, catalogMode)
                if (!isActive || catalogLoadGeneration != requestGeneration) return@launch
                uiState.value = CatalogUiState.Success(catalog)
                lastCatalogItems = catalog
                handleWatchWordMatches(catalog)
                snackbarHostState.showSnackbar("カタログを更新しました")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isActive && catalogLoadGeneration == requestGeneration) {
                    snackbarHostState.showSnackbar("更新に失敗しました")
                }
            } finally {
                if (
                    runningJob != null &&
                    catalogLoadJob == runningJob &&
                    catalogLoadGeneration == requestGeneration
                ) {
                    isRefreshing = false
                    catalogLoadJob = null
                }
            }
        }
    }

    val runPastThreadSearch: (String, ArchiveSearchScope?) -> Boolean = runPastThreadSearch@{ query, scope ->
        val client = httpClient
        if (client == null) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("ネットワーククライアントが利用できません")
            }
            return@runPastThreadSearch false
        }
        val requestGeneration = pastSearchGeneration + 1L
        pastSearchGeneration = requestGeneration
        pastSearchState = ArchiveSearchState.Loading
        pastSearchJob?.cancel()
        pastSearchJob = coroutineScope.launch {
            val runningJob = coroutineContext[Job]
            try {
                val items = fetchArchiveSearchResults(client, query, scope, archiveSearchJson)
                if (!isActive || pastSearchGeneration != requestGeneration) return@launch
                pastSearchState = ArchiveSearchState.Success(items)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (error: Throwable) {
                if (isActive && pastSearchGeneration == requestGeneration) {
                    pastSearchState = ArchiveSearchState.Error(error.message ?: "検索に失敗しました")
                }
            } finally {
                if (runningJob != null && pastSearchJob == runningJob) {
                    pastSearchJob = null
                }
            }
        }
        true
    }

    PlatformBackHandler(enabled = isDrawerOpen) {
        coroutineScope.launch { drawerState.close() }
    }
    PlatformBackHandler(enabled = !isDrawerOpen && isSearchActive) {
        isSearchActive = false
        searchQuery = ""
    }
    PlatformBackHandler(enabled = !isDrawerOpen && !isSearchActive) {
        onBack()
    }

    LaunchedEffect(board?.url, catalogMode) {
        if (board == null) {
            catalogLoadGeneration += 1L
            catalogLoadJob?.cancel()
            catalogLoadJob = null
            isRefreshing = false
            uiState.value = CatalogUiState.Error("板が選択されていません")
            return@LaunchedEffect
        }
        val requestGeneration = catalogLoadGeneration + 1L
        catalogLoadGeneration = requestGeneration
        catalogLoadJob?.cancel()
        isRefreshing = false
        uiState.value = CatalogUiState.Loading
        catalogLoadJob = coroutineScope.launch {
            val runningJob = coroutineContext[Job]
            try {
                val catalog = activeRepository.getCatalog(board.url, catalogMode)
                if (!isActive || catalogLoadGeneration != requestGeneration) return@launch
                uiState.value = CatalogUiState.Success(catalog)
                lastCatalogItems = catalog
                handleWatchWordMatches(catalog)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isActive && catalogLoadGeneration == requestGeneration) {
                    val message = when {
                        e.message?.contains("timeout", ignoreCase = true) == true -> "タイムアウト: サーバーが応答しません"
                        e.message?.contains("404") == true -> "板が見つかりません (404)"
                        e.message?.contains("500") == true -> "サーバーエラー (500)"
                        e.message?.contains("HTTP error") == true -> "ネットワークエラー: ${e.message}"
                        e.message?.contains("exceeds maximum") == true -> "データサイズが大きすぎます"
                        else -> "カタログを読み込めませんでした: ${e.message ?: "不明なエラー"}"
                    }
                    uiState.value = CatalogUiState.Error(message)
                }
            } finally {
                if (runningJob != null && catalogLoadJob == runningJob) {
                    catalogLoadJob = null
                }
            }
        }
    }

    val handleHistorySelection: (ThreadHistoryEntry) -> Unit = { entry ->
        coroutineScope.launch { drawerState.close() }
        onHistoryEntrySelected(entry)
    }

    val handleBatchDelete: () -> Unit = {
        coroutineScope.launch {
            onHistoryCleared()
            snackbarHostState.showSnackbar("履歴を一括削除しました")
            drawerState.close()
        }
    }

    val scrollCatalogToTop: () -> Unit = {
        coroutineScope.launch {
            when (catalogDisplayStyle) {
                CatalogDisplayStyle.Grid -> {
                    if (catalogGridState.layoutInfo.totalItemsCount > 0) {
                        catalogGridState.animateScrollToItem(0)
                    }
                }
                CatalogDisplayStyle.List -> {
                    if (catalogListState.layoutInfo.totalItemsCount > 0) {
                        catalogListState.animateScrollToItem(0)
                    }
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            HistoryDrawerContent(
                history = history,
                onHistoryEntryDismissed = onHistoryEntryDismissed,
                onHistoryEntrySelected = handleHistorySelection,
                onBoardClick = {
                    coroutineScope.launch {
                        drawerState.close()
                        onBack()
                    }
                },
                onRefreshClick = handleHistoryRefresh,
                onBatchDeleteClick = handleBatchDelete,
                onSettingsClick = {
                    isGlobalSettingsVisible = true
                }
            )
        }
    ) {
        Scaffold(
            modifier = modifier,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CatalogTopBar(
                    board = board,
                    mode = catalogMode,
                    searchQuery = searchQuery,
                    isSearchActive = isSearchActive,
                    onSearchQueryChange = { searchQuery = it },
                    onSearchActiveChange = { active ->
                        isSearchActive = active
                        if (!active) {
                            searchQuery = ""
                        }
                    },
                    onNavigationClick = { coroutineScope.launch { drawerState.open() } },
                    onModeSelected = { persistCatalogMode(it) },
                    onMenuAction = { action ->
                        if (action == CatalogMenuAction.Settings) {
                            isGlobalSettingsVisible = true
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("${action.label} はモックでのみ動作です")
                            }
                        }
                    }
                )
            },
            bottomBar = {
                CatalogNavigationBar(
                    menuEntries = catalogNavEntries,
                    onNavigate = { destination ->
                        when (destination) {
                            CatalogNavEntryId.CreateThread -> {
                                if (createThreadPassword.isBlank()) {
                                    createThreadPassword = lastUsedDeleteKey
                                }
                                showCreateThreadDialog = true
                            }
                            CatalogNavEntryId.ScrollToTop -> scrollCatalogToTop()
                            CatalogNavEntryId.RefreshCatalog -> performRefresh()
                            CatalogNavEntryId.PastThreadSearch -> showPastThreadSearchDialog = true
                            CatalogNavEntryId.Mode -> showModeDialog = true
                            CatalogNavEntryId.Settings -> showSettingsMenu = true
                        }
                    }
                )
            }
        ) { innerPadding ->
            val contentModifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(isDrawerOpen) {
                    if (!isDrawerOpen) return@pointerInput
                    awaitPointerEventScope {
                        awaitFirstDown()
                        coroutineScope.launch { drawerState.close() }
                    }
                }
            val currentState = uiState.value
            when (val state = currentState) {
                CatalogUiState.Loading -> LoadingCatalog(modifier = contentModifier)
                is CatalogUiState.Error -> CatalogError(message = state.message, modifier = contentModifier)
                is CatalogUiState.Success -> {
                    // FIX: カタログのソート・フィルタリングをバックグラウンドで実行し、UIスレッドの負荷を軽減
                    val visibleItems: List<CatalogItem> by produceState<List<CatalogItem>>(
                        initialValue = emptyList(),
                        key1 = state.items,
                        key2 = listOf(catalogMode, catalogNgWords, catalogNgFilteringEnabled, searchQuery)
                    ) {
                        value = withContext(AppDispatchers.parsing) {
                            state.items
                                .let { catalogMode.applyLocalSort(it) }
                                .filterByCatalogNgWords(catalogNgWords, catalogNgFilteringEnabled)
                                .filterByQuery(searchQuery)
                        }
                    }

                    CatalogSuccessContent(
                        items = visibleItems,
                        board = board,
                        repository = activeRepository,
                        isSearching = searchQuery.isNotBlank(),
                        onThreadSelected = onThreadSelected,
                        onRefresh = performRefresh,
                        isRefreshing = isRefreshing,
                        displayStyle = catalogDisplayStyle,
                        gridColumns = catalogGridColumns,
                        gridState = catalogGridState,
                        listState = catalogListState,
                        modifier = contentModifier
                    )
                }
            }
        }

        // Privacy filter overlay - allows interactions to pass through
        if (isPrivacyFilterEnabled) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                drawRect(color = Color.White.copy(alpha = 0.5f))
            }
        }

        if (showModeDialog) {
            AlertDialog(
                onDismissRequest = { showModeDialog = false },
                title = { Text("モード選択") },
                text = {
                    Column {
                        CatalogMode.entries.forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        persistCatalogMode(mode)
                                        showModeDialog = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = catalogMode == mode,
                                    onClick = {
                                        persistCatalogMode(mode)
                                        showModeDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showModeDialog = false }) {
                        Text("閉じる")
                    }
                }
            )
        }

        if (showDisplayStyleDialog) {
            DisplayStyleDialog(
                currentStyle = catalogDisplayStyle,
                currentGridColumns = catalogGridColumns,
                onStyleSelected = { style ->
                    updateCatalogDisplayStyle(style)
                    showDisplayStyleDialog = false
                },
                onGridColumnsSelected = { columns ->
                    updateCatalogGridColumns(columns)
                },
                onDismiss = { showDisplayStyleDialog = false }
            )
        }

        if (showCreateThreadDialog) {
            val resetCreateThreadDraft: () -> Unit = {
                createThreadName = ""
                createThreadEmail = ""
                createThreadTitle = ""
                createThreadComment = ""
                createThreadPassword = ""
                createThreadImage = null
            }
            val isCreateThreadSubmitEnabled = createThreadTitle.isNotBlank() || createThreadComment.isNotBlank()
            CreateThreadDialog(
                boardName = board?.name,
                attachmentPickerPreference = attachmentPickerPreference,
                preferredFileManagerPackage = preferredFileManagerPackage,
                name = createThreadName,
                onNameChange = { createThreadName = it },
                email = createThreadEmail,
                onEmailChange = { createThreadEmail = it },
                title = createThreadTitle,
                onTitleChange = { createThreadTitle = it },
                comment = createThreadComment,
                onCommentChange = { createThreadComment = it },
                password = createThreadPassword,
                onPasswordChange = { createThreadPassword = it },
                selectedImage = createThreadImage,
                onImageSelected = { createThreadImage = it },
                isSubmitEnabled = isCreateThreadSubmitEnabled,
                onDismiss = { showCreateThreadDialog = false },
                onSubmit = {
                    showCreateThreadDialog = false
                    val boardSummary = board
                    if (boardSummary == null) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("板が選択されていません")
                        }
                        return@CreateThreadDialog
                    }
                    val trimmedPassword = createThreadPassword.trim()
                    if (trimmedPassword.isNotBlank()) {
                        updateLastUsedDeleteKey(trimmedPassword)
                    }
                    val name = createThreadName
                    val email = createThreadEmail
                    val title = createThreadTitle
                    val comment = createThreadComment
                    val password = trimmedPassword
                    val imageData = createThreadImage
                    coroutineScope.launch {
                        try {
                            val threadId = activeRepository.createThread(
                                board = boardSummary.url,
                                name = name,
                                email = email,
                                subject = title,
                                comment = comment,
                                password = password,
                                imageFile = imageData?.bytes,
                                imageFileName = imageData?.fileName,
                                textOnly = imageData == null
                            )
                            if (threadId.isNullOrBlank()) {
                                snackbarHostState.showSnackbar("スレッドを作成しました。カタログ更新で確認してください")
                            } else {
                                snackbarHostState.showSnackbar("スレッドを作成しました (ID: $threadId)")
                            }
                            resetCreateThreadDraft()
                            // Refresh catalog to show the new thread
                            performRefresh()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("スレッド作成に失敗しました: ${e.message ?: "不明なエラー"}")
                        }
                    }
                },
                onClear = resetCreateThreadDraft
            )
        }

        val urlLauncher = rememberUrlLauncher()

        if (showPastThreadSearchDialog) {
            PastThreadSearchDialog(
                initialQuery = archiveSearchQuery.ifBlank { searchQuery },
                onDismiss = { showPastThreadSearchDialog = false },
                onSearch = { query ->
                    val trimmed = query.trim()
                    val appliedScope = archiveSearchScope
                    lastArchiveSearchScope = archiveSearchScope
                    archiveSearchQuery = trimmed
                    showPastThreadSearchDialog = false
                    if (runPastThreadSearch(trimmed, appliedScope)) {
                        isPastSearchSheetVisible = true
                    }
                }
            )
        }

        if (isPastSearchSheetVisible) {
            PastThreadSearchResultSheet(
                state = pastSearchState,
                onDismiss = {
                    pastSearchGeneration += 1L
                    pastSearchJob?.cancel()
                    pastSearchJob = null
                    isPastSearchSheetVisible = false
                },
                onRetry = {
                    runPastThreadSearch(archiveSearchQuery, lastArchiveSearchScope)
                },
                onItemSelected = { item ->
                    val catalogItem = CatalogItem(
                        id = item.threadId,
                        threadUrl = item.htmlUrl,
                        title = item.title,
                        thumbnailUrl = item.thumbUrl,
                        fullImageUrl = item.thumbUrl,
                        replyCount = 0
                    )
                    pastSearchGeneration += 1L
                    pastSearchJob?.cancel()
                    pastSearchJob = null
                    isPastSearchSheetVisible = false
                    onThreadSelected(catalogItem)
                }
            )
        }

        if (showSettingsMenu) {
            CatalogSettingsSheet(
                onDismiss = { showSettingsMenu = false },
                onAction = { menuItem ->
                    when (menuItem) {
                        CatalogSettingsMenuItem.ScrollToTop -> scrollCatalogToTop()
                        CatalogSettingsMenuItem.DisplayStyle -> showDisplayStyleDialog = true
                        CatalogSettingsMenuItem.NgManagement -> {
                            isNgManagementVisible = true
                        }
                        CatalogSettingsMenuItem.WatchWords -> {
                            isWatchWordsVisible = true
                        }
                        CatalogSettingsMenuItem.ExternalApp -> {
                            board?.let { b ->
                                val catalogUrl = if (catalogMode.sortParam != null) {
                                    "${b.url.trimEnd('/')}/futaba.php?mode=cat&sort=${catalogMode.sortParam}"
                                } else {
                                    "${b.url.trimEnd('/')}/futaba.php?mode=cat"
                                }
                                urlLauncher(catalogUrl)
                            }
                        }
                        CatalogSettingsMenuItem.Privacy -> {
                            coroutineScope.launch {
                                stateStore?.setPrivacyFilterEnabled(!isPrivacyFilterEnabled)
                            }
                        }
                    }
                    showSettingsMenu = false
                }
            )
        }

        if (isGlobalSettingsVisible) {
            GlobalSettingsScreen(
                onBack = { isGlobalSettingsVisible = false },
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
                onOpenCookieManager = cookieRepository?.let {
                    {
                        isGlobalSettingsVisible = false
                        isCookieManagementVisible = true
                    }
                },
                historyEntries = history,
                fileSystem = fileSystem,
                autoSavedThreadRepository = autoSavedThreadRepository,
                threadMenuEntries = threadMenuEntries,
                onThreadMenuEntriesChanged = onThreadMenuEntriesChanged,
                catalogNavEntries = catalogNavEntries,
                onCatalogNavEntriesChanged = onCatalogNavEntriesChanged,
                preferredFileManagerLabel = preferredFileManagerLabel,
                onFileManagerSelected = onFileManagerSelected,
                onClearPreferredFileManager = onClearPreferredFileManager
            )
        }

        if (isCookieManagementVisible && cookieRepository != null) {
            CookieManagementScreen(
                onBack = { isCookieManagementVisible = false },
                repository = cookieRepository
            )
        }

        if (isNgManagementVisible) {
            NgManagementSheet(
                ngHeaders = emptyList(),
                ngWords = catalogNgWords,
                ngFilteringEnabled = catalogNgFilteringEnabled,
                onDismiss = { isNgManagementVisible = false },
                onAddHeader = {},
                onAddWord = addCatalogNgWordEntry,
                onRemoveHeader = {},
                onRemoveWord = removeCatalogNgWordEntry,
                onToggleFiltering = toggleCatalogNgFiltering,
                includeHeaderSection = false
            )
        }
        if (isWatchWordsVisible) {
            WatchWordsSheet(
                watchWords = watchWords,
                onAddWord = addWatchWordEntry,
                onRemoveWord = removeWatchWordEntry,
                onDismiss = { isWatchWordsVisible = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateThreadDialog(
    boardName: String?,
    attachmentPickerPreference: AttachmentPickerPreference,
    preferredFileManagerPackage: String?,
    name: String,
    onNameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    title: String,
    onTitleChange: (String) -> Unit,
    comment: String,
    onCommentChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    selectedImage: com.valoser.futacha.shared.util.ImageData?,
    onImageSelected: (com.valoser.futacha.shared.util.ImageData?) -> Unit,
    isSubmitEnabled: Boolean,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit
) {
    val emailPresets = remember { listOf("ID表示", "IP表示", "sage") }

    ThreadFormDialog(
        title = "スレ立て",
        subtitle = boardName?.takeIf { it.isNotBlank() },
        barColorScheme = MaterialTheme.colorScheme,
        attachmentPickerPreference = attachmentPickerPreference,
        preferredFileManagerPackage = preferredFileManagerPackage,
        emailPresets = emailPresets,
        comment = comment,
        onCommentChange = onCommentChange,
        name = name,
        onNameChange = onNameChange,
        email = email,
        onEmailChange = onEmailChange,
        subject = title,
        onSubjectChange = onTitleChange,
        password = password,
        onPasswordChange = onPasswordChange,
        selectedImage = selectedImage,
        onImageSelected = onImageSelected,
        onDismiss = onDismiss,
        onSubmit = onSubmit,
        onClear = onClear,
        isSubmitEnabled = isSubmitEnabled,
        sendDescription = "スレ立て",
        showSubject = true,
        showPassword = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PastThreadSearchDialog(
    initialQuery: String,
    onDismiss: () -> Unit,
    onSearch: (query: String) -> Unit
) {
    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null
            )
        },
        title = { Text("過去スレ検索") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("検索ワード") },
                    placeholder = { Text("例: ふたば (空欄で全件取得)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSearch(query) }
            ) {
                Text("検索")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PastThreadSearchResultSheet(
    state: ArchiveSearchState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onItemSelected: (ArchiveSearchItem) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "過去スレ検索結果",
                style = MaterialTheme.typography.titleMedium
            )
            when (state) {
                ArchiveSearchState.Idle -> {
                    Text(
                        text = "検索ワードを入力するとここに結果が表示されます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ArchiveSearchState.Loading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text("検索中…")
                    }
                }
                is ArchiveSearchState.Error -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = onRetry) {
                            Text("再試行")
                        }
                    }
                }
                is ArchiveSearchState.Success -> {
                    if (state.items.isEmpty()) {
                        Text(
                            text = "見つかりませんでした",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(state.items, key = { it.threadId }) { item ->
                                PastSearchResultRow(
                                    item = item,
                                    onClick = { onItemSelected(item) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PastSearchResultRow(
    item: ArchiveSearchItem,
    onClick: () -> Unit
) {
    val imageLoader = LocalFutachaImageLoader.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(item.thumbUrl)
                    .crossfade(true)
                    .build(),
                imageLoader = imageLoader
            )
            val painterState by painter.state.collectAsState()
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                when (painterState) {
                    is AsyncImagePainter.State.Error, is AsyncImagePainter.State.Empty -> {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        Image(
                            painter = painter,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.title.orEmpty().ifBlank { "無題" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.server}/${item.board}  No.${item.threadId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                item.status?.let { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                item.uploadedAt?.let { uploadedAt ->
                    Text(
                        text = "uploaded: $uploadedAt",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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

@Composable
private fun LoadingCatalog(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CatalogError(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun CatalogSuccessContent(
    items: List<CatalogItem>,
    board: BoardSummary?,
    repository: BoardRepository,
    isSearching: Boolean,
    onThreadSelected: (CatalogItem) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    displayStyle: CatalogDisplayStyle,
    gridColumns: Int,
    gridState: LazyGridState,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        CatalogEmptyContent(
            isSearching = isSearching,
            modifier = modifier
        )
    } else {
        when (displayStyle) {
            CatalogDisplayStyle.Grid -> CatalogGrid(
                items = items,
                board = board,
                repository = repository,
                onThreadSelected = onThreadSelected,
                onRefresh = onRefresh,
                isRefreshing = isRefreshing,
                gridColumns = gridColumns,
                gridState = gridState,
                modifier = modifier
            )
            CatalogDisplayStyle.List -> CatalogList(
                items = items,
                board = board,
                repository = repository,
                onThreadSelected = onThreadSelected,
                onRefresh = onRefresh,
                isRefreshing = isRefreshing,
                listState = listState,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun CatalogEmptyContent(
    isSearching: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isSearching) "検索に一致するスレッドがありません" else "表示できるスレッドがありません",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CatalogGrid(
    items: List<CatalogItem>,
    board: BoardSummary?,
    repository: BoardRepository,
    onThreadSelected: (CatalogItem) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    gridColumns: Int,
    gridState: LazyGridState,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val maxOverscrollPx = remember(density) { with(density) { 64.dp.toPx() } }
    val refreshTriggerPx = remember(density) { with(density) { 56.dp.toPx() } }
    val edgeOffsetTolerancePx = remember(density) { with(density) { 24.dp.toPx() } }

    var overscrollTarget by remember { mutableFloatStateOf(0f) }
    val overscrollOffset by animateFloatAsState(
        targetValue = overscrollTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "catalogGridOverscroll"
    )

    val isAtTop by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val firstVisibleItem = layoutInfo.visibleItemsInfo.firstOrNull()
            firstVisibleItem != null &&
            firstVisibleItem.index == 0 &&
            firstVisibleItem.offset.y.toFloat() <= edgeOffsetTolerancePx
        }
    }

    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = layoutInfo.totalItemsCount
            if (lastVisibleItem == null || totalItems == 0) return@derivedStateOf false
            if (lastVisibleItem.index < totalItems - 1) return@derivedStateOf false
            val viewportEnd = layoutInfo.viewportEndOffset
            val lastItemEnd = lastVisibleItem.offset.y + lastVisibleItem.size.height
            val remainingSpace = viewportEnd - lastItemEnd
            remainingSpace.toFloat() <= edgeOffsetTolerancePx ||
                layoutInfo.visibleItemsInfo.size >= totalItems
        }
    }

    // Return overscroll space once refresh completes.
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            overscrollTarget = 0f
        }
    }

    LazyVerticalGrid(
        state = gridState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
            .offset { IntOffset(0, overscrollOffset.toInt()) }
            .pointerInput(isRefreshing, isAtTop, isAtBottom, refreshTriggerPx, maxOverscrollPx) {
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onDragStart = {
                        totalDrag = 0f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (isRefreshing) return@detectVerticalDragGestures
                        when {
                            isAtTop && dragAmount > 0f -> {
                                totalDrag += dragAmount
                                overscrollTarget = (totalDrag * 0.4f).coerceIn(0f, maxOverscrollPx)
                                change.consume()
                            }

                            isAtBottom && dragAmount < 0f -> {
                                totalDrag += dragAmount
                                overscrollTarget = (totalDrag * 0.4f).coerceIn(-maxOverscrollPx, 0f)
                                change.consume()
                            }
                        }
                    },
                    onDragEnd = {
                        if (abs(totalDrag) > refreshTriggerPx) {
                            onRefresh()
                        }
                        totalDrag = 0f
                        overscrollTarget = 0f
                    },
                    onDragCancel = {
                        totalDrag = 0f
                        overscrollTarget = 0f
                    }
                )
            },
        columns = GridCells.Fixed(gridColumns.coerceIn(MIN_CATALOG_GRID_COLUMNS, MAX_CATALOG_GRID_COLUMNS)),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(
            start = 4.dp,
            end = 4.dp,
            top = 8.dp,
            bottom = 8.dp
        )
    ) {
        items(
            items = items,
            key = { it.id }
        ) { catalogItem ->
            CatalogCard(
                item = catalogItem,
                boardUrl = board?.url,
                repository = repository,
                onClick = { onThreadSelected(catalogItem) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CatalogList(
    items: List<CatalogItem>,
    board: BoardSummary?,
    repository: BoardRepository,
    onThreadSelected: (CatalogItem) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val maxOverscrollPx = remember(density) { with(density) { 64.dp.toPx() } }
    val refreshTriggerPx = remember(density) { with(density) { 56.dp.toPx() } }
    val edgeOffsetTolerancePx = remember(density) { with(density) { 24.dp.toPx() } }

    var overscrollTarget by remember { mutableFloatStateOf(0f) }
    val overscrollOffset by animateFloatAsState(
        targetValue = overscrollTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "catalogListOverscroll"
    )

    val isAtTop by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val firstVisibleItem = layoutInfo.visibleItemsInfo.firstOrNull()
            firstVisibleItem != null &&
            firstVisibleItem.index == 0 &&
            firstVisibleItem.offset.toFloat() <= edgeOffsetTolerancePx
        }
    }

    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = layoutInfo.totalItemsCount
            if (lastVisibleItem == null || totalItems == 0) return@derivedStateOf false
            if (lastVisibleItem.index < totalItems - 1) return@derivedStateOf false
            val viewportEnd = layoutInfo.viewportEndOffset
            val lastItemEnd = lastVisibleItem.offset + lastVisibleItem.size
            val remainingSpace = viewportEnd - lastItemEnd
            remainingSpace.toFloat() <= edgeOffsetTolerancePx ||
                layoutInfo.visibleItemsInfo.size >= totalItems
        }
    }

    // Return overscroll space once refresh completes.
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            overscrollTarget = 0f
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(0, overscrollOffset.toInt()) }
            .pointerInput(isRefreshing, isAtTop, isAtBottom, refreshTriggerPx, maxOverscrollPx) {
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onDragStart = {
                        totalDrag = 0f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (isRefreshing) return@detectVerticalDragGestures
                        when {
                            isAtTop && dragAmount > 0f -> {
                                totalDrag += dragAmount
                                overscrollTarget = (totalDrag * 0.4f).coerceIn(0f, maxOverscrollPx)
                                change.consume()
                            }

                            isAtBottom && dragAmount < 0f -> {
                                totalDrag += dragAmount
                                overscrollTarget = (totalDrag * 0.4f).coerceIn(-maxOverscrollPx, 0f)
                                change.consume()
                            }
                        }
                    },
                    onDragEnd = {
                        if (abs(totalDrag) > refreshTriggerPx) {
                            onRefresh()
                        }
                        totalDrag = 0f
                        overscrollTarget = 0f
                    },
                    onDragCancel = {
                        totalDrag = 0f
                        overscrollTarget = 0f
                    }
                )
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 8.dp,
            bottom = 8.dp
        )
    ) {
        items(
            items = items,
            key = { it.id }
        ) { catalogItem ->
            CatalogListItem(
                item = catalogItem,
                boardUrl = board?.url,
                repository = repository,
                onClick = { onThreadSelected(catalogItem) }
            )
        }
    }
}

@Composable
private fun CatalogCard(
    item: CatalogItem,
    boardUrl: String?,
    repository: BoardRepository,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val targetSizePx = with(density) { 50.dp.toPx().toInt() }
    val hasPreviewImage = !item.thumbnailUrl.isNullOrBlank() || !item.fullImageUrl.isNullOrBlank()

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .border(
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ),
                shape = MaterialTheme.shapes.small
            ),
        onClick = onClick,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!hasPreviewImage) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    CatalogPreviewImage(
                        thumbnailUrl = item.thumbnailUrl,
                        fullImageUrl = item.fullImageUrl,
                        targetSizePx = targetSizePx,
                        contentDescription = item.title ?: "サムネイル",
                        modifier = Modifier.fillMaxSize(),
                        fallbackTint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.replyCount > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                        shape = MaterialTheme.shapes.extraSmall,
                        color = Color.White,
                        tonalElevation = 2.dp
                    ) {
                        Text(
                            text = "${item.replyCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                Text(
                    text = item.title ?: "無題",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = MaterialTheme.typography.bodySmall.fontSize
                )
            }
        }
    }
}

@Composable
private fun CatalogListItem(
    item: CatalogItem,
    boardUrl: String?,
    repository: BoardRepository,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val targetSizePx = with(density) { 72.dp.toPx().toInt() }
    val hasPreviewImage = !item.thumbnailUrl.isNullOrBlank() || !item.fullImageUrl.isNullOrBlank()

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!hasPreviewImage) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    CatalogPreviewImage(
                        thumbnailUrl = item.thumbnailUrl,
                        fullImageUrl = item.fullImageUrl,
                        targetSizePx = targetSizePx,
                        contentDescription = item.title ?: "サムネイル",
                        modifier = Modifier.fillMaxSize(),
                        fallbackTint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title?.takeIf { it.isNotBlank() } ?: "無題",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "No.${item.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "${item.replyCount}レス",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private const val CATALOG_SCREEN_TAG = "CatalogScreen"

@Composable
private fun CatalogPreviewImage(
    thumbnailUrl: String?,
    fullImageUrl: String?,
    targetSizePx: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    fallbackTint: Color = Color.Gray
) {
    val platformContext = LocalPlatformContext.current
    val imageLoader = LocalFutachaImageLoader.current
    val candidates = remember(thumbnailUrl, fullImageUrl) {
        buildList {
            thumbnailUrl?.takeIf { it.isNotBlank() }?.let(::add)
            fullImageUrl
                ?.takeIf { it.isNotBlank() && it != thumbnailUrl }
                ?.let(::add)
        }
    }
    var candidateIndex by remember(candidates) { mutableIntStateOf(0) }
    val activeUrl = candidates.getOrNull(candidateIndex)
    val imageRequest = remember(activeUrl, targetSizePx) {
        ImageRequest.Builder(platformContext)
            .data(activeUrl)
            .crossfade(true)
            .size(targetSizePx, targetSizePx)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
    val imagePainter = rememberAsyncImagePainter(
        model = imageRequest,
        imageLoader = imageLoader
    )
    val painterState by imagePainter.state.collectAsState()

    LaunchedEffect(painterState, candidateIndex, candidates.size) {
        if (painterState is AsyncImagePainter.State.Error && candidateIndex < candidates.lastIndex) {
            candidateIndex += 1
        }
    }

    val shouldShowFallback = activeUrl.isNullOrBlank() ||
        ((painterState is AsyncImagePainter.State.Error || painterState is AsyncImagePainter.State.Empty) &&
            candidateIndex >= candidates.lastIndex)

    if (shouldShowFallback) {
        Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = null,
            tint = fallbackTint
        )
    } else {
        Image(
            painter = imagePainter,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogTopBar(
    board: BoardSummary?,
    mode: CatalogMode,
    searchQuery: String,
    isSearchActive: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onNavigationClick: () -> Unit,
    onModeSelected: (CatalogMode) -> Unit,
    onMenuAction: (CatalogMenuAction) -> Unit
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onNavigationClick) {
                Icon(
                    imageVector = Icons.Outlined.Menu,
                    contentDescription = "履歴を開く"
                )
            }
        },
        title = {
            if (isSearchActive) {
                CatalogSearchTextField(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    focusRequester = focusRequester,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = board?.name ?: "カタログ",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = {
            if (isSearchActive) {
                IconButton(
                    onClick = {
                        if (searchQuery.isNotEmpty()) {
                            onSearchQueryChange("")
                        } else {
                            onSearchActiveChange(false)
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "検索を閉じる"
                    )
                }
            } else {
                IconButton(onClick = { onSearchActiveChange(true) }) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "検索"
                    )
                }
                Box {
                    IconButton(onClick = { isMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "メニュー"
                        )
                    }
                    DropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = { isMenuExpanded = false }
                    ) {
                        CatalogMenuAction.entries.forEach { action ->
                            DropdownMenuItem(
                                text = { Text(action.label) },
                                onClick = {
                                    isMenuExpanded = false
                                    onMenuAction(action)
                                }
                            )
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
private fun CatalogSearchTextField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .padding(end = 8.dp)
            .focusRequester(focusRequester),
        singleLine = true,
        placeholder = { Text("スレタイ検索") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null
            )
        },
        trailingIcon = null,
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
            unfocusedContainerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = MaterialTheme.colorScheme.onPrimary,
            unfocusedTextColor = MaterialTheme.colorScheme.onPrimary,
            cursorColor = MaterialTheme.colorScheme.onPrimary,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
            focusedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
            focusedTrailingIconColor = MaterialTheme.colorScheme.onPrimary,
            unfocusedTrailingIconColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
private fun CatalogNavigationBar(
    menuEntries: List<CatalogNavEntryConfig>,
    onNavigate: (CatalogNavEntryId) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleEntries = remember(menuEntries) {
        resolveCatalogNavBarEntries(menuEntries)
    }
    NavigationBar(modifier = modifier) {
        visibleEntries.forEach { entry ->
            val meta = entry.id.toMeta()
            NavigationBarItem(
                selected = false,
                onClick = { onNavigate(entry.id) },
                icon = {
                    Icon(
                        imageVector = meta.icon,
                        contentDescription = meta.label
                    )
                },
                label = { Text(meta.label) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogSettingsSheet(
    onDismiss: () -> Unit,
    onAction: (CatalogSettingsMenuItem) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "設定メニュー",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            CatalogSettingsMenuItem.entries.forEach { menuItem ->
                ListItem(
                    leadingContent = { Icon(imageVector = menuItem.icon, contentDescription = null) },
                    headlineContent = {
                        Column {
                            Text(menuItem.label)
                            menuItem.description?.let { description ->
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onAction(menuItem) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchWordsSheet(
    watchWords: List<String>,
    onAddWord: (String) -> Unit,
    onRemoveWord: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var input by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "監視ワード",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "タイトルが一致したスレを履歴に追加します",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "閉じる")
                }
            }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("追加するワード") },
                placeholder = { Text("例: 夏休み") },
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (input.isNotBlank()) {
                                onAddWord(input)
                                input = ""
                            }
                        },
                        enabled = input.isNotBlank()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "追加")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (watchWords.isEmpty()) {
                Text(
                    text = "まだ監視ワードは登録されていません",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(watchWords) { entry ->
                        ListItem(
                            headlineContent = { Text(entry) },
                            trailingContent = {
                                IconButton(onClick = { onRemoveWord(entry) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "削除")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DisplayStyleDialog(
    currentStyle: CatalogDisplayStyle,
    currentGridColumns: Int,
    onStyleSelected: (CatalogDisplayStyle) -> Unit,
    onGridColumnsSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("表示スタイル") },
        text = {
            Column {
                CatalogDisplayStyle.entries.forEach { style ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStyleSelected(style) }
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = style == currentStyle,
                            onClick = { onStyleSelected(style) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = style.label,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                if (currentStyle == CatalogDisplayStyle.Grid) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "列数",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (MIN_CATALOG_GRID_COLUMNS..MAX_CATALOG_GRID_COLUMNS).forEach { columns ->
                            FilterChip(
                                selected = columns == currentGridColumns,
                                onClick = { onGridColumnsSelected(columns) },
                                label = { Text("${columns}列") }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
}

// Helper functions

private fun List<CatalogItem>.filterByQuery(query: String): List<CatalogItem> {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty()) return this
    val normalizedQuery = trimmedQuery.lowercase()
    return filter { item ->
        val titleMatch = item.title?.lowercase()?.contains(normalizedQuery) == true
        val idMatch = item.id.lowercase().contains(normalizedQuery)
        val threadMatch = item.threadUrl.lowercase().contains(normalizedQuery)
        titleMatch || idMatch || threadMatch
    }
}

private fun List<CatalogItem>.filterByCatalogNgWords(
    catalogNgWords: List<String>,
    enabled: Boolean
): List<CatalogItem> {
    if (!enabled) return this
    val wordFilters = catalogNgWords.mapNotNull { it.trim().takeIf { trimmed -> trimmed.isNotBlank() }?.lowercase() }
    if (wordFilters.isEmpty()) return this
    return filterNot { item ->
        matchesCatalogNgWords(item, wordFilters)
    }
}

private fun matchesCatalogNgWords(
    item: CatalogItem,
    wordFilters: List<String>
): Boolean {
    val titleText = item.title?.lowercase().orEmpty()
    if (titleText.isEmpty()) return false
    return wordFilters.any { titleText.contains(it) }
}

private sealed interface ArchiveSearchState {
    data object Idle : ArchiveSearchState
    data object Loading : ArchiveSearchState
    data class Success(val items: List<ArchiveSearchItem>) : ArchiveSearchState
    data class Error(val message: String) : ArchiveSearchState
}

// Catalog navigation helpers

internal data class CatalogNavEntryMeta(
    val label: String,
    val icon: ImageVector
)

internal fun CatalogNavEntryId.toMeta(): CatalogNavEntryMeta {
    return when (this) {
        CatalogNavEntryId.CreateThread -> CatalogNavEntryMeta("スレッド作成", Icons.Rounded.Add)
        CatalogNavEntryId.ScrollToTop -> CatalogNavEntryMeta("一番上に行く", Icons.Rounded.VerticalAlignTop)
        CatalogNavEntryId.RefreshCatalog -> CatalogNavEntryMeta("カタログ更新", Icons.Rounded.Refresh)
        CatalogNavEntryId.PastThreadSearch -> CatalogNavEntryMeta("過去スレ検索", Icons.Rounded.History)
        CatalogNavEntryId.Mode -> CatalogNavEntryMeta("モード", Icons.AutoMirrored.Rounded.Sort)
        CatalogNavEntryId.Settings -> CatalogNavEntryMeta("設定", Icons.Rounded.Settings)
    }
}

private fun resolveCatalogNavBarEntries(menuEntries: List<CatalogNavEntryConfig>): List<CatalogNavEntryConfig> {
    return normalizeCatalogNavEntries(menuEntries)
        .filter { it.placement == CatalogNavEntryPlacement.BAR }
        .sortedWith(compareBy<CatalogNavEntryConfig> { it.order }.thenBy { it.id.defaultOrder })
}

// Catalog enums and constants

private enum class CatalogMenuAction(val label: String) {
    Settings("設定")
}

private enum class CatalogSettingsMenuItem(
    val label: String,
    val icon: ImageVector,
    val description: String?
) {
    WatchWords("監視ワード", Icons.Rounded.WatchLater, "監視中のワードを編集"),
    NgManagement("NG管理", Icons.Rounded.Block, "NGワードとIDを管理"),
    ExternalApp("外部アプリ", Icons.AutoMirrored.Rounded.OpenInNew, "外部アプリ連携を設定"),
    DisplayStyle("表示の切り替え", Icons.Rounded.ViewModule, "カタログ表示方法を変更"),
    ScrollToTop("一番上に行く", Icons.Rounded.VerticalAlignTop, "グリッドの先頭へ移動"),
    Privacy("プライバシー", Icons.Rounded.Lock, "プライバシー設定を確認")
}

private const val DEFAULT_CATALOG_GRID_COLUMNS = 5
private const val MIN_CATALOG_GRID_COLUMNS = 2
private const val MAX_CATALOG_GRID_COLUMNS = 8

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



