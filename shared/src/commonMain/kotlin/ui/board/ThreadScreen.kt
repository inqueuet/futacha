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
import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.ai.draftCommentParameter
import com.valoser.futacha.shared.ai.draftEmailParameter
import com.valoser.futacha.shared.ai.draftNameParameter
import com.valoser.futacha.shared.ai.draftPasswordParameter
import com.valoser.futacha.shared.ai.draftSubjectParameter
import com.valoser.futacha.shared.ai.searchQueryParameter
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
import com.valoser.futacha.shared.watch.WatchReadAloudPlaybackState
import com.valoser.futacha.shared.watch.WatchReadAloudStatus
import com.valoser.futacha.shared.watch.WatchReadAloudStatusStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime

private const val WATCH_READ_ALOUD_PROGRESS_UPDATE_STEP = 5

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
    aiCommand: FutachaAiCommand? = null,
    onAiCommandConsumed: (FutachaAiCommand) -> Unit = {},
    modifier: Modifier = Modifier
) {
    ThreadScreenContent(
        args = buildThreadScreenContentArgsFromContract(
            board = board,
            screenContract = screenContract,
            threadId = threadId,
            threadTitle = threadTitle,
            initialReplyCount = initialReplyCount,
            onBack = onBack,
            onScrollPositionPersist = onScrollPositionPersist,
            onScrollPositionPersistImmediately = onScrollPositionPersistImmediately,
            threadUrlOverride = threadUrlOverride,
            dependencies = dependencies,
            onRegisteredThreadUrlClick = onRegisteredThreadUrlClick,
            aiCommand = aiCommand,
            onAiCommandConsumed = onAiCommandConsumed,
            modifier = modifier
        )
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
    aiCommand: FutachaAiCommand? = null,
    onAiCommandConsumed: (FutachaAiCommand) -> Unit = {},
    modifier: Modifier = Modifier
) {
    ThreadScreen(
        board = board,
        screenContract = buildScreenContract(
            history = history,
            historyCallbacks = historyCallbacks,
            onHistoryEntrySelected = onHistoryEntrySelected,
            onHistoryEntryDismissed = onHistoryEntryDismissed,
            onHistoryCleared = onHistoryCleared,
            onHistoryEntryUpdated = onHistoryEntryUpdated,
            onHistoryRefresh = onHistoryRefresh,
            preferencesState = preferencesState,
            preferencesCallbacks = preferencesCallbacks
        ),
        threadId = threadId,
        threadTitle = threadTitle,
        initialReplyCount = initialReplyCount,
        onBack = onBack,
        onScrollPositionPersist = onScrollPositionPersist,
        onScrollPositionPersistImmediately = onScrollPositionPersistImmediately,
        threadUrlOverride = threadUrlOverride,
        dependencies = dependencies.withOverrides(
            repository = repository,
            httpClient = httpClient,
            fileSystem = fileSystem,
            cookieRepository = cookieRepository,
            stateStore = stateStore,
            autoSavedThreadRepository = autoSavedThreadRepository
        ),
        onRegisteredThreadUrlClick = onRegisteredThreadUrlClick,
        aiCommand = aiCommand,
        onAiCommandConsumed = onAiCommandConsumed,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class, FlowPreview::class)
@Composable
private fun ThreadScreenContent(
    args: ThreadScreenContentArgs
) {
    val preparedSetup = rememberThreadScreenPreparedSetupBundle(args)
    val setupHandles = rememberThreadScreenPreparedSetupHandles(preparedSetup)
    val contextHandles = setupHandles.contextHandles
    val board = contextHandles.board
    val history = contextHandles.history
    val threadId = contextHandles.threadId
    val threadTitle = contextHandles.threadTitle
    val initialReplyCount = contextHandles.initialReplyCount
    val threadUrlOverride = contextHandles.threadUrlOverride
    val onBack = contextHandles.onBack
    val onScrollPositionPersist = contextHandles.onScrollPositionPersist
    val onScrollPositionPersistImmediately = contextHandles.onScrollPositionPersistImmediately
    val onHistoryEntrySelected = contextHandles.onHistoryEntrySelected
    val onHistoryEntryDismissed = contextHandles.onHistoryEntryDismissed
    val onHistoryCleared = contextHandles.onHistoryCleared
    val onHistoryEntryUpdated = contextHandles.onHistoryEntryUpdated
    val onHistoryRefresh = contextHandles.onHistoryRefresh
    val onHistoryExport = contextHandles.onHistoryExport
    val onHistoryExportThenClear = contextHandles.onHistoryExportThenClear
    val onHistoryExportSelected = contextHandles.onHistoryExportSelected
    val onHistoryLoadImportPreview = contextHandles.onHistoryLoadImportPreview
    val onHistoryImport = contextHandles.onHistoryImport
    val onHistoryImportSelected = contextHandles.onHistoryImportSelected
    val httpClient = contextHandles.httpClient
    val fileSystem = contextHandles.fileSystem
    val cookieRepository = contextHandles.cookieRepository
    val stateStore = contextHandles.stateStore
    val preferencesState = contextHandles.preferencesState
    val preferencesCallbacks = contextHandles.preferencesCallbacks
    val modifier = contextHandles.modifier
    val runtimeStateRefs = setupHandles.runtimeStateRefs
    val readAloudStateRefs = setupHandles.readAloudStateRefs
    val saveJobStateRefs = setupHandles.saveJobStateRefs
    val interactionStateRefs = setupHandles.interactionStateRefs
    val formStateRefs = setupHandles.formStateRefs
    val refreshStateRefs = setupHandles.refreshStateRefs
    val searchStateRefs = setupHandles.searchStateRefs
    var resolvedThreadUrlOverride by runtimeStateRefs.resolvedThreadUrlOverride
    ThreadUrlOverrideSyncEffect(
        threadUrlOverride = threadUrlOverride,
        resolvedThreadUrlOverride = resolvedThreadUrlOverride,
        onResolvedThreadUrlOverrideChanged = { resolvedThreadUrlOverride = it }
    )
    val screenSetupHandles = setupHandles.setupHandles
    val activeRepository = screenSetupHandles.activeRepository
    val effectiveBoardUrl = screenSetupHandles.effectiveBoardUrl
    val uiState = runtimeStateRefs.uiState
    val runtimeHandles = setupHandles.runtimeHandles
    val snackbarHostState = runtimeHandles.snackbarHostState
    val coroutineScope = runtimeHandles.coroutineScope
    val externalUrlLauncher = runtimeHandles.externalUrlLauncher
    val handleUrlClick = runtimeHandles.handleUrlClick
    val archiveSearchJson = runtimeHandles.archiveSearchJson
    val persistentBindings = screenSetupHandles.persistentBindings
    val lastUsedDeleteKey = persistentBindings.lastUsedDeleteKey
    val updateLastUsedDeleteKey = persistentBindings.updateLastUsedDeleteKey
    val textSpeaker = runtimeHandles.textSpeaker
    var readAloudJob by readAloudStateRefs.job
    var readAloudStatus by readAloudStateRefs.status
    var sheetOverlayState by readAloudStateRefs.sheetOverlayState
    var currentReadAloudIndex by readAloudStateRefs.currentIndex
    var readAloudCancelRequestedByUser by readAloudStateRefs.cancelRequestedByUser
    val isAndroidPlatform = remember { isAndroid() }
    val autoSaveRepository = screenSetupHandles.autoSaveRepository
    val manualSaveRepository = screenSetupHandles.manualSaveRepository
    var autoSaveJob by saveJobStateRefs.autoSaveJob
    var manualSaveJob by saveJobStateRefs.manualSaveJob
    var singleMediaSaveJob by saveJobStateRefs.singleMediaSaveJob
    var refreshThreadJob by saveJobStateRefs.refreshThreadJob
    var isManualSaveInProgress by saveJobStateRefs.isManualSaveInProgress
    var isSingleMediaSaveInProgress by saveJobStateRefs.isSingleMediaSaveInProgress
    val lastAutoSaveTimestamp = saveJobStateRefs.lastAutoSaveTimestamp
    val lastAutoSavePosts = saveJobStateRefs.lastAutoSavePosts
    var isShowingOfflineCopy by saveJobStateRefs.isShowingOfflineCopy
    val drawerState = runtimeHandles.drawerState
    val isDrawerOpen by runtimeHandles.isDrawerOpen
    var actionInProgress by interactionStateRefs.actionInProgress
    var lastBusyActionNoticeAtMillis by interactionStateRefs.lastBusyActionNoticeAtMillis
    val saidaneOverrides = interactionStateRefs.saidaneOverrides
    var postOverlayState by interactionStateRefs.postOverlayState
    var isReplyDialogVisible by interactionStateRefs.isReplyDialogVisible
    var selectedThreadFilterOptions by formStateRefs.selectedThreadFilterOptions
    var selectedThreadSortOption by formStateRefs.selectedThreadSortOption
    var threadFilterKeyword by formStateRefs.threadFilterKeyword
    val threadFilterCache = formStateRefs.threadFilterCache
    val persistedSelfPostIdentifiers = persistentBindings.persistedSelfPostIdentifiers
    val selfPostIdentifierSet = persistentBindings.selfPostIdentifierSet
    val isSelfPost = persistentBindings.isSelfPost
    var modalOverlayState by interactionStateRefs.modalOverlayState
    var ngFilteringEnabled by interactionStateRefs.ngFilteringEnabled
    val ngHeaders = persistentBindings.ngHeaders
    val ngWords = persistentBindings.ngWords
    var replyName by formStateRefs.replyName
    var replyEmail by formStateRefs.replyEmail
    var replySubject by formStateRefs.replySubject
    var replyComment by formStateRefs.replyComment
    var replyPassword by formStateRefs.replyPassword
    var replyImageData by formStateRefs.replyImageData
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
    val stateRuntimeHandles = resolveThreadScreenStateRuntimeHandles(stateRuntimeBindingsBundle)
    val messageRuntime = stateRuntimeHandles.messageRuntime
    val showMessage = messageRuntime.showMessage
    val showOptionalMessage = messageRuntime.showOptionalMessage
    val applyThreadSaveErrorState = messageRuntime.applySaveErrorState
    val readAloudRuntimeBindings = stateRuntimeHandles.readAloudRuntimeBindings
    val cancelActiveReadAloud = readAloudRuntimeBindings.cancelActive
    val stopReadAloud = readAloudRuntimeBindings.stop
    val jobBindings = stateRuntimeHandles.jobBindings
    val threadNgMutationCallbacks = stateRuntimeHandles.threadNgMutationCallbacks
    val threadFilterBinding = stateRuntimeHandles.threadFilterBinding
    val replyDialogBinding = stateRuntimeHandles.replyDialogBinding
    ThreadReplyDialogAutofillEffect(
        isReplyDialogVisible = isReplyDialogVisible,
        lastUsedDeleteKey = lastUsedDeleteKey,
        replyDialogBinding = replyDialogBinding
    )
    var isRefreshing by refreshStateRefs.isRefreshing
    var manualRefreshGeneration by refreshStateRefs.manualRefreshGeneration
    var isHistoryRefreshing by refreshStateRefs.isHistoryRefreshing
    var saveProgress by saveJobStateRefs.saveProgress
    val isPrivacyFilterEnabled = screenSetupHandles.isPrivacyFilterEnabled
    val currentState = uiState.value
    val initialHistoryEntry = screenSetupHandles.initialHistoryEntry
    val lazyListState = runtimeHandles.lazyListState
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
    var hasRestoredInitialScroll by refreshStateRefs.hasRestoredInitialScroll
    val offlineLookupContext = screenSetupHandles.offlineLookupContext
    val offlineSources = screenSetupHandles.offlineSources
    val asyncRuntimeBindingsBundle = buildThreadScreenAsyncRuntimeBindingsBundle(
        ThreadScreenAsyncRuntimeInputs(
            currentAutoSaveJob = { autoSaveJob },
            setAutoSaveJob = { autoSaveJob = it },
            currentLastAutoSaveTimestampMillis = { lastAutoSaveTimestamp.value },
            setLastAutoSaveTimestampMillis = { lastAutoSaveTimestamp.value = it },
            currentLastAutoSavePosts = { lastAutoSavePosts.value },
            setLastAutoSavePosts = { lastAutoSavePosts.value = it },
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
            onOpenSaveSettings = {
                modalOverlayState = openThreadGlobalSettingsOverlay(modalOverlayState)
            },
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
                    totalItems = countThreadContentItems(
                        page = successUiState.page,
                        embeddedHtml = successUiState.embeddedHtml,
                        hasSummary = isThreadSummaryFeatureEnabled(preferencesState),
                        hasAiPostModeration = isAiPostFilterFeatureEnabled(preferencesState)
                    ),
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
    val asyncHandles = resolveThreadScreenAsyncHandles(asyncBindingsBundle)
    val threadDeleteByUserActionCallbacks = asyncHandles.threadDeleteByUserActionCallbacks
    val threadReplyActionCallbacks = asyncHandles.threadReplyActionCallbacks
    val autoSaveBindings = asyncHandles.autoSaveBindings
    val manualSaveBindings = asyncHandles.manualSaveBindings
    val singleMediaSaveBindings = asyncHandles.singleMediaSaveBindings
    val loadBindings = asyncHandles.loadBindings
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
    PlatformBackgroundLifecycleEffect {
        stopReadAloud()
    }

    val refreshThread = loadBindings.refreshThread

    LaunchedEffect(effectiveBoardUrl, threadId) {
        runtimeLifecycleBindings.onInitialRefresh()
    }

    var isSearchActive by searchStateRefs.isSearchActive
    val searchQueryState = searchStateRefs.searchQuery
    var debouncedThreadSearchQuery by rememberSaveable(threadId) { mutableStateOf("") }
    LaunchedEffect(searchQueryState) {
        snapshotFlow { searchQueryState.value }
            .map(::resolveThreadDebouncedSearchQuery)
            .distinctUntilChanged()
            .debounce(::resolveThreadSearchDebounceMillis)
            .collect { debouncedThreadSearchQuery = it }
    }
    var currentSearchResultIndex by searchStateRefs.currentSearchResultIndex
    val shouldPrepareReadAloudForAiCommand = when (args.aiCommand?.action) {
        FutachaAiAction.StartThreadReadAloud,
        FutachaAiAction.NextThreadReadAloud,
        FutachaAiAction.PreviousThreadReadAloud -> true
        else -> false
    }
    val postTextCache = remember(threadId) { ThreadPostTextCache() }
    val derivedRuntimeState = rememberThreadScreenDerivedRuntimeState(
        currentState,
        initialReplyCount,
        threadTitle,
        sheetOverlayState.isReadAloudControlsVisible,
        readAloudStatus,
        shouldPrepareReadAloudForCommand = shouldPrepareReadAloudForAiCommand,
        lazyListState,
        isSearchActive,
        debouncedThreadSearchQuery,
        postTextCache = postTextCache
    )
    val derivedUiState = derivedRuntimeState.derivedUiState
    val currentSuccessState = derivedUiState.successState
    var mediaPreviewCollection by remember(threadId) {
        mutableStateOf(
            MediaPreviewCollection(
                entries = emptyList(),
                indexByKey = emptyMap()
            )
        )
    }
    var mediaPreviewCollectionPosts by remember(threadId) {
        mutableStateOf<List<Post>?>(null)
    }
    val mediaPreviewCollectionMutex = remember(threadId) { Mutex() }
    val ensureMediaPreviewCollection: suspend () -> MediaPreviewCollection = {
        mediaPreviewCollectionMutex.withLock {
            val currentPosts = derivedUiState.currentPosts
            if (mediaPreviewCollectionPosts === currentPosts) {
                mediaPreviewCollection
            } else {
                withContext(AppDispatchers.parsing) {
                    buildMediaPreviewCollection(currentPosts)
                }.also { collection ->
                    mediaPreviewCollectionPosts = currentPosts
                    mediaPreviewCollection = collection
                }
            }
        }
    }
    val mediaPreviewEntries = mediaPreviewCollection.entries
    var mediaPreviewState by searchStateRefs.mediaPreviewState
    var restoreGalleryAfterMediaPreview by rememberSaveable { mutableStateOf(false) }
    var restoreGalleryAfterAttachmentAction by rememberSaveable { mutableStateOf(false) }
    val setMediaPreviewState: (ThreadMediaPreviewState) -> Unit = { nextState ->
        val isDismissingPreview = mediaPreviewState.previewMediaIndex != null && nextState.previewMediaIndex == null
        mediaPreviewState = nextState
        if (isDismissingPreview && restoreGalleryAfterMediaPreview) {
            restoreGalleryAfterMediaPreview = false
            modalOverlayState = openThreadGalleryOverlay(modalOverlayState)
        }
    }
    LaunchedEffect(derivedUiState.currentPosts) {
        if (mediaPreviewCollectionPosts !== null && mediaPreviewCollectionPosts !== derivedUiState.currentPosts) {
            mediaPreviewCollectionPosts = null
            mediaPreviewCollection = MediaPreviewCollection(
                entries = emptyList(),
                indexByKey = emptyMap()
            )
            setMediaPreviewState(normalizeThreadMediaPreviewState(mediaPreviewState, totalCount = 0))
        }
    }

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
        lastAutoSaveTimestampMillis = lastAutoSaveTimestamp.value,
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
    val watchReadAloudPlaybackState = when (readAloudStatus) {
        is ReadAloudStatus.Speaking -> WatchReadAloudPlaybackState.Speaking
        is ReadAloudStatus.Paused -> WatchReadAloudPlaybackState.Paused
        ReadAloudStatus.Idle -> null
    }
    val watchReadAloudProgressBucket = remember(
        watchReadAloudPlaybackState,
        currentReadAloudIndex,
        readAloudSegments.size
    ) {
        resolveWatchReadAloudProgressUpdateBucket(
            playbackState = watchReadAloudPlaybackState,
            currentIndex = currentReadAloudIndex,
            totalPosts = readAloudSegments.size
        )
    }
    LaunchedEffect(
        board.id,
        effectiveBoardUrl,
        threadId,
        watchReadAloudPlaybackState,
        watchReadAloudProgressBucket,
        readAloudSegments.size
    ) {
        if (watchReadAloudPlaybackState == null) {
            WatchReadAloudStatusStore.clearIfMatches(
                boardId = board.id,
                boardUrl = effectiveBoardUrl,
                threadId = threadId
            )
            return@LaunchedEffect
        }
        val segment = when (val status = readAloudStatus) {
            is ReadAloudStatus.Speaking -> status.segment
            is ReadAloudStatus.Paused -> status.segment
            ReadAloudStatus.Idle -> null
        }
        WatchReadAloudStatusStore.update(
            WatchReadAloudStatus(
                boardId = board.id,
                boardUrl = effectiveBoardUrl,
                threadId = threadId,
                state = watchReadAloudPlaybackState,
                postId = segment?.postId,
                currentIndex = currentReadAloudIndex.coerceAtLeast(0),
                totalPosts = readAloudSegments.size,
                updatedAtMillis = Clock.System.now().toEpochMilliseconds()
            )
        )
    }
    DisposableEffect(board.id, effectiveBoardUrl, threadId) {
        onDispose {
            WatchReadAloudStatusStore.clearIfMatches(
                boardId = board.id,
                boardUrl = effectiveBoardUrl,
                threadId = threadId
            )
        }
    }

    val density = LocalDensity.current
    val backSwipeMetrics = rememberThreadBackSwipeMetrics(density)
    val backSwipeEdgePx = backSwipeMetrics.edgePx
    val backSwipeTriggerPx = backSwipeMetrics.triggerPx
    val firstVisibleSegmentIndex = derivedRuntimeState.firstVisibleSegmentIndex

    val currentPage = derivedUiState.currentPage
    val searchMatches = derivedRuntimeState.searchMatches
    val postHighlightRanges = derivedRuntimeState.postHighlightRanges
    var searchScrollJob by remember(threadId) { mutableStateOf<Job?>(null) }
    val initialScrollRestoreState = rememberThreadInitialScrollRestoreState(
        hasRestoredInitialScroll,
        initialHistoryEntry,
        countThreadContentItems(
            page = currentPage,
            embeddedHtml = currentSuccessState?.embeddedHtml.orEmpty(),
            hasSummary = isThreadSummaryFeatureEnabled(preferencesState),
            hasAiPostModeration = isAiPostFilterFeatureEnabled(preferencesState)
        ).takeIf { it > 0 }
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
        searchQuery = debouncedThreadSearchQuery,
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
            setMediaPreviewState = setMediaPreviewState,
            mediaPreviewEntries = { mediaPreviewEntries },
            ensureMediaPreviewCollection = ensureMediaPreviewCollection,
            actionStateBindings = actionStateBindings,
            actionDependencies = actionDependencies,
            historyRefreshStateBindings = historyRefreshStateBindings,
            onHistoryRefresh = onHistoryRefresh,
            onHistoryExport = onHistoryExport,
            onHistoryExportThenClear = onHistoryExportThenClear,
            onHistoryExportSelected = onHistoryExportSelected,
            onHistoryLoadImportPreview = onHistoryLoadImportPreview,
            onHistoryImport = onHistoryImport,
            onHistoryImportSelected = onHistoryImportSelected,
            readAloudStateBindings = readAloudStateBindings,
            readAloudCallbacks = readAloudCallbacks,
            readAloudDependencies = readAloudDependencies,
            replyDialogBinding = replyDialogBinding,
            currentIsRefreshing = { isRefreshing },
            onScrollToPostIndex = { postIndex ->
                searchScrollJob?.cancel()
                searchScrollJob = coroutineScope.launch {
                    lazyListState.animateScrollToItem(
                        resolveThreadLazyListIndexForPost(
                            postIndex = postIndex,
                            page = currentSuccessState?.page,
                            embeddedHtml = currentSuccessState?.embeddedHtml.orEmpty(),
                            hasSummary = isThreadSummaryFeatureEnabled(preferencesState)
                        )
                    )
                }
            },
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
            setSearchQuery = { searchQueryState.value = it },
            setSearchActive = { isSearchActive = it },
            singleMediaSaveBindings = singleMediaSaveBindings,
            currentSuccessState = currentSuccessState,
            threadFilterBinding = threadFilterBinding,
            firstVisibleSegmentIndex = firstVisibleSegmentIndex,
            pauseReadAloud = pauseReadAloud,
            stopReadAloud = stopReadAloud,
            threadNgMutationCallbacks = threadNgMutationCallbacks,
            currentManualSaveJob = { manualSaveJob },
            setSaveProgress = { saveProgress = it },
            preferencesCallbacks = preferencesCallbacks,
            cookieRepository = cookieRepository
        )
    )
    val interactionUiHandles = resolveThreadScreenInteractionUiHandles(interactionUiBindings)
    val mediaBindings = interactionUiHandles.mediaBindings
    LaunchedEffect(mediaPreviewEntries.size) {
        mediaBindings.normalizePreviewState()
    }
    val actionBindings = interactionUiHandles.actionBindings
    val handleHistoryRefresh = interactionUiHandles.handleHistoryRefresh
    val startReadAloud = interactionUiHandles.startReadAloud
    val seekReadAloudToIndex = interactionUiHandles.seekReadAloudToIndex
    val handleMenuEntry = interactionUiHandles.handleMenuEntry
    val searchNavigationCallbacks = interactionUiHandles.searchNavigationCallbacks
    val postActionHandlers = interactionUiHandles.postActionHandlers
    val handleSaidaneAction = postActionHandlers.onSaidane
    val handleDelRequest = postActionHandlers.onDelRequest
    val openDeleteDialog = postActionHandlers.onOpenDeleteDialog
    val openQuoteSelection = postActionHandlers.onOpenQuoteSelection
    val handleNgRegistration = postActionHandlers.onNgRegister
    val performRefresh = interactionUiHandles.performRefresh
    LaunchedEffect(args.aiCommand, readAloudSegments.size) {
        val command = args.aiCommand ?: return@LaunchedEffect
        val requiresReadAloudSegments = when (command.action) {
            FutachaAiAction.StartThreadReadAloud,
            FutachaAiAction.NextThreadReadAloud,
            FutachaAiAction.PreviousThreadReadAloud -> true
            else -> false
        }
        if (requiresReadAloudSegments && readAloudSegments.isEmpty()) {
            return@LaunchedEffect
        }
        val shouldConsume = when (command.action) {
            FutachaAiAction.RefreshCurrentThread,
            FutachaAiAction.ScrollThreadToTop,
            FutachaAiAction.ScrollThreadToBottom,
            FutachaAiAction.StartThreadReadAloud,
            FutachaAiAction.PauseThreadReadAloud,
            FutachaAiAction.StopThreadReadAloud,
            FutachaAiAction.NextThreadReadAloud,
            FutachaAiAction.PreviousThreadReadAloud,
            FutachaAiAction.StartThreadSearch,
            FutachaAiAction.SearchThread,
            FutachaAiAction.NextSearchResult,
            FutachaAiAction.PreviousSearchResult,
            FutachaAiAction.OpenHistoryDrawer,
            FutachaAiAction.OpenGallery,
            FutachaAiAction.OpenThreadSettings,
            FutachaAiAction.OpenThreadExternally,
            FutachaAiAction.OpenCookieManagement,
            FutachaAiAction.SaveCurrentThread,
            FutachaAiAction.SaveThread,
            FutachaAiAction.DraftReply -> true
            else -> false
        }
        if (!shouldConsume) {
            return@LaunchedEffect
        }
        args.onAiCommandConsumed(command)
        when (command.action) {
            FutachaAiAction.RefreshCurrentThread -> {
                performRefresh()
            }
            FutachaAiAction.ScrollThreadToTop -> {
                handleMenuEntry(ThreadMenuEntryId.ScrollToTop)
            }
            FutachaAiAction.ScrollThreadToBottom -> {
                handleMenuEntry(ThreadMenuEntryId.ScrollToBottom)
            }
            FutachaAiAction.StartThreadReadAloud -> {
                sheetOverlayState = openThreadReadAloudOverlay(sheetOverlayState)
                startReadAloud()
            }
            FutachaAiAction.PauseThreadReadAloud -> {
                pauseReadAloud()
            }
            FutachaAiAction.StopThreadReadAloud -> {
                stopReadAloud()
                showMessage(buildReadAloudStoppedMessage())
            }
            FutachaAiAction.NextThreadReadAloud -> {
                seekReadAloudToIndex(
                    (currentReadAloudIndex + 1).coerceAtMost(readAloudSegments.lastIndex),
                    true
                )
            }
            FutachaAiAction.PreviousThreadReadAloud -> {
                seekReadAloudToIndex(
                    (currentReadAloudIndex - 1).coerceAtLeast(0),
                    true
                )
            }
            FutachaAiAction.StartThreadSearch -> {
                isSearchActive = true
            }
            FutachaAiAction.SearchThread -> {
                searchQueryState.value = command.searchQueryParameter().orEmpty()
                currentSearchResultIndex = -1
                isSearchActive = true
            }
            FutachaAiAction.NextSearchResult -> {
                isSearchActive = true
                searchNavigationCallbacks.onSearchNext()
            }
            FutachaAiAction.PreviousSearchResult -> {
                isSearchActive = true
                searchNavigationCallbacks.onSearchPrev()
            }
            FutachaAiAction.OpenHistoryDrawer -> {
                drawerState.open()
            }
            FutachaAiAction.OpenGallery -> {
                handleMenuEntry(ThreadMenuEntryId.Gallery)
            }
            FutachaAiAction.OpenThreadSettings -> {
                handleMenuEntry(ThreadMenuEntryId.Settings)
            }
            FutachaAiAction.OpenThreadExternally -> {
                handleMenuEntry(ThreadMenuEntryId.ExternalApp)
            }
            FutachaAiAction.OpenCookieManagement -> {
                if (cookieRepository != null) {
                    modalOverlayState = openThreadCookieManagementOverlay(modalOverlayState)
                }
            }
            FutachaAiAction.SaveCurrentThread,
            FutachaAiAction.SaveThread -> {
                handleMenuEntry(ThreadMenuEntryId.Save)
            }
            FutachaAiAction.DraftReply -> {
                val currentState = replyDialogBinding.currentState()
                val currentDraft = currentState.draft
                replyDialogBinding.setState(
                    openThreadReplyDialog(
                        state = currentState.copy(
                            draft = currentDraft.copy(
                                name = command.draftNameParameter() ?: currentDraft.name,
                                email = command.draftEmailParameter() ?: currentDraft.email,
                                subject = command.draftSubjectParameter() ?: currentDraft.subject,
                                comment = command.draftCommentParameter() ?: currentDraft.comment,
                                password = command.draftPasswordParameter() ?: currentDraft.password
                            )
                        ),
                        lastUsedDeleteKey = lastUsedDeleteKey
                    )
                )
            }
            else -> Unit
        }
    }
    val uiBindings = interactionUiHandles.uiBindings
    val scrollToPost = remember(currentSuccessState, lazyListState, coroutineScope, preferencesState) {
        buildThreadPostIndexAction(
            currentPosts = currentSuccessState?.page?.posts.orEmpty(),
            onScrollToPostIndex = { index ->
                coroutineScope.launch {
                    lazyListState.animateScrollToItem(
                        resolveThreadLazyListIndexForPost(
                            postIndex = index,
                            page = currentSuccessState?.page,
                            embeddedHtml = currentSuccessState?.embeddedHtml.orEmpty(),
                            hasSummary = isThreadSummaryFeatureEnabled(preferencesState)
                        )
                    )
                }
            }
        )
    }
    val openAttachmentActionsForPost = remember(currentSuccessState?.page?.posts, postOverlayState) {
        { post: Post ->
            val target = buildThreadAttachmentActionTarget(post, canJumpToPost = true)
            if (target != null) {
                postOverlayState = openThreadAttachmentActionOverlay(
                    currentState = postOverlayState,
                    target = target
                )
            }
        }
    }
    val onMediaLongPress = remember(postOverlayState) {
        { post: Post, url: String, mediaType: MediaType ->
            val target = buildThreadAttachmentActionTarget(
                post = post,
                preferredUrl = url,
                preferredMediaType = mediaType,
                canJumpToPost = false
            )
            if (target != null) {
                postOverlayState = openThreadAttachmentActionOverlay(
                    currentState = postOverlayState,
                    target = target
                )
            }
        }
    }
    val galleryCallbacks = remember(
        preferencesState.threadGalleryTapAction,
        currentSuccessState?.page?.posts,
        modalOverlayState,
        postOverlayState,
        mediaBindings
    ) {
        val openMediaFromGallery: (Post) -> Unit = { post ->
            buildMediaPreviewEntry(post)?.let { entry ->
                modalOverlayState = dismissThreadGalleryOverlay(modalOverlayState)
                restoreGalleryAfterMediaPreview = true
                mediaBindings.onMediaClick(entry.url, entry.mediaType)
            }
        }
        val primaryAction = when (preferencesState.threadGalleryTapAction) {
            ThreadGalleryTapAction.OpenMedia -> openMediaFromGallery
            ThreadGalleryTapAction.JumpToPost -> scrollToPost
        }
        buildThreadScreenGalleryCallbacks(
            onDismiss = {
                restoreGalleryAfterMediaPreview = false
                restoreGalleryAfterAttachmentAction = false
                modalOverlayState = dismissThreadGalleryOverlay(modalOverlayState)
            },
            onImageClick = primaryAction,
            onImageLongPress = { post ->
                modalOverlayState = dismissThreadGalleryOverlay(modalOverlayState)
                restoreGalleryAfterAttachmentAction = true
                openAttachmentActionsForPost(post)
            },
            onPostClick = { post ->
                restoreGalleryAfterMediaPreview = false
                restoreGalleryAfterAttachmentAction = false
                scrollToPost(post)
            }
        )
    }

    val appColorScheme = MaterialTheme.colorScheme
    val futabaThreadColorScheme = rememberFutabaThreadColorScheme(
        palette = preferencesState.themePalette,
        base = appColorScheme
    )
    val historyDrawerCallbacks = interactionUiHandles.historyDrawerCallbacks
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
            effectiveBoardUrl = effectiveBoardUrl,
            history = history,
            historyDrawerCallbacks = historyDrawerCallbacks,
            galleryCallbacks = galleryCallbacks,
            boardName = board.name,
            resolvedThreadTitle = resolvedThreadTitle,
            resolvedReplyCount = resolvedReplyCount,
            statusLabel = statusLabel,
            isSearchActive = isSearchActive,
            searchQueryState = searchQueryState,
            currentSearchResultIndex = currentSearchResultIndex,
            totalSearchMatches = searchMatches.size,
            uiState = uiState.value,
            refreshThread = refreshThread,
            threadFilterBinding = threadFilterBinding,
            threadDisplayMode = preferencesState.threadDisplayMode,
            persistedSelfPostIdentifiers = persistedSelfPostIdentifiers,
            ngHeaders = ngHeaders,
            ngWords = ngWords,
            ngFilteringEnabled = ngFilteringEnabled,
            threadFilterCache = threadFilterCache,
            postTextCache = postTextCache,
            lazyListState = lazyListState,
            saidaneOverrides = saidaneOverrides,
            selfPostIdentifierSet = selfPostIdentifierSet,
            postHighlightRanges = postHighlightRanges,
            postOverlayState = postOverlayState,
            setPostOverlayState = { postOverlayState = it },
            onSaidaneClick = handleSaidaneAction,
            onMediaClick = mediaBindings.onMediaClick,
            onMediaLongPress = onMediaLongPress,
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
            onDismissAttachmentActionSheet = {
                postOverlayState = dismissThreadAttachmentActionOverlay(postOverlayState)
                if (restoreGalleryAfterAttachmentAction) {
                    restoreGalleryAfterAttachmentAction = false
                    modalOverlayState = openThreadGalleryOverlay(modalOverlayState)
                }
            },
            onPreviewAttachment = { target ->
                postOverlayState = dismissThreadAttachmentActionOverlay(postOverlayState)
                if (restoreGalleryAfterAttachmentAction) {
                    restoreGalleryAfterAttachmentAction = false
                    restoreGalleryAfterMediaPreview = true
                }
                val previewEntry = buildMediaPreviewEntry(target.post)
                if (previewEntry != null) {
                    mediaBindings.onMediaClick(previewEntry.url, previewEntry.mediaType)
                } else {
                    mediaBindings.onMediaClick(target.url, target.mediaType)
                }
            },
            onJumpToAttachmentPost = { target ->
                postOverlayState = dismissThreadAttachmentActionOverlay(postOverlayState)
                restoreGalleryAfterAttachmentAction = false
                restoreGalleryAfterMediaPreview = false
                scrollToPost(target.post)
            },
            onSaveAttachment = { target ->
                postOverlayState = dismissThreadAttachmentActionOverlay(postOverlayState)
                buildMediaPreviewEntry(
                    post = target.post,
                    preferredUrl = target.url,
                    preferredMediaType = target.mediaType
                )?.let(singleMediaSaveBindings.savePreviewMedia)
                if (restoreGalleryAfterAttachmentAction) {
                    restoreGalleryAfterAttachmentAction = false
                    modalOverlayState = openThreadGalleryOverlay(modalOverlayState)
                }
            },
            onOpenAttachmentExternally = { target ->
                postOverlayState = dismissThreadAttachmentActionOverlay(postOverlayState)
                restoreGalleryAfterAttachmentAction = false
                restoreGalleryAfterMediaPreview = false
                handleUrlClick(target.url)
            },
            onQuoteFromActionSheet = openQuoteSelection,
            onNgRegisterFromActionSheet = handleNgRegistration,
            onSaidaneFromActionSheet = handleSaidaneAction,
            onDelRequestFromActionSheet = handleDelRequest,
            onDeleteFromActionSheet = openDeleteDialog,
            onDismissCookieRecoveryGuide = {
                modalOverlayState = dismissThreadCookieRecoveryGuideOverlay(modalOverlayState)
            },
            onOpenCookieManagerFromRecoveryGuide = {
                modalOverlayState = openThreadCookieManagementOverlay(modalOverlayState)
            },
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
                        snackbarHostState = snackbarHostState,
                        onOpenCookieManager = cookieRepository?.let {
                            {
                                modalOverlayState = openThreadCookieRecoveryGuideOverlay(modalOverlayState)
                            }
                        },
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

private fun resolveWatchReadAloudProgressUpdateBucket(
    playbackState: WatchReadAloudPlaybackState?,
    currentIndex: Int,
    totalPosts: Int
): Int {
    if (playbackState == null) return -1
    val normalizedIndex = currentIndex.coerceAtLeast(0)
    val lastIndex = totalPosts - 1
    return when {
        playbackState == WatchReadAloudPlaybackState.Paused -> normalizedIndex
        normalizedIndex == 0 -> 0
        lastIndex >= 0 && normalizedIndex >= lastIndex -> lastIndex
        else -> normalizedIndex / WATCH_READ_ALOUD_PROGRESS_UPDATE_STEP
    }
}
