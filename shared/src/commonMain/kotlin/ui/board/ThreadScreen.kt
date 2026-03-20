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
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.Logger
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
    screenContract: ScreenContract,
    threadId: String,
    threadTitle: String?,
    initialReplyCount: Int?,
    onBack: () -> Unit,
    onScrollPositionPersist: (threadId: String, index: Int, offset: Int) -> Unit = { _, _, _ -> },
    onScrollPositionPersistImmediately: (threadId: String, index: Int, offset: Int) -> Unit = { _, _, _ -> },
    threadUrlOverride: String? = null,
    dependencies: ThreadScreenDependencies = ThreadScreenDependencies(),
    onRegisteredThreadUrlClick: (String) -> Boolean = { false },
    modifier: Modifier = Modifier
) {
    ThreadScreen(
        board = board,
        history = screenContract.history,
        threadId = threadId,
        threadTitle = threadTitle,
        initialReplyCount = initialReplyCount,
        threadUrlOverride = threadUrlOverride,
        onBack = onBack,
        historyCallbacks = screenContract.historyCallbacks,
        onScrollPositionPersist = onScrollPositionPersist,
        onScrollPositionPersistImmediately = onScrollPositionPersistImmediately,
        dependencies = dependencies,
        preferencesState = screenContract.preferencesState,
        preferencesCallbacks = screenContract.preferencesCallbacks,
        onRegisteredThreadUrlClick = onRegisteredThreadUrlClick,
        modifier = modifier
    )
}

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
    historyCallbacks: ScreenHistoryCallbacks = ScreenHistoryCallbacks(),
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit = historyCallbacks.onHistoryEntrySelected,
    onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit = historyCallbacks.onHistoryEntryDismissed,
    onHistoryCleared: () -> Unit = historyCallbacks.onHistoryCleared,
    onHistoryEntryUpdated: (ThreadHistoryEntry) -> Unit = historyCallbacks.onHistoryEntryUpdated,
    onHistoryRefresh: suspend () -> Unit = historyCallbacks.onHistoryRefresh,
    onScrollPositionPersist: (threadId: String, index: Int, offset: Int) -> Unit = { _, _, _ -> },
    onScrollPositionPersistImmediately: (threadId: String, index: Int, offset: Int) -> Unit = { _, _, _ -> },
    dependencies: ThreadScreenDependencies = ThreadScreenDependencies(),
    repository: BoardRepository? = dependencies.repository,
    httpClient: io.ktor.client.HttpClient? = dependencies.httpClient,
    fileSystem: FileSystem? = dependencies.fileSystem,
    cookieRepository: CookieRepository? = dependencies.cookieRepository,
    stateStore: com.valoser.futacha.shared.state.AppStateStore? = dependencies.stateStore,
    autoSavedThreadRepository: SavedThreadRepository? = dependencies.autoSavedThreadRepository,
    preferencesState: ScreenPreferencesState,
    preferencesCallbacks: ScreenPreferencesCallbacks = ScreenPreferencesCallbacks(),
    onRegisteredThreadUrlClick: (String) -> Boolean = { false },
    modifier: Modifier = Modifier
) {
    val mutableStateBundle = rememberThreadScreenMutableStateBundle(
        boardId = board.id,
        threadId = threadId,
        threadUrlOverride = threadUrlOverride
    )
    var resolvedThreadUrlOverride by mutableStateBundle.resolvedThreadUrlOverride
    ThreadUrlOverrideSyncEffect(
        threadUrlOverride = threadUrlOverride,
        resolvedThreadUrlOverride = resolvedThreadUrlOverride,
        onResolvedThreadUrlOverrideChanged = { resolvedThreadUrlOverride = it }
    )
    val coreSetupBundle = rememberThreadScreenCoreSetupBundle(
        board = board,
        history = history,
        threadId = threadId,
        repository = repository,
        fileSystem = fileSystem,
        stateStore = stateStore,
        autoSavedThreadRepository = autoSavedThreadRepository,
        manualSaveDirectory = preferencesState.manualSaveDirectory,
        manualSaveLocation = preferencesState.manualSaveLocation,
        resolvedThreadUrlOverride = resolvedThreadUrlOverride,
        onRegisteredThreadUrlClick = onRegisteredThreadUrlClick
    )
    val environmentBundle = coreSetupBundle.environmentBundle
    val activeRepository = environmentBundle.activeRepository
    val effectiveBoardUrl = environmentBundle.effectiveBoardUrl
    val uiState = mutableStateBundle.uiState
    val runtimeObjectBundle = coreSetupBundle.runtimeObjectBundle
    val snackbarHostState = runtimeObjectBundle.snackbarHostState
    val coroutineScope = runtimeObjectBundle.coroutineScope
    val platformRuntimeBindings = coreSetupBundle.platformRuntimeBindings
    val externalUrlLauncher = platformRuntimeBindings.externalUrlLauncher
    val handleUrlClick = platformRuntimeBindings.handleUrlClick
    val archiveSearchJson = platformRuntimeBindings.archiveSearchJson
    val persistentBindings = coreSetupBundle.persistentBindings
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
    val readAloudStateBindings = buildThreadScreenReadAloudStateBindings(
        ThreadScreenReadAloudStateInputs(
            currentJob = { readAloudJob },
            currentStatus = { readAloudStatus },
            currentIndex = { currentReadAloudIndex },
            currentCancelRequestedByUser = { readAloudCancelRequestedByUser },
            setJob = { readAloudJob = it },
            setStatus = { readAloudStatus = it },
            setIndex = { currentReadAloudIndex = it },
            setCancelRequestedByUser = { readAloudCancelRequestedByUser = it }
        )
    )
    val stateRuntimeBindingsBundle = buildThreadScreenStateRuntimeBindingsBundle(
        runtimeJobInputs = ThreadScreenRuntimeJobInputs(
            readAloudStateBindings = readAloudStateBindings,
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
            }
        ),
        messageNgInputs = ThreadScreenMessageNgInputs(
            coroutineScope = coroutineScope,
            showSnackbar = snackbarHostState::showSnackbar,
            screenPreferencesCallbacks = preferencesCallbacks,
            stateStore = stateStore,
            onFallbackHeadersChanged = persistentBindings.onFallbackHeadersChanged,
            onFallbackWordsChanged = persistentBindings.onFallbackWordsChanged,
            currentHeaders = { ngHeaders },
            currentWords = { ngWords },
            isFilteringEnabled = { ngFilteringEnabled },
            setFilteringEnabled = { ngFilteringEnabled = it }
        ),
        formInputs = ThreadScreenFormInputs(
            currentFilterOptions = { selectedThreadFilterOptions },
            currentFilterSortOption = { selectedThreadSortOption },
            currentFilterKeyword = { threadFilterKeyword },
            setFilterOptions = { selectedThreadFilterOptions = it },
            setFilterSortOption = { selectedThreadSortOption = it },
            setFilterKeyword = { threadFilterKeyword = it },
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
    ThreadReplyDialogAutofillEffect(
        isReplyDialogVisible = isReplyDialogVisible,
        lastUsedDeleteKey = lastUsedDeleteKey,
        replyDialogBinding = replyDialogBinding
    )
    var isRefreshing by mutableStateBundle.isRefreshing
    var manualRefreshGeneration by mutableStateBundle.manualRefreshGeneration
    var isHistoryRefreshing by mutableStateBundle.isHistoryRefreshing
    var saveProgress by mutableStateBundle.saveProgress
    val isPrivacyFilterEnabled = coreSetupBundle.isPrivacyFilterEnabled
    val currentState = uiState.value
    val initialHistoryEntry = environmentBundle.initialHistoryEntry
    val lazyListState = runtimeObjectBundle.lazyListState
    val actionStateBindings = ThreadScreenActionStateBindings(
        currentActionInProgress = { actionInProgress },
        setActionInProgress = { actionInProgress = it },
        currentLastBusyNoticeAtMillis = { lastBusyActionNoticeAtMillis },
        setLastBusyNoticeAtMillis = { lastBusyActionNoticeAtMillis = it }
    )
    val actionDependencies = ThreadScreenActionDependencies(
        busyNoticeIntervalMillis = ACTION_BUSY_NOTICE_INTERVAL_MS,
        showMessage = showMessage,
        onDebugLog = { message -> Logger.d(THREAD_ACTION_LOG_TAG, message) },
        onInfoLog = { message -> Logger.i(THREAD_ACTION_LOG_TAG, message) },
        onErrorLog = { message, error -> Logger.e(THREAD_ACTION_LOG_TAG, message, error) }
    )
    val historyRefreshStateBindings = ThreadScreenHistoryRefreshStateBindings(
        currentIsHistoryRefreshing = { isHistoryRefreshing },
        setIsHistoryRefreshing = { isHistoryRefreshing = it }
    )
    val readAloudCallbacks = ThreadScreenReadAloudCallbacks(
        showMessage = showMessage,
        showOptionalMessage = showOptionalMessage,
        scrollToPostIndex = { postIndex ->
            lazyListState.animateScrollToItem(postIndex)
        },
        speakText = textSpeaker::speak,
        cancelActiveReadAloud = cancelActiveReadAloud
    )
    var hasRestoredInitialScroll by mutableStateBundle.hasRestoredInitialScroll
    val offlineLookupContext = environmentBundle.offlineLookupContext
    val offlineSources = environmentBundle.offlineSources
    val asyncRuntimeBindingsBundle = buildThreadScreenAsyncRuntimeBindingsBundle(
        ThreadScreenAsyncRuntimeInputs(
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
            onOpenSaveDirectoryPicker = preferencesCallbacks.onOpenSaveDirectoryPicker,
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
    )
    val asyncBindingsBundle = rememberThreadScreenAsyncBindingsBundle(
        coroutineScope = coroutineScope,
        activeRepository = activeRepository,
        history = history,
        threadId = threadId,
        threadTitle = threadTitle,
        board = board,
        effectiveBoardUrl = effectiveBoardUrl,
        resolvedThreadUrlOverride = resolvedThreadUrlOverride,
        archiveSearchJson = archiveSearchJson,
        offlineLookupContext = offlineLookupContext,
        offlineSources = offlineSources,
        httpClient = httpClient,
        fileSystem = fileSystem,
        autoSaveRepository = autoSaveRepository,
        manualSaveRepository = manualSaveRepository,
        asyncRuntimeBindingsBundle = asyncRuntimeBindingsBundle,
        preferencesState = preferencesState,
        isAndroidPlatform = isAndroidPlatform
    )
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
    val persistCurrentScrollPositionImmediately = remember(
        lazyListState,
        threadId,
        onScrollPositionPersistImmediately
    ) {
        {
            onScrollPositionPersistImmediately(
                threadId,
                lazyListState.firstVisibleItemIndex,
                lazyListState.firstVisibleItemScrollOffset
            )
        }
    }
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
        onPersistCurrentScrollPosition = persistCurrentScrollPositionImmediately,
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
    val autoSaveEffectState = rememberThreadAutoSaveEffectState(
        currentPageForAutoSave = currentPageForAutoSave,
        threadId = threadId,
        isShowingOfflineCopy = isShowingOfflineCopy,
        autoSaveRepository = autoSaveRepository,
        httpClient = httpClient,
        fileSystem = fileSystem,
        autoSaveJob = autoSaveJob,
        lastAutoSaveTimestampMillis = lastAutoSaveTimestamp.value
    )
    ThreadAutoSaveLaunchEffect(
        threadId = threadId,
        currentPageForAutoSave = currentPageForAutoSave,
        isShowingOfflineCopy = isShowingOfflineCopy,
        httpClient = httpClient,
        fileSystem = fileSystem,
        autoSaveEffectState = autoSaveEffectState,
        onStartAutoSave = autoSaveBindings.startAutoSave
    )

    val resolvedReplyCount = derivedUiState.resolvedReplyCount
    val resolvedThreadTitle = derivedUiState.resolvedThreadTitle
    val statusLabel = derivedUiState.statusLabel
    val readAloudSegments = derivedRuntimeState.readAloudSegments
    val readAloudDependencies = ThreadScreenReadAloudDependencies(
        currentSegments = { readAloudSegments }
    )
    ThreadReadAloudIndexEffect(
        segmentCount = readAloudSegments.size,
        currentReadAloudIndex = currentReadAloudIndex,
        onCurrentReadAloudIndexChanged = { currentReadAloudIndex = it }
    )

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
    ThreadInitialScrollRestoreEffect(
        threadId = threadId,
        restoreState = initialScrollRestoreState,
        lazyListState = lazyListState,
        onRestoreCompleted = { hasRestoredInitialScroll = true },
        onRestoreFailed = { message, _ ->
            Logger.w(THREAD_SCREEN_TAG, message)
        }
    )
    ThreadSearchIndexEffects(
        searchQuery = searchQuery,
        threadId = threadId,
        searchMatches = searchMatches,
        currentSearchResultIndex = currentSearchResultIndex,
        onCurrentSearchResultIndexChanged = { currentSearchResultIndex = it }
    )
    ThreadScrollPersistenceEffect(
        threadId = threadId,
        lazyListState = lazyListState,
        onScrollPositionPersist = onScrollPositionPersist
    )

    val overlayStateBindings = ThreadScreenOverlayStateBindings(
        currentModalOverlayState = { modalOverlayState },
        setModalOverlayState = { modalOverlayState = it },
        currentSheetOverlayState = { sheetOverlayState },
        setSheetOverlayState = { sheetOverlayState = it },
        currentPostOverlayState = { postOverlayState },
        setPostOverlayState = { postOverlayState = it }
    )
    val interactionUiBindings = buildThreadScreenInteractionUiWiring(
        ThreadScreenInteractionUiWiringInputs(
            coroutineScope = coroutineScope,
            lazyListState = lazyListState,
            drawerState = drawerState,
            snackbarHostState = snackbarHostState,
            overlayStateBindings = overlayStateBindings,
            mediaPreviewState = { mediaPreviewState },
            setMediaPreviewState = { mediaPreviewState = it },
            mediaPreviewEntries = { mediaPreviewEntries },
            actionStateBindings = actionStateBindings,
            actionDependencies = actionDependencies,
            historyRefreshStateBindings = historyRefreshStateBindings,
            onHistoryRefresh = onHistoryRefresh,
            readAloudStateBindings = readAloudStateBindings,
            readAloudCallbacks = readAloudCallbacks,
            readAloudDependencies = readAloudDependencies,
            replyDialogBinding = replyDialogBinding,
            currentIsRefreshing = { isRefreshing },
            currentUiState = { uiState.value },
            currentSearchIndex = { currentSearchResultIndex },
            setCurrentSearchIndex = { currentSearchResultIndex = it },
            currentSearchMatches = { searchMatches },
            onHistoryEntrySelected = onHistoryEntrySelected,
            onHistoryEntryDismissed = onHistoryEntryDismissed,
            onHistoryCleared = onHistoryCleared,
            onBack = onBack,
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
            onStartRefresh = startManualRefresh,
            onHandleThreadSaveRequest = handleThreadSaveRequest,
            onShowMessage = showMessage,
            onOpenExternalApp = {
                externalUrlLauncher(buildThreadExternalAppUrl(effectiveBoardUrl, threadId))
            },
            onTogglePrivacy = {
                coroutineScope.launch {
                    stateStore?.setPrivacyFilterEnabled(!isPrivacyFilterEnabled)
                }
            },
            setSearchQuery = { searchQuery = it },
            setSearchActive = { isSearchActive = it },
            singleMediaSaveBindings = singleMediaSaveBindings,
            currentSuccessState = currentSuccessState,
            threadFilterBinding = threadFilterBinding,
            firstVisibleSegmentIndex = { firstVisibleSegmentIndex },
            pauseReadAloud = pauseReadAloud,
            stopReadAloud = stopReadAloud,
            threadNgMutationCallbacks = threadNgMutationCallbacks,
            currentManualSaveJob = { manualSaveJob },
            setSaveProgress = { saveProgress = it },
            preferencesCallbacks = preferencesCallbacks,
            cookieRepository = cookieRepository
        )
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
    val historyDrawerCallbacks = interactionBindings.historyDrawerCallbacks
    val readAloudIndicatorSegment = (readAloudStatus as? ReadAloudStatus.Speaking)?.segment
    val replyDialogState = replyDialogBinding.currentState()
    val currentFilterUiState = threadFilterBinding.currentState()
    val overlayActionCallbacks = buildThreadScreenOverlayActionCallbacks(
        ThreadScreenOverlayActionInputs(
            currentPostOverlayState = { postOverlayState },
            setPostOverlayState = { postOverlayState = it },
            isSelfPost = isSelfPost,
            replyDialogBinding = replyDialogBinding,
            effectiveBoardUrl = effectiveBoardUrl,
            threadId = threadId,
            boardId = board.id,
            stateStore = stateStore,
            coroutineScope = coroutineScope,
            updateLastUsedDeleteKey = updateLastUsedDeleteKey,
            showMessage = showMessage,
            refreshThread = refreshThread,
            actionBindings = actionBindings,
            threadDeleteByUserActionCallbacks = threadDeleteByUserActionCallbacks,
            threadReplyActionCallbacks = threadReplyActionCallbacks
        )
    )
    val hostBindingsBundle = buildThreadScreenHostBindingsBundle(
        ThreadScreenHostRuntimeInputs(
            modifier = modifier,
            coroutineScope = coroutineScope,
            drawerState = drawerState,
            snackbarHostState = snackbarHostState,
            history = history,
            historyDrawerCallbacks = historyDrawerCallbacks,
            boardName = board.name,
            resolvedThreadTitle = resolvedThreadTitle,
            resolvedReplyCount = resolvedReplyCount,
            statusLabel = statusLabel,
            isSearchActive = isSearchActive,
            searchQuery = searchQuery,
            currentSearchResultIndex = currentSearchResultIndex,
            totalSearchMatches = searchMatches.size,
            uiState = uiState.value,
            refreshThread = refreshThread,
            threadFilterBinding = threadFilterBinding,
            persistedSelfPostIdentifiers = persistedSelfPostIdentifiers,
            ngHeaders = ngHeaders,
            ngWords = ngWords,
            ngFilteringEnabled = ngFilteringEnabled,
            threadFilterCache = threadFilterCache,
            lazyListState = lazyListState,
            saidaneOverrides = saidaneOverrides,
            selfPostIdentifierSet = selfPostIdentifierSet,
            postHighlightRanges = postHighlightRanges,
            postOverlayState = postOverlayState,
            setPostOverlayState = { postOverlayState = it },
            onSaidaneClick = handleSaidaneAction,
            onMediaClick = mediaBindings.onMediaClick,
            onUrlClick = handleUrlClick,
            onRefresh = performRefresh,
            isRefreshing = isRefreshing,
            sheetOverlayState = sheetOverlayState,
            modalOverlayState = modalOverlayState,
            replyDialogState = replyDialogState,
            mediaPreviewState = mediaPreviewState,
            mediaPreviewEntries = mediaPreviewEntries,
            galleryPosts = currentSuccessState?.page?.posts,
            isSingleMediaSaveInProgress = isSingleMediaSaveInProgress,
            readAloudSegments = readAloudSegments,
            currentReadAloudIndex = currentReadAloudIndex,
            firstVisibleSegmentIndex = firstVisibleSegmentIndex,
            readAloudStatus = readAloudStatus,
            isPrivacyFilterEnabled = isPrivacyFilterEnabled,
            saveProgress = saveProgress,
            preferencesState = preferencesState,
            uiBindings = uiBindings,
            filterUiState = currentFilterUiState,
            fileSystem = fileSystem,
            autoSavedThreadRepository = autoSaveRepository,
            cookieRepository = cookieRepository,
            appColorScheme = appColorScheme,
            overlayActionCallbacks = overlayActionCallbacks,
            onQuoteFromActionSheet = openQuoteSelection,
            onNgRegisterFromActionSheet = handleNgRegistration,
            onSaidaneFromActionSheet = handleSaidaneAction,
            onDelRequestFromActionSheet = handleDelRequest,
            onDeleteFromActionSheet = openDeleteDialog,
            onBackSwipe = onBack,
            actionInProgress = actionInProgress,
            readAloudIndicatorSegment = readAloudIndicatorSegment,
            isDrawerOpen = isDrawerOpen,
            backSwipeEdgePx = backSwipeEdgePx,
            backSwipeTriggerPx = backSwipeTriggerPx,
            onReplySubmit = {
                handleThreadScreenReplySubmit(
                    ThreadScreenReplySubmitDependencies(
                        replyDialogBinding = replyDialogBinding,
                        effectiveBoardUrl = effectiveBoardUrl,
                        threadId = threadId,
                        boardId = board.id,
                        coroutineScope = coroutineScope,
                        stateStore = stateStore,
                        updateLastUsedDeleteKey = updateLastUsedDeleteKey,
                        actionBindings = actionBindings,
                        threadReplyActionCallbacks = threadReplyActionCallbacks,
                        refreshThread = refreshThread,
                        showMessage = showMessage
                    )
                )
            }
        )
    )

    MaterialTheme(
        colorScheme = futabaThreadColorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes
    ) {
        ThreadScreenScaffoldHost(
            bindings = hostBindingsBundle.scaffoldBindings
        ) {
            ThreadScreenContentHost(
                bindings = hostBindingsBundle.contentBindings,
                modifier = Modifier.matchParentSize()
            )
        }
        ThreadScreenOverlayHost(
            bindings = hostBindingsBundle.overlayBindings
        )
    }
}
