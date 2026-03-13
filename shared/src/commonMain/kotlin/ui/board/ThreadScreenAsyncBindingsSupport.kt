package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json

internal data class ThreadScreenAsyncBindingsBundle(
    val runnerBindings: ThreadScreenRunnerBindings,
    val saveExecutionBindings: ThreadScreenSaveExecutionBindingsBundle,
    val loadBindings: ThreadScreenLoadBindings
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
