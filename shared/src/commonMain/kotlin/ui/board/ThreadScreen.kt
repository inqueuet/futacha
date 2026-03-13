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
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.SaveDirectorySelection
import com.valoser.futacha.shared.util.isAndroid
import com.valoser.futacha.shared.util.resolveThreadTitle
import kotlinx.coroutines.*
import kotlin.time.Clock
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
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
    onRegisteredThreadUrlClick: (String) -> Boolean = { false },
    modifier: Modifier = Modifier
) {
    val mutableStateBundle = rememberThreadScreenMutableStateBundle(
        boardId = board.id,
        threadId = threadId,
        threadUrlOverride = threadUrlOverride
    )
    var resolvedThreadUrlOverride by mutableStateBundle.resolvedThreadUrlOverride
    LaunchedEffect(threadUrlOverride) {
        resolveThreadUrlOverrideSyncState(
            currentResolvedThreadUrlOverride = resolvedThreadUrlOverride,
            incomingThreadUrlOverride = threadUrlOverride
        )?.let { resolvedThreadUrlOverride = it }
    }
    val environmentBundle = remember(
        repository,
        autoSavedThreadRepository,
        fileSystem,
        manualSaveDirectory,
        manualSaveLocation,
        history,
        threadId,
        board,
        resolvedThreadUrlOverride
    ) {
        buildThreadScreenEnvironmentBundle(
            repository = repository,
            autoSavedThreadRepository = autoSavedThreadRepository,
            fileSystem = fileSystem,
            manualSaveDirectory = manualSaveDirectory,
            manualSaveLocation = manualSaveLocation,
            history = history,
            threadId = threadId,
            board = board,
            resolvedThreadUrlOverride = resolvedThreadUrlOverride
        )
    }
    val activeRepository = environmentBundle.activeRepository
    val effectiveBoardUrl = environmentBundle.effectiveBoardUrl
    val uiState = mutableStateBundle.uiState
    val runtimeObjectBundle = rememberThreadScreenRuntimeObjectBundle(
        threadId = threadId,
        initialHistoryEntry = environmentBundle.initialHistoryEntry
    )
    val snackbarHostState = runtimeObjectBundle.snackbarHostState
    val coroutineScope = runtimeObjectBundle.coroutineScope
    val platformContext = LocalPlatformContext.current
    val platformRuntimeBindings = rememberThreadScreenPlatformRuntimeBindings(
        platformContext = platformContext,
        onRegisteredThreadUrlClick = onRegisteredThreadUrlClick
    )
    val externalUrlLauncher = platformRuntimeBindings.externalUrlLauncher
    val handleUrlClick = platformRuntimeBindings.handleUrlClick
    val archiveSearchJson = platformRuntimeBindings.archiveSearchJson
    val persistentBindings = rememberThreadScreenPersistentBindings(
        stateStore = stateStore,
        coroutineScope = coroutineScope,
        boardId = board.id,
        threadId = threadId
    )
    val lastUsedDeleteKey = persistentBindings.lastUsedDeleteKey
    val updateLastUsedDeleteKey = persistentBindings.updateLastUsedDeleteKey
    val textSpeaker = platformRuntimeBindings.textSpeaker
    var readAloudJob by mutableStateBundle.readAloudJob
    var readAloudStatus by mutableStateBundle.readAloudStatus
    var sheetOverlayState by mutableStateBundle.sheetOverlayState
    var currentReadAloudIndex by mutableStateBundle.currentReadAloudIndex
    var readAloudCancelRequestedByUser by mutableStateBundle.readAloudCancelRequestedByUser
    val isAndroidPlatform = remember { isAndroid() }
    val autoSaveRepository = environmentBundle.autoSaveRepository
    val manualSaveRepository = environmentBundle.manualSaveRepository
    val legacyManualSaveRepository = environmentBundle.legacyManualSaveRepository
    var autoSaveJob by mutableStateBundle.autoSaveJob
    var manualSaveJob by mutableStateBundle.manualSaveJob
    var singleMediaSaveJob by mutableStateBundle.singleMediaSaveJob
    var refreshThreadJob by mutableStateBundle.refreshThreadJob
    var isManualSaveInProgress by mutableStateBundle.isManualSaveInProgress
    var isSingleMediaSaveInProgress by mutableStateBundle.isSingleMediaSaveInProgress
    val lastAutoSaveTimestamp = mutableStateBundle.lastAutoSaveTimestamp
    var isShowingOfflineCopy by mutableStateBundle.isShowingOfflineCopy
    val drawerState = runtimeObjectBundle.drawerState
    val isDrawerOpen by runtimeObjectBundle.isDrawerOpen
    var actionInProgress by mutableStateBundle.actionInProgress
    var lastBusyActionNoticeAtMillis by mutableStateBundle.lastBusyActionNoticeAtMillis
    val saidaneOverrides = mutableStateBundle.saidaneOverrides
    var postOverlayState by mutableStateBundle.postOverlayState
    var isReplyDialogVisible by mutableStateBundle.isReplyDialogVisible
    var selectedThreadFilterOptions by mutableStateBundle.selectedThreadFilterOptions
    var selectedThreadSortOption by mutableStateBundle.selectedThreadSortOption
    var threadFilterKeyword by mutableStateBundle.threadFilterKeyword
    val threadFilterCache = mutableStateBundle.threadFilterCache
    val persistedSelfPostIdentifiers = persistentBindings.persistedSelfPostIdentifiers
    val selfPostIdentifierSet = persistentBindings.selfPostIdentifierSet
    val isSelfPost = persistentBindings.isSelfPost
    var modalOverlayState by mutableStateBundle.modalOverlayState
    var ngFilteringEnabled by mutableStateBundle.ngFilteringEnabled
    val ngHeaders = persistentBindings.ngHeaders
    val ngWords = persistentBindings.ngWords
    var replyName by mutableStateBundle.replyName
    var replyEmail by mutableStateBundle.replyEmail
    var replySubject by mutableStateBundle.replySubject
    var replyComment by mutableStateBundle.replyComment
    var replyPassword by mutableStateBundle.replyPassword
    var replyImageData by mutableStateBundle.replyImageData
    val stateRuntimeBindingsBundle = buildThreadScreenStateRuntimeBindingsBundle(
        currentReadAloudState = {
            ThreadReadAloudRuntimeState(
                job = readAloudJob,
                status = readAloudStatus,
                currentIndex = currentReadAloudIndex,
                cancelRequestedByUser = readAloudCancelRequestedByUser
            )
        },
        setReadAloudState = { state ->
            readAloudJob = state.job
            readAloudStatus = state.status
            currentReadAloudIndex = state.currentIndex
            readAloudCancelRequestedByUser = state.cancelRequestedByUser
        },
        onStopPlayback = textSpeaker::stop,
        currentAutoSaveJob = { autoSaveJob },
        setAutoSaveJob = { autoSaveJob = it },
        currentManualSaveJob = { manualSaveJob },
        setManualSaveJob = { manualSaveJob = it },
        currentSingleMediaSaveJob = { singleMediaSaveJob },
        setSingleMediaSaveJob = { singleMediaSaveJob = it },
        currentRefreshThreadJob = { refreshThreadJob },
        setRefreshThreadJob = { refreshThreadJob = it },
        setIsManualSaveInProgress = { isManualSaveInProgress = it },
        setIsSingleMediaSaveInProgress = { isSingleMediaSaveInProgress = it },
        onDismissReadAloudOverlay = {
            sheetOverlayState = dismissThreadReadAloudOverlay(sheetOverlayState)
        },
        coroutineScope = coroutineScope,
        showSnackbar = snackbarHostState::showSnackbar,
        onManualSaveDirectoryChanged = onManualSaveDirectoryChanged,
        onSaveDirectorySelectionChanged = onSaveDirectorySelectionChanged,
        onOpenSaveDirectoryPicker = onOpenSaveDirectoryPicker,
        stateStore = stateStore,
        onFallbackHeadersChanged = persistentBindings.onFallbackHeadersChanged,
        onFallbackWordsChanged = persistentBindings.onFallbackWordsChanged,
        currentHeaders = { ngHeaders },
        currentWords = { ngWords },
        isFilteringEnabled = { ngFilteringEnabled },
        setFilteringEnabled = { ngFilteringEnabled = it },
        currentFilterOptions = { selectedThreadFilterOptions },
        currentSortOption = { selectedThreadSortOption },
        currentKeyword = { threadFilterKeyword },
        setFilterOptions = { selectedThreadFilterOptions = it },
        setSortOption = { selectedThreadSortOption = it },
        setKeyword = { threadFilterKeyword = it },
        currentReplyName = { replyName },
        currentReplyEmail = { replyEmail },
        currentReplySubject = { replySubject },
        currentReplyComment = { replyComment },
        currentReplyPassword = { replyPassword },
        currentReplyImageData = { replyImageData },
        setReplyName = { replyName = it },
        setReplyEmail = { replyEmail = it },
        setReplySubject = { replySubject = it },
        setReplyComment = { replyComment = it },
        setReplyPassword = { replyPassword = it },
        setReplyImageData = { replyImageData = it },
        isReplyDialogVisible = { isReplyDialogVisible },
        setReplyDialogVisible = { isReplyDialogVisible = it }
    )
    val readAloudRuntimeBindings = stateRuntimeBindingsBundle.readAloudRuntimeBindings
    val cancelActiveReadAloud = readAloudRuntimeBindings.cancelActive
    val stopReadAloud = readAloudRuntimeBindings.stop
    val jobBindings = stateRuntimeBindingsBundle.jobBindings
    val messageRuntime = stateRuntimeBindingsBundle.messageRuntime
    val showMessage = messageRuntime.showMessage
    val showOptionalMessage = messageRuntime.showOptionalMessage
    val applyThreadSaveErrorState = messageRuntime.applySaveErrorState
    val threadNgMutationCallbacks = stateRuntimeBindingsBundle.threadNgMutationCallbacks
    val threadFilterBinding = stateRuntimeBindingsBundle.threadFilterBinding
    val replyDialogBinding = stateRuntimeBindingsBundle.replyDialogBinding
    LaunchedEffect(isReplyDialogVisible, lastUsedDeleteKey) {
        resolveThreadReplyDialogAutofillState(
            isReplyDialogVisible = isReplyDialogVisible,
            currentState = replyDialogBinding.currentState(),
            lastUsedDeleteKey = lastUsedDeleteKey
        )?.let { replyDialogBinding.setState(it) }
    }
    var isRefreshing by mutableStateBundle.isRefreshing
    var manualRefreshGeneration by mutableStateBundle.manualRefreshGeneration
    var isHistoryRefreshing by mutableStateBundle.isHistoryRefreshing
    var saveProgress by mutableStateBundle.saveProgress
    val isPrivacyFilterEnabled by stateStore?.isPrivacyFilterEnabled?.collectAsState(initial = false)
        ?: remember { mutableStateOf(false) }
    val currentState = uiState.value
    val initialHistoryEntry = environmentBundle.initialHistoryEntry
    val lazyListState = runtimeObjectBundle.lazyListState
    var hasRestoredInitialScroll by mutableStateBundle.hasRestoredInitialScroll
    val offlineLookupContext = environmentBundle.offlineLookupContext
    val offlineSources = environmentBundle.offlineSources
    val asyncRuntimeBindingsBundle = buildThreadScreenAsyncRuntimeBindingsBundle(
        currentAutoSaveJob = { autoSaveJob },
        setAutoSaveJob = { autoSaveJob = it },
        currentLastAutoSaveTimestampMillis = { lastAutoSaveTimestamp.value },
        setLastAutoSaveTimestampMillis = { lastAutoSaveTimestamp.value = it },
        currentIsShowingOfflineCopy = { isShowingOfflineCopy },
        currentManualSaveJob = { manualSaveJob },
        setManualSaveJob = { manualSaveJob = it },
        setIsManualSaveInProgress = { isManualSaveInProgress = it },
        currentIsManualSaveInProgress = { isManualSaveInProgress },
        currentIsSingleMediaSaveInProgress = { isSingleMediaSaveInProgress },
        setSaveProgress = { saveProgress = it },
        currentUiState = { uiState.value },
        showMessage = showMessage,
        applySaveErrorState = applyThreadSaveErrorState,
        onOpenSaveDirectoryPicker = onOpenSaveDirectoryPicker,
        currentSingleMediaSaveJob = { singleMediaSaveJob },
        setSingleMediaSaveJob = { singleMediaSaveJob = it },
        setIsSingleMediaSaveInProgress = { isSingleMediaSaveInProgress = it },
        showOptionalMessage = showOptionalMessage,
        currentRefreshThreadJob = { refreshThreadJob },
        setRefreshThreadJob = { refreshThreadJob = it },
        currentManualRefreshGeneration = { manualRefreshGeneration },
        setManualRefreshGeneration = { manualRefreshGeneration = it },
        setIsRefreshing = { isRefreshing = it },
        setUiState = { uiState.value = it },
        setResolvedThreadUrlOverride = { resolvedThreadUrlOverride = it },
        setIsShowingOfflineCopy = { isShowingOfflineCopy = it },
        onHistoryEntryUpdated = onHistoryEntryUpdated,
        onRestoreManualRefreshScroll = { successUiState, savedIndex, savedOffset ->
            restoreThreadScrollPositionSafely(
                listState = lazyListState,
                savedIndex = savedIndex,
                savedOffset = savedOffset,
                totalItems = successUiState.page.posts.size,
                onFailure = { message, _ ->
                    Logger.w(THREAD_SCREEN_TAG, message)
                }
            )
        }
    )
    val asyncBindingsBundle = remember(
        activeRepository,
        board,
        httpClient,
        fileSystem,
        autoSaveRepository,
        threadId,
        threadTitle,
        board.url,
        effectiveBoardUrl,
        resolvedThreadUrlOverride,
        archiveSearchJson,
        offlineLookupContext,
        offlineSources,
        history,
        manualSaveDirectory,
        manualSaveLocation,
        resolvedManualSaveDirectory
    ) {
        buildThreadScreenAsyncBindingsBundle(
            coroutineScope = coroutineScope,
            repository = activeRepository,
            history = history,
            threadId = threadId,
            threadTitle = threadTitle,
            board = board,
            boardUrl = board.url,
            effectiveBoardUrl = effectiveBoardUrl,
            threadUrlOverride = resolvedThreadUrlOverride,
            archiveSearchJson = archiveSearchJson,
            offlineLookupContext = offlineLookupContext,
            offlineSources = offlineSources,
            currentThreadUrlOverride = { resolvedThreadUrlOverride },
            httpClient = httpClient,
            fileSystem = fileSystem,
            autoSaveRepository = autoSaveRepository,
            manualSaveRepository = manualSaveRepository,
            minAutoSaveIntervalMillis = AUTO_SAVE_INTERVAL_MS,
            autoSaveStateBindings = asyncRuntimeBindingsBundle.autoSaveStateBindings,
            manualSaveStateBindings = asyncRuntimeBindingsBundle.manualSaveStateBindings,
            manualSaveDirectory = manualSaveDirectory,
            manualSaveLocation = manualSaveLocation,
            resolvedManualSaveDirectory = resolvedManualSaveDirectory,
            requiresManualLocationSelection = requiresThreadManualSaveLocationSelection(
                isAndroidPlatform,
                manualSaveLocation
            ),
            manualSaveCallbacks = asyncRuntimeBindingsBundle.manualSaveCallbacks,
            singleMediaSaveStateBindings = asyncRuntimeBindingsBundle.singleMediaSaveStateBindings,
            singleMediaSaveCallbacks = asyncRuntimeBindingsBundle.singleMediaSaveCallbacks,
            loadStateBindings = asyncRuntimeBindingsBundle.loadStateBindings,
            loadUiCallbacks = asyncRuntimeBindingsBundle.loadUiCallbacks,
            onWarning = { message ->
                Logger.w(THREAD_SCREEN_TAG, message)
            },
            onInfo = { message ->
                Logger.i(THREAD_SCREEN_TAG, message)
            }
        )
    }
    val runnerBindings = asyncBindingsBundle.runnerBindings
    val threadDeleteByUserActionCallbacks = runnerBindings.deleteByUserActionCallbacks
    val threadReplyActionCallbacks = runnerBindings.replyActionCallbacks
    val saveExecutionBindings = asyncBindingsBundle.saveExecutionBindings
    val autoSaveBindings = saveExecutionBindings.autoSaveBindings
    val manualSaveBindings = saveExecutionBindings.manualSaveBindings
    val singleMediaSaveBindings = saveExecutionBindings.singleMediaSaveBindings
    val loadBindings = asyncBindingsBundle.loadBindings
    val startManualRefresh = loadBindings.startManualRefresh
    val handleThreadSaveRequest = manualSaveBindings.handleThreadSaveRequest
    val runtimeLifecycleBindings = buildThreadScreenLifecycleBindings(
        coroutineScope = coroutineScope,
        resolvePauseMessage = readAloudRuntimeBindings.pause,
        onShowPauseMessage = snackbarHostState::showSnackbar,
        onStopReadAloud = stopReadAloud,
        onCloseTextSpeaker = textSpeaker::close,
        onResetJobsForThreadChange = { jobBindings.resetForThreadChange() },
        onCancelAllJobs = { jobBindings.cancelAll() },
        isDrawerOpen = { isDrawerOpen },
        onCloseDrawer = drawerState::close,
        onBack = onBack,
        onRefreshThread = loadBindings.refreshThread
    )
    val pauseReadAloud = runtimeLifecycleBindings.pauseReadAloud
    DisposableEffect(textSpeaker) {
        onDispose {
            runtimeLifecycleBindings.onTextSpeakerDispose()
        }
    }
    LaunchedEffect(threadId) {
        runtimeLifecycleBindings.onThreadChanged()
    }
    DisposableEffect(Unit) {
        onDispose {
            runtimeLifecycleBindings.onScreenDispose()
        }
    }
    PlatformBackHandler {
        runtimeLifecycleBindings.onBackPressed()
    }

    val refreshThread = loadBindings.refreshThread

    LaunchedEffect(effectiveBoardUrl, threadId) {
        runtimeLifecycleBindings.onInitialRefresh()
    }

    var isSearchActive by mutableStateBundle.isSearchActive
    var searchQuery by mutableStateBundle.searchQuery
    var currentSearchResultIndex by mutableStateBundle.currentSearchResultIndex
    val derivedRuntimeState = rememberThreadScreenDerivedRuntimeState(
        currentState,
        initialReplyCount,
        threadTitle,
        sheetOverlayState.isReadAloudControlsVisible,
        readAloudStatus,
        lazyListState,
        isSearchActive,
        searchQuery
    )
    val derivedUiState = derivedRuntimeState.derivedUiState
    val currentSuccessState = derivedUiState.successState
    val mediaPreviewEntries = derivedRuntimeState.mediaPreviewEntries
    var mediaPreviewState by mutableStateBundle.mediaPreviewState

    val currentPageForAutoSave = derivedUiState.currentPage
    val autoSaveEffectState = remember(
        currentPageForAutoSave,
        threadId,
        isShowingOfflineCopy,
        autoSaveRepository,
        httpClient,
        fileSystem,
        autoSaveJob,
        lastAutoSaveTimestamp.value
    ) {
        resolveThreadAutoSaveEffectState(
            page = currentPageForAutoSave,
            expectedThreadId = threadId,
            isShowingOfflineCopy = isShowingOfflineCopy,
            hasAutoSaveRepository = autoSaveRepository != null,
            hasHttpClient = httpClient != null,
            hasFileSystem = fileSystem != null,
            isAutoSaveInProgress = autoSaveJob?.isActive == true,
            lastAutoSaveTimestampMillis = lastAutoSaveTimestamp.value,
            nowMillis = Clock.System.now().toEpochMilliseconds(),
            minIntervalMillis = AUTO_SAVE_INTERVAL_MS
        )
    }
    LaunchedEffect(threadId, currentPageForAutoSave, isShowingOfflineCopy, httpClient, fileSystem) {
        autoSaveEffectState.page?.let(autoSaveBindings.startAutoSave)
    }

    val resolvedReplyCount = derivedUiState.resolvedReplyCount
    val resolvedThreadTitle = derivedUiState.resolvedThreadTitle
    val statusLabel = derivedUiState.statusLabel
    val readAloudSegments = derivedRuntimeState.readAloudSegments
    LaunchedEffect(readAloudSegments.size) {
        resolveThreadReadAloudIndexUpdate(
            currentIndex = currentReadAloudIndex,
            segmentCount = readAloudSegments.size
        )?.let { currentReadAloudIndex = it }
    }

    val density = LocalDensity.current
    val backSwipeMetrics = rememberThreadBackSwipeMetrics(density)
    val backSwipeEdgePx = backSwipeMetrics.edgePx
    val backSwipeTriggerPx = backSwipeMetrics.triggerPx
    val firstVisibleSegmentIndex = derivedRuntimeState.firstVisibleSegmentIndex

    val currentPage = derivedUiState.currentPage
    val searchMatches = derivedRuntimeState.searchMatches
    val postHighlightRanges = derivedRuntimeState.postHighlightRanges
    val initialScrollRestoreState = rememberThreadInitialScrollRestoreState(
        hasRestoredInitialScroll,
        initialHistoryEntry,
        currentPage?.posts?.size
    )
    LaunchedEffect(
        threadId,
        initialHistoryEntry?.lastReadItemIndex,
        initialHistoryEntry?.lastReadItemOffset,
        currentPage?.posts?.size
    ) {
        if (!initialScrollRestoreState.shouldRestore) return@LaunchedEffect
        restoreThreadScrollPositionSafely(
            listState = lazyListState,
            savedIndex = initialScrollRestoreState.savedIndex,
            savedOffset = initialScrollRestoreState.savedOffset,
            totalItems = initialScrollRestoreState.totalItems,
            onFailure = { message, _ ->
                Logger.w(THREAD_SCREEN_TAG, message)
            }
        )
        hasRestoredInitialScroll = true
    }

    LaunchedEffect(searchQuery, threadId) {
        resolveThreadSearchQueryResetIndex(
            currentIndex = currentSearchResultIndex
        )?.let { currentSearchResultIndex = it }
    }

    LaunchedEffect(searchMatches) {
        resolveThreadSearchResultIndexUpdate(
            currentIndex = currentSearchResultIndex,
            matchCount = searchMatches.size
        )?.let { currentSearchResultIndex = it }
    }
    LaunchedEffect(threadId, lazyListState) {
        collectThreadScrollPositionPersistence(
            listState = lazyListState,
            threadId = threadId,
            onScrollPositionPersist = onScrollPositionPersist
        )
    }

    val interactionUiBindings = buildThreadScreenInteractionUiAggregateBundle(
        currentPreviewState = { mediaPreviewState },
        setPreviewState = { mediaPreviewState = it },
        currentMediaEntries = { mediaPreviewEntries },
        coroutineScope = coroutineScope,
        currentActionInProgress = { actionInProgress },
        setActionInProgress = { actionInProgress = it },
        currentLastBusyNoticeAtMillis = { lastBusyActionNoticeAtMillis },
        setLastBusyNoticeAtMillis = { lastBusyActionNoticeAtMillis = it },
        busyNoticeIntervalMillis = ACTION_BUSY_NOTICE_INTERVAL_MS,
        showMessage = showMessage,
        showOptionalMessage = showOptionalMessage,
        onActionDebugLog = { message -> Logger.d(THREAD_ACTION_LOG_TAG, message) },
        onActionInfoLog = { message -> Logger.i(THREAD_ACTION_LOG_TAG, message) },
        onActionErrorLog = { message, error -> Logger.e(THREAD_ACTION_LOG_TAG, message, error) },
        currentIsHistoryRefreshing = { isHistoryRefreshing },
        setIsHistoryRefreshing = { isHistoryRefreshing = it },
        onHistoryRefresh = onHistoryRefresh,
        showHistoryRefreshMessage = snackbarHostState::showSnackbar,
        currentReadAloudState = {
            ThreadReadAloudRuntimeState(
                job = readAloudJob,
                status = readAloudStatus,
                currentIndex = currentReadAloudIndex,
                cancelRequestedByUser = readAloudCancelRequestedByUser
            )
        },
        setReadAloudState = { state ->
            readAloudJob = state.job
            readAloudStatus = state.status
            currentReadAloudIndex = state.currentIndex
            readAloudCancelRequestedByUser = state.cancelRequestedByUser
        },
        scrollToReadAloudPostIndex = { postIndex ->
            lazyListState.animateScrollToItem(postIndex)
        },
        speakReadAloudText = textSpeaker::speak,
        cancelActiveReadAloud = cancelActiveReadAloud,
        currentReadAloudSegments = { readAloudSegments },
        isRefreshing = { isRefreshing },
        onOpenReplyDialog = {
            replyDialogBinding.setState(
                openThreadReplyDialog(
                    state = replyDialogBinding.currentState(),
                    lastUsedDeleteKey = lastUsedDeleteKey
                )
            )
        },
        onScrollTop = {
            coroutineScope.launch { lazyListState.animateScrollToItem(0) }
        },
        onScrollBottom = {
            coroutineScope.launch {
                val currentState = uiState.value
                if (currentState is ThreadUiState.Success) {
                    val lastIndex = currentState.page.posts.size - 1
                    if (lastIndex >= 0) {
                        lazyListState.animateScrollToItem(lastIndex)
                    }
                }
            }
        },
        onShowRefreshBusyMessage = {
            showMessage(buildThreadRefreshBusyMessage())
        },
        onStartRefreshFromMenu = {
            startManualRefresh(
                lazyListState.firstVisibleItemIndex,
                lazyListState.firstVisibleItemScrollOffset
            )
        },
        onOpenGallery = {
            modalOverlayState = openThreadGalleryOverlay(modalOverlayState)
        },
        onDelegateToSaveHandler = {
            handleThreadSaveRequest()
        },
        onShowFilterSheet = {
            sheetOverlayState = openThreadFilterOverlay(sheetOverlayState)
        },
        onShowSettingsSheet = {
            sheetOverlayState = openThreadSettingsOverlay(sheetOverlayState)
        },
        onClearNgHeaderPrefill = {
            postOverlayState = postOverlayState.copy(ngHeaderPrefill = null)
        },
        onShowNgManagement = {
            postOverlayState = openThreadNgManagementOverlay(postOverlayState)
        },
        onOpenExternalApp = {
            externalUrlLauncher(buildThreadExternalAppUrl(effectiveBoardUrl, threadId))
        },
        onShowReadAloudControls = {
            sheetOverlayState = openThreadReadAloudOverlay(sheetOverlayState)
        },
        onTogglePrivacy = {
            coroutineScope.launch {
                stateStore?.setPrivacyFilterEnabled(!isPrivacyFilterEnabled)
            }
        },
        currentSearchIndex = { currentSearchResultIndex },
        setCurrentSearchIndex = { currentSearchResultIndex = it },
        currentSearchMatches = { searchMatches },
        onScrollToSearchMatchPostIndex = { targetPostIndex ->
            if (targetPostIndex != null) {
                coroutineScope.launch {
                    lazyListState.animateScrollToItem(targetPostIndex)
                }
            }
        },
        onCloseDrawerAfterHistorySelection = {
            coroutineScope.launch { drawerState.close() }
        },
        onHistoryEntrySelected = onHistoryEntrySelected,
        currentOverlayState = { postOverlayState },
        setOverlayState = { postOverlayState = it },
        lastUsedDeleteKey = lastUsedDeleteKey,
        currentSaidaneLabel = { post ->
            saidaneOverrides[post.id] ?: post.saidaneLabel
        },
        isSelfPost = isSelfPost,
        onSaidaneLabelUpdated = { post, updatedLabel ->
            saidaneOverrides[post.id] = updatedLabel
        },
        repository = activeRepository,
        effectiveBoardUrl = effectiveBoardUrl,
        threadId = threadId,
        currentFirstVisibleItemIndex = { lazyListState.firstVisibleItemIndex },
        currentFirstVisibleItemOffset = { lazyListState.firstVisibleItemScrollOffset },
        onStartRefreshFromPull = startManualRefresh,
        onHistoryEntryDismissed = onHistoryEntryDismissed,
        onBoardClick = {
            coroutineScope.launch {
                drawerState.close()
                onBack()
            }
        },
        onHistoryBatchDeleteClick = {
            coroutineScope.launch {
                onHistoryCleared()
                showThreadMessage(buildThreadHistoryBatchDeleteMessage(), snackbarHostState::showSnackbar)
                drawerState.close()
            }
        },
        onHistorySettingsClick = {
            modalOverlayState = openThreadGlobalSettingsOverlay(modalOverlayState)
        },
        onSearchQueryChange = { searchQuery = it },
        onSearchClose = { isSearchActive = false },
        onBack = onBack,
        onOpenHistory = {
            coroutineScope.launch { drawerState.open() }
        },
        onSearch = { isSearchActive = true },
        onOpenGlobalSettings = {
            modalOverlayState = openThreadGlobalSettingsOverlay(modalOverlayState)
        },
        replyDialogBinding = replyDialogBinding,
        mediaPreviewEntryCount = mediaPreviewEntries.size,
        onSavePreviewMedia = singleMediaSaveBindings.savePreviewMedia,
        galleryPosts = currentSuccessState?.page?.posts,
        onDismissGallery = {
            modalOverlayState = dismissThreadGalleryOverlay(modalOverlayState)
        },
        onScrollToPostIndex = { index ->
            coroutineScope.launch {
                lazyListState.animateScrollToItem(index)
            }
        },
        threadFilterBinding = threadFilterBinding,
        onDismissSettingsSheet = {
            sheetOverlayState = dismissThreadSettingsOverlay(sheetOverlayState)
        },
        onDismissFilterSheet = {
            sheetOverlayState = dismissThreadFilterOverlay(sheetOverlayState)
        },
        onApplySettingsActionState = { actionState ->
            sheetOverlayState = applyThreadSettingsActionOverlayState(
                currentState = sheetOverlayState,
                actionState = actionState
            )
        },
        firstVisibleSegmentIndex = { firstVisibleSegmentIndex },
        onPauseReadAloud = pauseReadAloud,
        onStopReadAloud = stopReadAloud,
        onShowReadAloudStoppedMessage = {
            showMessage(buildReadAloudStoppedMessage())
        },
        onDismissReadAloudControls = {
            sheetOverlayState = dismissThreadReadAloudOverlay(sheetOverlayState)
        },
        onDismissNgManagement = {
            postOverlayState = dismissThreadNgManagementOverlay(postOverlayState)
        },
        ngMutationCallbacks = threadNgMutationCallbacks,
        onDismissSaveProgress = {
            saveProgress = null
        },
        onCancelSaveProgress = {
            manualSaveJob?.cancel()
            showMessage("保存をキャンセルしました")
        },
        onDismissGlobalSettings = {
            modalOverlayState = dismissThreadGlobalSettingsOverlay(modalOverlayState)
        },
        onBackgroundRefreshChanged = onBackgroundRefreshChanged,
        onLightweightModeChanged = onLightweightModeChanged,
        onManualSaveDirectoryChanged = onManualSaveDirectoryChanged,
        onSaveDirectorySelectionChanged = onSaveDirectorySelectionChanged,
        onOpenSaveDirectoryPicker = onOpenSaveDirectoryPicker,
        onOpenCookieManager = cookieRepository?.let {
            {
                modalOverlayState = openThreadCookieManagementOverlay(modalOverlayState)
            }
        },
        onFileManagerSelected = onFileManagerSelected,
        onClearPreferredFileManager = onClearPreferredFileManager,
        onThreadMenuEntriesChanged = onThreadMenuEntriesChanged,
        onCatalogNavEntriesChanged = onCatalogNavEntriesChanged,
        onDismissCookieManagement = {
            modalOverlayState = dismissThreadCookieManagementOverlay(modalOverlayState)
        }
    )
    val mediaBindings = interactionUiBindings.mediaBindings
    LaunchedEffect(mediaPreviewEntries.size) {
        mediaBindings.normalizePreviewState()
    }
    val controllerBindings = interactionUiBindings.controllerBindings
    val actionExecutionBindings = controllerBindings.actionExecutionBindings
    val actionBindings = actionExecutionBindings.actionBindings
    val handleHistoryRefresh = actionExecutionBindings.historyRefreshBindings.handleHistoryRefresh
    val readAloudBindings = actionExecutionBindings.readAloudBindings
    val startReadAloud = readAloudBindings.startReadAloud
    val seekReadAloudToIndex = readAloudBindings.seekReadAloudToIndex
    val interactionBindings = controllerBindings.interactionBindings
    val handleMenuEntry = interactionBindings.menuEntryHandler
    val searchNavigationCallbacks = interactionBindings.searchNavigationCallbacks
    val postActionHandlers = interactionBindings.postActionHandlers
    val handleSaidaneAction = postActionHandlers.onSaidane
    val handleDelRequest = postActionHandlers.onDelRequest
    val openDeleteDialog = postActionHandlers.onOpenDeleteDialog
    val openQuoteSelection = postActionHandlers.onOpenQuoteSelection
    val handleNgRegistration = postActionHandlers.onNgRegister
    val performRefresh = interactionBindings.refreshHandler
    val uiBindings = interactionUiBindings.uiBindings

    val appColorScheme = MaterialTheme.colorScheme
    val futabaThreadColorScheme = rememberFutabaThreadColorScheme(appColorScheme)

    MaterialTheme(
        colorScheme = futabaThreadColorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes
    ) {
    val historyDrawerCallbacks = interactionBindings.historyDrawerCallbacks
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            HistoryDrawerContent(
                history = history,
                onHistoryEntryDismissed = historyDrawerCallbacks.onHistoryEntryDismissed,
                onHistoryEntrySelected = historyDrawerCallbacks.onHistoryEntrySelected,
                onBoardClick = historyDrawerCallbacks.onBoardClick,
                onRefreshClick = historyDrawerCallbacks.onRefreshClick,
                onBatchDeleteClick = historyDrawerCallbacks.onBatchDeleteClick,
                onSettingsClick = historyDrawerCallbacks.onSettingsClick
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
                        onSearchQueryChange = uiBindings.topBarCallbacks.onSearchQueryChange,
                        onSearchPrev = uiBindings.topBarCallbacks.onSearchPrev,
                        onSearchNext = uiBindings.topBarCallbacks.onSearchNext,
                        onSearchSubmit = uiBindings.topBarCallbacks.onSearchSubmit,
                        onSearchClose = uiBindings.topBarCallbacks.onSearchClose,
                        onBack = uiBindings.topBarCallbacks.onBack,
                        onOpenHistory = uiBindings.topBarCallbacks.onOpenHistory,
                        onSearch = uiBindings.topBarCallbacks.onSearch,
                        onMenuSettings = uiBindings.topBarCallbacks.onMenuSettings
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
                        onAction = uiBindings.actionBarCallbacks.onAction
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
                        val threadFilterUiState = threadFilterBinding.currentState()
                        val threadFilterComputationState = remember(
                            threadFilterUiState,
                            persistedSelfPostIdentifiers,
                            ngHeaders,
                            ngWords,
                            ngFilteringEnabled
                        ) {
                            resolveThreadFilterComputationState(
                                uiState = threadFilterUiState,
                                selfPostIdentifiers = persistedSelfPostIdentifiers,
                                ngHeaders = ngHeaders,
                                ngWords = ngWords,
                                ngFilteringEnabled = ngFilteringEnabled
                            )
                        }
                        val hasNgFilters = threadFilterComputationState.hasNgFilters
                        val hasThreadFilters = threadFilterComputationState.hasThreadFilters
                        val postsFingerprint by produceState(
                            initialValue = buildLightweightThreadPostListFingerprint(state.page.posts),
                            key1 = state.page.posts,
                            key2 = threadFilterComputationState.shouldComputeFullPostFingerprint
                        ) {
                            value = if (threadFilterComputationState.shouldComputeFullPostFingerprint) {
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
                            threadFilterComputationState
                        ) {
                            buildThreadFilterCacheKey(
                                postsFingerprint = postsFingerprint,
                                computationState = threadFilterComputationState,
                                ngHeaders = ngHeaders,
                                ngWords = ngWords
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
                            if (threadFilterComputationState.criteria.options.contains(ThreadFilterOption.Keyword)) {
                                delay(THREAD_FILTER_DEBOUNCE_MILLIS)
                            }
                            value = withContext(AppDispatchers.parsing) {
                                val hasNgWordFilters = hasNgFilters && ngWords.any { it.isNotBlank() }
                                val hasThreadLowerBodyFilters = threadFilterComputationState.criteria.options.any {
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
                                    criteria = threadFilterComputationState.criteria,
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
                                postOverlayState = openThreadPostActionOverlay(
                                    currentState = postOverlayState,
                                    post = post
                                )
                            },
                            onQuoteRequestedForPost = { post ->
                                postOverlayState = openThreadQuoteOverlay(
                                    currentState = postOverlayState,
                                    post = post
                                )
                            },
                            onSaidaneClick = handleSaidaneAction,
                            onMediaClick = mediaBindings.onMediaClick,
                            onUrlClick = handleUrlClick,
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

    val sheetTarget = postOverlayState.actionSheetState.targetPost
    if (postOverlayState.actionSheetState.isActionSheetVisible && sheetTarget != null) {
        ThreadPostActionSheet(
            post = sheetTarget,
            onDismiss = {
                postOverlayState = dismissThreadPostActionOverlay(postOverlayState)
            },
            onQuote = { openQuoteSelection(sheetTarget) },
            onNgRegister = { handleNgRegistration(sheetTarget) },
            onSaidane = { handleSaidaneAction(sheetTarget) },
            isSaidaneEnabled = resolveThreadPostActionSheetState(
                isSelfPost = isSelfPost(sheetTarget)
            ).isSaidaneEnabled,
            onDelRequest = { handleDelRequest(sheetTarget) },
            onDelete = { openDeleteDialog(sheetTarget) }
        )
    }

    val deleteTarget = postOverlayState.deleteDialogState.targetPost
    if (deleteTarget != null) {
        DeleteByUserDialog(
            post = deleteTarget,
            password = postOverlayState.deleteDialogState.password,
            onPasswordChange = { value ->
                postOverlayState = updateThreadDeleteOverlayInput(
                    currentState = postOverlayState,
                    password = value
                )
            },
            imageOnly = postOverlayState.deleteDialogState.imageOnly,
            onImageOnlyChange = { value ->
                postOverlayState = updateThreadDeleteOverlayInput(
                    currentState = postOverlayState,
                    imageOnly = value
                )
            },
            onDismiss = {
                postOverlayState = dismissThreadDeleteOverlay(postOverlayState)
            },
            onConfirm = {
                val submitOutcome = resolveThreadScreenDeleteSubmitOutcome(
                    overlayState = postOverlayState,
                    targetPost = deleteTarget,
                    boardUrl = effectiveBoardUrl,
                    threadId = threadId
                )
                if (submitOutcome.validationMessage != null) {
                    showMessage(submitOutcome.validationMessage)
                    return@DeleteByUserDialog
                }
                val deleteActionConfig = submitOutcome.actionConfig ?: return@DeleteByUserDialog
                postOverlayState = submitOutcome.nextOverlayState ?: postOverlayState
                submitOutcome.normalizedPassword?.let(updateLastUsedDeleteKey)
                actionBindings.launch(
                    successMessage = "本人削除を実行しました",
                    failurePrefix = "本人削除に失敗しました",
                    onSuccess = { refreshThread() }
                ) {
                    performThreadDeleteByUserAction(
                        config = deleteActionConfig,
                        callbacks = threadDeleteByUserActionCallbacks
                    )
                }
            }
        )
    }

    val quoteTarget = postOverlayState.quoteSelectionState.targetPost
    if (quoteTarget != null) {
        QuoteSelectionDialog(
            post = quoteTarget,
            onDismiss = { postOverlayState = dismissThreadQuoteOverlay(postOverlayState) },
            onConfirm = uiBindings.quoteSelectionConfirm
        )
    }

    val replyDialogState = replyDialogBinding.currentState()
    if (replyDialogState.isVisible) {
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
                comment = replyDialogState.draft.comment,
                onCommentChange = uiBindings.replyDialogCallbacks.onCommentChange,
                name = replyDialogState.draft.name,
                onNameChange = uiBindings.replyDialogCallbacks.onNameChange,
                email = replyDialogState.draft.email,
                onEmailChange = uiBindings.replyDialogCallbacks.onEmailChange,
                subject = replyDialogState.draft.subject,
                onSubjectChange = uiBindings.replyDialogCallbacks.onSubjectChange,
                password = replyDialogState.draft.password,
                onPasswordChange = uiBindings.replyDialogCallbacks.onPasswordChange,
                selectedImage = replyDialogState.draft.imageData,
                onImageSelected = uiBindings.replyDialogCallbacks.onImageSelected,
                onDismiss = uiBindings.replyDialogCallbacks.onDismiss,
                onSubmit = {
                    val submitState = replyDialogBinding.currentState()
                    val submitOutcome = resolveThreadScreenReplySubmitOutcome(
                        state = submitState,
                        boardUrl = effectiveBoardUrl,
                        threadId = threadId
                    )
                    if (submitOutcome.validationMessage != null) {
                        showMessage(submitOutcome.validationMessage)
                        return@ThreadFormDialog
                    }
                    val replyActionConfig = submitOutcome.actionConfig ?: return@ThreadFormDialog
                    replyDialogBinding.setState(submitOutcome.dismissedState ?: return@ThreadFormDialog)
                    submitOutcome.normalizedPassword?.let(updateLastUsedDeleteKey)
                    actionBindings.launch(
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
                            val completedState = submitOutcome.completedState
                            if (completedState != null) {
                                replyDialogBinding.setState(completedState)
                                refreshThread()
                            }
                        }
                    ) {
                        performThreadReplyAction(
                            config = replyActionConfig,
                            callbacks = threadReplyActionCallbacks
                        )
                    }
                },
                onClear = uiBindings.replyDialogCallbacks.onClear,
                isSubmitEnabled = replyDialogState.draft.comment.trim().isNotBlank() &&
                    hasDeleteKeyForSubmit(replyDialogState.draft.password),
                sendDescription = "返信",
                showSubject = true,
                showPassword = true
            )
        }
    }

    val mediaPreviewDialogState = resolveThreadMediaPreviewDialogState(
        state = mediaPreviewState,
        entries = mediaPreviewEntries,
        isSaveInProgress = isSingleMediaSaveInProgress
    )
    mediaPreviewDialogState?.let { dialogState ->
        ThreadMediaPreviewDialog(
            state = dialogState,
            onDismiss = uiBindings.mediaPreviewDialogCallbacks.onDismiss,
            onNavigateNext = uiBindings.mediaPreviewDialogCallbacks.onNavigateNext,
            onNavigatePrevious = uiBindings.mediaPreviewDialogCallbacks.onNavigatePrevious,
            onSave = uiBindings.mediaPreviewDialogCallbacks.onSave
        )
    }

    if (modalOverlayState.isGalleryVisible && currentSuccessState != null) {
        uiBindings.galleryCallbacks?.let { galleryCallbacks ->
            ThreadImageGallery(
                posts = currentSuccessState.page.posts,
                onDismiss = galleryCallbacks.onDismiss,
                onImageClick = galleryCallbacks.onImageClick
            )
        }
    }
    if (sheetOverlayState.isSettingsVisible) {
        ThreadSettingsSheet(
            onDismiss = uiBindings.settingsSheetCallbacks.onDismiss,
            menuEntries = threadMenuEntries,
            onAction = uiBindings.settingsSheetCallbacks.onAction
        )
    }

    if (sheetOverlayState.isFilterVisible) {
        val filterUiState = threadFilterBinding.currentState()
        ThreadFilterSheet(
            selectedOptions = filterUiState.options,
            activeSortOption = filterUiState.sortOption,
            keyword = filterUiState.keyword,
            onOptionToggle = uiBindings.filterSheetCallbacks.onOptionToggle,
            onKeywordChange = uiBindings.filterSheetCallbacks.onKeywordChange,
            onClear = uiBindings.filterSheetCallbacks.onClear,
            onDismiss = uiBindings.filterSheetCallbacks.onDismiss
        )
    }

    if (sheetOverlayState.isReadAloudControlsVisible) {
        ReadAloudControlSheet(
            segments = readAloudSegments,
            currentIndex = currentReadAloudIndex,
            visibleSegmentIndex = firstVisibleSegmentIndex,
            status = readAloudStatus,
            onSeek = uiBindings.readAloudControlCallbacks.onSeek,
            onSeekToVisible = uiBindings.readAloudControlCallbacks.onSeekToVisible,
            onPlay = uiBindings.readAloudControlCallbacks.onPlay,
            onPause = uiBindings.readAloudControlCallbacks.onPause,
            onStop = uiBindings.readAloudControlCallbacks.onStop,
            onDismiss = uiBindings.readAloudControlCallbacks.onDismiss
        )
    }

    if (postOverlayState.isNgManagementVisible) {
        NgManagementSheet(
            onDismiss = uiBindings.ngManagementCallbacks.onDismiss,
            ngHeaders = ngHeaders,
            ngWords = ngWords,
            ngFilteringEnabled = ngFilteringEnabled,
            onAddHeader = uiBindings.ngManagementCallbacks.onAddHeader,
            onAddWord = uiBindings.ngManagementCallbacks.onAddWord,
            onRemoveHeader = uiBindings.ngManagementCallbacks.onRemoveHeader,
            onRemoveWord = uiBindings.ngManagementCallbacks.onRemoveWord,
            onToggleFiltering = uiBindings.ngManagementCallbacks.onToggleFiltering,
            initialInput = postOverlayState.ngHeaderPrefill
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
        onDismissRequest = uiBindings.saveProgressDialogCallbacks.onDismissRequest,
        onCancelRequest = uiBindings.saveProgressDialogCallbacks.onCancelRequest
    )

    if (modalOverlayState.isGlobalSettingsVisible) {
        GlobalSettingsScreen(
            onBack = uiBindings.globalSettingsCallbacks.onBack,
            appVersion = appVersion,
            isBackgroundRefreshEnabled = isBackgroundRefreshEnabled,
            onBackgroundRefreshChanged = uiBindings.globalSettingsCallbacks.onBackgroundRefreshChanged,
            isLightweightModeEnabled = isLightweightModeEnabled,
            onLightweightModeChanged = uiBindings.globalSettingsCallbacks.onLightweightModeChanged,
            manualSaveDirectory = manualSaveDirectory,
            resolvedManualSaveDirectory = resolvedManualSaveDirectory,
            onManualSaveDirectoryChanged = uiBindings.globalSettingsCallbacks.onManualSaveDirectoryChanged,
            saveDirectorySelection = saveDirectorySelection,
            onSaveDirectorySelectionChanged = uiBindings.globalSettingsCallbacks.onSaveDirectorySelectionChanged,
            onOpenSaveDirectoryPicker = uiBindings.globalSettingsCallbacks.onOpenSaveDirectoryPicker,
            onOpenCookieManager = uiBindings.globalSettingsCallbacks.onOpenCookieManager,
            preferredFileManagerLabel = preferredFileManagerLabel,
            onFileManagerSelected = uiBindings.globalSettingsCallbacks.onFileManagerSelected,
            onClearPreferredFileManager = uiBindings.globalSettingsCallbacks.onClearPreferredFileManager,
            historyEntries = history,
            fileSystem = fileSystem,
            autoSavedThreadRepository = autoSaveRepository,
            threadMenuEntries = threadMenuEntries,
            onThreadMenuEntriesChanged = uiBindings.globalSettingsCallbacks.onThreadMenuEntriesChanged,
            catalogNavEntries = catalogNavEntries,
            onCatalogNavEntriesChanged = uiBindings.globalSettingsCallbacks.onCatalogNavEntriesChanged
        )
    }

    if (modalOverlayState.isCookieManagementVisible && cookieRepository != null) {
        CookieManagementScreen(
            onBack = uiBindings.cookieManagementCallbacks.onBack,
            repository = cookieRepository
        )
    }
}
}
