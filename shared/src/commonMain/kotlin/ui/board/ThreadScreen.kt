package com.valoser.futacha.shared.ui.board

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.valoser.futacha.shared.audio.TextSpeaker
import com.valoser.futacha.shared.audio.createTextSpeaker
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.network.extractArchiveSearchScope
import com.valoser.futacha.shared.network.fetchArchiveSearchResults
import com.valoser.futacha.shared.network.selectLatestArchiveMatch
import com.valoser.futacha.shared.network.NetworkException
import io.ktor.client.plugins.ResponseException
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.SavedMediaType
import com.valoser.futacha.shared.service.SingleMediaSaveService
import com.valoser.futacha.shared.service.ThreadSaveService
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.SaveDirectorySelection
import com.valoser.futacha.shared.util.isAndroid
import com.valoser.futacha.shared.util.rememberUrlLauncher
import com.valoser.futacha.shared.util.resolveThreadTitle
import kotlinx.coroutines.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime

// Thread UI state
sealed interface ThreadUiState {
    data object Loading : ThreadUiState
    data class Error(val message: String = "スレッドを読み込めませんでした") : ThreadUiState
    data class Success(val page: ThreadPage) : ThreadUiState
}

private fun isSaveLocationPermissionIssue(error: Throwable): Boolean {
    val message = error.message?.lowercase().orEmpty()
    return message.contains("cannot resolve tree uri") ||
        message.contains("write permission lost for tree uri") ||
        message.contains("please select the folder again") ||
        message.contains("invalid bookmark data") ||
        message.contains("failed to resolve bookmark") ||
        message.contains("bookmark url has no filesystem path")
}

private fun isDefaultManualSaveRoot(directory: String): Boolean {
    val normalized = directory
        .trim()
        .removePrefix("./")
        .trimEnd('/')
    return normalized.equals(DEFAULT_MANUAL_SAVE_ROOT, ignoreCase = true)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class, FlowPreview::class)
