package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SaveProgress
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.ThreadSaveService
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.resolveThreadTitle
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.time.Clock

internal data class ThreadScreenAutoSaveStateBindings(
    val currentAutoSaveJob: () -> Job?,
    val setAutoSaveJob: (Job?) -> Unit,
    val currentLastAutoSaveTimestampMillis: () -> Long,
    val setLastAutoSaveTimestampMillis: (Long) -> Unit,
    val currentIsShowingOfflineCopy: () -> Boolean
)

internal data class ThreadScreenAutoSaveDependencies(
    val autoSaveRepository: SavedThreadRepository?,
    val httpClient: HttpClient?,
    val fileSystem: FileSystem?,
    val threadId: String,
    val threadTitle: String?,
    val board: BoardSummary,
    val effectiveBoardUrl: String,
    val currentTimeMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    val buildSaveRuntime: (HttpClient, FileSystem) -> ThreadSaveRuntime = ::buildThreadSaveRuntime,
    val performAutoSave: suspend (ThreadAutoSaveRunnerConfig, ThreadAutoSaveRunnerCallbacks) -> ThreadAutoSaveRunResult = ::performThreadAutoSave,
    val indexSavedThread: suspend (SavedThreadRepository?, SavedThread?, String?) -> Unit = { repository, savedThread, failureMessage ->
        indexSavedThreadOrLog(repository, savedThread, THREAD_AUTO_SAVE_TAG, failureMessage)
    },
    val onFailureLog: (String, Throwable) -> Unit = { message, failure ->
        com.valoser.futacha.shared.util.Logger.e(THREAD_AUTO_SAVE_TAG, message, failure)
    }
)

internal data class ThreadScreenAutoSaveBindings(
    val startAutoSave: (ThreadPage) -> Unit
)

internal data class ThreadScreenSaveExecutionBindingsBundle(
    val autoSaveBindings: ThreadScreenAutoSaveBindings,
    val manualSaveBindings: ThreadScreenManualSaveBindings,
    val singleMediaSaveBindings: ThreadScreenSingleMediaSaveBindings
)

internal data class ThreadScreenManualSaveStateBindings(
    val currentManualSaveJob: () -> Job?,
    val setManualSaveJob: (Job?) -> Unit,
    val setIsManualSaveInProgress: (Boolean) -> Unit,
    val currentIsManualSaveInProgress: () -> Boolean,
    val currentIsSingleMediaSaveInProgress: () -> Boolean,
    val setSaveProgress: (SaveProgress?) -> Unit,
    val currentUiState: () -> ThreadUiState
)

internal data class ThreadScreenManualSaveDependencies(
    val manualSaveRepository: SavedThreadRepository?,
    val httpClient: HttpClient?,
    val fileSystem: FileSystem?,
    val threadId: String,
    val threadTitle: String?,
    val board: BoardSummary,
    val effectiveBoardUrl: String,
    val manualSaveDirectory: String,
    val manualSaveLocation: SaveLocation?,
    val resolvedManualSaveDirectory: String?,
    val requiresManualLocationSelection: Boolean,
    val buildSaveRuntime: (HttpClient, FileSystem) -> ThreadSaveRuntime = ::buildThreadSaveRuntime,
    val launchProgressCollector: CoroutineScope.(ThreadSaveService, (SaveProgress?) -> Unit) -> Job = { saveService, onProgress ->
        launchThreadSaveProgressCollector(saveService, onProgress)
    },
    val performManualSave: suspend (ThreadManualSaveRunnerConfig, ThreadManualSaveRunnerCallbacks) -> ThreadManualSaveRunResult = ::performThreadManualSave,
    val indexSavedThread: suspend (SavedThreadRepository?, SavedThread?, String?) -> Unit = { repository, savedThread, failureMessage ->
        indexSavedThreadOrLog(repository, savedThread, THREAD_AUTO_SAVE_TAG, failureMessage)
    }
)

internal data class ThreadScreenManualSaveCallbacks(
    val showMessage: (String) -> Unit,
    val applySaveErrorState: (ThreadManualSaveErrorState) -> Unit,
    val openSaveDirectoryPicker: (() -> Unit)? = null
)

internal data class ThreadScreenManualSaveBindings(
    val handleThreadSaveRequest: () -> Unit
)

