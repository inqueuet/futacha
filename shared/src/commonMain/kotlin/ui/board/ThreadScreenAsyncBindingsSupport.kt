package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json

internal data class ThreadScreenAsyncBindingsBundle(
    val runnerBindings: ThreadScreenRunnerBindings,
    val saveExecutionBindings: ThreadScreenSaveExecutionBindingsBundle,
    val loadBindings: ThreadScreenLoadBindings
)

internal data class ThreadScreenAsyncBindingsInputs(
    val coroutineScope: CoroutineScope,
    val repository: BoardRepository,
    val history: List<ThreadHistoryEntry>,
    val threadId: String,
    val threadTitle: String?,
    val board: BoardSummary,
    val boardUrl: String,
    val effectiveBoardUrl: String,
    val threadUrlOverride: String?,
    val archiveSearchJson: Json,
    val offlineLookupContext: OfflineThreadLookupContext,
    val offlineSources: List<OfflineThreadSource>,
    val currentThreadUrlOverride: () -> String?,
    val httpClient: HttpClient?,
    val fileSystem: FileSystem?,
    val autoSaveRepository: SavedThreadRepository?,
    val manualSaveRepository: SavedThreadRepository?,
    val minAutoSaveIntervalMillis: Long,
    val runtimeBindings: ThreadScreenAsyncRuntimeBindingsBundle,
    val manualSaveDirectory: String,
    val manualSaveLocation: SaveLocation?,
    val resolvedManualSaveDirectory: String?,
    val requiresManualLocationSelection: Boolean,
    val onWarning: (String) -> Unit = {},
    val onInfo: (String) -> Unit = {}
)

internal data class ThreadScreenAsyncRuntimeBindingsBundle(
    val autoSaveStateBindings: ThreadScreenAutoSaveStateBindings,
    val manualSaveStateBindings: ThreadScreenManualSaveStateBindings,
    val manualSaveCallbacks: ThreadScreenManualSaveCallbacks,
    val singleMediaSaveStateBindings: ThreadScreenSingleMediaSaveStateBindings,
    val singleMediaSaveCallbacks: ThreadScreenSingleMediaSaveCallbacks,
    val loadStateBindings: ThreadScreenLoadStateBindings,
    val loadUiCallbacks: ThreadScreenLoadUiCallbacks
)

internal data class ThreadScreenAsyncRuntimeInputs(
    val currentAutoSaveJob: () -> kotlinx.coroutines.Job?,
    val setAutoSaveJob: (kotlinx.coroutines.Job?) -> Unit,
    val currentLastAutoSaveTimestampMillis: () -> Long,
    val setLastAutoSaveTimestampMillis: (Long) -> Unit,
    val currentIsShowingOfflineCopy: () -> Boolean,
    val currentManualSaveJob: () -> kotlinx.coroutines.Job?,
    val setManualSaveJob: (kotlinx.coroutines.Job?) -> Unit,
    val setIsManualSaveInProgress: (Boolean) -> Unit,
    val currentIsManualSaveInProgress: () -> Boolean,
    val currentIsSingleMediaSaveInProgress: () -> Boolean,
    val setSaveProgress: (com.valoser.futacha.shared.model.SaveProgress?) -> Unit,
    val currentUiState: () -> ThreadUiState,
    val showMessage: (String) -> Unit,
    val applySaveErrorState: (ThreadManualSaveErrorState) -> Unit,
    val onOpenSaveDirectoryPicker: (() -> Unit)?,
    val currentSingleMediaSaveJob: () -> kotlinx.coroutines.Job?,
    val setSingleMediaSaveJob: (kotlinx.coroutines.Job?) -> Unit,
    val setIsSingleMediaSaveInProgress: (Boolean) -> Unit,
    val showOptionalMessage: (String?) -> Unit,
    val currentRefreshThreadJob: () -> kotlinx.coroutines.Job?,
    val setRefreshThreadJob: (kotlinx.coroutines.Job?) -> Unit,
    val currentManualRefreshGeneration: () -> Long,
    val setManualRefreshGeneration: (Long) -> Unit,
    val setIsRefreshing: (Boolean) -> Unit,
    val setUiState: (ThreadUiState) -> Unit,
    val setResolvedThreadUrlOverride: (String?) -> Unit,
    val setIsShowingOfflineCopy: (Boolean) -> Unit,
    val onHistoryEntryUpdated: (ThreadHistoryEntry) -> Unit,
    val onRestoreManualRefreshScroll: suspend (ThreadUiState.Success, Int, Int) -> Unit
)

