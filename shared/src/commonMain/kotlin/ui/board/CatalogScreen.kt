package com.valoser.futacha.shared.ui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
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
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.rememberUrlLauncher
import com.valoser.futacha.shared.util.SaveDirectorySelection
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.time.Clock
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
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
    httpClient: io.ktor.client.HttpClient? = null
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
    // ボードごとにモードを覚える。キーなしだと画面を離れるたびにデフォルト(多い順)へ戻ってしまう。
    val catalogModeMapState = stateStore?.catalogModes?.collectAsState(initial = emptyMap())
    var catalogMode by rememberSaveable(board?.id) {
        mutableStateOf(catalogModeMapState?.value?.get(board?.id.orEmpty()) ?: CatalogMode.default)
    }
    var isRefreshing by remember { mutableStateOf(false) }
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
    var lastArchiveSearchScope by remember { mutableStateOf<ArchiveSearchScope?>(archiveSearchScope) }
    val showNgMessage: (String) -> Unit = { message ->
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }
    LaunchedEffect(board?.id, catalogModeMapState?.value) {
        val boardId = board?.id ?: return@LaunchedEffect
        val persisted = catalogModeMapState?.value?.get(boardId) ?: return@LaunchedEffect
        catalogMode = persisted
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
        if (board == null || stateStore == null) return

        val timestamp = Clock.System.now().toEpochMilliseconds()

        catalog.forEach { item ->
            val titleText = item.title?.lowercase().orEmpty()
            if (titleText.isEmpty()) return@forEach
            if (normalizedFilters.any { titleText.contains(it) }) {
                val entry = ThreadHistoryEntry(
                    threadId = item.id,
                    boardId = board.id,
                    title = item.title?.takeIf { it.isNotBlank() } ?: "無題",
                    titleImageUrl = item.thumbnailUrl ?: "",
                    boardName = board.name,
                    boardUrl = board.url,
                    lastVisitedEpochMillis = timestamp,
                    replyCount = item.replyCount
                )
                try {
                    stateStore.upsertHistoryEntry(entry)
                } catch (_: Exception) {
                    // Ignore failures to keep catalog refresh resilient
                }
            }
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
            trimmed.isEmpty() -> showNgMessage("NGワードに含める文字列を入力してください")
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
        coroutineScope.launch {
            isHistoryRefreshing = true
            snackbarHostState.showSnackbar("履歴を更新中...")
            try {
                onHistoryRefresh()
                snackbarHostState.showSnackbar("履歴を更新しました")
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("履歴の更新に失敗しました: ${e.message ?: "不明なエラー"}")
            } finally {
                isHistoryRefreshing = false
            }
        }
    }

    val performRefresh: () -> Unit = {
        if (!isRefreshing && board != null) {
            coroutineScope.launch {
                isRefreshing = true
                try {
                    val catalog = activeRepository.getCatalog(board.url, catalogMode)
                    uiState.value = CatalogUiState.Success(catalog)
                    lastCatalogItems = catalog
                    handleWatchWordMatches(catalog)
                    snackbarHostState.showSnackbar("カタログを更新しました")
                } catch (e: Exception) {
                    if (e !is kotlinx.coroutines.CancellationException) {
                        snackbarHostState.showSnackbar("更新に失敗しました")
                    }
                } finally {
                    isRefreshing = false
                }
            }
        }
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
            uiState.value = CatalogUiState.Error("板が選択されていません")
            return@LaunchedEffect
        }
        uiState.value = CatalogUiState.Loading

        // Use try-finally to ensure cleanup even on cancellation
            try {
                val catalog = activeRepository.getCatalog(board.url, catalogMode)
                // Check if still active before updating state
                if (isActive) {
                    uiState.value = CatalogUiState.Success(catalog)
                    lastCatalogItems = catalog
                    handleWatchWordMatches(catalog)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
            // Rethrow cancellation to properly cancel the coroutine
            throw e
        } catch (e: Exception) {
            if (isActive) {
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
        gesturesEnabled = isDrawerOpen,
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
                onThreadRefreshClick = {
                    performRefresh()
                },
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
                                snackbarHostState.showSnackbar("${action.label} はモック動作です")
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
                        value = withContext(Dispatchers.Default) {
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
                            snackbarHostState.showSnackbar("スレッドを作成中...")
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
                            snackbarHostState.showSnackbar("スレッドを作成しました (ID: $threadId)")
                            resetCreateThreadDraft()
                            // Refresh catalog to show the new thread
                            performRefresh()
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
                    val client = httpClient
                    if (client == null) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("ネットワーククライアントが利用できません")
                        }
                        return@PastThreadSearchDialog
                    }
                    isPastSearchSheetVisible = true
                    pastSearchState = ArchiveSearchState.Loading
                    coroutineScope.launch {
                        pastSearchState = runCatching {
                            fetchArchiveSearchResults(client, trimmed, appliedScope, archiveSearchJson)
                        }.fold(
                            onSuccess = { ArchiveSearchState.Success(it) },
                            onFailure = { error ->
                                ArchiveSearchState.Error(error.message ?: "検索に失敗しました")
                            }
                        )
                    }
                }
            )
        }

        if (isPastSearchSheetVisible) {
            PastThreadSearchResultSheet(
                state = pastSearchState,
                onDismiss = { isPastSearchSheetVisible = false },
                onRetry = {
                    val client = httpClient
                    if (client == null) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("ネットワーククライアントが利用できません")
                        }
                        return@PastThreadSearchResultSheet
                    }
                    pastSearchState = ArchiveSearchState.Loading
                    coroutineScope.launch {
                        pastSearchState = runCatching {
                            fetchArchiveSearchResults(client, archiveSearchQuery, lastArchiveSearchScope, archiveSearchJson)
                        }.fold(
                            onSuccess = { ArchiveSearchState.Success(it) },
                            onFailure = { error ->
                                ArchiveSearchState.Error(error.message ?: "検索に失敗しました")
                            }
                        )
                    }
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
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                when (painter.state) {
                    is AsyncImagePainter.State.Error, AsyncImagePainter.State.Empty -> {
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
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maxOverscrollPx = remember(density) { with(density) { 64.dp.toPx() } }
    val refreshTriggerPx = remember(density) { with(density) { 56.dp.toPx() } }
    val edgeOffsetTolerancePx = remember(density) { with(density) { 24.dp.toPx() } }

    // アニメーション用のオフセット
    val overscrollOffset = remember { Animatable(0f) }

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

    // リフレッシュ完了時にアニメーションで空間を戻す
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            overscrollOffset.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    LazyVerticalGrid(
        state = gridState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
            .offset { IntOffset(0, overscrollOffset.value.toInt()) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalDrag = 0f

                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val dragEvent = event.changes.firstOrNull()

                        if (dragEvent != null && dragEvent.pressed) {
                            val delta = dragEvent.positionChangeIgnoreConsumed().y

                            // 上端で下向きにドラッグ
                            if (isAtTop && delta > 0 && !isRefreshing) {
                                totalDrag += delta
                                val newOffset = (totalDrag * 0.4f).coerceIn(0f, maxOverscrollPx)
                                coroutineScope.launch {
                                    overscrollOffset.snapTo(newOffset)
                                }
                                dragEvent.consume()
                            }
                            // 下端で上向きにドラッグ
                            else if (isAtBottom && delta < 0 && !isRefreshing) {
                                totalDrag += delta
                                val newOffset = (totalDrag * 0.4f).coerceIn(-maxOverscrollPx, 0f)
                                coroutineScope.launch {
                                    overscrollOffset.snapTo(newOffset)
                                }
                                dragEvent.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    // ドラッグ終了
                    if (totalDrag > refreshTriggerPx) {
                        coroutineScope.launch { onRefresh() }
                    } else if (totalDrag < -refreshTriggerPx) {
                        coroutineScope.launch { onRefresh() }
                    }

                    totalDrag = 0f
                    coroutineScope.launch {
                        overscrollOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                }
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
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maxOverscrollPx = remember(density) { with(density) { 64.dp.toPx() } }
    val refreshTriggerPx = remember(density) { with(density) { 56.dp.toPx() } }
    val edgeOffsetTolerancePx = remember(density) { with(density) { 24.dp.toPx() } }

    // アニメーション用のオフセット
    val overscrollOffset = remember { Animatable(0f) }

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

    // リフレッシュ完了時にアニメーションで空間を戻す
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            overscrollOffset.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(0, overscrollOffset.value.toInt()) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalDrag = 0f

                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val dragEvent = event.changes.firstOrNull()

                        if (dragEvent != null && dragEvent.pressed) {
                            val delta = dragEvent.positionChangeIgnoreConsumed().y

                            // 上端で下向きにドラッグ
                            if (isAtTop && delta > 0 && !isRefreshing) {
                                totalDrag += delta
                                val newOffset = (totalDrag * 0.4f).coerceIn(0f, maxOverscrollPx)
                                coroutineScope.launch {
                                    overscrollOffset.snapTo(newOffset)
                                }
                                dragEvent.consume()
                            }
                            // 下端で上向きにドラッグ
                            else if (isAtBottom && delta < 0 && !isRefreshing) {
                                totalDrag += delta
                                val newOffset = (totalDrag * 0.4f).coerceIn(-maxOverscrollPx, 0f)
                                coroutineScope.launch {
                                    overscrollOffset.snapTo(newOffset)
                                }
                                dragEvent.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    // ドラッグ終了
                    if (totalDrag > refreshTriggerPx) {
                        coroutineScope.launch { onRefresh() }
                    } else if (totalDrag < -refreshTriggerPx) {
                        coroutineScope.launch { onRefresh() }
                    }

                    totalDrag = 0f
                    coroutineScope.launch {
                        overscrollOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                }
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
    val platformContext = LocalPlatformContext.current
    val density = LocalDensity.current

    // 4列グリッドでの推定カードサイズ(画面幅360dpの場合約75dp)
    // 1.5倍程度の拡大率に抑えるため、50dpでリクエスト
    val targetSizePx = with(density) { 50.dp.toPx().toInt() }

    val imageUrl = item.fullImageUrl ?: item.thumbnailUrl
    val thumbnailUrl = item.thumbnailUrl

    // 画像リクエストを作成
    val imageRequest = remember(imageUrl, thumbnailUrl, targetSizePx) {
        ImageRequest.Builder(platformContext)
            .data(imageUrl)
            .crossfade(true)
            .size(targetSizePx, targetSizePx)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    val imageLoader = LocalFutachaImageLoader.current
    val imagePainter = rememberAsyncImagePainter(
        model = imageRequest,
        imageLoader = imageLoader
    )

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
                if (imageUrl.isNullOrBlank()) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Image(
                        painter = imagePainter,
                        contentDescription = item.title ?: "サムネイル",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
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
    val platformContext = LocalPlatformContext.current
    val density = LocalDensity.current
    val targetSizePx = with(density) { 72.dp.toPx().toInt() }

    val imageUrl = item.fullImageUrl ?: item.thumbnailUrl
    val thumbnailUrl = item.thumbnailUrl

    // 画像リクエストを作成
    val imageRequest = remember(imageUrl, thumbnailUrl, targetSizePx) {
        ImageRequest.Builder(platformContext)
            .data(imageUrl)
            .crossfade(true)
            .size(targetSizePx, targetSizePx)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    val imageLoader = LocalFutachaImageLoader.current
    val imagePainter = rememberAsyncImagePainter(
        model = imageRequest,
        imageLoader = imageLoader
    )

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
                if (imageUrl.isNullOrBlank()) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Image(
                        painter = imagePainter,
                        contentDescription = item.title ?: "サムネイル",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
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

// Archive search

private data class ArchiveSearchScope(val server: String, val board: String)

@Serializable
private data class ArchiveSearchItem(
    val threadId: String,
    val server: String,
    val board: String,
    val title: String? = null,
    val htmlUrl: String,
    val thumbUrl: String? = null,
    val status: String? = null,
    @Serializable(with = ArchiveSearchTimestampSerializer::class)
    val createdAt: Long? = null,
    @Serializable(with = ArchiveSearchTimestampSerializer::class)
    val finalizedAt: Long? = null,
    @Serializable(with = ArchiveSearchTimestampSerializer::class)
    val uploadedAt: Long? = null
)

@Serializable
private data class ArchiveSearchResponse(
    val query: String? = null,
    val filter: String? = null,
    val count: Int? = null,
    val results: List<ArchiveSearchItem> = emptyList()
)

private sealed interface ArchiveSearchState {
    data object Idle : ArchiveSearchState
    data object Loading : ArchiveSearchState
    data class Success(val items: List<ArchiveSearchItem>) : ArchiveSearchState
    data class Error(val message: String) : ArchiveSearchState
}

private object ArchiveSearchTimestampSerializer : KSerializer<Long?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ArchiveSearchTimestamp", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Long? {
        val jsonDecoder = decoder as? JsonDecoder ?: return runCatching { decoder.decodeLong() }.getOrNull()
        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonNull) return null
        val primitive = element.jsonPrimitive
        return primitive.longOrNull ?: primitive.content.toLongOrNull()
    }

    override fun serialize(encoder: Encoder, value: Long?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeLong(value)
        }
    }
}

private fun extractArchiveSearchScope(board: BoardSummary?): ArchiveSearchScope? {
    if (board == null) return null
    return runCatching {
        val baseUrl = BoardUrlResolver.resolveBoardBaseUrl(board.url)
        val parsed = Url(baseUrl)
        val server = parsed.host.substringBefore('.', parsed.host).ifBlank { return null }
        val boardSlug = BoardUrlResolver.resolveBoardSlug(board.url).ifBlank { return null }
        ArchiveSearchScope(server = server, board = boardSlug)
    }.getOrNull()
}

private fun buildArchiveSearchUrl(
    query: String,
    scope: ArchiveSearchScope?
): String {
    val params = buildList<String> {
        if (query.isNotBlank()) {
            add("q=${query.encodeURLParameter()}")
        }
        scope?.let {
            add("server=${it.server.encodeURLParameter()}")
            add("board=${it.board.encodeURLParameter()}")
        }
    }
    return buildString {
        append("https://spider.serendipity01234.workers.dev/search")
        if (params.isNotEmpty()) {
            append("?")
            append(params.joinToString("&"))
        }
    }
}

private suspend fun fetchArchiveSearchResults(
    httpClient: io.ktor.client.HttpClient,
    query: String,
    scope: ArchiveSearchScope?,
    json: Json
): List<ArchiveSearchItem> {
    val url = buildArchiveSearchUrl(query, scope)
    val response = httpClient.get(url)
    if (!response.status.isSuccess()) {
        throw IllegalStateException("検索に失敗しました: ${response.status}")
    }
    val body = response.bodyAsText()
    return parseArchiveSearchResults(body, scope, json)
}

private fun parseArchiveSearchResults(
    body: String,
    scope: ArchiveSearchScope?,
    json: Json
): List<ArchiveSearchItem> {
    val element = runCatching { json.parseToJsonElement(body) }.getOrElse {
        throw IllegalStateException("検索結果の解析に失敗しました")
    }
    val itemsElement = when (element) {
        is JsonArray -> element
        is JsonObject -> {
            element["results"]
                ?: element["items"]
                ?: element["data"]
                ?: element["threads"]
        }
        else -> null
    }
    val items = itemsElement as? JsonArray
        ?: throw IllegalStateException("検索結果の形式が不明です")
    return items.mapNotNull { parseArchiveSearchItem(it, scope) }
}

private fun parseArchiveSearchItem(
    element: JsonElement,
    scope: ArchiveSearchScope?
): ArchiveSearchItem? {
    val obj = element as? JsonObject ?: return null
    val htmlUrl = obj.firstString("htmlUrl", "html_url", "url", "link", "href") ?: return null
    val threadId = obj.firstString("threadId", "thread_id", "id", "thread")
        ?: extractThreadIdFromUrl(htmlUrl)
        ?: return null
    val server = obj.firstString("server", "srv") ?: scope?.server.orEmpty()
    val board = obj.firstString("board", "boardId", "brd") ?: scope?.board.orEmpty()
    val title = obj.firstString("title", "subject")
    val thumbUrl = obj.firstString("thumbUrl", "thumb_url", "thumb", "thumbnail", "image")
    val status = obj.firstString("status", "state")
    val createdAt = obj.firstLong("createdAt", "created_at", "created")
    val finalizedAt = obj.firstLong("finalizedAt", "finalized_at", "finalized")
    val uploadedAt = obj.firstLong("uploadedAt", "uploaded_at", "uploaded")
    return ArchiveSearchItem(
        threadId = threadId,
        server = server,
        board = board,
        title = title,
        htmlUrl = htmlUrl,
        thumbUrl = thumbUrl,
        status = status,
        createdAt = createdAt,
        finalizedAt = finalizedAt,
        uploadedAt = uploadedAt
    )
}

private fun JsonObject.firstString(vararg keys: String): String? {
    keys.forEach { key ->
        val value = this[key]?.asStringOrNull()?.trim()
        if (!value.isNullOrEmpty()) {
            return value
        }
    }
    return null
}

private fun JsonObject.firstLong(vararg keys: String): Long? {
    keys.forEach { key ->
        val value = this[key]?.asLongOrNull()
        if (value != null) {
            return value
        }
    }
    return null
}

private fun JsonElement.asStringOrNull(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    if (this is JsonNull) return null
    val content = primitive.content
    return if (content == "null") null else content
}

private fun JsonElement.asLongOrNull(): Long? {
    val primitive = this as? JsonPrimitive ?: return null
    if (this is JsonNull) return null
    return primitive.longOrNull ?: primitive.content.toLongOrNull()
}

private fun extractThreadIdFromUrl(url: String): String? {
    val primary = Regex("""/res/(\d+)\.htm""").find(url)?.groupValues?.getOrNull(1)
    if (!primary.isNullOrBlank()) return primary
    return Regex("""(\d+)(?:\.htm)?$""").find(url)?.groupValues?.getOrNull(1)
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
    NgManagement("NG管理", Icons.Rounded.Block, "NGワード・IDを管理"),
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