@Composable
fun ThreadScreen(
    board: BoardSummary,
    history: List<ThreadHistoryEntry>,
    threadId: String,
    threadTitle: String?,
    initialReplyCount: Int?,
    threadUrlOverride: String? = null,
    onBack: () -> Unit,
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit = {},
    onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit = {},
    onHistoryCleared: () -> Unit = {},
    onHistoryEntryUpdated: (ThreadHistoryEntry) -> Unit = {},
    onHistoryRefresh: suspend () -> Unit = {},
    onScrollPositionPersist: (threadId: String, index: Int, offset: Int) -> Unit = { _, _, _ -> },
    repository: BoardRepository? = null,
    httpClient: io.ktor.client.HttpClient? = null,
    fileSystem: FileSystem? = null,
    cookieRepository: CookieRepository? = null,
    stateStore: com.valoser.futacha.shared.state.AppStateStore? = null,
    autoSavedThreadRepository: SavedThreadRepository? = null,
    appVersion: String,
    isBackgroundRefreshEnabled: Boolean = false,
    onBackgroundRefreshChanged: (Boolean) -> Unit = {},
    isLightweightModeEnabled: Boolean = false,
    onLightweightModeChanged: (Boolean) -> Unit = {},
    manualSaveDirectory: String = MANUAL_SAVE_DIRECTORY,
    manualSaveLocation: SaveLocation? = null,
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
    threadMenuEntries: List<ThreadMenuEntryConfig> = defaultThreadMenuEntries(),
    onThreadMenuEntriesChanged: (List<ThreadMenuEntryConfig>) -> Unit = {},
    catalogNavEntries: List<CatalogNavEntryConfig> = defaultCatalogNavEntries(),
    onCatalogNavEntriesChanged: (List<CatalogNavEntryConfig>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeRepository = remember(repository) {
        repository ?: FakeBoardRepository()
    }
    var resolvedThreadUrlOverride by rememberSaveable(board.id, threadId) { mutableStateOf(threadUrlOverride) }
    LaunchedEffect(threadUrlOverride) {
        if (!threadUrlOverride.isNullOrBlank()) {
            resolvedThreadUrlOverride = threadUrlOverride
        }
    }
    val effectiveBoardUrl = remember(resolvedThreadUrlOverride, board.url) {
        resolveEffectiveBoardUrl(resolvedThreadUrlOverride, board.url)
    }
    val uiState = remember(board.id, threadId) { mutableStateOf<ThreadUiState>(ThreadUiState.Loading) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val archiveSearchJson = remember {
        Json { ignoreUnknownKeys = true }
    }
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
    val platformContext = LocalPlatformContext.current
    val textSpeaker = remember(platformContext) { createTextSpeaker(platformContext) }
    var readAloudJob by remember { mutableStateOf<Job?>(null) }
    var readAloudStatus by remember { mutableStateOf<ReadAloudStatus>(ReadAloudStatus.Idle) }
    var isReadAloudControlsVisible by remember { mutableStateOf(false) }
    var currentReadAloudIndex by rememberSaveable(threadId) { mutableStateOf(0) }
    var readAloudCancelRequestedByUser by remember { mutableStateOf(false) }
    val cancelActiveReadAloud: () -> Unit = {
        readAloudCancelRequestedByUser = true
        readAloudJob?.cancel()
        textSpeaker.stop()
    }
    val stopReadAloud: () -> Unit = {
        cancelActiveReadAloud()
        readAloudJob = null
        readAloudStatus = ReadAloudStatus.Idle
        currentReadAloudIndex = 0
    }
    val pauseReadAloud: () -> Unit = pauseReadAloud@{
        val segment = (readAloudStatus as? ReadAloudStatus.Speaking)?.segment ?: return@pauseReadAloud
        readAloudStatus = ReadAloudStatus.Paused(segment)
        cancelActiveReadAloud()
        readAloudJob = null
        coroutineScope.launch {
            snackbarHostState.showSnackbar("読み上げを一時停止しました")
        }
    }
    DisposableEffect(textSpeaker) {
        onDispose {
            stopReadAloud()
            textSpeaker.close()
        }
    }
    val isAndroidPlatform = remember { isAndroid() }
    val autoSaveRepository = autoSavedThreadRepository
    val manualSaveRepository = remember(fileSystem, manualSaveDirectory, manualSaveLocation) {
        fileSystem?.let { fs ->
            SavedThreadRepository(
                fs,
                baseDirectory = manualSaveDirectory,
                baseSaveLocation = manualSaveLocation
            )
        }
    }
    val legacyManualSaveRepository = remember(fileSystem, manualSaveDirectory, manualSaveLocation) {
        val fs = fileSystem ?: return@remember null
        val isCurrentDefaultPath = manualSaveLocation is SaveLocation.Path &&
            isDefaultManualSaveRoot(manualSaveDirectory)
        if (isCurrentDefaultPath) {
            null
        } else {
            SavedThreadRepository(
                fs,
                baseDirectory = DEFAULT_MANUAL_SAVE_ROOT,
                baseSaveLocation = SaveLocation.Path(DEFAULT_MANUAL_SAVE_ROOT)
            )
        }
    }
    var autoSaveJob by remember { mutableStateOf<Job?>(null) }
    var manualSaveJob by remember { mutableStateOf<Job?>(null) }
    var singleMediaSaveJob by remember { mutableStateOf<Job?>(null) }
    var refreshThreadJob by remember { mutableStateOf<Job?>(null) }
    var isManualSaveInProgress by remember { mutableStateOf(false) }
    var isSingleMediaSaveInProgress by remember { mutableStateOf(false) }
    val lastAutoSaveTimestamp = rememberSaveable(threadId) { mutableStateOf(0L) }
    var isShowingOfflineCopy by rememberSaveable(threadId) { mutableStateOf(false) }
    LaunchedEffect(threadId) {
        stopReadAloud()
        isReadAloudControlsVisible = false
        autoSaveJob?.cancel()
        manualSaveJob?.cancel()
        singleMediaSaveJob?.cancel()
        refreshThreadJob?.cancel()
        autoSaveJob = null
        manualSaveJob = null
        singleMediaSaveJob = null
        isManualSaveInProgress = false
        isSingleMediaSaveInProgress = false
        refreshThreadJob = null
    }
    DisposableEffect(Unit) {
        onDispose {
            autoSaveJob?.cancel()
            manualSaveJob?.cancel()
            singleMediaSaveJob?.cancel()
            refreshThreadJob?.cancel()
        }
    }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val isDrawerOpen by remember {
        derivedStateOf {
            drawerState.currentValue == DrawerValue.Open ||
                drawerState.targetValue == DrawerValue.Open
        }
    }
    var actionInProgress by remember { mutableStateOf(false) }
    var lastBusyActionNoticeAtMillis by remember { mutableStateOf(0L) }
    val saidaneOverrides = remember(threadId) { mutableStateMapOf<String, String>() }
    var actionTargetPost by remember { mutableStateOf<Post?>(null) }
    var deleteDialogTarget by remember { mutableStateOf<Post?>(null) }
    var quoteSelectionTarget by remember { mutableStateOf<Post?>(null) }
    var isActionSheetVisible by remember { mutableStateOf(false) }
    var pendingDeletePassword by remember { mutableStateOf("") }
    var pendingDeleteImageOnly by remember { mutableStateOf(false) }
    var isReplyDialogVisible by remember { mutableStateOf(false) }
    var isThreadSettingsSheetVisible by remember { mutableStateOf(false) }
    var isThreadFilterSheetVisible by remember { mutableStateOf(false) }
    var selectedThreadFilterOptions by remember { mutableStateOf(emptySet<ThreadFilterOption>()) }
    var selectedThreadSortOption by rememberSaveable { mutableStateOf<ThreadFilterSortOption?>(null) }
    var threadFilterKeyword by rememberSaveable { mutableStateOf("") }
    val threadFilterCache = remember(threadId) { linkedMapOf<ThreadFilterCacheKey, ThreadPage>() }
    val persistedSelfPostIdentifiersState = stateStore?.selfPostIdentifiersByThread?.collectAsState(initial = emptyMap())
    val persistedSelfPostMap = persistedSelfPostIdentifiersState?.value ?: emptyMap()
    val scopedSelfPostKey = remember(board.id, threadId) {
        if (board.id.isBlank()) threadId else "${board.id}::$threadId"
    }
    val persistedSelfPostIdentifiers = remember(persistedSelfPostMap, scopedSelfPostKey, threadId) {
        buildList {
            addAll(persistedSelfPostMap[scopedSelfPostKey].orEmpty())
            if (scopedSelfPostKey != threadId) {
                addAll(persistedSelfPostMap[threadId].orEmpty())
            }
        }
    }
    val selfPostIdentifierSet = remember(threadId, persistedSelfPostIdentifiers) {
        persistedSelfPostIdentifiers
            .mapNotNull { it.trim().takeIf { trimmed -> trimmed.isNotBlank() } }
            .toSet()
    }
    val isSelfPost: (Post) -> Boolean = remember(selfPostIdentifierSet) {
        { post -> selfPostIdentifierSet.contains(post.id.trim()) }
    }
    var isGlobalSettingsVisible by remember { mutableStateOf(false) }
    var isCookieManagementVisible by remember { mutableStateOf(false) }
    var isNgManagementVisible by remember { mutableStateOf(false) }
    var ngHeaderPrefill by remember(board.id, threadId) { mutableStateOf<String?>(null) }
    var ngFilteringEnabled by rememberSaveable(board.id, threadId) { mutableStateOf(true) }
    val fallbackNgHeadersState = rememberSaveable(board.id, threadId) { mutableStateOf<List<String>>(emptyList()) }
    val fallbackNgWordsState = rememberSaveable(board.id, threadId) { mutableStateOf<List<String>>(emptyList()) }
    val ngHeadersState = stateStore?.ngHeaders?.collectAsState(initial = fallbackNgHeadersState.value)
    val ngWordsState = stateStore?.ngWords?.collectAsState(initial = fallbackNgWordsState.value)
    val ngHeaders = ngHeadersState?.value ?: fallbackNgHeadersState.value
    val ngWords = ngWordsState?.value ?: fallbackNgWordsState.value
    val showMessage: (String) -> Unit = { message ->
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }
    val persistNgHeaders: (List<String>) -> Unit = { updated ->
        if (stateStore != null) {
            coroutineScope.launch {
                stateStore.setNgHeaders(updated)
            }
        } else {
            fallbackNgHeadersState.value = updated
        }
    }
    val persistNgWords: (List<String>) -> Unit = { updated ->
        if (stateStore != null) {
            coroutineScope.launch {
                stateStore.setNgWords(updated)
            }
        } else {
            fallbackNgWordsState.value = updated
        }
    }
    val urlLauncher = rememberUrlLauncher()

    val handleThreadFilterToggle: (ThreadFilterOption) -> Unit = { option ->
        val currentlySelected = option in selectedThreadFilterOptions
        var updatedOptions = if (currentlySelected) {
            selectedThreadFilterOptions - option
        } else {
            selectedThreadFilterOptions + option
        }
        if (option.sortOption != null && !currentlySelected) {
            updatedOptions = updatedOptions.filter { it.sortOption == null || it == option }.toSet()
        }
        selectedThreadFilterOptions = updatedOptions

        option.sortOption?.let { sortOption ->
            selectedThreadSortOption = if (currentlySelected) null else sortOption
        }
    }

    val addNgHeaderEntry: (String) -> Unit = { value ->
        val trimmed = value.trim()
        when {
            trimmed.isEmpty() -> showMessage("NGヘッダーに含める文字列を入力してください")
            ngHeaders.any { it.equals(trimmed, ignoreCase = true) } -> showMessage("そのNGヘッダーはすでに登録されています")
            else -> {
                persistNgHeaders(ngHeaders + trimmed)
                showMessage("NGヘッダーを追加しました")
            }
        }
    }
    val addNgWordEntry: (String) -> Unit = { value ->
        val trimmed = value.trim()
        when {
            trimmed.isEmpty() -> showMessage("NGワードに含める文字列を入力してください")
            ngWords.any { it.equals(trimmed, ignoreCase = true) } -> showMessage("そのNGワードはすでに登録されています")
            else -> {
                persistNgWords(ngWords + trimmed)
                showMessage("NGワードを追加しました")
            }
        }
    }
    val removeNgHeaderEntry: (String) -> Unit = { entry ->
        persistNgHeaders(ngHeaders.filterNot { it == entry })
        showMessage("NGヘッダーを削除しました")
    }
    val removeNgWordEntry: (String) -> Unit = { entry ->
        persistNgWords(ngWords.filterNot { it == entry })
        showMessage("NGワードを削除しました")
    }
    val toggleNgFiltering: () -> Unit = {
        ngFilteringEnabled = !ngFilteringEnabled
        showMessage(if (ngFilteringEnabled) "NG表示を有効にしました" else "NG表示を無効にしました")
    }
    var replyName by rememberSaveable(board.id, threadId) { mutableStateOf("") }
    var replyEmail by rememberSaveable(board.id, threadId) { mutableStateOf("") }
    var replySubject by rememberSaveable(board.id, threadId) { mutableStateOf("") }
    var replyComment by rememberSaveable(board.id, threadId) { mutableStateOf("") }
    var replyPassword by rememberSaveable(board.id, threadId) { mutableStateOf("") }
    var replyImageData by remember { mutableStateOf<ImageData?>(null) }
    LaunchedEffect(lastUsedDeleteKey) {
        if (replyPassword.isBlank() && lastUsedDeleteKey.isNotBlank()) {
            replyPassword = lastUsedDeleteKey
        }
    }
    var isGalleryVisible by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var manualRefreshGeneration by remember { mutableStateOf(0L) }
    var isHistoryRefreshing by remember { mutableStateOf(false) }
    var saveProgress by remember { mutableStateOf<SaveProgress?>(null) }
    val isPrivacyFilterEnabled by stateStore?.isPrivacyFilterEnabled?.collectAsState(initial = false)
        ?: remember { mutableStateOf(false) }
    val currentState = uiState.value
    val normalizedBoardUrlForHistory = remember(board.url) {
        board.url.trim().substringBefore('?').trimEnd('/').lowercase()
    }
    val initialHistoryEntry = remember(history, threadId, board.id, normalizedBoardUrlForHistory) {
        history.firstOrNull { entry ->
            if (entry.threadId != threadId) {
                false
            } else if (entry.boardId.isNotBlank() && board.id.isNotBlank()) {
                entry.boardId == board.id
            } else {
                entry.boardUrl.trim().substringBefore('?').trimEnd('/').lowercase() == normalizedBoardUrlForHistory
            }
        }
    }
    val lazyListState = remember(threadId, initialHistoryEntry) {
        LazyListState(
            initialHistoryEntry?.lastReadItemIndex ?: 0,
            initialHistoryEntry?.lastReadItemOffset ?: 0
        )
    }
    suspend fun restoreScrollPositionSafely(savedIndex: Int, savedOffset: Int, totalItems: Int) {
        if (totalItems <= 0) return
        val clampedIndex = savedIndex.coerceIn(0, totalItems - 1)
        val clampedOffset = savedOffset.coerceAtLeast(0)
        runCatching {
            lazyListState.scrollToItem(clampedIndex, clampedOffset)
        }.onFailure { error ->
            Logger.w(
                THREAD_SCREEN_TAG,
                "Failed to restore scroll position index=$clampedIndex offset=$clampedOffset: ${error.message}"
            )
        }
    }

    suspend fun loadOfflineThread(): ThreadPage? {
        val localFileSystem = fileSystem ?: return null
        val boardIdCandidates = buildList {
            add(board.id.ifBlank { null })
            add(initialHistoryEntry?.boardId?.ifBlank { null })
            add(
                runCatching { BoardUrlResolver.resolveBoardSlug(effectiveBoardUrl) }
                    .getOrNull()
                    ?.ifBlank { null }
            )
            add(
                runCatching { BoardUrlResolver.resolveBoardSlug(initialHistoryEntry?.boardUrl.orEmpty()) }
                    .getOrNull()
                    ?.ifBlank { null }
            )
            add(null)
        }.distinct()
        val expectedBoardKeys = buildSet<String> {
            normalizeBoardUrlForOfflineLookup(effectiveBoardUrl)?.let(::add)
            normalizeBoardUrlForOfflineLookup(board.url)?.let(::add)
            normalizeBoardUrlForOfflineLookup(initialHistoryEntry?.boardUrl)?.let(::add)
        }

        suspend fun loadMetadata(repository: SavedThreadRepository?): SavedThreadMetadata? {
            repository ?: return null
            for (candidateBoardId in boardIdCandidates) {
                val metadata = repository.loadThreadMetadata(threadId, candidateBoardId).getOrNull() ?: continue
                if (candidateBoardId == null && !metadata.matchesBoardForOfflineFallback(expectedBoardKeys)) {
                    Logger.w(
                        THREAD_SCREEN_TAG,
                        "Skip offline metadata due to board mismatch: threadId=$threadId boardUrl=${metadata.boardUrl}"
                    )
                    continue
                }
                return metadata
            }
            return null
        }

        loadMetadata(autoSaveRepository)?.let { metadata ->
            return metadata.toThreadPage(localFileSystem, AUTO_SAVE_DIRECTORY)
        }

        loadMetadata(manualSaveRepository)?.let { metadata ->
            return metadata.toThreadPage(
                fileSystem = localFileSystem,
                baseDirectory = manualSaveDirectory,
                baseSaveLocation = manualSaveLocation
            )
        }

        loadMetadata(legacyManualSaveRepository)?.let { metadata ->
            return metadata.toThreadPage(
                fileSystem = localFileSystem,
                baseDirectory = DEFAULT_MANUAL_SAVE_ROOT,
                baseSaveLocation = SaveLocation.Path(DEFAULT_MANUAL_SAVE_ROOT)
            )
        }

        if (autoSaveRepository != null || manualSaveRepository != null || legacyManualSaveRepository != null) {
            Logger.i(
                THREAD_SCREEN_TAG,
                "Offline metadata not found for threadId=$threadId boardIdCandidates=$boardIdCandidates"
            )
        }

        return null
    }

    fun shouldFetchByUrl(threadUrl: String?, targetThreadId: String): Boolean {
        if (threadUrl.isNullOrBlank()) return false
        val match = THREAD_URL_ID_REGEX
            .find(threadUrl)
            ?.groupValues
            ?.getOrNull(1)
            ?: return false
        return match == targetThreadId
    }

    suspend fun tryArchiveFallback(): ArchiveFallbackOutcome {
        val client = httpClient ?: return ArchiveFallbackOutcome.NoMatch
        val scope = extractArchiveSearchScope(resolvedThreadUrlOverride ?: board.url)
        val queryCandidates = buildList {
            normalizeArchiveQuery(threadId, maxLength = 64)
                .takeIf { it.isNotBlank() }
                ?.let { add(it) }
            normalizeArchiveQuery(threadTitle, maxLength = 120)
                .takeIf { it.isNotBlank() }
                ?.let { add(it) }
        }.distinct()
        if (queryCandidates.isEmpty()) return ArchiveFallbackOutcome.NoMatch
        for (query in queryCandidates) {
            val results = try {
                withContext(AppDispatchers.io) {
                    fetchArchiveSearchResults(client, query, scope, archiveSearchJson)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (error: Throwable) {
                Logger.w(THREAD_SCREEN_TAG, "Archive search failed for $threadId: ${error.message}")
                continue
            }
            val match = selectLatestArchiveMatch(results, threadId) ?: continue
            val pageResult = try {
                Result.success(
                    withContext(AppDispatchers.io) {
                        activeRepository.getThreadByUrl(match.htmlUrl)
                    }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                Result.failure(t)
            }
            val page = pageResult.getOrNull()
            if (page != null) {
                Logger.i(THREAD_SCREEN_TAG, "Archive refresh succeeded for $threadId")
                return ArchiveFallbackOutcome.Success(page, match.htmlUrl)
            }
            val error = pageResult.exceptionOrNull()
            val status = (error as? NetworkException)?.statusCode
            if (status == 404 || status == 410) {
                return ArchiveFallbackOutcome.NotFound
            }
        }
        return ArchiveFallbackOutcome.NoMatch
    }

    suspend fun loadThreadWithOfflineFallback(allowOfflineFallback: Boolean): Pair<ThreadPage, Boolean> {
        try {
            isShowingOfflineCopy = false
            val page = if (shouldFetchByUrl(resolvedThreadUrlOverride, threadId)) {
                withContext(AppDispatchers.io) {
                    activeRepository.getThreadByUrl(resolvedThreadUrlOverride.orEmpty())
                }
            } else {
                withContext(AppDispatchers.io) {
                    activeRepository.getThread(effectiveBoardUrl, threadId)
                }
            }
            return page to false
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val statusCode = e.statusCodeOrNull()
            if (statusCode == 404 || statusCode == 410) {
                val archived = withTimeoutOrNull(ARCHIVE_FALLBACK_TIMEOUT_MS) {
                    tryArchiveFallback()
                } ?: run {
                    Logger.w(
                        THREAD_SCREEN_TAG,
                        "Archive fallback timed out for threadId=$threadId after ${ARCHIVE_FALLBACK_TIMEOUT_MS}ms"
                    )
                    ArchiveFallbackOutcome.NoMatch
                }
                when (archived) {
                    is ArchiveFallbackOutcome.Success -> {
                        resolvedThreadUrlOverride = archived.threadUrl
                        return archived.page to false
                    }
                    is ArchiveFallbackOutcome.NotFound -> if (!allowOfflineFallback) throw e
                    is ArchiveFallbackOutcome.NoMatch -> Unit
                }
            }
            if (!allowOfflineFallback || !isOfflineFallbackCandidate(e)) throw e
            val offlinePage = withContext(Dispatchers.IO) {
                withTimeoutOrNull(OFFLINE_FALLBACK_TIMEOUT_MS) {
                    loadOfflineThread()
                }
            }
            if (offlinePage != null) {
                isShowingOfflineCopy = true
                return offlinePage to true
            }
            throw e
        }
    }

    val handleMenuEntry: (ThreadMenuEntryId) -> Unit = { entryId ->
        when (entryId) {
            ThreadMenuEntryId.Reply -> {
                if (replyPassword.isBlank()) {
                    replyPassword = lastUsedDeleteKey
                }
                isReplyDialogVisible = true
            }
            ThreadMenuEntryId.ScrollToTop -> {
                coroutineScope.launch { lazyListState.animateScrollToItem(0) }
            }
            ThreadMenuEntryId.ScrollToBottom -> {
                coroutineScope.launch {
                    val currentState = uiState.value
                    if (currentState is ThreadUiState.Success) {
                        val lastIndex = currentState.page.posts.size - 1
                        if (lastIndex >= 0) {
                            lazyListState.animateScrollToItem(lastIndex)
                        }
                    }
                }
            }
            ThreadMenuEntryId.Refresh -> {
                if (isRefreshing) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("更新中です…")
                    }
                } else {
                    val savedIndex = lazyListState.firstVisibleItemIndex
                    val savedOffset = lazyListState.firstVisibleItemScrollOffset
                    val requestGeneration = manualRefreshGeneration + 1L
                    manualRefreshGeneration = requestGeneration
                    isRefreshing = true
                    refreshThreadJob?.cancel()
                    refreshThreadJob = coroutineScope.launch {
                        val runningJob = coroutineContext[Job]
                        try {
                            val (page, usedOffline) = loadThreadWithOfflineFallback(allowOfflineFallback = true)
                            if (isActive) {
                                uiState.value = ThreadUiState.Success(page)
                                restoreScrollPositionSafely(savedIndex, savedOffset, page.posts.size)
                                onHistoryEntryUpdated(
                                    buildHistoryEntryFromPage(
                                        page = page,
                                        history = history,
                                        threadId = threadId,
                                        threadTitle = threadTitle,
                                        board = board,
                                        overrideThreadUrl = resolvedThreadUrlOverride
                                    )
                                )
                                val successMessage = if (usedOffline) {
                                    "ネットワーク接続不可: ローカルコピーを表示しています"
                                } else {
                                    "スレッドを更新しました"
                                }
                                snackbarHostState.showSnackbar(successMessage)
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            val status = e.statusCodeOrNull()
                            val failureMessage = when (status) {
                                404 -> "更新に失敗しました: スレッドが見つかりません (404)"
                                410 -> "更新に失敗しました: スレッドは削除済みです (410)"
                                else -> "更新に失敗しました: ${e.message ?: "不明なエラー"}"
                            }
                            snackbarHostState.showSnackbar(failureMessage)
                        } finally {
                            if (manualRefreshGeneration == requestGeneration) {
                                isRefreshing = false
                            }
                            if (runningJob != null && refreshThreadJob == runningJob) {
                                refreshThreadJob = null
                            }
                        }
                    }
                }
            }
            ThreadMenuEntryId.Gallery -> {
                isGalleryVisible = true
            }
            ThreadMenuEntryId.Save -> {
                run {
                    if (isManualSaveInProgress || isSingleMediaSaveInProgress) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("保存処理を実行中です…")
                        }
                        return@run
                    }

                    if (
                        isAndroidPlatform &&
                        manualSaveLocation !is SaveLocation.TreeUri &&
                        manualSaveLocation !is SaveLocation.Path
                    ) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("保存先が未選択です。設定からフォルダを選択してください。")
                            onOpenSaveDirectoryPicker?.invoke()
                        }
                        return@run
                    }

                    val currentStateValue = uiState.value
                    if (currentStateValue is ThreadUiState.Success) {
                        if (httpClient != null && fileSystem != null) {
                            isManualSaveInProgress = true
                            manualSaveJob = coroutineScope.launch {
                                var progressJob: Job? = null
                                try {
                                    val saveService = ThreadSaveService(
                                        httpClient = httpClient,
                                        fileSystem = fileSystem
                                    )
                                    progressJob = launch {
                                        saveService.saveProgress
                                            .collect { progress ->
                                                saveProgress = progress
                                            }
                                    }
                                    val page = currentStateValue.page
                                    val resolvedTitle = resolveThreadTitle(
                                        page.posts.firstOrNull(),
                                        threadTitle
                                    )
                                    val result = saveService.saveThread(
                                        threadId = threadId,
                                        boardId = board.id,
                                        boardName = board.name,
                                        boardUrl = effectiveBoardUrl,
                                        title = resolvedTitle,
                                        expiresAtLabel = page.expiresAtLabel,
                                        posts = page.posts,
                                        baseSaveLocation = manualSaveLocation,
                                        baseDirectory = manualSaveDirectory,
                                        writeMetadata = true
                                    )
                                    result.onSuccess { savedThread ->
                                        manualSaveRepository
                                            ?.addThreadToIndex(savedThread)
                                            ?.onFailure { Logger.e(THREAD_AUTO_SAVE_TAG, "Failed to index manually saved thread $threadId", it) }
                                        saveProgress = null
                                        snackbarHostState.showSnackbar("スレッドを保存しました")
                                    }.onFailure { error ->
                                        saveProgress = null
                                        if (isSaveLocationPermissionIssue(error)) {
                                            onManualSaveDirectoryChanged(DEFAULT_MANUAL_SAVE_ROOT)
                                            onSaveDirectorySelectionChanged(SaveDirectorySelection.MANUAL_INPUT)
                                            snackbarHostState.showSnackbar("保存先の権限が失われました。フォルダを再選択してください。")
                                            onOpenSaveDirectoryPicker?.invoke()
                                        } else {
                                            snackbarHostState.showSnackbar("保存に失敗しました: ${error.message}")
                                        }
                                    }
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    saveProgress = null
                                    if (isSaveLocationPermissionIssue(e)) {
                                        onManualSaveDirectoryChanged(DEFAULT_MANUAL_SAVE_ROOT)
                                        onSaveDirectorySelectionChanged(SaveDirectorySelection.MANUAL_INPUT)
                                        snackbarHostState.showSnackbar("保存先の権限が失われました。フォルダを再選択してください。")
                                        onOpenSaveDirectoryPicker?.invoke()
                                    } else {
                                        snackbarHostState.showSnackbar("エラーが発生しました: ${e.message}")
                                    }
                                } finally {
                                    progressJob?.cancel()
                                    saveProgress = null
                                    isManualSaveInProgress = false
                                    if (manualSaveJob === this.coroutineContext[Job]) {
                                        manualSaveJob = null
                                    }
                                }
                            }
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("保存機能が利用できません")
                            }
                        }
                    } else {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("スレッドの読み込みが完了していません")
                        }
                    }
                }
            }
            ThreadMenuEntryId.Filter -> {
                isThreadFilterSheetVisible = true
            }
            ThreadMenuEntryId.Settings -> {
                isThreadSettingsSheetVisible = true
            }
            ThreadMenuEntryId.NgManagement -> {
                ngHeaderPrefill = null
                isNgManagementVisible = true
            }
            ThreadMenuEntryId.ExternalApp -> {
                val baseUrl = effectiveBoardUrl.trimEnd('/').removeSuffix("/futaba.php")
                val threadUrl = "$baseUrl/res/${threadId}.htm"
                urlLauncher(threadUrl)
            }
            ThreadMenuEntryId.ReadAloud -> {
                isReadAloudControlsVisible = true
            }
            ThreadMenuEntryId.Privacy -> {
                coroutineScope.launch {
                    stateStore?.setPrivacyFilterEnabled(!isPrivacyFilterEnabled)
                }
            }
        }
    }
    val handleHistoryRefresh: () -> Unit = handleHistoryRefresh@{
        if (isHistoryRefreshing) return@handleHistoryRefresh
        isHistoryRefreshing = true
        coroutineScope.launch {
            try {
                onHistoryRefresh()
                snackbarHostState.showSnackbar("履歴を更新しました")
            } catch (e: HistoryRefresher.RefreshAlreadyRunningException) {
                snackbarHostState.showSnackbar("履歴更新はすでに実行中です")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("履歴の更新に失敗しました: ${e.message ?: "不明なエラー"}")
            } finally {
                isHistoryRefreshing = false
            }
        }
    }

    suspend fun startAutoSave(page: ThreadPage) {
        val repository = autoSaveRepository ?: return
        val client = httpClient ?: return
        val localFileSystem = fileSystem ?: return
        if (autoSaveJob?.isActive == true) return
        val now = Clock.System.now().toEpochMilliseconds()
        if (now - lastAutoSaveTimestamp.value < AUTO_SAVE_INTERVAL_MS) return
        autoSaveJob = coroutineScope.launch {
            val runningJob = coroutineContext[Job]
            val attemptStartedAt = Clock.System.now().toEpochMilliseconds()
            try {
                val result = try {
                    val saveService = ThreadSaveService(client, localFileSystem)
                    val resolvedTitle = resolveThreadTitle(page.posts.firstOrNull(), threadTitle)
                    Result.success(
                        saveService.saveThread(
                            threadId = threadId,
                            boardId = board.id,
                            boardName = board.name,
                            boardUrl = effectiveBoardUrl,
                            title = resolvedTitle,
                            expiresAtLabel = page.expiresAtLabel,
                            posts = page.posts,
                            baseDirectory = AUTO_SAVE_DIRECTORY,
                            writeMetadata = true
                        )
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Result.failure(t)
                }
                result.onSuccess { saveResult ->
                    saveResult.onSuccess { savedThread ->
                        lastAutoSaveTimestamp.value = Clock.System.now().toEpochMilliseconds()
                        repository.addThreadToIndex(savedThread)
                            .onFailure {
                                Logger.e(THREAD_AUTO_SAVE_TAG, "Failed to index auto-saved thread $threadId", it)
                            }
                    }.onFailure {
                        Logger.e(THREAD_AUTO_SAVE_TAG, "Auto-save failed for thread $threadId", it)
                    }
                }.onFailure {
                    Logger.e(THREAD_AUTO_SAVE_TAG, "Auto-save job failed unexpectedly for thread $threadId", it)
                }
                if (lastAutoSaveTimestamp.value < attemptStartedAt) {
                    // 失敗ケースでも次回試行まで最短間隔を確保し、連続失敗による負荷集中を避ける。
                    lastAutoSaveTimestamp.value = attemptStartedAt
                }
            } finally {
                if (runningJob != null && autoSaveJob == runningJob) {
                    autoSaveJob = null
                }
            }
        }
    }

    PlatformBackHandler(enabled = isDrawerOpen) {
        coroutineScope.launch { drawerState.close() }
    }
    PlatformBackHandler(enabled = !isDrawerOpen) {
        onBack()
    }

    val refreshThread: () -> Unit = remember(
        effectiveBoardUrl,
        threadId,
        activeRepository,
        history,
        onHistoryEntryUpdated,
        resolvedThreadUrlOverride,
        threadTitle,
        board.id,
        board.name
    ) {
        {
            refreshThreadJob?.cancel()
            refreshThreadJob = coroutineScope.launch {
                val runningJob = coroutineContext[Job]
                uiState.value = ThreadUiState.Loading
                try {
                    val (page, usedOffline) = loadThreadWithOfflineFallback(allowOfflineFallback = true)
                    if (isActive) {
                        uiState.value = ThreadUiState.Success(page)
                        onHistoryEntryUpdated(
                            buildHistoryEntryFromPage(
                                page = page,
                                history = history,
                                threadId = threadId,
                                threadTitle = threadTitle,
                                board = board,
                                overrideThreadUrl = resolvedThreadUrlOverride
                            )
                        )
                        if (usedOffline) {
                            snackbarHostState.showSnackbar("ローカルコピーを表示しています")
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isActive) {
                        val statusCode = e.statusCodeOrNull()
                        val message = when {
                            e.message?.contains("timeout", ignoreCase = true) == true -> "タイムアウト: サーバーが応答しません"
                            statusCode == 404 -> "スレッドが見つかりません (404)"
                            statusCode == 410 -> "スレッドは削除済みです (410)"
                            statusCode != null && statusCode >= 500 -> "サーバーエラー ($statusCode)"
                            e.message?.contains("HTTP error") == true -> "ネットワークエラー: ${e.message}"
                            e.message?.contains("exceeds maximum") == true -> "データサイズが大きすぎます"
                            else -> "スレッドを読み込めませんでした: ${e.message ?: "不明なエラー"}"
                        }
                        uiState.value = ThreadUiState.Error(message)
                        snackbarHostState.showSnackbar(message)
                    }
                } finally {
                    if (runningJob != null && refreshThreadJob == runningJob) {
                        refreshThreadJob = null
                    }
                }
            }
        }
    }

    LaunchedEffect(effectiveBoardUrl, threadId) {
        refreshThread()
    }

    val currentSuccessState = currentState as? ThreadUiState.Success
    val currentPosts = currentSuccessState?.page?.posts ?: emptyList()
    val mediaPreviewEntries by produceState<List<MediaPreviewEntry>>(
        initialValue = emptyList(),
        key1 = currentPosts
    ) {
        value = withContext(AppDispatchers.parsing) {
            buildMediaPreviewEntries(currentPosts)
        }
    }
    var previewMediaIndex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(mediaPreviewEntries.size) {
        if (previewMediaIndex != null && previewMediaIndex !in mediaPreviewEntries.indices) {
            previewMediaIndex = null
        }
    }
    val handleMediaClick: (String, MediaType) -> Unit = { url, mediaType ->
        val targetIndex = mediaPreviewEntries.indexOfFirst { it.url == url && it.mediaType == mediaType }
        if (targetIndex >= 0) {
            previewMediaIndex = targetIndex
        }
    }

    // Auto-save logic: Trigger when thread content instance changes or offline state changes.
    val currentPageForAutoSave = currentSuccessState?.page
    LaunchedEffect(threadId, currentPageForAutoSave, isShowingOfflineCopy, httpClient, fileSystem) {
        if (
            currentPageForAutoSave != null &&
            !isShowingOfflineCopy &&
            currentPageForAutoSave.threadId == threadId
        ) {
            startAutoSave(currentPageForAutoSave)
        }
    }

    val resolvedReplyCount: Int? = when (currentState) {
        is ThreadUiState.Success -> currentState.page.posts.size
        else -> initialReplyCount
    }
    val resolvedThreadTitle = when (currentState) {
        is ThreadUiState.Success -> threadTitle ?: currentState.page.posts.firstOrNull()?.subject ?: "スレッド"
        else -> threadTitle ?: "スレッド"
    }
    val expiresLabel = (currentState as? ThreadUiState.Success)
        ?.page
        ?.expiresAtLabel
        ?.takeIf { it.isNotBlank() }
    val statusLabel = buildString {
        resolvedReplyCount?.let { append("${it}レス") }
        if (!expiresLabel.isNullOrBlank()) {
            if (isNotEmpty()) append(" / ")
            append(expiresLabel)
        }
    }.ifBlank { null }

    val shouldPrepareReadAloudSegments = isReadAloudControlsVisible ||
        readAloudStatus is ReadAloudStatus.Speaking ||
        readAloudStatus is ReadAloudStatus.Paused
    val readAloudSegments by produceState<List<ReadAloudSegment>>(
        initialValue = emptyList(),
        key1 = currentPosts,
        key2 = shouldPrepareReadAloudSegments
    ) {
        if (!shouldPrepareReadAloudSegments || currentPosts.isEmpty()) {
            value = emptyList()
            return@produceState
        }
        value = withContext(AppDispatchers.parsing) {
            buildReadAloudSegments(currentPosts)
        }
    }
    LaunchedEffect(readAloudSegments.size) {
        if (currentReadAloudIndex > readAloudSegments.size) {
            currentReadAloudIndex = readAloudSegments.size
        }
    }

    val density = LocalDensity.current
    val backSwipeEdgePx = remember(density) { with(density) { 48.dp.toPx() } }
    val backSwipeTriggerPx = remember(density) { with(density) { 96.dp.toPx() } }
    val firstVisibleSegmentIndex by remember(readAloudSegments, lazyListState, isReadAloudControlsVisible) {
        derivedStateOf {
            if (!isReadAloudControlsVisible) return@derivedStateOf -1
            val firstVisibleItem = lazyListState.firstVisibleItemIndex
            if (readAloudSegments.isEmpty()) return@derivedStateOf -1
            findFirstVisibleReadAloudSegmentIndex(readAloudSegments, firstVisibleItem)
        }
    }

    val startReadAloud: () -> Unit = startReadAloud@{
        if (readAloudSegments.isEmpty()) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("読み上げ対象がありません")
            }
            return@startReadAloud
        }
        if (readAloudJob != null) return@startReadAloud
        if (currentReadAloudIndex >= readAloudSegments.size) {
            currentReadAloudIndex = 0
        }
        readAloudCancelRequestedByUser = false
        readAloudJob = coroutineScope.launch {
            var completedNormally = false
            var index = currentReadAloudIndex
            try {
                while (index < readAloudSegments.size && isActive) {
                    val segment = readAloudSegments[index]
                    readAloudStatus = ReadAloudStatus.Speaking(segment)
                    lazyListState.animateScrollToItem(segment.postIndex)
                    currentReadAloudIndex = index
                    textSpeaker.speak(segment.body)
                    index++
                }
                if (index >= readAloudSegments.size && isActive) {
                    completedNormally = true
                    currentReadAloudIndex = 0
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (!readAloudCancelRequestedByUser) {
                    throw e
                }
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("読み上げ中にエラーが発生しました: ${e.message ?: "不明なエラー"}")
            } finally {
                readAloudCancelRequestedByUser = false
                if (completedNormally) {
                    readAloudStatus = ReadAloudStatus.Idle
                    snackbarHostState.showSnackbar("読み上げを完了しました")
                } else if (readAloudStatus !is ReadAloudStatus.Paused) {
                    readAloudStatus = ReadAloudStatus.Idle
                }
                readAloudJob = null
            }
        }
    }
    val seekReadAloudToIndex: (Int, Boolean) -> Unit = seek@{ targetIndex, shouldScroll ->
        if (readAloudSegments.isEmpty()) return@seek
        val wasPlaying = readAloudStatus is ReadAloudStatus.Speaking || readAloudStatus is ReadAloudStatus.Paused
        val clampedIndex = targetIndex.coerceIn(0, readAloudSegments.lastIndex.coerceAtLeast(0))
        cancelActiveReadAloud()
        readAloudJob = null
        readAloudStatus = ReadAloudStatus.Idle
        currentReadAloudIndex = clampedIndex
        val targetSegment = readAloudSegments.getOrNull(clampedIndex)
        if (shouldScroll && targetSegment != null) {
            coroutineScope.launch {
                lazyListState.animateScrollToItem(targetSegment.postIndex)
            }
        }
        if (wasPlaying) {
            startReadAloud()
        }
    }

    var isSearchActive by rememberSaveable(threadId) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(threadId) { mutableStateOf("") }
    var currentSearchResultIndex by remember(threadId) { mutableStateOf(0) }
    val currentPage = (currentState as? ThreadUiState.Success)?.page
    val searchTargets by produceState<List<ThreadSearchTarget>>(
        initialValue = emptyList(),
        key1 = isSearchActive,
        key2 = currentPage?.posts
    ) {
        if (!isSearchActive || currentPage == null) {
            value = emptyList()
            return@produceState
        }
        value = withContext(AppDispatchers.parsing) {
            buildThreadSearchTargets(currentPage.posts)
        }
    }
    val normalizedSearchQuery = remember(searchQuery) { searchQuery.trim() }
    val searchMatches by produceState<List<ThreadSearchMatch>>(
        initialValue = emptyList(),
        key1 = isSearchActive,
        key2 = normalizedSearchQuery,
        key3 = searchTargets
    ) {
        if (!isSearchActive || normalizedSearchQuery.isBlank() || searchTargets.isEmpty()) {
            value = emptyList()
            return@produceState
        }
        // Avoid re-running expensive full-text scans for every keystroke.
        delay(THREAD_SEARCH_DEBOUNCE_MILLIS)
        value = withContext(AppDispatchers.parsing) {
            buildThreadSearchMatches(searchTargets, normalizedSearchQuery)
        }
    }
    val postHighlightRanges = remember(isSearchActive, searchMatches) {
        if (isSearchActive) {
            searchMatches.associate { it.postId to it.highlightRanges }
        } else {
            emptyMap()
        }
    }

    LaunchedEffect(searchQuery, threadId) {
        currentSearchResultIndex = 0
    }

    LaunchedEffect(searchMatches) {
        if (searchMatches.isEmpty()) {
            currentSearchResultIndex = 0
        } else if (currentSearchResultIndex !in searchMatches.indices) {
            currentSearchResultIndex = 0
        }
    }

    fun scrollToSearchMatch(targetIndex: Int) {
        val target = searchMatches.getOrNull(targetIndex) ?: return
        coroutineScope.launch {
            lazyListState.animateScrollToItem(target.postIndex)
        }
    }

    fun focusCurrentSearchMatch() {
        if (searchMatches.isNotEmpty()) {
            scrollToSearchMatch(currentSearchResultIndex)
        }
    }

    fun moveToNextSearchMatch() {
        if (searchMatches.isEmpty()) return
        val nextIndex = if (currentSearchResultIndex + 1 >= searchMatches.size) 0 else currentSearchResultIndex + 1
        currentSearchResultIndex = nextIndex
        scrollToSearchMatch(nextIndex)
    }

    fun moveToPreviousSearchMatch() {
        if (searchMatches.isEmpty()) return
        val previousIndex = if (currentSearchResultIndex - 1 < 0) searchMatches.lastIndex else currentSearchResultIndex - 1
        currentSearchResultIndex = previousIndex
        scrollToSearchMatch(previousIndex)
    }

    LaunchedEffect(threadId) {
        snapshotFlow {
            lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .debounce(1_000)
            .collect { (index, offset) ->
                onScrollPositionPersist(threadId, index, offset)
            }
    }

    val handleHistorySelection: (ThreadHistoryEntry) -> Unit = { entry ->
        coroutineScope.launch { drawerState.close() }
        onHistoryEntrySelected(entry)
    }

    fun <T> launchThreadAction(
        successMessage: String,
        failurePrefix: String,
        onSuccess: (T) -> Unit = {},
        block: suspend () -> T
    ) {
        coroutineScope.launch {
            if (actionInProgress) {
                val nowMillis = Clock.System.now().toEpochMilliseconds()
                if (nowMillis - lastBusyActionNoticeAtMillis >= ACTION_BUSY_NOTICE_INTERVAL_MS) {
                    lastBusyActionNoticeAtMillis = nowMillis
                    coroutineScope.launch { snackbarHostState.showSnackbar("処理中です…") }
                }
                return@launch
            }
            actionInProgress = true
            Logger.d(THREAD_ACTION_LOG_TAG, "Starting thread action: success='$successMessage', failure='$failurePrefix'")
            try {
                val result = block()
                Logger.i(THREAD_ACTION_LOG_TAG, "Thread action succeeded: $successMessage")
                onSuccess(result)
                coroutineScope.launch { snackbarHostState.showSnackbar(successMessage) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(THREAD_ACTION_LOG_TAG, "Thread action failed: $failurePrefix", e)
                val detail = e.message?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
                coroutineScope.launch { snackbarHostState.showSnackbar("$failurePrefix$detail") }
            } finally {
                actionInProgress = false
            }
        }
    }

    val handleSaidaneAction: (Post) -> Unit = handleSaidaneAction@{ post ->
        if (isSelfPost(post)) {
            isActionSheetVisible = false
            coroutineScope.launch { snackbarHostState.showSnackbar("自分のレスにはそうだねできません") }
            return@handleSaidaneAction
        }
        isActionSheetVisible = false
        val baseLabel = saidaneOverrides[post.id] ?: post.saidaneLabel
        launchThreadAction(
            successMessage = "そうだねを送信しました",
            failurePrefix = "そうだねに失敗しました",
            onSuccess = {
                saidaneOverrides[post.id] = incrementSaidaneLabel(baseLabel)
            }
        ) {
            activeRepository.voteSaidane(effectiveBoardUrl, threadId, post.id)
        }
    }

    val handleDelRequest: (Post) -> Unit = { post ->
        isActionSheetVisible = false
        launchThreadAction(
            successMessage = "DEL依頼を送信しました",
            failurePrefix = "DEL依頼に失敗しました"
        ) {
            activeRepository.requestDeletion(effectiveBoardUrl, threadId, post.id, DEFAULT_DEL_REASON_CODE)
        }
    }

    val openDeleteDialog: (Post) -> Unit = { post ->
        isActionSheetVisible = false
        deleteDialogTarget = post
        pendingDeletePassword = lastUsedDeleteKey
        pendingDeleteImageOnly = false
    }

    val openQuoteSelection: (Post) -> Unit = { post ->
        isActionSheetVisible = false
        actionTargetPost = null
        quoteSelectionTarget = post
    }

    val handleNgRegistration: (Post) -> Unit = { post ->
        isActionSheetVisible = false
        actionTargetPost = null
        val prefillValue = buildNgHeaderPrefillValue(post)
        ngHeaderPrefill = prefillValue
        if (prefillValue == null) {
            coroutineScope.launch { snackbarHostState.showSnackbar("IDが見つかりませんでした") }
        }
        isNgManagementVisible = true
    }

    val performRefresh: () -> Unit = refresh@{
        if (isRefreshing) return@refresh
        val requestGeneration = manualRefreshGeneration + 1L
        manualRefreshGeneration = requestGeneration
        isRefreshing = true
        refreshThreadJob?.cancel()
        refreshThreadJob = coroutineScope.launch {
            val runningJob = coroutineContext[Job]
            val savedIndex = lazyListState.firstVisibleItemIndex
            val savedOffset = lazyListState.firstVisibleItemScrollOffset
            try {
                val (page, usedOffline) = loadThreadWithOfflineFallback(allowOfflineFallback = true)
                uiState.value = ThreadUiState.Success(page)
                restoreScrollPositionSafely(savedIndex, savedOffset, page.posts.size)

                onHistoryEntryUpdated(buildHistoryEntryFromPage(
                    page = page,
                    history = history,
                    threadId = threadId,
                    threadTitle = threadTitle,
                    board = board,
                    overrideThreadUrl = resolvedThreadUrlOverride
                ))

                val successMessage = if (usedOffline) {
                    "ネットワーク接続不可: ローカルコピーを表示しています"
                } else {
                    "スレッドを更新しました"
                }
                snackbarHostState.showSnackbar(successMessage)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val status = e.statusCodeOrNull()
                val failureMessage = when (status) {
                    404 -> "更新に失敗しました: スレッドが見つかりません (404)"
                    410 -> "更新に失敗しました: スレッドは削除済みです (410)"
                    else -> "更新に失敗しました: ${e.message ?: "不明なエラー"}"
                }
                snackbarHostState.showSnackbar(failureMessage)
            } finally {
                if (manualRefreshGeneration == requestGeneration) {
                    isRefreshing = false
                }
                if (runningJob != null && refreshThreadJob == runningJob) {
                    refreshThreadJob = null
                }
            }
        }
    }

    val handleBatchDelete: () -> Unit = {
        coroutineScope.launch {
            onHistoryCleared()
            snackbarHostState.showSnackbar("履歴を一括削除しました")
            drawerState.close()
        }
    }

    val appColorScheme = MaterialTheme.colorScheme
    val futabaThreadColorScheme = rememberFutabaThreadColorScheme(appColorScheme)

    MaterialTheme(
        colorScheme = futabaThreadColorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes
    ) {
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
                MaterialTheme(
                    colorScheme = appColorScheme,
                    typography = MaterialTheme.typography,
                    shapes = MaterialTheme.shapes
                ) {
                    ThreadTopBar(
                        boardName = board.name,
                        threadTitle = resolvedThreadTitle,
                        replyCount = resolvedReplyCount,
                        statusLabel = statusLabel,
                        isSearchActive = isSearchActive,
                        searchQuery = searchQuery,
                        currentSearchIndex = currentSearchResultIndex,
                        totalSearchMatches = searchMatches.size,
                        onSearchQueryChange = { searchQuery = it },
                        onSearchPrev = { moveToPreviousSearchMatch() },
                        onSearchNext = { moveToNextSearchMatch() },
                        onSearchSubmit = { focusCurrentSearchMatch() },
                        onSearchClose = { isSearchActive = false },
                        onBack = onBack,
                        onOpenHistory = { coroutineScope.launch { drawerState.open() } },
                        onSearch = { isSearchActive = true },
                        onMenuSettings = { isGlobalSettingsVisible = true }
                    )
                }
            },
            bottomBar = {
                MaterialTheme(
                    colorScheme = appColorScheme,
                    typography = MaterialTheme.typography,
                    shapes = MaterialTheme.shapes
                ) {
                    ThreadActionBar(
                        menuEntries = threadMenuEntries,
                        onAction = handleMenuEntry
                    )
                }
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
                .pointerInput(onBack, isDrawerOpen, backSwipeEdgePx, backSwipeTriggerPx) {
                    if (isDrawerOpen) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (down.position.x > backSwipeEdgePx) {
                            waitForUpOrCancellation()
                            return@awaitEachGesture
                        }
                        var totalDx = 0f
                        var totalDy = 0f
                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }
                                ?: event.changes.firstOrNull()
                                ?: continue
                            if (event.changes.none { it.pressed } || !change.pressed) break
                            val delta = change.positionChange()
                            totalDx = (totalDx + delta.x).coerceAtLeast(0f)
                            totalDy += abs(delta.y)
                            if (totalDy > backSwipeTriggerPx && totalDy > totalDx) {
                                break
                            }
                            if (totalDx > backSwipeTriggerPx && totalDx > totalDy) {
                                change.consume()
                                onBack()
                                break
                            }
                        }
                    }
                }
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 0.dp)

            Box(modifier = contentModifier) {
                when (val state = uiState.value) {
                    ThreadUiState.Loading -> ThreadLoading(modifier = Modifier.matchParentSize())
                    is ThreadUiState.Error -> ThreadError(
                        message = state.message,
                        modifier = Modifier.matchParentSize(),
                        onRetry = refreshThread
                    )

                    is ThreadUiState.Success -> {
                        val threadFilterCriteria = remember(
                            selectedThreadFilterOptions,
                            threadFilterKeyword,
                            selectedThreadSortOption,
                            persistedSelfPostIdentifiers
                        ) {
                            ThreadFilterCriteria(
                                options = selectedThreadFilterOptions,
                                keyword = threadFilterKeyword,
                                selfPostIdentifiers = persistedSelfPostIdentifiers,
                                sortOption = selectedThreadSortOption
                            )
                        }
                        val hasNgFilters = ngFilteringEnabled && (
                            ngHeaders.any { it.isNotBlank() } ||
                                ngWords.any { it.isNotBlank() }
                            )
                        val hasThreadFilters = threadFilterCriteria.options.isNotEmpty()
                        val shouldComputeFullPostFingerprint = hasNgFilters || hasThreadFilters
                        val postsFingerprint by produceState(
                            initialValue = buildLightweightThreadPostListFingerprint(state.page.posts),
                            key1 = state.page.posts,
                            key2 = shouldComputeFullPostFingerprint
                        ) {
                            value = if (shouldComputeFullPostFingerprint) {
                                withContext(AppDispatchers.parsing) {
                                    buildThreadPostListFingerprint(state.page.posts)
                                }
                            } else {
                                buildLightweightThreadPostListFingerprint(state.page.posts)
                            }
                        }
                        val filterCacheKey = remember(
                            postsFingerprint,
                            hasNgFilters,
                            ngHeaders,
                            ngWords,
                            threadFilterCriteria
                        ) {
                            ThreadFilterCacheKey(
                                postsFingerprint = postsFingerprint,
                                ngEnabled = hasNgFilters,
                                ngHeadersFingerprint = stableNormalizedListFingerprint(ngHeaders),
                                ngWordsFingerprint = stableNormalizedListFingerprint(ngWords),
                                filterOptionsFingerprint = stableThreadFilterOptionSetFingerprint(threadFilterCriteria.options),
                                keyword = threadFilterCriteria.keyword.trim().lowercase(),
                                selfIdentifiersFingerprint = stableNormalizedListFingerprint(threadFilterCriteria.selfPostIdentifiers),
                                sortOption = threadFilterCriteria.sortOption
                            )
                        }
                        val cachedFilteredPage = remember(filterCacheKey, hasNgFilters, hasThreadFilters) {
                            if (!hasNgFilters && !hasThreadFilters) {
                                null
                            } else {
                                threadFilterCache[filterCacheKey]
                            }
                        }
                        val filteredPage by produceState(
                            initialValue = cachedFilteredPage ?: state.page,
                            key1 = filterCacheKey
                        ) {
                            threadFilterCache[filterCacheKey]?.let {
                                value = it
                                return@produceState
                            }
                            if (!hasNgFilters && !hasThreadFilters) {
                                value = state.page
                                return@produceState
                            }
                            // Keyword filtering can be expensive in large threads.
                            if (threadFilterCriteria.options.contains(ThreadFilterOption.Keyword)) {
                                delay(THREAD_FILTER_DEBOUNCE_MILLIS)
                            }
                            value = withContext(AppDispatchers.parsing) {
                                val hasNgWordFilters = hasNgFilters && ngWords.any { it.isNotBlank() }
                                val hasThreadLowerBodyFilters = threadFilterCriteria.options.any {
                                    it == ThreadFilterOption.Url || it == ThreadFilterOption.Keyword
                                }
                                val precomputedLowerBodyByPostId = if (hasNgWordFilters || hasThreadLowerBodyFilters) {
                                    buildLowerBodyByPostId(state.page.posts)
                                } else {
                                    emptyMap()
                                }
                                val ngFiltered = applyNgFilters(
                                    page = state.page,
                                    ngHeaders = ngHeaders,
                                    ngWords = ngWords,
                                    enabled = hasNgFilters,
                                    precomputedLowerBodyByPostId = precomputedLowerBodyByPostId
                                )
                                applyThreadFilters(
                                    page = ngFiltered,
                                    criteria = threadFilterCriteria,
                                    precomputedLowerBodyByPostId = precomputedLowerBodyByPostId
                                )
                            }
                            if (threadFilterCache.size >= THREAD_FILTER_CACHE_MAX_ENTRIES) {
                                val iterator = threadFilterCache.entries.iterator()
                                if (iterator.hasNext()) {
                                    iterator.next()
                                    iterator.remove()
                                }
                            }
                            threadFilterCache[filterCacheKey] = value
                        }
                        ThreadContent(
                            page = filteredPage,
                            listState = lazyListState,
                            saidaneOverrides = saidaneOverrides,
                            selfPostIdentifiers = selfPostIdentifierSet,
                            searchHighlightRanges = postHighlightRanges,
                            onPostLongPress = { post ->
                                actionTargetPost = post
                                isActionSheetVisible = true
                            },
                            onQuoteRequestedForPost = { post ->
                                isActionSheetVisible = false
                                actionTargetPost = null
                                quoteSelectionTarget = post
                            },
                            onSaidaneClick = handleSaidaneAction,
                            onMediaClick = handleMediaClick,
                            onUrlClick = urlLauncher,
                            onRefresh = performRefresh,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }

                if (actionInProgress) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                    )
                }
                if (readAloudStatus is ReadAloudStatus.Speaking) {
                    val segment = (readAloudStatus as ReadAloudStatus.Speaking).segment
                    ReadAloudIndicator(
                        segment = segment,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 80.dp)
                    )
                }
            }
        }
    }

    val sheetTarget = actionTargetPost
    if (isActionSheetVisible && sheetTarget != null) {
        ThreadPostActionSheet(
            post = sheetTarget,
            onDismiss = {
                isActionSheetVisible = false
                actionTargetPost = null
            },
            onQuote = { openQuoteSelection(sheetTarget) },
            onNgRegister = { handleNgRegistration(sheetTarget) },
            onSaidane = { handleSaidaneAction(sheetTarget) },
            isSaidaneEnabled = !isSelfPost(sheetTarget),
            onDelRequest = { handleDelRequest(sheetTarget) },
            onDelete = { openDeleteDialog(sheetTarget) }
        )
    }

    val deleteTarget = deleteDialogTarget
    if (deleteTarget != null) {
        DeleteByUserDialog(
            post = deleteTarget,
            password = pendingDeletePassword,
            onPasswordChange = { pendingDeletePassword = it },
            imageOnly = pendingDeleteImageOnly,
            onImageOnlyChange = { pendingDeleteImageOnly = it },
            onDismiss = {
                deleteDialogTarget = null
                pendingDeletePassword = ""
                pendingDeleteImageOnly = false
            },
            onConfirm = {
                val trimmed = pendingDeletePassword.trim()
                if (trimmed.isBlank()) {
                    coroutineScope.launch { snackbarHostState.showSnackbar("削除キーを入力してください") }
                    return@DeleteByUserDialog
                }
                val imageOnly = pendingDeleteImageOnly
                deleteDialogTarget = null
                pendingDeletePassword = ""
                pendingDeleteImageOnly = false
                updateLastUsedDeleteKey(trimmed)
                launchThreadAction(
                    successMessage = "本人削除を実行しました",
                    failurePrefix = "本人削除に失敗しました",
                    onSuccess = { refreshThread() }
                ) {
                    activeRepository.deleteByUser(
                        effectiveBoardUrl,
                        threadId,
                        deleteTarget.id,
                        trimmed,
                        imageOnly
                    )
                }
            }
        )
    }

    val quoteTarget = quoteSelectionTarget
    if (quoteTarget != null) {
        QuoteSelectionDialog(
            post = quoteTarget,
            onDismiss = { quoteSelectionTarget = null },
            onConfirm = { selectedLines ->
                val quoteBody = selectedLines.joinToString("\n").trimEnd()
                if (quoteBody.isNotBlank()) {
                    val existing = replyComment.trimEnd()
                    val builder = StringBuilder()
                    if (existing.isNotBlank()) {
                        builder.append(existing)
                        builder.append("\n")
                    }
                    builder.append(quoteBody)
                    builder.append("\n")
                    replyComment = builder.toString()
                    isReplyDialogVisible = true
                }
                quoteSelectionTarget = null
            }
        )
    }

    if (isReplyDialogVisible) {
        val emailPresets = remember { listOf("ID表示", "IP表示", "sage") }
        val subtitle = remember(board.name, resolvedThreadTitle) {
            listOfNotNull(
                board.name.takeIf { it.isNotBlank() },
                resolvedThreadTitle.takeIf { it.isNotBlank() }
            ).joinToString(" · ").ifBlank { null }
        }
        MaterialTheme(
            colorScheme = appColorScheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes
        ) {
            ThreadFormDialog(
                title = "返信",
                subtitle = subtitle,
                barColorScheme = appColorScheme,
                attachmentPickerPreference = attachmentPickerPreference,
                preferredFileManagerPackage = preferredFileManagerPackage,
                emailPresets = emailPresets,
                comment = replyComment,
                onCommentChange = { replyComment = it },
                name = replyName,
                onNameChange = { replyName = it },
                email = replyEmail,
                onEmailChange = { replyEmail = it },
                subject = replySubject,
                onSubjectChange = { replySubject = it },
                password = replyPassword,
                onPasswordChange = { replyPassword = it },
                selectedImage = replyImageData,
                onImageSelected = { replyImageData = it },
                onDismiss = { isReplyDialogVisible = false },
                onSubmit = {
                    val trimmedPassword = replyPassword.trim()
                    if (trimmedPassword.isBlank()) {
                        coroutineScope.launch { snackbarHostState.showSnackbar("削除キーを入力してください") }
                        return@ThreadFormDialog
                    }
                    if (replyComment.trim().isBlank()) {
                        coroutineScope.launch { snackbarHostState.showSnackbar("コメントを入力してください") }
                        return@ThreadFormDialog
                    }
                    isReplyDialogVisible = false
                    updateLastUsedDeleteKey(trimmedPassword)
                    val name = replyName
                    val email = replyEmail
                    val subject = replySubject
                    val comment = replyComment
                    val imageData = replyImageData
                    val textOnly = imageData == null
                    launchThreadAction(
                        successMessage = "返信を送信しました",
                        failurePrefix = "返信の送信に失敗しました",
                        onSuccess = { thisNo ->
                            if (!thisNo.isNullOrBlank()) {
                                stateStore?.let { store ->
                                    coroutineScope.launch {
                                        store.addSelfPostIdentifier(
                                            threadId = threadId,
                                            identifier = thisNo,
                                            boardId = board.id.ifBlank { null }
                                        )
                                    }
                                }
                            }
                            replySubject = ""
                            replyComment = ""
                            replyPassword = ""
                            replyImageData = null
                            refreshThread()
                        }
                    ) {
                        activeRepository.replyToThread(
                            effectiveBoardUrl,
                            threadId,
                            name,
                            email,
                            subject,
                            comment,
                            trimmedPassword,
                            imageData?.bytes,
                            imageData?.fileName,
                            textOnly
                        )
                    }
                },
                onClear = {
                    replyName = ""
                    replyEmail = ""
                    replySubject = ""
                    replyComment = ""
                    replyPassword = ""
                    replyImageData = null
                },
                isSubmitEnabled = replyComment.trim().isNotBlank() && replyPassword.trim().isNotBlank(),
                sendDescription = "返信",
                showSubject = true,
                showPassword = true
            )
        }
    }

    val totalMediaCount = mediaPreviewEntries.size
    val previewMediaEntry = previewMediaIndex?.let { mediaPreviewEntries.getOrNull(it) }
    val navigateToNextMedia: () -> Unit = next@{
        if (totalMediaCount == 0) return@next
        val currentIndex = previewMediaIndex ?: 0
        previewMediaIndex = (currentIndex + 1) % totalMediaCount
    }
    val navigateToPreviousMedia: () -> Unit = previous@{
        if (totalMediaCount == 0) return@previous
        val currentIndex = previewMediaIndex ?: 0
        previewMediaIndex = (currentIndex + totalMediaCount - 1) % totalMediaCount
    }
    val savePreviewMedia: (MediaPreviewEntry) -> Unit = savePreviewMedia@{ entry ->
        if (isManualSaveInProgress || isSingleMediaSaveInProgress) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("保存処理を実行中です…")
            }
            return@savePreviewMedia
        }

        if (!isRemoteMediaUrl(entry.url)) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("このメディアは保存に対応していません")
            }
            return@savePreviewMedia
        }

        if (
            isAndroidPlatform &&
            manualSaveLocation !is SaveLocation.TreeUri &&
            manualSaveLocation !is SaveLocation.Path
        ) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("保存先が未選択です。設定からフォルダを選択してください。")
                onOpenSaveDirectoryPicker?.invoke()
            }
            return@savePreviewMedia
        }

        val client = httpClient
        val localFileSystem = fileSystem
        if (client == null || localFileSystem == null) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("保存機能が利用できません")
            }
            return@savePreviewMedia
        }

        isSingleMediaSaveInProgress = true
        singleMediaSaveJob?.cancel()
        singleMediaSaveJob = coroutineScope.launch {
            try {
                val mediaSaveService = SingleMediaSaveService(
                    httpClient = client,
                    fileSystem = localFileSystem
                )
                val result = mediaSaveService.saveMedia(
                    mediaUrl = entry.url,
                    boardId = board.id,
                    threadId = threadId,
                    baseSaveLocation = manualSaveLocation,
                    baseDirectory = manualSaveDirectory
                )
                result.onSuccess { savedMedia ->
                    val mediaLabel = when (savedMedia.mediaType) {
                        SavedMediaType.VIDEO -> "動画"
                        SavedMediaType.IMAGE -> "画像"
                    }
                    snackbarHostState.showSnackbar("${mediaLabel}を保存しました: ${savedMedia.fileName}")
                }.onFailure { error ->
                    if (isSaveLocationPermissionIssue(error)) {
                        onManualSaveDirectoryChanged(DEFAULT_MANUAL_SAVE_ROOT)
                        onSaveDirectorySelectionChanged(SaveDirectorySelection.MANUAL_INPUT)
                        snackbarHostState.showSnackbar("保存先の権限が失われました。フォルダを再選択してください。")
                        onOpenSaveDirectoryPicker?.invoke()
                    } else {
                        snackbarHostState.showSnackbar("保存に失敗しました: ${error.message}")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isSaveLocationPermissionIssue(e)) {
                    onManualSaveDirectoryChanged(DEFAULT_MANUAL_SAVE_ROOT)
                    onSaveDirectorySelectionChanged(SaveDirectorySelection.MANUAL_INPUT)
                    snackbarHostState.showSnackbar("保存先の権限が失われました。フォルダを再選択してください。")
                    onOpenSaveDirectoryPicker?.invoke()
                } else {
                    snackbarHostState.showSnackbar("エラーが発生しました: ${e.message}")
                }
            } finally {
                isSingleMediaSaveInProgress = false
                if (singleMediaSaveJob === this.coroutineContext[Job]) {
                    singleMediaSaveJob = null
                }
            }
        }
    }
    previewMediaEntry?.let { entry ->
        val canSaveCurrentMedia = isRemoteMediaUrl(entry.url)
        when (entry.mediaType) {
            MediaType.Image -> ImagePreviewDialog(
                entry = entry,
                currentIndex = previewMediaIndex ?: 0,
                totalCount = totalMediaCount,
                onDismiss = { previewMediaIndex = null },
                onNavigateNext = navigateToNextMedia,
                onNavigatePrevious = navigateToPreviousMedia,
                onSave = { savePreviewMedia(entry) },
                isSaveEnabled = canSaveCurrentMedia && !isSingleMediaSaveInProgress,
                isSaveInProgress = isSingleMediaSaveInProgress
            )
            MediaType.Video -> VideoPreviewDialog(
                entry = entry,
                currentIndex = previewMediaIndex ?: 0,
                totalCount = totalMediaCount,
                onDismiss = { previewMediaIndex = null },
                onNavigateNext = navigateToNextMedia,
                onNavigatePrevious = navigateToPreviousMedia,
                onSave = { savePreviewMedia(entry) },
                isSaveEnabled = canSaveCurrentMedia && !isSingleMediaSaveInProgress,
                isSaveInProgress = isSingleMediaSaveInProgress
            )
        }
    }

    if (isGalleryVisible && currentSuccessState != null) {
        ThreadImageGallery(
            posts = currentSuccessState.page.posts,
            onDismiss = { isGalleryVisible = false },
            onImageClick = { post ->
                val index = currentSuccessState.page.posts.indexOfFirst { it.id == post.id }
                if (index >= 0) {
                    coroutineScope.launch {
                        lazyListState.animateScrollToItem(index)
                    }
                }
            }
        )
    }
    if (isThreadSettingsSheetVisible) {
        ThreadSettingsSheet(
            onDismiss = { isThreadSettingsSheetVisible = false },
            menuEntries = threadMenuEntries,
            onAction = { menuEntryId ->
                isThreadSettingsSheetVisible = false
                when (menuEntryId) {
                    ThreadMenuEntryId.NgManagement -> {
                        ngHeaderPrefill = null
                        isNgManagementVisible = true
                    }
                    ThreadMenuEntryId.ExternalApp -> {
                        // 外部アプリで開く
                        // board.urlからfutaba.phpを削除してからres/xxx.htmを追加
                        val baseUrl = effectiveBoardUrl.trimEnd('/').removeSuffix("/futaba.php")
                        val threadUrl = "$baseUrl/res/${threadId}.htm"
                        urlLauncher(threadUrl)
                    }
                    ThreadMenuEntryId.Privacy -> {
                        coroutineScope.launch {
                            stateStore?.setPrivacyFilterEnabled(!isPrivacyFilterEnabled)
                        }
                    }
                    ThreadMenuEntryId.ReadAloud -> {
                        isReadAloudControlsVisible = true
                    }
                    ThreadMenuEntryId.Settings -> {
                        isThreadSettingsSheetVisible = true
                    }
                    else -> handleMenuEntry(menuEntryId)
                }
            }
        )
    }

    if (isThreadFilterSheetVisible) {
            ThreadFilterSheet(
                selectedOptions = selectedThreadFilterOptions,
                activeSortOption = selectedThreadSortOption,
                keyword = threadFilterKeyword,
                onOptionToggle = handleThreadFilterToggle,
                onKeywordChange = { threadFilterKeyword = it },
                onClear = {
                    selectedThreadFilterOptions = emptySet()
                    selectedThreadSortOption = null
                    threadFilterKeyword = ""
                },
                onDismiss = {
                    isThreadFilterSheetVisible = false
                }
            )
        }

    if (isReadAloudControlsVisible) {
        ReadAloudControlSheet(
            segments = readAloudSegments,
            currentIndex = currentReadAloudIndex,
            visibleSegmentIndex = firstVisibleSegmentIndex,
            status = readAloudStatus,
            onSeek = { index ->
                seekReadAloudToIndex(index, true)
            },
            onSeekToVisible = {
                if (firstVisibleSegmentIndex >= 0) {
                    seekReadAloudToIndex(firstVisibleSegmentIndex, true)
                }
            },
            onPlay = {
                startReadAloud()
            },
            onPause = pauseReadAloud,
            onStop = {
                stopReadAloud()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("読み上げを停止しました")
                }
            },
            onDismiss = {
                isReadAloudControlsVisible = false
            }
        )
    }

    if (isNgManagementVisible) {
        NgManagementSheet(
            onDismiss = {
                isNgManagementVisible = false
                ngHeaderPrefill = null
            },
            ngHeaders = ngHeaders,
            ngWords = ngWords,
            ngFilteringEnabled = ngFilteringEnabled,
            onAddHeader = addNgHeaderEntry,
            onAddWord = addNgWordEntry,
            onRemoveHeader = removeNgHeaderEntry,
            onRemoveWord = removeNgWordEntry,
            onToggleFiltering = toggleNgFiltering,
            initialInput = ngHeaderPrefill
        )
    }

    // Privacy filter overlay - allows interactions to pass through
    if (isPrivacyFilterEnabled) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawRect(color = Color.White.copy(alpha = 0.5f))
        }
    }

    // 保存進捗ダイアログ
    SaveProgressDialog(
        progress = saveProgress,
        onDismissRequest = {
            saveProgress = null
        },
        onCancelRequest = {
            manualSaveJob?.cancel()
            coroutineScope.launch {
                snackbarHostState.showSnackbar("保存をキャンセルしました")
            }
        }
    )

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
            preferredFileManagerLabel = preferredFileManagerLabel,
            onFileManagerSelected = onFileManagerSelected,
            onClearPreferredFileManager = onClearPreferredFileManager,
            historyEntries = history,
            fileSystem = fileSystem,
            autoSavedThreadRepository = autoSaveRepository,
            threadMenuEntries = threadMenuEntries,
            onThreadMenuEntriesChanged = onThreadMenuEntriesChanged,
            catalogNavEntries = catalogNavEntries,
            onCatalogNavEntriesChanged = onCatalogNavEntriesChanged
        )
    }

    if (isCookieManagementVisible && cookieRepository != null) {
        CookieManagementScreen(
            onBack = { isCookieManagementVisible = false },
            repository = cookieRepository
        )
    }
}
}