internal fun buildThreadScreenLoadUiCallbacks(
    onUiStateChanged: (ThreadUiState) -> Unit,
    onHistoryEntryUpdated: (ThreadHistoryEntry) -> Unit,
    onShowOptionalMessage: (String?) -> Unit,
    onRestoreManualRefreshScroll: suspend (ThreadUiState.Success, Int, Int) -> Unit
): ThreadScreenLoadUiCallbacks {
    return ThreadScreenLoadUiCallbacks(
        onManualRefreshSuccess = { outcome, savedIndex, savedOffset ->
            val successUiState = outcome.uiState as? ThreadUiState.Success
            val historyEntry = outcome.historyEntry
            if (successUiState != null && historyEntry != null) {
                onUiStateChanged(successUiState)
                onRestoreManualRefreshScroll(successUiState, savedIndex, savedOffset)
                onHistoryEntryUpdated(historyEntry)
                onShowOptionalMessage(outcome.snackbarMessage)
            }
        },
        onManualRefreshFailure = { outcome ->
            onShowOptionalMessage(outcome.snackbarMessage)
        },
        onInitialLoadSuccess = { outcome ->
            outcome.uiState?.let(onUiStateChanged)
            outcome.historyEntry?.let(onHistoryEntryUpdated)
            onShowOptionalMessage(outcome.snackbarMessage)
        },
        onInitialLoadFailure = { outcome ->
            outcome.uiState?.let(onUiStateChanged)
            onShowOptionalMessage(outcome.snackbarMessage)
        }
    )
}

internal fun buildThreadScreenAsyncRuntimeBindingsBundle(
    inputs: ThreadScreenAsyncRuntimeInputs
): ThreadScreenAsyncRuntimeBindingsBundle {
    return buildThreadScreenAsyncRuntimeBindingsBundle(
        currentAutoSaveJob = inputs.currentAutoSaveJob,
        setAutoSaveJob = inputs.setAutoSaveJob,
        currentLastAutoSaveTimestampMillis = inputs.currentLastAutoSaveTimestampMillis,
        setLastAutoSaveTimestampMillis = inputs.setLastAutoSaveTimestampMillis,
        currentIsShowingOfflineCopy = inputs.currentIsShowingOfflineCopy,
        currentManualSaveJob = inputs.currentManualSaveJob,
        setManualSaveJob = inputs.setManualSaveJob,
        setIsManualSaveInProgress = inputs.setIsManualSaveInProgress,
        currentIsManualSaveInProgress = inputs.currentIsManualSaveInProgress,
        currentIsSingleMediaSaveInProgress = inputs.currentIsSingleMediaSaveInProgress,
        setSaveProgress = inputs.setSaveProgress,
        currentUiState = inputs.currentUiState,
        showMessage = inputs.showMessage,
        applySaveErrorState = inputs.applySaveErrorState,
        onOpenSaveDirectoryPicker = inputs.onOpenSaveDirectoryPicker,
        currentSingleMediaSaveJob = inputs.currentSingleMediaSaveJob,
        setSingleMediaSaveJob = inputs.setSingleMediaSaveJob,
        setIsSingleMediaSaveInProgress = inputs.setIsSingleMediaSaveInProgress,
        showOptionalMessage = inputs.showOptionalMessage,
        currentRefreshThreadJob = inputs.currentRefreshThreadJob,
        setRefreshThreadJob = inputs.setRefreshThreadJob,
        currentManualRefreshGeneration = inputs.currentManualRefreshGeneration,
        setManualRefreshGeneration = inputs.setManualRefreshGeneration,
        setIsRefreshing = inputs.setIsRefreshing,
        setUiState = inputs.setUiState,
        setResolvedThreadUrlOverride = inputs.setResolvedThreadUrlOverride,
        setIsShowingOfflineCopy = inputs.setIsShowingOfflineCopy,
        onHistoryEntryUpdated = inputs.onHistoryEntryUpdated,
        onRestoreManualRefreshScroll = inputs.onRestoreManualRefreshScroll
    )
}