internal fun buildThreadScreenManualSaveBindings(
    coroutineScope: CoroutineScope,
    stateBindings: ThreadScreenManualSaveStateBindings,
    dependencies: ThreadScreenManualSaveDependencies,
    callbacks: ThreadScreenManualSaveCallbacks
): ThreadScreenManualSaveBindings {
    return ThreadScreenManualSaveBindings(
        handleThreadSaveRequest = save@{
            when (resolveThreadSaveAvailability(
                isAnySaveInProgress = stateBindings.currentIsManualSaveInProgress() || stateBindings.currentIsSingleMediaSaveInProgress(),
                requiresManualLocationSelection = dependencies.requiresManualLocationSelection,
                hasStorageDependencies = dependencies.httpClient != null && dependencies.fileSystem != null,
                isThreadReady = stateBindings.currentUiState() is ThreadUiState.Success
            )) {
                ThreadSaveAvailability.Busy -> {
                    callbacks.showMessage(buildThreadSaveBusyMessage())
                    return@save
                }
                ThreadSaveAvailability.LocationRequired -> {
                    callbacks.showMessage(buildThreadSaveLocationRequiredMessage())
                    callbacks.openSaveDirectoryPicker?.invoke()
                    return@save
                }
                ThreadSaveAvailability.Unavailable -> {
                    callbacks.showMessage(buildThreadSaveUnavailableMessage())
                    return@save
                }
                ThreadSaveAvailability.NotReady -> {
                    callbacks.showMessage(buildThreadSaveNotReadyMessage())
                    return@save
                }
                ThreadSaveAvailability.Ready -> Unit
            }

            val currentState = stateBindings.currentUiState() as? ThreadUiState.Success ?: run {
                callbacks.showMessage(buildThreadSaveNotReadyMessage())
                return@save
            }
            val client = dependencies.httpClient ?: run {
                callbacks.showMessage(buildThreadSaveUnavailableMessage())
                return@save
            }
            val localFileSystem = dependencies.fileSystem ?: run {
                callbacks.showMessage(buildThreadSaveUnavailableMessage())
                return@save
            }

            stateBindings.setIsManualSaveInProgress(true)
            val nextJob = coroutineScope.launch(start = CoroutineStart.LAZY) {
                val runningJob = coroutineContext[Job]
                var progressJob: Job? = null
                try {
                    val saveRuntime = dependencies.buildSaveRuntime(client, localFileSystem)
                    progressJob = dependencies.launchProgressCollector(
                        this,
                        saveRuntime.saveService
                    ) { progress ->
                        stateBindings.setSaveProgress(progress)
                    }
                    val page = currentState.page
                    val resolvedTitle = resolveThreadTitle(page.posts.firstOrNull(), dependencies.threadTitle)
                    when (
                        val saveResult = dependencies.performManualSave(
                            buildThreadManualSaveRunnerConfig(
                                threadId = dependencies.threadId,
                                boardId = dependencies.board.id,
                                boardName = dependencies.board.name,
                                boardUrl = dependencies.effectiveBoardUrl,
                                title = resolvedTitle,
                                expiresAtLabel = page.expiresAtLabel,
                                posts = page.posts,
                                baseSaveLocation = dependencies.manualSaveLocation,
                                baseDirectory = dependencies.manualSaveDirectory
                            ),
                            saveRuntime.manualCallbacks
                        )
                    ) {
                        is ThreadManualSaveRunResult.Success,
                        is ThreadManualSaveRunResult.Failure -> when (
                            val outcome = resolveThreadManualSaveUiOutcome(
                                saveResult = saveResult,
                                threadId = dependencies.threadId,
                                manualSaveDirectory = dependencies.manualSaveDirectory,
                                manualSaveLocation = dependencies.manualSaveLocation,
                                resolvedManualSaveDirectory = dependencies.resolvedManualSaveDirectory
                            )
                        ) {
                            is ThreadManualSaveUiOutcome.Success -> {
                                dependencies.indexSavedThread(
                                    dependencies.manualSaveRepository,
                                    outcome.savedThread,
                                    outcome.indexFailureMessage
                                )
                                stateBindings.setSaveProgress(null)
                                callbacks.showMessage(outcome.successState.message)
                            }
                            is ThreadManualSaveUiOutcome.Failure -> {
                                stateBindings.setSaveProgress(null)
                                callbacks.applySaveErrorState(outcome.errorState)
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } finally {
                    progressJob?.cancel()
                    stateBindings.setSaveProgress(null)
                    stateBindings.setIsManualSaveInProgress(false)
                    stateBindings.setManualSaveJob(
                        resolveTrackedJobAfterCompletion(
                            trackedJob = stateBindings.currentManualSaveJob(),
                            runningJob = runningJob
                        )
                    )
                }
            }
            stateBindings.setManualSaveJob(nextJob)
            nextJob.start()
        }
    )
}

internal data class ThreadScreenSingleMediaSaveStateBindings(
    val currentSingleMediaSaveJob: () -> Job?,
    val setSingleMediaSaveJob: (Job?) -> Unit,
    val setIsSingleMediaSaveInProgress: (Boolean) -> Unit,
    val currentIsManualSaveInProgress: () -> Boolean,
    val currentIsSingleMediaSaveInProgress: () -> Boolean
)

internal data class ThreadScreenSingleMediaSaveDependencies(
    val saveRunnerCallbacks: ThreadSingleMediaSaveRunnerCallbacks?,
    val boardId: String,
    val threadId: String,
    val manualSaveLocation: SaveLocation?,
    val manualSaveDirectory: String,
    val resolvedManualSaveDirectory: String?,
    val requiresManualLocationSelection: Boolean,
    val hasStorageDependencies: Boolean,
    val onOpenSaveDirectoryPicker: (() -> Unit)? = null,
    val performSingleMediaSave: suspend (ThreadSingleMediaSaveRunnerConfig, ThreadSingleMediaSaveRunnerCallbacks) -> ThreadSingleMediaSaveRunResult = ::performThreadSingleMediaSave
)

internal data class ThreadScreenSingleMediaSaveCallbacks(
    val showOptionalMessage: (String?) -> Unit,
    val applySaveErrorState: (ThreadManualSaveErrorState) -> Unit,
    val showMessage: (String) -> Unit
)

internal data class ThreadScreenSingleMediaSaveBindings(
    val savePreviewMedia: (MediaPreviewEntry) -> Unit
)

internal fun buildThreadScreenSingleMediaSaveBindings(
    coroutineScope: CoroutineScope,
    stateBindings: ThreadScreenSingleMediaSaveStateBindings,
    dependencies: ThreadScreenSingleMediaSaveDependencies,
    callbacks: ThreadScreenSingleMediaSaveCallbacks
): ThreadScreenSingleMediaSaveBindings {
    return ThreadScreenSingleMediaSaveBindings(
        savePreviewMedia = savePreviewMedia@{ entry ->
            val saveRequestState = resolveThreadMediaSaveRequestState(
                isAnySaveInProgress = stateBindings.currentIsManualSaveInProgress() || stateBindings.currentIsSingleMediaSaveInProgress(),
                isRemoteMedia = isRemoteMediaUrl(entry.url),
                requiresManualLocationSelection = dependencies.requiresManualLocationSelection,
                hasStorageDependencies = dependencies.hasStorageDependencies
            )
            if (!saveRequestState.canStartSave) {
                callbacks.showOptionalMessage(saveRequestState.message)
                if (saveRequestState.shouldOpenSaveDirectoryPicker) {
                    dependencies.onOpenSaveDirectoryPicker?.invoke()
                }
                return@savePreviewMedia
            }

            val saveRunnerCallbacks = dependencies.saveRunnerCallbacks ?: return@savePreviewMedia
            stateBindings.setIsSingleMediaSaveInProgress(true)
            stateBindings.currentSingleMediaSaveJob()?.cancel()
            val nextJob = coroutineScope.launch(start = CoroutineStart.LAZY) {
                try {
                    when (
                        val outcome = resolveThreadSingleMediaSaveUiOutcome(
                            saveResult = dependencies.performSingleMediaSave(
                                buildThreadSingleMediaSaveRunnerConfig(
                                    mediaUrl = entry.url,
                                    boardId = dependencies.boardId,
                                    threadId = dependencies.threadId,
                                    baseSaveLocation = dependencies.manualSaveLocation,
                                    baseDirectory = dependencies.manualSaveDirectory
                                ),
                                saveRunnerCallbacks
                            ),
                            manualSaveDirectory = dependencies.manualSaveDirectory,
                            manualSaveLocation = dependencies.manualSaveLocation,
                            resolvedManualSaveDirectory = dependencies.resolvedManualSaveDirectory
                        )
                    ) {
                        is ThreadSingleMediaSaveUiOutcome.Success -> {
                            callbacks.showMessage(outcome.successState.message)
                        }
                        is ThreadSingleMediaSaveUiOutcome.Failure -> {
                            callbacks.applySaveErrorState(outcome.errorState)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } finally {
                    stateBindings.setIsSingleMediaSaveInProgress(false)
                    stateBindings.setSingleMediaSaveJob(
                        resolveTrackedJobAfterCompletion(
                            trackedJob = stateBindings.currentSingleMediaSaveJob(),
                            runningJob = coroutineContext[Job]
                        )
                    )
                }
            }
            stateBindings.setSingleMediaSaveJob(nextJob)
            nextJob.start()
        }
    )
}

internal fun buildThreadScreenAutoSaveBindings(
    coroutineScope: CoroutineScope,
    minIntervalMillis: Long,
    stateBindings: ThreadScreenAutoSaveStateBindings,
    dependencies: ThreadScreenAutoSaveDependencies
): ThreadScreenAutoSaveBindings {
    return ThreadScreenAutoSaveBindings(
        startAutoSave = start@{ page ->
            val repository = dependencies.autoSaveRepository ?: return@start
            val client = dependencies.httpClient ?: return@start
            val localFileSystem = dependencies.fileSystem ?: return@start
            val now = dependencies.currentTimeMillis()
            if (
                resolveThreadAutoSaveAvailability(
                    pageThreadId = page.threadId,
                    expectedThreadId = dependencies.threadId,
                    isShowingOfflineCopy = stateBindings.currentIsShowingOfflineCopy(),
                    hasAutoSaveRepository = true,
                    hasHttpClient = true,
                    hasFileSystem = true,
                    isAutoSaveInProgress = stateBindings.currentAutoSaveJob()?.isActive == true,
                    lastAutoSaveTimestampMillis = stateBindings.currentLastAutoSaveTimestampMillis(),
                    nowMillis = now,
                    minIntervalMillis = minIntervalMillis
                ) != ThreadAutoSaveAvailability.Ready
            ) {
                return@start
            }
            val nextJob = coroutineScope.launch(start = CoroutineStart.LAZY) {
                val runningJob = coroutineContext[Job]
                val attemptStartedAt = dependencies.currentTimeMillis()
                try {
                    val saveRuntime = dependencies.buildSaveRuntime(client, localFileSystem)
                    val resolvedTitle = resolveThreadTitle(page.posts.firstOrNull(), dependencies.threadTitle)
                    val autoSaveResult = dependencies.performAutoSave(
                        buildThreadAutoSaveRunnerConfig(
                            threadId = dependencies.threadId,
                            boardId = dependencies.board.id,
                            boardName = dependencies.board.name,
                            boardUrl = dependencies.effectiveBoardUrl,
                            title = resolvedTitle,
                            expiresAtLabel = page.expiresAtLabel,
                            posts = page.posts,
                            previousTimestampMillis = stateBindings.currentLastAutoSaveTimestampMillis(),
                            attemptStartedAtMillis = attemptStartedAt,
                            completionTimestampMillis = dependencies.currentTimeMillis()
                        ),
                        saveRuntime.autoCallbacks
                    )
                    val applyState = buildThreadAutoSaveUiApplyState(
                        completionState = autoSaveResult.completionState,
                        threadId = dependencies.threadId
                    )
                    stateBindings.setLastAutoSaveTimestampMillis(applyState.nextTimestampMillis)
                    dependencies.indexSavedThread(
                        repository,
                        applyState.savedThread,
                        applyState.indexFailureMessage
                    )
                    if (applyState.failureMessage != null && applyState.failure != null) {
                        dependencies.onFailureLog(applyState.failureMessage, applyState.failure)
                    }
                } finally {
                    stateBindings.setAutoSaveJob(
                        resolveTrackedJobAfterCompletion(
                            trackedJob = stateBindings.currentAutoSaveJob(),
                            runningJob = runningJob
                        )
                    )
                }
            }
            stateBindings.setAutoSaveJob(nextJob)
            nextJob.start()
        }
    )
}

internal fun buildThreadScreenSaveExecutionBindingsBundle(
    coroutineScope: CoroutineScope,
    minIntervalMillis: Long,
    autoSaveStateBindings: ThreadScreenAutoSaveStateBindings,
    autoSaveDependencies: ThreadScreenAutoSaveDependencies,
    manualSaveStateBindings: ThreadScreenManualSaveStateBindings,
    manualSaveDependencies: ThreadScreenManualSaveDependencies,
    manualSaveCallbacks: ThreadScreenManualSaveCallbacks,
    singleMediaSaveStateBindings: ThreadScreenSingleMediaSaveStateBindings,
    singleMediaSaveDependencies: ThreadScreenSingleMediaSaveDependencies,
    singleMediaSaveCallbacks: ThreadScreenSingleMediaSaveCallbacks
): ThreadScreenSaveExecutionBindingsBundle {
    return ThreadScreenSaveExecutionBindingsBundle(
        autoSaveBindings = buildThreadScreenAutoSaveBindings(
            coroutineScope = coroutineScope,
            minIntervalMillis = minIntervalMillis,
            stateBindings = autoSaveStateBindings,
            dependencies = autoSaveDependencies
        ),
        manualSaveBindings = buildThreadScreenManualSaveBindings(
            coroutineScope = coroutineScope,
            stateBindings = manualSaveStateBindings,
            dependencies = manualSaveDependencies,
            callbacks = manualSaveCallbacks
        ),
        singleMediaSaveBindings = buildThreadScreenSingleMediaSaveBindings(
            coroutineScope = coroutineScope,
            stateBindings = singleMediaSaveStateBindings,
            dependencies = singleMediaSaveDependencies,
            callbacks = singleMediaSaveCallbacks
        )
    )
}