// Thread UI components
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadTopBar(
    boardName: String,
    threadTitle: String,
    replyCount: Int?,
    statusLabel: String?,
    isSearchActive: Boolean,
    searchQuery: String,
    currentSearchIndex: Int,
    totalSearchMatches: Int,
    onSearchQueryChange: (String) -> Unit,
    onSearchPrev: () -> Unit,
    onSearchNext: () -> Unit,
    onSearchSubmit: () -> Unit,
    onSearchClose: () -> Unit,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onSearch: () -> Unit,
    onMenuSettings: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
        }
    }
    var isMenuExpanded by remember { mutableStateOf(false) }
    val displayIndex = if (totalSearchMatches <= 0) {
        0
    } else {
        (currentSearchIndex.coerceIn(0, totalSearchMatches - 1) + 1)
    }
    TopAppBar(
        navigationIcon = {
            if (!isSearchActive) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "戻る"
                    )
                }
            }
        },
        title = {
            if (isSearchActive) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = { Text("スレ内検索") },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .focusRequester(focusRequester),
                            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.onPrimary,
                                focusedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                unfocusedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                focusedPlaceholderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                focusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                unfocusedTextColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            trailingIcon = {
                                IconButton(onClick = onSearchClose) {
                                    Icon(Icons.Rounded.Close, contentDescription = "検索を閉じる")
                                }
                            }
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "$displayIndex / $totalSearchMatches",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = onSearchPrev, enabled = totalSearchMatches > 0) {
                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "前の検索結果")
                        }
                        IconButton(onClick = onSearchNext, enabled = totalSearchMatches > 0) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "次の検索結果")
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = threadTitle,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = boardName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                        )
                        if (statusLabel == null) {
                            replyCount?.let {
                                Text(
                                    text = "  /  ${it}レス",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    statusLabel?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        },
        actions = {
            if (!isSearchActive) {
                IconButton(onClick = onSearch) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "スレ内検索"
                    )
                }
                IconButton(onClick = onOpenHistory) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = "履歴を開く"
                    )
                }
                IconButton(onClick = { isMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "その他"
                    )
                }
                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("設定") },
                        onClick = {
                            isMenuExpanded = false
                            onMenuSettings()
                        }
                    )
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
private fun ThreadLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.CircularProgressIndicator()
    }
}