internal fun buildThreadScreenAsyncRuntimeBindingsBundle(
    currentAutoSaveJob: () -> kotlinx.coroutines.Job?,
    setAutoSaveJob: (kotlinx.coroutines.Job?) -> Unit,
    currentLastAutoSaveTimestampMillis: () -> Long,
    setLastAutoSaveTimestampMillis: (Long) -> Unit,
    currentIsShowingOfflineCopy: () -> Boolean,
    currentManualSaveJob: () -> kotlinx.coroutines.Job?,
    setManualSaveJob: (kotlinx.coroutines.Job?) -> Unit,
    setIsManualSaveInProgress: (Boolean) -> Unit,
    currentIsManualSaveInProgress: () -> Boolean,
    currentIsSingleMediaSaveInProgress: () -> Boolean,
    setSaveProgress: (com.valoser.futacha.shared.model.SaveProgress?) -> Unit,
    currentUiState: () -> ThreadUiState,
    showMessage: (String) -> Unit,
    applySaveErrorState: (ThreadManualSaveErrorState) -> Unit,
    onOpenSaveDirectoryPicker: (() -> Unit)?,
    currentSingleMediaSaveJob: () -> kotlinx.coroutines.Job?,
    setSingleMediaSaveJob: (kotlinx.coroutines.Job?) -> Unit,
    setIsSingleMediaSaveInProgress: (Boolean) -> Unit,
    showOptionalMessage: (String?) -> Unit,
    currentRefreshThreadJob: () -> kotlinx.coroutines.Job?,
    setRefreshThreadJob: (kotlinx.coroutines.Job?) -> Unit,
    currentManualRefreshGeneration: () -> Long,
    setManualRefreshGeneration: (Long) -> Unit,
    setIsRefreshing: (Boolean) -> Unit,
    setUiState: (ThreadUiState) -> Unit,
    setResolvedThreadUrlOverride: (String?) -> Unit,
    setIsShowingOfflineCopy: (Boolean) -> Unit,
    onHistoryEntryUpdated: (ThreadHistoryEntry) -> Unit,
    onRestoreManualRefreshScroll: suspend (ThreadUiState.Success, Int, Int) -> Unit
): ThreadScreenAsyncRuntimeBindingsBundle {
    return ThreadScreenAsyncRuntimeBindingsBundle(
        autoSaveStateBindings = ThreadScreenAutoSaveStateBindings(
            currentAutoSaveJob = currentAutoSaveJob,
            setAutoSaveJob = setAutoSaveJob,
            currentLastAutoSaveTimestampMillis = currentLastAutoSaveTimestampMillis,
            setLastAutoSaveTimestampMillis = setLastAutoSaveTimestampMillis,
            currentIsShowingOfflineCopy = currentIsShowingOfflineCopy
        ),
        manualSaveStateBindings = ThreadScreenManualSaveStateBindings(
            currentManualSaveJob = currentManualSaveJob,
            setManualSaveJob = setManualSaveJob,
            setIsManualSaveInProgress = setIsManualSaveInProgress,
            currentIsManualSaveInProgress = currentIsManualSaveInProgress,
            currentIsSingleMediaSaveInProgress = currentIsSingleMediaSaveInProgress,
            setSaveProgress = setSaveProgress,
            currentUiState = currentUiState
        ),
        manualSaveCallbacks = ThreadScreenManualSaveCallbacks(
            showMessage = showMessage,
            applySaveErrorState = applySaveErrorState,
            openSaveDirectoryPicker = onOpenSaveDirectoryPicker
        ),
        singleMediaSaveStateBindings = ThreadScreenSingleMediaSaveStateBindings(
            currentSingleMediaSaveJob = currentSingleMediaSaveJob,
            setSingleMediaSaveJob = setSingleMediaSaveJob,
            setIsSingleMediaSaveInProgress = setIsSingleMediaSaveInProgress,
            currentIsManualSaveInProgress = currentIsManualSaveInProgress,
            currentIsSingleMediaSaveInProgress = currentIsSingleMediaSaveInProgress
        ),
        singleMediaSaveCallbacks = ThreadScreenSingleMediaSaveCallbacks(
            showOptionalMessage = showOptionalMessage,
            applySaveErrorState = applySaveErrorState,
            showMessage = showMessage
        ),
        loadStateBindings = ThreadScreenLoadStateBindings(
            currentRefreshThreadJob = currentRefreshThreadJob,
            setRefreshThreadJob = setRefreshThreadJob,
            currentManualRefreshGeneration = currentManualRefreshGeneration,
            setManualRefreshGeneration = setManualRefreshGeneration,
            setIsRefreshing = setIsRefreshing,
            setUiState = setUiState,
            setResolvedThreadUrlOverride = setResolvedThreadUrlOverride,
            setIsShowingOfflineCopy = setIsShowingOfflineCopy
        ),
        loadUiCallbacks = buildThreadScreenLoadUiCallbacks(
            onUiStateChanged = setUiState,
            onHistoryEntryUpdated = onHistoryEntryUpdated,
            onShowOptionalMessage = showOptionalMessage,
            onRestoreManualRefreshScroll = onRestoreManualRefreshScroll
        )
    )
}

internal fun buildThreadScreenAsyncBindingsBundle(
    inputs: ThreadScreenAsyncBindingsInputs
): ThreadScreenAsyncBindingsBundle {
    return buildThreadScreenAsyncBindingsBundle(
        coroutineScope = inputs.coroutineScope,
        repository = inputs.repository,
        history = inputs.history,
        threadId = inputs.threadId,
        threadTitle = inputs.threadTitle,
        board = inputs.board,
        boardUrl = inputs.boardUrl,
        effectiveBoardUrl = inputs.effectiveBoardUrl,
        threadUrlOverride = inputs.threadUrlOverride,
        archiveSearchJson = inputs.archiveSearchJson,
        offlineLookupContext = inputs.offlineLookupContext,
        offlineSources = inputs.offlineSources,
        currentThreadUrlOverride = inputs.currentThreadUrlOverride,
        httpClient = inputs.httpClient,
        fileSystem = inputs.fileSystem,
        autoSaveRepository = inputs.autoSaveRepository,
        manualSaveRepository = inputs.manualSaveRepository,
        minAutoSaveIntervalMillis = inputs.minAutoSaveIntervalMillis,
        autoSaveStateBindings = inputs.runtimeBindings.autoSaveStateBindings,
        manualSaveStateBindings = inputs.runtimeBindings.manualSaveStateBindings,
        manualSaveDirectory = inputs.manualSaveDirectory,
        manualSaveLocation = inputs.manualSaveLocation,
        resolvedManualSaveDirectory = inputs.resolvedManualSaveDirectory,
        requiresManualLocationSelection = inputs.requiresManualLocationSelection,
        manualSaveCallbacks = inputs.runtimeBindings.manualSaveCallbacks,
        singleMediaSaveStateBindings = inputs.runtimeBindings.singleMediaSaveStateBindings,
        singleMediaSaveCallbacks = inputs.runtimeBindings.singleMediaSaveCallbacks,
        loadStateBindings = inputs.runtimeBindings.loadStateBindings,
        loadUiCallbacks = inputs.runtimeBindings.loadUiCallbacks,
        onWarning = inputs.onWarning,
        onInfo = inputs.onInfo
    )
}