@Composable
private fun ThreadError(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(onClick = onRetry) {
                Text("再読み込み")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadContent(
    page: ThreadPage,
    listState: LazyListState,
    saidaneOverrides: Map<String, String>,
    selfPostIdentifiers: Set<String> = emptySet(),
    searchHighlightRanges: Map<String, List<IntRange>>,
    onPostLongPress: (Post) -> Unit,
    onQuoteRequestedForPost: (Post) -> Unit,
    onSaidaneClick: (Post) -> Unit,
    onMediaClick: ((String, MediaType) -> Unit)? = null,
    onUrlClick: (String) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val derivedPostData by produceState(
        initialValue = ThreadPostDerivedData(),
        key1 = page.posts
    ) {
        value = withContext(AppDispatchers.parsing) {
            ThreadPostDerivedData(
                posterIdLabels = buildPosterIdLabels(page.posts),
                postIndex = page.posts.associateBy { it.id },
                referencedByMap = buildReferencedPostsMap(page.posts),
                postsByPosterId = buildPostsByPosterId(page.posts)
            )
        }
    }
    val posterIdLabels = derivedPostData.posterIdLabels
    val postIndex = derivedPostData.postIndex
    val referencedByMap = derivedPostData.referencedByMap
    val postsByPosterId = derivedPostData.postsByPosterId
    var quotePreviewState by remember(page.posts) { mutableStateOf<QuotePreviewState?>(null) }
    val density = LocalDensity.current
    val maxOverscrollPx = remember(density) { with(density) { 64.dp.toPx() } }
    val refreshTriggerPx = remember(density) { with(density) { 56.dp.toPx() } }
    val edgeOffsetTolerancePx = remember(density) { with(density) { 24.dp.toPx() } }
    val isScrolling by remember {
        derivedStateOf { listState.isScrollInProgress }
    }
    val showQuotePreview: (String, List<Post>) -> Unit = preview@ { quoteText, targets ->
        if (isScrolling || targets.isEmpty()) return@preview
        quotePreviewState = QuotePreviewState(
            quoteText = quoteText,
            targetPosts = targets,
            posterIdLabels = posterIdLabels
        )
    }

    var overscrollTarget by remember { mutableFloatStateOf(0f) }
    val overscrollOffset by animateFloatAsState(
        targetValue = overscrollTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "threadOverscroll"
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
    val latestIsAtTop by rememberUpdatedState(isAtTop)
    val latestIsAtBottom by rememberUpdatedState(isAtBottom)

    // リフレッシュ完了時に空間を戻す
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            overscrollTarget = 0f
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, overscrollOffset.toInt()) }
                    .pointerInput(isRefreshing, refreshTriggerPx, maxOverscrollPx) {
                        var totalDrag = 0f
                        detectVerticalDragGestures(
                            onDragStart = {
                                totalDrag = 0f
                            },
                            onVerticalDrag = { change, dragAmount ->
                                if (isRefreshing) return@detectVerticalDragGestures
                                when {
                                    latestIsAtTop && dragAmount > 0f -> {
                                        totalDrag += dragAmount
                                        overscrollTarget = (totalDrag * 0.4f).coerceIn(0f, maxOverscrollPx)
                                        change.consume()
                                    }

                                    latestIsAtBottom && dragAmount < 0f -> {
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
                state = listState,
                verticalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = 24.dp
                )
            ) {
                page.deletedNotice?.takeIf { it.isNotBlank() }?.let { notice ->
                    item(key = "thread-notice") {
                        ThreadNoticeCard(message = notice)
                    }
                }
                itemsIndexed(
                    items = page.posts,
                    key = { _, post -> post.id }
                ) { index, post ->
                    val isSelfPost = selfPostIdentifiers.contains(post.id.trim())
                    val normalizedPosterId = normalizePosterIdValue(post.posterId)
                    ThreadPostCard(
                        post = post,
                        isOp = index == 0,
                        isSelfPost = isSelfPost,
                        posterIdLabel = posterIdLabels[post.id],
                        posterIdValue = normalizedPosterId,
                        saidaneLabelOverride = saidaneOverrides[post.id],
                        highlightRanges = searchHighlightRanges[post.id] ?: emptyList(),
                        onQuoteClick = { reference ->
                            val targets = reference.targetPostIds.mapNotNull { postIndex[it] }
                            showQuotePreview(reference.text, targets)
                        },
                        onUrlClick = onUrlClick,
                        onQuoteRequested = { onQuoteRequestedForPost(post) },
                        onPosterIdClick = normalizedPosterId
                            ?.let { normalizedId ->
                                postsByPosterId[normalizedId]
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.let { sameIdPosts ->
                                        {
                                            showQuotePreview("ID:$normalizedId のレス", sameIdPosts)
                                        }
                                    }
                            },
                        onReferencedByClick = referencedByMap[post.id]
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { referencingPosts ->
                                {
                                    showQuotePreview(">>${post.id} を引用したレス", referencingPosts)
                                }
                        },
                        onSaidaneClick = { onSaidaneClick(post) },
                        onMediaClick = onMediaClick,
                        onLongPress = {
                            if (quotePreviewState == null) {
                                onPostLongPress(post)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (index != page.posts.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                        )
                    }
                }
                page.expiresAtLabel?.takeIf { it.isNotBlank() }?.let { footerLabel ->
                    item(key = "thread-expires-label") {
                        Text(
                            text = footerLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 16.dp)
                        )
                    }
                }
            }

            ThreadScrollbar(
                listState = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(vertical = 12.dp, horizontal = 4.dp)
            )
        }
    }
    quotePreviewState?.let { state ->
        QuotePreviewDialog(
            state = state,
            onDismiss = { quotePreviewState = null },
            onMediaClick = onMediaClick,
            onUrlClick = onUrlClick,
            onQuoteClick = { reference ->
                val targets = reference.targetPostIds.mapNotNull { postIndex[it] }
                showQuotePreview(reference.text, targets)
            }
        )
    }
}

@Composable
private fun ThreadScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val layoutInfo = listState.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return
    val totalItems = layoutInfo.totalItemsCount
    if (totalItems <= 0) return
    val viewportHeightPx = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    if (viewportHeightPx <= 0) return
    val avgItemSizePx = visibleItems
        .map { it.size }
        .filter { it > 0 }
        .takeIf { it.isNotEmpty() }
        ?.average()
        ?.toFloat()
        ?: return
    val contentHeightPx = avgItemSizePx * totalItems
    if (contentHeightPx <= viewportHeightPx) return

    val firstVisibleSize = visibleItems.firstOrNull()?.size?.coerceAtLeast(1) ?: 1
    val partialIndex = listState.firstVisibleItemScrollOffset / firstVisibleSize.toFloat()
    val totalScrollableItems = (totalItems - visibleItems.size).coerceAtLeast(1)
    val scrollFraction = ((listState.firstVisibleItemIndex + partialIndex) / totalScrollableItems)
        .coerceIn(0f, 1f)
    val thumbHeightFraction = (viewportHeightPx / contentHeightPx).coerceIn(0.05f, 1f)

    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val thumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)

    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .width(6.dp)
    ) {
        val trackWidth = size.width
        val trackHeight = size.height
        val trackCornerRadius = CornerRadius(trackWidth / 2f, trackWidth / 2f)
        drawRoundRect(
            color = trackColor,
            size = Size(trackWidth, trackHeight),
            cornerRadius = trackCornerRadius
        )
        val thumbHeightPx = (trackHeight * thumbHeightFraction).coerceAtLeast(trackWidth)
        val thumbOffsetPx = (trackHeight - thumbHeightPx) * scrollFraction
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(x = 0f, y = thumbOffsetPx),
            size = Size(trackWidth, thumbHeightPx),
            cornerRadius = trackCornerRadius
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreadPostCard(
    post: Post,
    isOp: Boolean,
    isSelfPost: Boolean = false,
    posterIdLabel: PosterIdLabel?,
    posterIdValue: String?,
    saidaneLabelOverride: String?,
    highlightRanges: List<IntRange> = emptyList(),
    onQuoteClick: (QuoteReference) -> Unit,
    onUrlClick: (String) -> Unit,
    onQuoteRequested: (() -> Unit)? = null,
    onPosterIdClick: (() -> Unit)? = null,
    onReferencedByClick: (() -> Unit)? = null,
    onMediaClick: ((String, MediaType) -> Unit)? = null,
    onSaidaneClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val platformContext = LocalPlatformContext.current
    val backgroundColor = when {
        post.isDeleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.surface
    }
    val saidaneLabel = saidaneLabelOverride ?: post.saidaneLabel
    val cardModifier = if (onLongPress != null) {
        modifier.pointerInput(onLongPress) {
            detectTapGestures(onLongPress = { onLongPress() })
        }
    } else {
        modifier
    }

    Column(
        modifier = cardModifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ThreadPostMetadata(
            post = post,
            isOp = isOp,
            isSelfPost = isSelfPost,
            posterIdLabel = posterIdLabel,
            posterIdValue = posterIdValue,
            saidaneLabel = saidaneLabel,
            onUrlClick = onUrlClick,
            onQuoteRequested = onQuoteRequested,
            onSaidaneClick = onSaidaneClick,
            onPosterIdClick = onPosterIdClick,
            onReferencedByClick = onReferencedByClick
        )
        val thumbnailForDisplay = post.thumbnailUrl ?: post.imageUrl
        thumbnailForDisplay?.let { displayUrl ->
            val thumbnailRequest = remember(platformContext, displayUrl) {
                ImageRequest.Builder(platformContext)
                    .data(displayUrl)
                    .crossfade(true)
                    .build()
            }
                AsyncImage(
                    model = thumbnailRequest,
                    imageLoader = LocalFutachaImageLoader.current,
                    contentDescription = "添付画像",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(backgroundColor)
                    .clickable {
                        val targetUrl = post.imageUrl ?: displayUrl
                        onMediaClick?.invoke(targetUrl, determineMediaType(targetUrl))
                    },
                contentScale = ContentScale.Fit
            )
        }
        ThreadMessageText(
            messageHtml = post.messageHtml,
            isDeleted = post.isDeleted,
            quoteReferences = post.quoteReferences,
            onQuoteClick = onQuoteClick,
            onUrlClick = onUrlClick,
            highlightRanges = highlightRanges
        )
    }
}

@Composable
private fun ThreadPostMetadata(
    post: Post,
    isOp: Boolean,
    isSelfPost: Boolean = false,
    posterIdLabel: PosterIdLabel?,
    posterIdValue: String?,
    saidaneLabel: String?,
    onUrlClick: (String) -> Unit,
    onQuoteRequested: (() -> Unit)? = null,
    onSaidaneClick: (() -> Unit)? = null,
    onPosterIdClick: (() -> Unit)? = null,
    onReferencedByClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val subjectText = post.subject?.ifBlank { "無題" } ?: "無題"
        val authorText = post.author?.ifBlank { "名無し" } ?: "名無し"
        val subjectColor = when {
            subjectText.contains("無念") || subjectText.contains("株") -> MaterialTheme.colorScheme.tertiary
            isOp -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurface
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = (post.order ?: 0).toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = subjectText,
                style = MaterialTheme.typography.titleMedium,
                color = subjectColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = authorText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.weight(1f))
            if (post.referencedCount > 0) {
                ReplyCountLabel(
                    count = post.referencedCount,
                    onClick = onReferencedByClick
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val timestampText = remember(post.timestamp) {
                extractTimestampWithoutId(post.timestamp)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = timestampText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                posterIdLabel?.let { label ->
                    val idModifier = if (posterIdValue != null && onPosterIdClick != null) {
                        Modifier.clickable(onClick = onPosterIdClick)
                    } else {
                        Modifier
                    }
                    Text(
                        modifier = idModifier,
                        text = label.text,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (label.highlight) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (saidaneLabel != null && onSaidaneClick != null) {
                    val canSendSaidane = !isSelfPost
                    SaidaneLink(
                        label = saidaneLabel,
                        enabled = canSendSaidane,
                        onClick = onSaidaneClick
                    )
                }
            }
            Text(
                text = "No.${post.id}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val fileName = extractFileNameFromUrl(post.imageUrl ?: post.thumbnailUrl)
            val targetUrl = post.imageUrl ?: post.thumbnailUrl
            if (fileName != null && targetUrl != null) {
                var showFileMenu by remember { mutableStateOf(false) }
                Box {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = FutabaLinkColor,
                        textDecoration = TextDecoration.None,
                        modifier = Modifier.clickable { showFileMenu = true }
                    )
                    DropdownMenu(
                        expanded = showFileMenu,
                        onDismissRequest = { showFileMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("添付を開く") },
                            onClick = {
                                showFileMenu = false
                                onUrlClick(targetUrl)
                            }
                        )
                        onQuoteRequested?.let { quote ->
                            DropdownMenuItem(
                                text = { Text("引用") },
                                onClick = {
                                    showFileMenu = false
                                    quote()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadMessageText(
    messageHtml: String,
    isDeleted: Boolean,
    quoteReferences: List<QuoteReference>,
    onQuoteClick: (QuoteReference) -> Unit,
    onUrlClick: (String) -> Unit,
    highlightRanges: List<IntRange> = emptyList(),
    modifier: Modifier = Modifier
) {
    val highlightStyle = SpanStyle(
        background = MaterialTheme.colorScheme.secondary.copy(alpha = 0.32f)
    )
    val annotationCacheKey = remember(messageHtml, quoteReferences) {
        buildThreadMessageAnnotationCacheKey(messageHtml, quoteReferences)
    }
    val baseAnnotated: AnnotatedString by produceState(
        initialValue = threadMessageAnnotationBaseCache.get(annotationCacheKey) ?: AnnotatedString(""),
        key1 = annotationCacheKey,
        key2 = messageHtml,
        key3 = quoteReferences
    ) {
        threadMessageAnnotationBaseCache.get(annotationCacheKey)?.let {
            value = it
            return@produceState
        }
        if (messageHtml.isBlank()) {
            value = AnnotatedString("")
            return@produceState
        }
        value = withContext(AppDispatchers.parsing) {
            buildAnnotatedMessageBase(messageHtml, quoteReferences)
        }
        threadMessageAnnotationBaseCache.put(annotationCacheKey, value)
    }
    val annotated = remember(baseAnnotated, highlightRanges, highlightStyle) {
        applyHighlightsToAnnotatedMessage(baseAnnotated, highlightRanges, highlightStyle)
    }

    val textColor = if (isDeleted) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        modifier = modifier.pointerInput(annotated, quoteReferences, onUrlClick) {
            detectTapGestures(
                onLongPress = { position ->
                    val layout = textLayoutResult ?: return@detectTapGestures
                    val offset = layout.getOffsetForPosition(position)
                    val quoteIndex = annotated
                        .getStringAnnotations(QUOTE_ANNOTATION_TAG, offset, offset)
                        .firstOrNull()
                        ?.item
                        ?.toIntOrNull()
                    quoteIndex
                        ?.let { index -> quoteReferences.getOrNull(index) }
                        ?.takeIf { it.targetPostIds.isNotEmpty() }
                        ?.let(onQuoteClick)
                },
                onTap = { position ->
                    val layout = textLayoutResult ?: return@detectTapGestures
                    val offset = layout.getOffsetForPosition(position)
                    val url = annotated
                        .getStringAnnotations(URL_ANNOTATION_TAG, offset, offset)
                        .firstOrNull()
                        ?.item
                    if (url != null) {
                        onUrlClick(url)
                        return@detectTapGestures
                    }
                    val quoteIndex = annotated
                        .getStringAnnotations(QUOTE_ANNOTATION_TAG, offset, offset)
                        .firstOrNull()
                        ?.item
                        ?.toIntOrNull()
                    quoteIndex
                        ?.let { index -> quoteReferences.getOrNull(index) }
                        ?.takeIf { it.targetPostIds.isNotEmpty() }
                        ?.let(onQuoteClick)
                }
            )
        },
        text = annotated,
        style = MaterialTheme.typography.bodyMedium.copy(color = textColor),
        onTextLayout = { textLayoutResult = it }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadPostActionSheet(
    post: Post,
    onDismiss: () -> Unit,
    onQuote: () -> Unit,
    onNgRegister: () -> Unit,
    onSaidane: () -> Unit,
    isSaidaneEnabled: Boolean = true,
    onDelRequest: () -> Unit,
    onDelete: () -> Unit
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
                text = "No.${post.id} の操作",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            ListItem(
                leadingContent = {
                    Icon(Icons.Outlined.FormatQuote, contentDescription = null)
                },
                headlineContent = { Text("引用") },
                supportingContent = { Text("レス内容を返信欄にコピー") },
                modifier = Modifier.clickable { onQuote() }
            )
            ListItem(
                leadingContent = {
                    Icon(Icons.Rounded.Block, contentDescription = null)
                },
                headlineContent = { Text("NG登録") },
                supportingContent = { Text("IDやワードをNG管理に追加") },
                modifier = Modifier.clickable { onNgRegister() }
            )
            ListItem(
                leadingContent = {
                    Icon(Icons.Outlined.ThumbUp, contentDescription = null)
                },
                headlineContent = { Text("そうだね") },
                supportingContent = { Text("レスにそうだねを送信") },
                modifier = Modifier
                    .alpha(if (isSaidaneEnabled) 1f else 0.5f)
                    .clickable(
                        enabled = isSaidaneEnabled,
                        onClick = onSaidane
                    )
            )
            ListItem(
                leadingContent = {
                    Icon(Icons.Outlined.Flag, contentDescription = null)
                },
                headlineContent = { Text("DEL 依頼") },
                supportingContent = { Text("管理人へ削除依頼を送信") },
                modifier = Modifier.clickable { onDelRequest() }
            )
            ListItem(
                leadingContent = {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                },
                headlineContent = { Text("削除 (本人)") },
                supportingContent = { Text("削除キーでレスまたは画像を削除") },
                modifier = Modifier.clickable { onDelete() }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DeleteByUserDialog(
    post: Post,
    password: String,
    onPasswordChange: (String) -> Unit,
    imageOnly: Boolean,
    onImageOnlyChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("削除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        },
        title = { Text("No.${post.id} を削除") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("削除キー") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = imageOnly,
                        onCheckedChange = onImageOnlyChange
                    )
                    Text("画像だけ消す")
                }
            }
        }
    )
}

@Composable
private fun QuoteSelectionDialog(
    post: Post,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val selectionItems = remember(post.id) { buildQuoteSelectionItems(post) }
    var selectedIds by remember(post.id) {
        mutableStateOf(
            selectionItems
                .filter { it.isDefault }
                .map { it.id }
                .toSet()
                .ifEmpty { selectionItems.firstOrNull()?.let { setOf(it.id) } ?: emptySet() }
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val lines = selectionItems
                        .filter { selectedIds.contains(it.id) }
                        .map { it.content }
                    onConfirm(lines)
                },
                enabled = selectedIds.isNotEmpty(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("コピー")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("キャンセル")
            }
        },
        title = { Text("引用する内容を選択") },
        text = {
            if (selectionItems.isEmpty()) {
                Text(
                    text = "引用できる内容がありません",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    selectionItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    selectedIds = if (selectedIds.contains(item.id)) {
                                        selectedIds - item.id
                                    } else {
                                        selectedIds + item.id
                                    }
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedIds.contains(item.id),
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) {
                                        selectedIds + item.id
                                    } else {
                                        selectedIds - item.id
                                    }
                                }
                            )
                            Column(
                                modifier = Modifier.padding(start = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = item.preview,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadImageGallery(
    posts: List<Post>,
    onDismiss: () -> Unit,
    onImageClick: (Post) -> Unit
) {
    val imagesWithPosts = remember(posts) {
        posts.filter { it.imageUrl != null && it.thumbnailUrl != null }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "画像一覧 (${imagesWithPosts.size}枚)",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (imagesWithPosts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "画像がありません",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 600.dp)
                ) {
                    items(imagesWithPosts) { post ->
                        GalleryImageItem(
                            post = post,
                            onClick = {
                                onDismiss()
                                onImageClick(post)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun GalleryImageItem(
    post: Post,
    onClick: () -> Unit
) {
    val platformContext = LocalPlatformContext.current
    val thumbnailRequest = remember(platformContext, post.thumbnailUrl) {
        ImageRequest.Builder(platformContext)
            .data(post.thumbnailUrl)
            .crossfade(true)
            .build()
    }
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = MaterialTheme.shapes.medium
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = thumbnailRequest,
                imageLoader = LocalFutachaImageLoader.current,
                contentDescription = "No.${post.id}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text(
                    text = "No.${post.id}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

private const val QUOTE_ANNOTATION_TAG = "quote"
private const val URL_ANNOTATION_TAG = "url"
private const val THREAD_SCREEN_TAG = "ThreadScreen"

private sealed interface ArchiveFallbackOutcome {
    data class Success(val page: ThreadPage, val threadUrl: String?) : ArchiveFallbackOutcome
    data object NotFound : ArchiveFallbackOutcome
    data object NoMatch : ArchiveFallbackOutcome
}
private val THREAD_URL_ID_REGEX = Regex("""/res/(\d+)\.html?""", RegexOption.IGNORE_CASE)
private val URL_REGEX = Regex("""https?://[^\s\<\>"'()]+""", RegexOption.IGNORE_CASE)
private val SCHEMELESS_URL_REGEX = Regex("""ttps?://[^\s\<\>"'()]+""", RegexOption.IGNORE_CASE)
private val URL_LINK_TEXT_REGEX = Regex("""URL(?:ﾘﾝｸ|リンク)\(([^)]+)\)""", RegexOption.IGNORE_CASE)
private val BR_TAG_REGEX = Regex("(?i)<br\\s*/?>")
private val P_TAG_REGEX = Regex("(?i)</p>")
private val HTML_TAG_REGEX = Regex("<[^>]+>")
private val HEX_ENTITY_REGEX = Regex("&#x([0-9a-fA-F]+);")
private val NUM_ENTITY_REGEX = Regex("&#(\\d+);")

private const val THREAD_ACTION_LOG_TAG = "ThreadActions"
private const val DEFAULT_DEL_REASON_CODE = "110"
private const val AUTO_SAVE_INTERVAL_MS = 60_000L
private const val THREAD_AUTO_SAVE_TAG = "ThreadAutoSave"
private const val ARCHIVE_FALLBACK_TIMEOUT_MS = 20_000L
private const val OFFLINE_FALLBACK_TIMEOUT_MS = 10_000L
private const val THREAD_SEARCH_DEBOUNCE_MILLIS = 180L
private const val THREAD_FILTER_DEBOUNCE_MILLIS = 120L
private const val THREAD_FILTER_CACHE_MAX_ENTRIES = 8
private const val THREAD_MESSAGE_ANNOTATION_CACHE_MAX_ENTRIES = 512
private const val ACTION_BUSY_NOTICE_INTERVAL_MS = 1_000L

private val FutabaQuoteGreen = Color(0xFF789922)
private val FutabaLinkColor = Color(0xFF800000)

private val urlSpanStyle = SpanStyle(
    color = FutabaLinkColor,
    textDecoration = TextDecoration.Underline
)

private data class UrlMatch(
    val url: String,
    val range: IntRange
)

private class ThreadMessageAnnotationCache(
    private val maxEntries: Int
) {
    private val entries = LinkedHashMap<String, AnnotatedString>()

    fun get(key: String): AnnotatedString? {
        val value = entries.remove(key) ?: return null
        entries[key] = value
        return value
    }

    fun put(key: String, value: AnnotatedString) {
        entries.remove(key)
        entries[key] = value
        while (entries.size > maxEntries) {
            val iterator = entries.entries.iterator()
            if (!iterator.hasNext()) break
            iterator.next()
            iterator.remove()
        }
    }
}

private val threadMessageAnnotationBaseCache = ThreadMessageAnnotationCache(
    maxEntries = THREAD_MESSAGE_ANNOTATION_CACHE_MAX_ENTRIES
)

private fun buildThreadMessageAnnotationCacheKey(
    messageHtml: String,
    quoteReferences: List<QuoteReference>
): String {
    var hash = 17
    quoteReferences.forEach { reference ->
        hash = 31 * hash + reference.text.hashCode()
        reference.targetPostIds.forEach { targetId ->
            hash = 31 * hash + targetId.hashCode()
        }
    }
    return "${messageHtml.hashCode()}:${quoteReferences.size}:$hash"
}

private fun Throwable.statusCodeOrNull(): Int? {
    var current: Throwable? = this
    while (current != null) {
        when (current) {
            is NetworkException -> {
                current.statusCode?.let { return it }
            }
            is ResponseException -> return current.response.status.value
        }
        current = current.cause
    }
    return null
}

private fun isOfflineFallbackCandidate(error: Throwable): Boolean {
    if (error.statusCodeOrNull() != null) return true
    var current: Throwable? = error
    while (current != null) {
        if (current is NetworkException) return true
        val message = current.message?.lowercase().orEmpty()
        if (
            message.contains("timeout") ||
            message.contains("network") ||
            message.contains("http error") ||
            message.contains("connection") ||
            message.contains("unable to resolve") ||
            message.contains("dns") ||
            message.contains("socket")
        ) {
            return true
        }
        current = current.cause
    }
    return false
}

private fun SavedThreadMetadata.matchesBoardForOfflineFallback(expectedBoardKeys: Set<String>): Boolean {
    if (expectedBoardKeys.isEmpty()) return true
    val metadataBoardKey = normalizeBoardUrlForOfflineLookup(boardUrl) ?: return false
    return metadataBoardKey in expectedBoardKeys
}

private fun normalizeBoardUrlForOfflineLookup(value: String?): String? {
    val candidate = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val normalizedBase = runCatching {
        BoardUrlResolver.resolveBoardBaseUrl(candidate)
    }.getOrElse {
        candidate
    }
    return normalizedBase
        .substringBefore('?')
        .trimEnd('/')
        .lowercase()
}

private fun normalizeArchiveQuery(raw: String?, maxLength: Int): String {
    if (raw.isNullOrBlank() || maxLength <= 0) return ""
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val builder = StringBuilder(minOf(trimmed.length, maxLength))
    var previousWasWhitespace = false
    for (ch in trimmed) {
        val isWhitespace = ch.isWhitespace()
        if (isWhitespace) {
            if (!previousWasWhitespace && builder.isNotEmpty()) {
                builder.append(' ')
            }
        } else {
            builder.append(ch)
            if (builder.length >= maxLength) break
        }
        previousWasWhitespace = isWhitespace
    }
    return builder.toString().trim()
}

private fun buildAnnotatedMessageBase(
    html: String,
    quoteReferences: List<QuoteReference>
): AnnotatedString {
    val lines = messageHtmlToLines(html)
    var referenceIndex = 0
    val urlMatches = mutableListOf<UrlMatch>()
    val built = buildAnnotatedString {
        lines.forEachIndexed { index, line ->
            val content = line.trimEnd()
            val isQuote = content.startsWith(">") || content.startsWith("＞")
            if (isQuote) {
                val spanStyle = SpanStyle(color = FutabaQuoteGreen, fontWeight = FontWeight.SemiBold)
                val reference = quoteReferences.getOrNull(referenceIndex)
                if (reference != null && reference.targetPostIds.isNotEmpty()) {
                    pushStringAnnotation(QUOTE_ANNOTATION_TAG, referenceIndex.toString())
                    appendStyledText(content, spanStyle)
                    pop()
                    referenceIndex += 1
                } else {
                    appendStyledText(content, spanStyle)
                }
            } else {
                appendStyledText(content, SpanStyle())
            }
            if (index != lines.lastIndex) {
                append("\n")
            }
        }
    }
    val builtText = built.toString()
    urlMatches += URL_REGEX.findAll(builtText).map { match ->
        UrlMatch(url = match.value, range = match.range)
    }
    urlMatches += SCHEMELESS_URL_REGEX.findAll(builtText).map { match ->
        UrlMatch(url = "h${match.value}", range = match.range)
    }
    urlMatches += URL_LINK_TEXT_REGEX.findAll(builtText).mapNotNull { match ->
        val target = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val normalized = if (target.startsWith("http")) target else "https://$target"
        UrlMatch(url = normalized, range = match.range)
    }
    val distinct = urlMatches
        .sortedBy { it.range.first }
        .distinctBy { it.range.first to it.range.last }
    val builder = AnnotatedString.Builder(built)
    distinct.forEach { match ->
        builder.addStringAnnotation(
            tag = URL_ANNOTATION_TAG,
            annotation = match.url,
            start = match.range.first,
            end = match.range.last + 1
        )
        builder.addStyle(
            style = urlSpanStyle,
            start = match.range.first,
            end = match.range.last + 1
        )
    }
    return builder.toAnnotatedString()
}

private fun applyHighlightsToAnnotatedMessage(
    base: AnnotatedString,
    highlightRanges: List<IntRange>,
    highlightStyle: SpanStyle
): AnnotatedString {
    if (highlightRanges.isEmpty()) return base
    val normalizedHighlights = highlightRanges
        .filter { it.first >= 0 && it.last >= it.first }
        .sortedBy { it.first }
    if (normalizedHighlights.isEmpty()) return base
    if (base.text.isEmpty()) return base

    val builder = AnnotatedString.Builder(base)
    val textLastIndex = base.text.lastIndex
    normalizedHighlights.forEach { range ->
        val start = range.first.coerceIn(0, textLastIndex)
        val endExclusive = (range.last + 1).coerceIn(start + 1, base.text.length)
        builder.addStyle(highlightStyle, start, endExclusive)
    }
    return builder.toAnnotatedString()
}

private fun AnnotatedString.Builder.appendStyledText(
    text: String,
    style: SpanStyle
) {
    if (text.isEmpty()) return
    withStyle(style) {
        append(text)
    }
}

private fun messageHtmlToLines(html: String): List<String> {
    val normalized = html
        .replace(BR_TAG_REGEX, "\n")
        .replace(P_TAG_REGEX, "\n\n")
    val withoutTags = normalized.replace(HTML_TAG_REGEX, "")
    val decoded = decodeAllHtmlEntities(withoutTags)
    return decoded.lines()
}

private fun decodeAllHtmlEntities(value: String): String {
    var result = value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#039;", "'")
        .replace("&nbsp;", " ")

    // Decode hexadecimal entities like &#x1F43B; (🐻)
    result = HEX_ENTITY_REGEX.replace(result) { match ->
        val hexValue = match.groupValues.getOrNull(1) ?: return@replace match.value
        val codePoint = runCatching { hexValue.toInt(16) }.getOrNull()
        if (codePoint != null && codePoint in 0x20..0x10FFFF) {
            codePointToString(codePoint)
        } else {
            match.value
        }
    }

    // Decode numeric entities like &#128059; (🐻)
    result = NUM_ENTITY_REGEX.replace(result) { match ->
        val numValue = match.groupValues.getOrNull(1) ?: return@replace match.value
        val codePoint = runCatching { numValue.toInt() }.getOrNull()
        if (codePoint != null && codePoint in 0x20..0x10FFFF) {
            codePointToString(codePoint)
        } else {
            match.value
        }
    }

    return result
}

private fun codePointToString(codePoint: Int): String {
    return if (codePoint <= 0xFFFF) {
        codePoint.toChar().toString()
    } else {
        // Handle surrogate pairs for code points > 0xFFFF
        val high = ((codePoint - 0x10000) shr 10) + 0xD800
        val low = ((codePoint - 0x10000) and 0x3FF) + 0xDC00
        "${high.toChar()}${low.toChar()}"
    }
}

private fun messageHtmlToPlainText(html: String): String {
    return messageHtmlToLines(html)
        .map { it.trimEnd() }
        .joinToString("\n")
}

private fun buildQuoteSelectionItems(post: Post): List<QuoteSelectionItem> {
    val items = mutableListOf<QuoteSelectionItem>()
    items += QuoteSelectionItem(
        id = "number-${post.id}",
        title = "レスNo.",
        preview = "No.${post.id}",
        content = ">No.${post.id}",
        isDefault = true
    )
    extractFileNameFromUrl(post.imageUrl)?.let { fileName ->
        items += QuoteSelectionItem(
            id = "file-${post.id}",
            title = "ファイル名",
        preview = fileName,
        content = ">$fileName"
    )
    }
    val bodyLines = messageHtmlToLines(post.messageHtml)
        .map { it.trim() }
        .filter { it.isNotBlank() }
    bodyLines.forEachIndexed { index, line ->
        items += QuoteSelectionItem(
            id = "line-$index",
            title = "本文 ${index + 1}行目",
            preview = line,
            content = ">$line"
        )
    }
    return items
}

private fun extractFileNameFromUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val sanitized = url.substringBefore('#').substringBefore('?')
    val name = sanitized.substringAfterLast('/', "")
    return name.takeIf { it.isNotBlank() }
}

private fun extractTimestampWithoutId(timestamp: String): String {
    val idx = timestamp.indexOf("ID:")
    if (idx == -1) return timestamp.trim()
    return timestamp.substring(0, idx).trimEnd()
}

@Composable
private fun QuotePreviewDialog(
    state: QuotePreviewState,
    onDismiss: () -> Unit,
    onMediaClick: ((String, MediaType) -> Unit)? = null,
    onUrlClick: (String) -> Unit,
    onQuoteClick: (QuoteReference) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
            dismissOnBackPress = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        ) {
            val scrollState = rememberScrollState()
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    text = state.quoteText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    thickness = 1.dp
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(scrollState)
                ) {
                    state.targetPosts.forEachIndexed { index, post ->
                    ThreadPostCard(
                        post = post,
                        isOp = post.order == 0,
                        posterIdLabel = state.posterIdLabels[post.id],
                        posterIdValue = normalizePosterIdValue(post.posterId),
                        saidaneLabelOverride = null,
                        onQuoteClick = onQuoteClick,
                        onUrlClick = onUrlClick,
                        onSaidaneClick = null,
                        onLongPress = null,
                        onMediaClick = onMediaClick,
                        modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                        if (index != state.targetPosts.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                thickness = 1.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun applyNgFilters(
    page: ThreadPage,
    ngHeaders: List<String>,
    ngWords: List<String>,
    enabled: Boolean,
    precomputedLowerBodyByPostId: Map<String, String>? = null
): ThreadPage {
    if (!enabled) return page
    val headerFilters = ngHeaders.mapNotNull { it.trim().takeIf { trimmed -> trimmed.isNotBlank() }?.lowercase() }
    val wordFilters = ngWords.mapNotNull { it.trim().takeIf { trimmed -> trimmed.isNotBlank() }?.lowercase() }
    if (headerFilters.isEmpty() && wordFilters.isEmpty()) return page
    val lowerBodyByPostId = if (wordFilters.isEmpty()) {
        emptyMap()
    } else {
        precomputedLowerBodyByPostId ?: buildLowerBodyByPostId(page.posts)
    }
    val filteredPosts = page.posts.filterNot { post ->
        matchesNgFilters(post, headerFilters, wordFilters, lowerBodyByPostId)
    }
    return page.copy(posts = filteredPosts)
}

private fun applyThreadFilters(
    page: ThreadPage,
    criteria: ThreadFilterCriteria,
    precomputedLowerBodyByPostId: Map<String, String>? = null
): ThreadPage {
    if (criteria.options.isEmpty()) return page
    val normalizedSelfPostIdentifiers = criteria.selfPostIdentifiers
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()
    val needsLowerBodyByPostId = criteria.options.any {
        it == ThreadFilterOption.Url || it == ThreadFilterOption.Keyword
    }
    val lowerBodyByPostId = if (needsLowerBodyByPostId) {
        precomputedLowerBodyByPostId ?: buildLowerBodyByPostId(page.posts)
    } else {
        emptyMap()
    }
    val filteredPosts = page.posts.filter { post ->
        matchesThreadFilters(
            post = post,
            criteria = criteria,
            lowerBodyByPostId = lowerBodyByPostId,
            normalizedSelfPostIdentifiers = normalizedSelfPostIdentifiers
        )
    }
    val sortedPosts = sortThreadPosts(filteredPosts, criteria.sortOption)
    return page.copy(posts = sortedPosts)
}

private fun matchesThreadFilters(
    post: Post,
    criteria: ThreadFilterCriteria,
    lowerBodyByPostId: Map<String, String>,
    normalizedSelfPostIdentifiers: Set<String>
): Boolean {
    val filterOptions = criteria.options.filter { it.sortOption == null }
    if (filterOptions.isEmpty()) return true
    val lowerText = lowerBodyByPostId[post.id] ?: ""
    val headerText = buildPostHeaderText(post)
    return filterOptions.any { option ->
        when (option) {
            ThreadFilterOption.SelfPosts ->
                matchesSelfFilter(post, normalizedSelfPostIdentifiers)
            ThreadFilterOption.Deleted -> post.isDeleted
            ThreadFilterOption.Url -> THREAD_FILTER_URL_REGEX.containsMatchIn(lowerText)
            ThreadFilterOption.Image -> post.imageUrl?.isNotBlank() == true
            ThreadFilterOption.Keyword -> matchesKeyword(lowerText, post.subject ?: "", criteria.keyword)
            else -> true
        }
    }
}

private fun matchesSelfFilter(
    post: Post,
    normalizedStoredIdentifiers: Set<String>
): Boolean {
    if (normalizedStoredIdentifiers.isEmpty()) return false
    return post.id in normalizedStoredIdentifiers
}

private fun parseSaidaneCount(label: String?): Int? {
    val source = label ?: return null
    return Regex("""\d+""").find(source)?.value?.toIntOrNull()
}

private fun sortThreadPosts(
    posts: List<Post>,
    sortOption: ThreadFilterSortOption?
): List<Post> {
    return when (sortOption) {
        ThreadFilterSortOption.Saidane -> posts.sortedByDescending { parseSaidaneCount(it.saidaneLabel) ?: 0 }
        ThreadFilterSortOption.Replies -> posts.sortedByDescending { it.referencedCount }
        null -> posts
    }
}

private fun matchesKeyword(lowerText: String, subject: String, keywordInput: String): Boolean {
    val keywords = keywordInput
        .split(',')
        .mapNotNull { it.trim().takeIf { trimmed -> trimmed.isNotBlank() }?.lowercase() }
    if (keywords.isEmpty()) return false
    val lowerSubject = subject.lowercase()
    return keywords.any { keyword ->
        lowerText.contains(keyword) || lowerSubject.contains(keyword)
    }
}

private val THREAD_FILTER_URL_REGEX =
    Regex("""https?://[^\s"'<>]+|www\.[^\s"'<>]+""", RegexOption.IGNORE_CASE)

private fun matchesNgFilters(
    post: Post,
    headerFilters: List<String>,
    wordFilters: List<String>,
    lowerBodyByPostId: Map<String, String>
): Boolean {
    if (headerFilters.isNotEmpty()) {
        val headerText = buildPostHeaderText(post)
        if (headerFilters.any { headerText.contains(it) }) {
            return true
        }
    }
    if (wordFilters.isNotEmpty()) {
        val bodyText = lowerBodyByPostId[post.id] ?: ""
        if (wordFilters.any { bodyText.contains(it) }) {
            return true
        }
    }
    return false
}

private fun buildLowerBodyByPostId(posts: List<Post>): Map<String, String> {
    if (posts.isEmpty()) return emptyMap()
    return posts.associate { post ->
        post.id to messageHtmlToPlainText(post.messageHtml).lowercase()
    }
}

private fun buildPostHeaderText(post: Post): String {
    return listOfNotNull(
        post.subject,
        post.author,
        post.posterId,
        "No.${post.id}",
        post.timestamp
    ).joinToString(" ") { it.lowercase() }
}

private data class QuotePreviewState(
    val quoteText: String,
    val targetPosts: List<Post>,
    val posterIdLabels: Map<String, PosterIdLabel>
)

private data class ThreadPostDerivedData(
    val posterIdLabels: Map<String, PosterIdLabel> = emptyMap(),
    val postIndex: Map<String, Post> = emptyMap(),
    val referencedByMap: Map<String, List<Post>> = emptyMap(),
    val postsByPosterId: Map<String, List<Post>> = emptyMap()
)

private data class ThreadPostListFingerprint(
    val size: Int,
    val firstPostId: String?,
    val lastPostId: String?,
    val rollingHash: Long
)

private data class ThreadFilterCacheKey(
    val postsFingerprint: ThreadPostListFingerprint,
    val ngEnabled: Boolean,
    val ngHeadersFingerprint: Int,
    val ngWordsFingerprint: Int,
    val filterOptionsFingerprint: Int,
    val keyword: String,
    val selfIdentifiersFingerprint: Int,
    val sortOption: ThreadFilterSortOption?
)

private data class QuoteSelectionItem(
    val id: String,
    val title: String,
    val preview: String,
    val content: String,
    val isDefault: Boolean = false
)

private data class ThreadSearchMatch(
    val postId: String,
    val postIndex: Int,
    val highlightRanges: List<IntRange>
)

private data class ThreadSearchTarget(
    val postId: String,
    val postIndex: Int,
    val searchableText: String,
    val messagePlainText: String
)

private fun buildLightweightThreadPostListFingerprint(posts: List<Post>): ThreadPostListFingerprint {
    return ThreadPostListFingerprint(
        size = posts.size,
        firstPostId = posts.firstOrNull()?.id,
        lastPostId = posts.lastOrNull()?.id,
        rollingHash = 0L
    )
}

private fun buildThreadPostListFingerprint(posts: List<Post>): ThreadPostListFingerprint {
    if (posts.isEmpty()) {
        return buildLightweightThreadPostListFingerprint(posts)
    }
    var rollingHash = 1_469_598_103_934_665_603L
    posts.forEach { post ->
        val idHash = post.id.hashCode().toLong()
        val posterHash = post.posterId?.hashCode()?.toLong() ?: 0L
        val quotedCount = post.quoteReferences.size.toLong()
        val referencedCount = post.referencedCount.toLong()
        val deletedFlag = if (post.isDeleted) 1L else 0L
        rollingHash = (rollingHash * 1_099_511_628_211L) xor idHash
        rollingHash = (rollingHash * 1_099_511_628_211L) xor posterHash
        rollingHash = (rollingHash * 1_099_511_628_211L) xor quotedCount
        rollingHash = (rollingHash * 1_099_511_628_211L) xor referencedCount
        rollingHash = (rollingHash * 1_099_511_628_211L) xor deletedFlag
    }
    return ThreadPostListFingerprint(
        size = posts.size,
        firstPostId = posts.first().id,
        lastPostId = posts.last().id,
        rollingHash = rollingHash
    )
}

private fun findFirstVisibleReadAloudSegmentIndex(
    segments: List<ReadAloudSegment>,
    firstVisibleItemIndex: Int
): Int {
    if (segments.isEmpty()) return -1
    var left = 0
    var right = segments.lastIndex
    var result = -1
    while (left <= right) {
        val mid = left + (right - left) / 2
        val postIndex = segments[mid].postIndex
        if (postIndex >= firstVisibleItemIndex) {
            result = mid
            right = mid - 1
        } else {
            left = mid + 1
        }
    }
    return result
}

private fun stableNormalizedListFingerprint(values: List<String>): Int {
    var hash = 1
    values.forEach { raw ->
        val normalized = raw.trim().lowercase()
        if (normalized.isNotEmpty()) {
            hash = 31 * hash + normalized.hashCode()
        }
    }
    return hash
}

private fun stableThreadFilterOptionSetFingerprint(options: Set<ThreadFilterOption>): Int {
    if (options.isEmpty()) return 0
    var hash = 1
    options
        .map { it.name }
        .sorted()
        .forEach { name ->
            hash = 31 * hash + name.hashCode()
        }
    return hash
}

private fun buildPosterIdLabels(posts: List<Post>): Map<String, PosterIdLabel> {
    if (posts.isEmpty()) return emptyMap()
    val totals = mutableMapOf<String, Int>()
    posts.forEach { post ->
        normalizePosterIdValue(post.posterId)?.let { normalized ->
            totals[normalized] = (totals[normalized] ?: 0) + 1
        }
    }
    if (totals.isEmpty()) return emptyMap()
    val runningCounts = mutableMapOf<String, Int>()
    val labels = mutableMapOf<String, PosterIdLabel>()
    posts.forEach { post ->
        val normalized = normalizePosterIdValue(post.posterId) ?: return@forEach
        val nextIndex = (runningCounts[normalized] ?: 0) + 1
        runningCounts[normalized] = nextIndex
        val total = totals.getValue(normalized)
        labels[post.id] = PosterIdLabel(
            text = formatPosterIdLabel(normalized, nextIndex, total),
            highlight = total > 1 && nextIndex > 1
        )
    }
    return labels
}

private fun buildPostsByPosterId(posts: List<Post>): Map<String, List<Post>> {
    if (posts.isEmpty()) return emptyMap()
    val groups = mutableMapOf<String, MutableList<Post>>()
    posts.forEach { post ->
        val normalized = normalizePosterIdValue(post.posterId) ?: return@forEach
        groups.getOrPut(normalized) { mutableListOf() }.add(post)
    }
    if (groups.isEmpty()) return emptyMap()
    return groups.mapValues { (_, value) -> value.toList() }
}

private fun buildReferencedPostsMap(posts: List<Post>): Map<String, List<Post>> {
    if (posts.isEmpty()) return emptyMap()
    val orderIndex = posts.mapIndexed { index, post -> post.id to index }.toMap()
    val referencedBy = mutableMapOf<String, MutableList<Post>>()
    posts.forEach { source ->
        source.quoteReferences.forEach { reference ->
            reference.targetPostIds.forEach { targetId ->
                val bucket = referencedBy.getOrPut(targetId) { mutableListOf() }
                if (bucket.none { it.id == source.id }) {
                    bucket.add(source)
                }
            }
        }
    }
    if (referencedBy.isEmpty()) return emptyMap()
    return referencedBy.mapValues { (_, value) ->
        value
            .distinctBy { it.id }
            .sortedBy { orderIndex[it.id] ?: Int.MAX_VALUE }
    }
}

private fun buildThreadSearchTargets(posts: List<Post>): List<ThreadSearchTarget> {
    if (posts.isEmpty()) return emptyList()
    return posts.mapIndexed { index, post ->
        val messagePlainText = messageHtmlToPlainText(post.messageHtml)
        ThreadSearchTarget(
            postId = post.id,
            postIndex = index,
            searchableText = buildSearchTextForPost(post, messagePlainText),
            messagePlainText = messagePlainText
        )
    }
}

private fun buildThreadSearchMatches(
    searchTargets: List<ThreadSearchTarget>,
    query: String
): List<ThreadSearchMatch> {
    if (searchTargets.isEmpty()) return emptyList()
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isEmpty()) return emptyList()
    return searchTargets.mapNotNull { target ->
        val haystack = target.searchableText
        if (haystack.contains(normalizedQuery)) {
            val ranges = computeHighlightRanges(target.messagePlainText, normalizedQuery)
            ThreadSearchMatch(target.postId, target.postIndex, ranges)
        } else {
            null
        }
    }
}

private fun buildSearchTextForPost(post: Post, messagePlainText: String): String {
    val builder = StringBuilder()
    post.subject?.takeIf { it.isNotBlank() }?.let {
        builder.appendLine(it)
    }
    post.author?.takeIf { it.isNotBlank() }?.let {
        builder.appendLine(it)
    }
    post.posterId?.takeIf { it.isNotBlank() }?.let {
        builder.appendLine(it)
    }
    builder.appendLine(post.id)
    builder.append(messagePlainText)
    return builder.toString().lowercase()
}

private fun computeHighlightRanges(text: String, normalizedQuery: String): List<IntRange> {
    if (normalizedQuery.isEmpty()) return emptyList()
    val normalizedText = text.lowercase()
    val ranges = mutableListOf<IntRange>()
    var startIndex = normalizedText.indexOf(normalizedQuery)
    while (startIndex >= 0) {
        val endIndex = startIndex + normalizedQuery.length - 1
        ranges.add(startIndex..endIndex)
        startIndex = normalizedText.indexOf(normalizedQuery, startIndex + normalizedQuery.length)
    }
    return ranges
}

@Composable
private fun ImagePreviewDialog(
    entry: MediaPreviewEntry,
    currentIndex: Int,
    totalCount: Int,
    onDismiss: () -> Unit,
    onNavigateNext: () -> Unit,
    onNavigatePrevious: () -> Unit,
    onSave: (() -> Unit)? = null,
    isSaveEnabled: Boolean = true,
    isSaveInProgress: Boolean = false
) {
    val platformContext = LocalPlatformContext.current
    val imageLoader = LocalFutachaImageLoader.current
    val urlLauncher = rememberUrlLauncher()
    var scale by remember { mutableStateOf(1f) }
    var translation by remember { mutableStateOf(Offset.Zero) }
    var swipeDistance by remember { mutableStateOf(0f) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(entry.url) {
        scale = 1f
        translation = Offset.Zero
        swipeDistance = 0f
    }
    val previewRequest = remember(entry.url) {
        ImageRequest.Builder(platformContext)
            .data(entry.url)
            .crossfade(true)
            .build()
    }
    val painter = rememberAsyncImagePainter(
        model = previewRequest,
        imageLoader = imageLoader
    )
    val painterState by painter.state.collectAsState()
    val targetContentScale by remember(previewSize, painterState) {
        derivedStateOf {
            val imageSize = painter.intrinsicSize
            val containerWidth = previewSize.width.toFloat()
            val containerHeight = previewSize.height.toFloat()
            if (
                imageSize.width > 0f &&
                imageSize.height > 0f &&
                containerWidth > 0f &&
                containerHeight > 0f
            ) {
                val imageAspect = imageSize.width / imageSize.height
                val containerAspect = containerWidth / containerHeight
                if (imageAspect < containerAspect) {
                    ContentScale.FillHeight
                } else {
                    ContentScale.FillWidth
                }
            } else {
                ContentScale.Fit
            }
        }
    }
    val isLoadingState = painterState is AsyncImagePainter.State.Loading
    val isErrorState = painterState is AsyncImagePainter.State.Error

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { previewSize = it }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            swipeDistance += dragAmount
                            if (swipeDistance < -200f) {
                                swipeDistance = 0f
                                onDismiss()
                            }
                        },
                        onDragEnd = { swipeDistance = 0f },
                        onDragCancel = { swipeDistance = 0f }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan: Offset, zoom: Float, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        translation += pan
                    }
                }
                .pointerInput(entry.url, previewSize.width) {
                    detectTapGestures { offset ->
                        val width = previewSize.width.toFloat()
                        if (width <= 0f) return@detectTapGestures
                        if (offset.x < width / 2f) {
                            onNavigatePrevious()
                        } else {
                            onNavigateNext()
                        }
                    }
                }
        ) {
            Image(
                painter = painter,
                contentDescription = "プレビュー画像",
                contentScale = targetContentScale,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = translation.x
                        translationY = translation.y
                        alpha = if (isErrorState) 0f else 1f
                    }
            )
            if (isLoadingState) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (isErrorState) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "画像を読み込めませんでした",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    TextButton(onClick = { urlLauncher(entry.url) }) {
                        Text("ブラウザで開く")
                    }
                }
            }
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.small,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 32.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = entry.title,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${currentIndex + 1}/${totalCount}",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onSave != null) {
                    FilledTonalButton(
                        onClick = onSave,
                        enabled = isSaveEnabled && !isSaveInProgress,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.5f),
                            contentColor = Color.White,
                            disabledContainerColor = Color.Black.copy(alpha = 0.35f),
                            disabledContentColor = Color.White.copy(alpha = 0.55f)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(if (isSaveInProgress) "保存中..." else "保存")
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "プレビューを閉じる"
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoPreviewDialog(
    entry: MediaPreviewEntry,
    currentIndex: Int,
    totalCount: Int,
    onDismiss: () -> Unit,
    onNavigateNext: () -> Unit,
    onNavigatePrevious: () -> Unit,
    onSave: (() -> Unit)? = null,
    isSaveEnabled: Boolean = true,
    isSaveInProgress: Boolean = false
) {
    var swipeDistance by remember { mutableStateOf(0f) }
    var playbackState by remember { mutableStateOf(VideoPlayerState.Buffering) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var isMuted by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(0.9f) }
    var videoSize by remember { mutableStateOf<IntSize?>(null) }
    val urlLauncher = rememberUrlLauncher()
    val videoContentModifier by remember(previewSize, videoSize) {
        derivedStateOf {
            val containerWidth = previewSize.width.toFloat()
            val containerHeight = previewSize.height.toFloat()
            val size = videoSize
            if (containerWidth <= 0f || containerHeight <= 0f || size == null || size.width <= 0 || size.height <= 0) {
                Modifier.fillMaxSize()
            } else {
                val videoAspect = size.width.toFloat() / size.height.toFloat()
                val containerAspect = containerWidth / containerHeight
                if (videoAspect < containerAspect) {
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(videoAspect, matchHeightConstraintsFirst = true)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(videoAspect)
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { previewSize = it }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            swipeDistance += dragAmount
                            if (swipeDistance < -200f) {
                                swipeDistance = 0f
                                onDismiss()
                            }
                        },
                        onDragEnd = { swipeDistance = 0f },
                        onDragCancel = { swipeDistance = 0f }
                    )
                }
                .pointerInput(entry.url, previewSize.width) {
                    detectTapGestures { offset ->
                        val width = previewSize.width.toFloat()
                        if (width <= 0f) return@detectTapGestures
                        if (offset.x < width / 2f) {
                            onNavigatePrevious()
                        } else {
                            onNavigateNext()
                        }
                    }
                }
        ) {
            PlatformVideoPlayer(
                videoUrl = entry.url,
                modifier = videoContentModifier.align(Alignment.Center),
                onStateChanged = { playbackState = it },
                onVideoSizeKnown = { width, height ->
                    videoSize = if (width > 0 && height > 0) IntSize(width, height) else null
                },
                volume = volume,
                isMuted = isMuted
            )
            val isBuffering = playbackState == VideoPlayerState.Buffering || playbackState == VideoPlayerState.Idle
            if (isBuffering) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (playbackState == VideoPlayerState.Error) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "動画を再生できませんでした",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    TextButton(onClick = { urlLauncher(entry.url) }) {
                        Text("ブラウザで開く")
                    }
                }
            }
            val showCloseButton = playbackState != VideoPlayerState.Ready
            if (showCloseButton) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(40.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "プレビューを閉じる"
                    )
                }
            }
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { isMuted = !isMuted },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = if (isMuted || volume <= 0f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                                    contentDescription = if (isMuted) "ミュート解除" else "ミュート",
                                    tint = Color.White
                                )
                            }
                            Text(
                                text = if (isMuted) "ミュート中" else "音量 ${(volume * 100).roundToInt()}%",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        TextButton(onClick = { volume = 1f; isMuted = false }) {
                            Text(
                                text = "リセット",
                                color = Color.White
                            )
                        }
                    }
                    Slider(
                        value = volume,
                        onValueChange = {
                            volume = it
                            if (isMuted && it > 0f) {
                                isMuted = false
                            }
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        )
                    )
                    if (onSave != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = onSave,
                                enabled = isSaveEnabled && !isSaveInProgress,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = Color.White,
                                    disabledContentColor = Color.White.copy(alpha = 0.5f)
                                )
                            ) {
                                Text(if (isSaveInProgress) "保存中..." else "この動画を保存")
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class MediaType {
    Image,
    Video
}

private fun isRemoteMediaUrl(url: String): Boolean {
    val normalized = url.trim()
    return normalized.startsWith("https://", ignoreCase = true) ||
        normalized.startsWith("http://", ignoreCase = true)
}

private fun determineMediaType(url: String): MediaType {
    val cleaned = url.substringBefore('?')
    val extension = cleaned.substringAfterLast('.', "").lowercase()
    return if (extension in setOf("mp4", "webm", "mkv", "mov", "avi", "ts", "flv")) {
        MediaType.Video
    } else {
        MediaType.Image
    }
}

private data class MediaPreviewEntry(
    val url: String,
    val mediaType: MediaType,
    val postId: String,
    val title: String
)

private fun buildMediaPreviewEntries(posts: List<Post>): List<MediaPreviewEntry> {
    return posts.mapNotNull { post ->
        val targetUrl = post.imageUrl?.takeIf { it.isNotBlank() }
            ?: post.thumbnailUrl?.takeIf { it.isNotBlank() }
        if (targetUrl.isNullOrBlank()) return@mapNotNull null
        MediaPreviewEntry(
            url = targetUrl,
            mediaType = determineMediaType(targetUrl),
            postId = post.id,
            title = extractPreviewTitle(post)
        )
    }
}

private fun extractPreviewTitle(post: Post): String {
    val firstLine = messageHtmlToLines(post.messageHtml).firstOrNull()?.trim()
    if (!firstLine.isNullOrBlank()) return firstLine
    val subject = post.subject?.trim()
    if (!subject.isNullOrBlank()) return subject
    return "No.${post.id}"
}

private fun normalizePosterIdValue(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    val withoutPrefix = if (trimmed.startsWith("ID:", ignoreCase = true)) {
        trimmed.substring(3)
    } else {
        trimmed
    }
    return withoutPrefix.trim().takeIf { it.isNotBlank() }
}

private fun buildNgHeaderPrefillValue(post: Post): String? {
    val normalized = normalizePosterIdValue(post.posterId) ?: return null
    val withoutSlip = normalized.substringBefore('/')
    return withoutSlip.takeIf { it.isNotBlank() }
}

private fun formatPosterIdLabel(value: String, index: Int, total: Int): String {
    val safeIndex = index.coerceAtLeast(1)
    val safeTotal = total.coerceAtLeast(safeIndex)
    return "ID:$value(${safeIndex}/${safeTotal})"
}

private data class PosterIdLabel(
    val text: String,
    val highlight: Boolean
)

@Composable
private fun ThreadNoticeCard(message: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun ReplyCountLabel(
    count: Int,
    onClick: (() -> Unit)? = null
) {
    val labelModifier = onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier
    Text(
        modifier = labelModifier,
        text = "${count}レス",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.tertiary
    )
}

@Composable
private fun SaidaneLink(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val normalized = if (label == "+") "そうだね" else label
    Text(
        text = normalized,
        style = MaterialTheme.typography.labelMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        ),
        textDecoration = TextDecoration.None,
        modifier = Modifier.clickable(
            enabled = enabled,
            onClick = onClick
        )
    )
}

@Composable
private fun ThreadActionBar(
    menuEntries: List<ThreadMenuEntryConfig>,
    onAction: (ThreadMenuEntryId) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleActions = remember(menuEntries) {
        resolveThreadActionBarEntries(menuEntries)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = NavigationBarDefaults.containerColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            visibleActions.forEach { action ->
                val meta = action.id.toMeta()
                IconButton(onClick = { onAction(action.id) }) {
                    Icon(
                        imageVector = meta.icon,
                        contentDescription = meta.label,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun resolveThreadActionBarEntries(menuEntries: List<ThreadMenuEntryConfig>): List<ThreadMenuEntryConfig> {
    val normalized = normalizeThreadMenuEntries(menuEntries)
    return normalized
        .filter { it.placement == ThreadMenuEntryPlacement.BAR }
        .sortedWith(compareBy<ThreadMenuEntryConfig> { it.order }.thenBy { it.id.defaultOrder })
}

private fun resolveThreadSettingsMenuEntries(menuEntries: List<ThreadMenuEntryConfig>): List<ThreadMenuEntryConfig> {
    return normalizeThreadMenuEntries(menuEntries)
        .filter { it.placement == ThreadMenuEntryPlacement.SHEET }
        .sortedWith(compareBy<ThreadMenuEntryConfig> { it.order }.thenBy { it.id.defaultOrder })
}

internal data class ThreadMenuEntryMeta(
    val label: String,
    val icon: ImageVector
)

internal fun ThreadMenuEntryId.toMeta(): ThreadMenuEntryMeta {
    return when (this) {
        ThreadMenuEntryId.Reply -> ThreadMenuEntryMeta("返信", Icons.Rounded.Edit)
        ThreadMenuEntryId.ScrollToTop -> ThreadMenuEntryMeta("最上部", Icons.Filled.ArrowUpward)
        ThreadMenuEntryId.ScrollToBottom -> ThreadMenuEntryMeta("最下部", Icons.Filled.ArrowDownward)
        ThreadMenuEntryId.Refresh -> ThreadMenuEntryMeta("更新", Icons.Rounded.Refresh)
        ThreadMenuEntryId.Gallery -> ThreadMenuEntryMeta("画像", Icons.Outlined.Image)
        ThreadMenuEntryId.Save -> ThreadMenuEntryMeta("保存", Icons.Rounded.Archive)
        ThreadMenuEntryId.Filter -> ThreadMenuEntryMeta("レスフィルター", Icons.Rounded.FilterList)
        ThreadMenuEntryId.Settings -> ThreadMenuEntryMeta("設定", Icons.Rounded.Settings)
        ThreadMenuEntryId.NgManagement -> ThreadMenuEntryMeta("NG管理", Icons.Rounded.Block)
        ThreadMenuEntryId.ExternalApp -> ThreadMenuEntryMeta("外部アプリ", Icons.AutoMirrored.Rounded.OpenInNew)
        ThreadMenuEntryId.ReadAloud -> ThreadMenuEntryMeta("読み上げ", Icons.AutoMirrored.Rounded.VolumeUp)
        ThreadMenuEntryId.Privacy -> ThreadMenuEntryMeta("プライバシー", Icons.Rounded.Lock)
    }
}

internal fun ThreadMenuEntryConfig.toMeta(): ThreadMenuEntryMeta = id.toMeta()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadSettingsSheet(
    onDismiss: () -> Unit,
    menuEntries: List<ThreadMenuEntryConfig>,
    onAction: (ThreadMenuEntryId) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val visibleItems = remember(menuEntries) {
        resolveThreadSettingsMenuEntries(menuEntries)
    }
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
            visibleItems.forEach { menuItem ->
                val meta = menuItem.id.toMeta()
                ListItem(
                    leadingContent = { Icon(imageVector = meta.icon, contentDescription = null) },
                    headlineContent = { Text(meta.label) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onAction(menuItem.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadFilterSheet(
    selectedOptions: Set<ThreadFilterOption>,
    activeSortOption: ThreadFilterSortOption?,
    keyword: String,
    onOptionToggle: (ThreadFilterOption) -> Unit,
    onKeywordChange: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "レスフィルター",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "絞り込みたい条件をタップしてオン／オフしてください",
                style = MaterialTheme.typography.bodySmall
            )
            HorizontalDivider()
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ThreadFilterOption.entries.forEachIndexed { index, option ->
                    val selected = option in selectedOptions
                    val isActiveSort = option.sortOption != null && activeSortOption == option.sortOption
                    ListItem(
                        leadingContent = {
                            Icon(imageVector = option.icon, contentDescription = null)
                        },
                        headlineContent = {
                            Text(option.label)
                        },
                        supportingContent = {
                            if (isActiveSort) {
                                Text(
                                    text = "表示: ${option.sortOption.displayLabel}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        trailingContent = {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "選択済み",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                if (isActiveSort) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                else Color.Transparent
                            )
                            .clickable {
                                onOptionToggle(option)
                            }
                    )
                    if (index < ThreadFilterOption.entries.lastIndex) {
                        HorizontalDivider()
                    }
                    if (option == ThreadFilterOption.Keyword && selected) {
                        OutlinedTextField(
                            value = keyword,
                            onValueChange = onKeywordChange,
                            label = { Text("キーワード") },
                            placeholder = { Text("表示したいキーワードをカンマ区切りで") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onClear) {
                    Text("フィルターをクリア")
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("閉じる")
                }
            }
        }
    }
}

@Composable
private fun SectionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(label)
    }
}

private data class ReadAloudSegment(
    val postIndex: Int,
    val postId: String,
    val body: String
)

private sealed interface ReadAloudStatus {
    object Idle : ReadAloudStatus
    data class Speaking(val segment: ReadAloudSegment) : ReadAloudStatus
    data class Paused(val segment: ReadAloudSegment) : ReadAloudStatus
}

@Composable
private fun ReadAloudIndicator(
    segment: ReadAloudSegment,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .wrapContentHeight(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
    ) {
        Text(
            text = "読み上げ中: No.${segment.postId}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

private val READ_ALOUD_SKIPPED_PHRASES = listOf(
    "スレッドを立てた人によって削除されました",
    "書き込みをした人によって削除されました",
    "管理者によって削除されました"
)
private val READ_ALOUD_URL_REGEX = Regex("(?i)\\b(?:https?|ftp)://\\S+|\\bttps?://\\S+|\\bttp://\\S+")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadAloudControlSheet(
    segments: List<ReadAloudSegment>,
    currentIndex: Int,
    visibleSegmentIndex: Int,
    status: ReadAloudStatus,
    onSeek: (Int) -> Unit,
    onSeekToVisible: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val totalSegments = segments.size
    val completedSegments = currentIndex.coerceIn(0, totalSegments)
    val currentSegment = when {
        status is ReadAloudStatus.Speaking -> status.segment
        status is ReadAloudStatus.Paused -> status.segment
        segments.isNotEmpty() -> segments.getOrNull(currentIndex.coerceIn(0, segments.lastIndex))
        else -> null
    }
    val canSeek = segments.isNotEmpty()
    var sliderValue by remember { mutableFloatStateOf(currentIndex.toFloat()) }
    LaunchedEffect(currentIndex, segments.size) {
        sliderValue = currentIndex.coerceIn(0, (segments.lastIndex).coerceAtLeast(0)).toFloat()
    }
    val isPlaying = status is ReadAloudStatus.Speaking
    val isPaused = status is ReadAloudStatus.Paused
    val playLabel = if (isPaused) "再開" else "再生"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "読み上げプレーヤー",
                style = MaterialTheme.typography.titleMedium
            )
            if (totalSegments > 0) {
                Text(
                    text = "進捗 ${completedSegments} / $totalSegments",
                    style = MaterialTheme.typography.bodyMedium
                )
                LinearProgressIndicator(
                    progress = { completedSegments / totalSegments.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
                if (canSeek) {
                    val maxIndex = (totalSegments - 1).coerceAtLeast(0)
                    Slider(
                        value = sliderValue.coerceIn(0f, maxIndex.toFloat()),
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = {
                            onSeek(sliderValue.roundToInt())
                        },
                        valueRange = 0f..maxIndex.toFloat(),
                        steps = (maxIndex - 1).coerceAtLeast(0),
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "No.${segments.getOrNull(sliderValue.roundToInt())?.postId ?: "-"} から",
                            style = MaterialTheme.typography.bodySmall
                        )
                        val visibleLabel = segments.getOrNull(visibleSegmentIndex)?.postId
                        if (visibleSegmentIndex in segments.indices && visibleLabel != null) {
                            TextButton(onClick = onSeekToVisible) {
                                Text("表示位置 (No.$visibleLabel) へ移動")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                currentSegment?.let {
                    Text(
                        text = "現在: No.${it.postId}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                Text(
                    text = "読み上げ対象スレッドがありません",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onPlay,
                    enabled = totalSegments > 0 && !isPlaying,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(playLabel)
                }
                OutlinedButton(
                    onClick = onPause,
                    enabled = isPlaying,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("一時停止")
                }
                OutlinedButton(
                    onClick = onStop,
                    enabled = status !is ReadAloudStatus.Idle,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("停止")
                }
            }
        }
    }
}

private fun buildReadAloudSegments(posts: List<Post>): List<ReadAloudSegment> {
    return posts.mapIndexedNotNull { index, post ->
        if (post.isDeleted) return@mapIndexedNotNull null
        val lines = messageHtmlToLines(post.messageHtml)
            .map { stripUrlsForReadAloud(it).trim() }
            .filter { it.isNotBlank() && !it.startsWith(">") && !it.startsWith("＞") }
        if (lines.isEmpty()) return@mapIndexedNotNull null
        if (containsDeletionNotice(lines)) return@mapIndexedNotNull null
        val body = lines.joinToString("\n")
        ReadAloudSegment(index, post.id, body)
    }
}

private fun stripUrlsForReadAloud(value: String): String {
    val withoutUrls = READ_ALOUD_URL_REGEX.replace(value, "")
    return withoutUrls.replace(Regex("\\s{2,}"), " ")
}

private fun containsDeletionNotice(lines: List<String>): Boolean {
    if (lines.isEmpty()) return false
    return lines.any { line ->
        READ_ALOUD_SKIPPED_PHRASES.any { phrase ->
            line.contains(phrase)
        }
    }
}

private enum class ThreadFilterSortOption(val displayLabel: String) {
    Saidane("そうだね数が多い順"),
    Replies("返信数が多い順")
}

private enum class ThreadFilterOption(
    val label: String,
    val icon: ImageVector,
    val sortOption: ThreadFilterSortOption? = null
) {
    SelfPosts("自分の書き込み", Icons.Rounded.Person),
    HighSaidane("そうだねが多い", Icons.Rounded.ThumbUp, ThreadFilterSortOption.Saidane),
    HighReplies("返信が多い", Icons.AutoMirrored.Rounded.ReplyAll, ThreadFilterSortOption.Replies),
    Deleted("削除されたレス", Icons.Rounded.DeleteSweep),
    Url("URLを含むレス", Icons.Rounded.Link),
    Image("画像レス", Icons.Outlined.Image),
    Keyword("キーワード", Icons.Rounded.Search);

    companion object {
        val entries = values().toList()
    }
}

private data class ThreadFilterCriteria(
    val options: Set<ThreadFilterOption>,
    val keyword: String,
    val selfPostIdentifiers: List<String>,
    val sortOption: ThreadFilterSortOption?
)