internal fun buildThreadScreenAsyncBindingsBundle(
    coroutineScope: CoroutineScope,
    repository: BoardRepository,
    history: List<ThreadHistoryEntry>,
    threadId: String,
    threadTitle: String?,
    board: BoardSummary,
    boardUrl: String,
    effectiveBoardUrl: String,
    threadUrlOverride: String?,
    archiveSearchJson: Json,
    offlineLookupContext: OfflineThreadLookupContext,
    offlineSources: List<OfflineThreadSource>,
    currentThreadUrlOverride: () -> String?,
    httpClient: HttpClient?,
    fileSystem: FileSystem?,
    autoSaveRepository: SavedThreadRepository?,
    manualSaveRepository: SavedThreadRepository?,
    minAutoSaveIntervalMillis: Long,
    autoSaveStateBindings: ThreadScreenAutoSaveStateBindings,
    manualSaveStateBindings: ThreadScreenManualSaveStateBindings,
    manualSaveDirectory: String,
    manualSaveLocation: SaveLocation?,
    resolvedManualSaveDirectory: String?,
    requiresManualLocationSelection: Boolean,
    manualSaveCallbacks: ThreadScreenManualSaveCallbacks,
    singleMediaSaveStateBindings: ThreadScreenSingleMediaSaveStateBindings,
    singleMediaSaveCallbacks: ThreadScreenSingleMediaSaveCallbacks,
    loadStateBindings: ThreadScreenLoadStateBindings,
    loadUiCallbacks: ThreadScreenLoadUiCallbacks,
    onWarning: (String) -> Unit = {},
    onInfo: (String) -> Unit = {}
): ThreadScreenAsyncBindingsBundle {
    val runnerBindings = buildThreadScreenRunnerBindings(
        repository = repository,
        httpClient = httpClient,
        fileSystem = fileSystem,
        threadId = threadId,
        threadTitle = threadTitle,
        boardUrl = boardUrl,
        effectiveBoardUrl = effectiveBoardUrl,
        threadUrlOverride = threadUrlOverride,
        archiveSearchJson = archiveSearchJson,
        offlineLookupContext = offlineLookupContext,
        offlineSources = offlineSources,
        currentThreadUrlOverride = currentThreadUrlOverride,
        onWarning = onWarning,
        onInfo = onInfo
    )
    val saveExecutionBindings = buildThreadScreenSaveExecutionBindingsBundle(
        coroutineScope = coroutineScope,
        minIntervalMillis = minAutoSaveIntervalMillis,
        autoSaveStateBindings = autoSaveStateBindings,
        autoSaveDependencies = ThreadScreenAutoSaveDependencies(
            autoSaveRepository = autoSaveRepository,
            httpClient = httpClient,
            fileSystem = fileSystem,
            threadId = threadId,
            threadTitle = threadTitle,
            board = board,
            effectiveBoardUrl = effectiveBoardUrl
        ),
        manualSaveStateBindings = manualSaveStateBindings,
        manualSaveDependencies = ThreadScreenManualSaveDependencies(
            manualSaveRepository = manualSaveRepository,
            httpClient = httpClient,
            fileSystem = fileSystem,
            threadId = threadId,
            threadTitle = threadTitle,
            board = board,
            effectiveBoardUrl = effectiveBoardUrl,
            manualSaveDirectory = manualSaveDirectory,
            manualSaveLocation = manualSaveLocation,
            resolvedManualSaveDirectory = resolvedManualSaveDirectory,
            requiresManualLocationSelection = requiresManualLocationSelection
        ),
        manualSaveCallbacks = manualSaveCallbacks,
        singleMediaSaveStateBindings = singleMediaSaveStateBindings,
        singleMediaSaveDependencies = ThreadScreenSingleMediaSaveDependencies(
            saveRunnerCallbacks = runnerBindings.singleMediaSaveRunnerCallbacks,
            boardId = board.id,
            threadId = threadId,
            manualSaveLocation = manualSaveLocation,
            manualSaveDirectory = manualSaveDirectory,
            resolvedManualSaveDirectory = resolvedManualSaveDirectory,
            requiresManualLocationSelection = requiresManualLocationSelection,
            hasStorageDependencies = httpClient != null && fileSystem != null
        ),
        singleMediaSaveCallbacks = singleMediaSaveCallbacks
    )
    return ThreadScreenAsyncBindingsBundle(
        runnerBindings = runnerBindings,
        saveExecutionBindings = saveExecutionBindings,
        loadBindings = buildThreadScreenLoadBindings(
            coroutineScope = coroutineScope,
            loadRunnerConfig = runnerBindings.loadRunnerConfig,
            loadRunnerCallbacks = runnerBindings.loadRunnerCallbacks,
            history = history,
            threadId = threadId,
            threadTitle = threadTitle,
            board = board,
            stateBindings = loadStateBindings,
            uiCallbacks = loadUiCallbacks
        )
    )
}

@Composable
internal fun rememberThreadScreenAsyncBindingsBundle(
    coroutineScope: CoroutineScope,
    activeRepository: BoardRepository,
    history: List<ThreadHistoryEntry>,
    threadId: String,
    threadTitle: String?,
    board: BoardSummary,
    effectiveBoardUrl: String,
    resolvedThreadUrlOverride: String?,
    archiveSearchJson: Json,
    offlineLookupContext: OfflineThreadLookupContext,
    offlineSources: List<OfflineThreadSource>,
    httpClient: HttpClient?,
    fileSystem: FileSystem?,
    autoSaveRepository: SavedThreadRepository?,
    manualSaveRepository: SavedThreadRepository?,
    asyncRuntimeBindingsBundle: ThreadScreenAsyncRuntimeBindingsBundle,
    preferencesState: ScreenPreferencesState,
    isAndroidPlatform: Boolean
): ThreadScreenAsyncBindingsBundle {
    return remember(
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
        preferencesState.manualSaveDirectory,
        preferencesState.manualSaveLocation,
        preferencesState.resolvedManualSaveDirectory
    ) {
        buildThreadScreenAsyncBindingsBundle(
            ThreadScreenAsyncBindingsInputs(
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
                runtimeBindings = asyncRuntimeBindingsBundle,
                manualSaveDirectory = preferencesState.manualSaveDirectory,
                manualSaveLocation = preferencesState.manualSaveLocation,
                resolvedManualSaveDirectory = preferencesState.resolvedManualSaveDirectory,
                requiresManualLocationSelection = requiresThreadManualSaveLocationSelection(
                    isAndroidPlatform,
                    preferencesState.manualSaveLocation
                ),
                onWarning = { message ->
                    Logger.w(THREAD_SCREEN_TAG, message)
                },
                onInfo = { message ->
                    Logger.i(THREAD_SCREEN_TAG, message)
                }
            )
        )
    }
}
