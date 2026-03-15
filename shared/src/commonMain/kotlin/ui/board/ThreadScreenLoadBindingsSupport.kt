package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

internal data class ThreadScreenLoadStateBindings(
    val currentRefreshThreadJob: () -> Job?,
    val setRefreshThreadJob: (Job?) -> Unit,
    val currentManualRefreshGeneration: () -> Long,
    val setManualRefreshGeneration: (Long) -> Unit,
    val setIsRefreshing: (Boolean) -> Unit,
    val setUiState: (ThreadUiState) -> Unit,
    val setResolvedThreadUrlOverride: (String?) -> Unit,
    val setIsShowingOfflineCopy: (Boolean) -> Unit
)

internal data class ThreadScreenLoadUiCallbacks(
    val onManualRefreshSuccess: suspend (ThreadLoadUiOutcome, Int, Int) -> Unit,
    val onManualRefreshFailure: (ThreadLoadUiOutcome) -> Unit,
    val onInitialLoadSuccess: (ThreadLoadUiOutcome) -> Unit,
    val onInitialLoadFailure: (ThreadLoadUiOutcome) -> Unit
)

internal data class ThreadScreenLoadBindings(
    val startManualRefresh: (Int, Int) -> Unit,
    val refreshThread: () -> Unit
)

internal fun buildThreadScreenLoadBindings(
    coroutineScope: CoroutineScope,
    loadRunnerConfig: ThreadLoadRunnerConfig,
    loadRunnerCallbacks: ThreadLoadRunnerCallbacks,
    history: List<ThreadHistoryEntry>,
    threadId: String,
    threadTitle: String?,
    board: BoardSummary,
    stateBindings: ThreadScreenLoadStateBindings,
    uiCallbacks: ThreadScreenLoadUiCallbacks
): ThreadScreenLoadBindings {
    val startManualRefresh: (Int, Int) -> Unit = { savedIndex, savedOffset ->
        val requestGeneration = stateBindings.currentManualRefreshGeneration() + 1L
        stateBindings.setManualRefreshGeneration(requestGeneration)
        stateBindings.setIsRefreshing(true)
        stateBindings.currentRefreshThreadJob()?.cancel()
        val nextJob = coroutineScope.launch(start = CoroutineStart.LAZY) {
            val runningJob = coroutineContext[Job]
            try {
                stateBindings.setIsShowingOfflineCopy(false)
                val loadResult = performThreadLoadWithOfflineFallback(
                    config = loadRunnerConfig,
                    callbacks = loadRunnerCallbacks
                )
                stateBindings.setResolvedThreadUrlOverride(loadResult.nextThreadUrlOverride)
                stateBindings.setIsShowingOfflineCopy(loadResult.usedOffline)
                if (isActive) {
                    uiCallbacks.onManualRefreshSuccess(
                        buildThreadManualRefreshUiOutcome(
                            page = loadResult.page,
                            history = history,
                            threadId = threadId,
                            threadTitle = threadTitle,
                            board = board,
                            overrideThreadUrl = loadResult.nextThreadUrlOverride,
                            usedOffline = loadResult.usedOffline
                        ),
                        savedIndex,
                        savedOffset
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiCallbacks.onManualRefreshFailure(
                    buildThreadManualRefreshFailureUiOutcome(
                        error = e,
                        statusCode = e.statusCodeOrNull()
                    )
                )
            } finally {
                if (stateBindings.currentManualRefreshGeneration() == requestGeneration) {
                    stateBindings.setIsRefreshing(false)
                }
                if (runningJob != null && stateBindings.currentRefreshThreadJob() == runningJob) {
                    stateBindings.setRefreshThreadJob(null)
                }
            }
        }
        stateBindings.setRefreshThreadJob(nextJob)
        nextJob.start()
    }

    val refreshThread: () -> Unit = {
        stateBindings.currentRefreshThreadJob()?.cancel()
        val nextJob = coroutineScope.launch(start = CoroutineStart.LAZY) {
            val runningJob = coroutineContext[Job]
            stateBindings.setUiState(ThreadUiState.Loading)
            try {
                stateBindings.setIsShowingOfflineCopy(false)
                val loadResult = performThreadLoadWithOfflineFallback(
                    config = loadRunnerConfig,
                    callbacks = loadRunnerCallbacks
                )
                stateBindings.setResolvedThreadUrlOverride(loadResult.nextThreadUrlOverride)
                stateBindings.setIsShowingOfflineCopy(loadResult.usedOffline)
                if (isActive) {
                    uiCallbacks.onInitialLoadSuccess(
                        buildThreadInitialLoadUiOutcome(
                            page = loadResult.page,
                            history = history,
                            threadId = threadId,
                            threadTitle = threadTitle,
                            board = board,
                            overrideThreadUrl = loadResult.nextThreadUrlOverride,
                            usedOffline = loadResult.usedOffline
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isActive) {
                    uiCallbacks.onInitialLoadFailure(
                        buildThreadInitialLoadFailureUiOutcome(
                            error = e,
                            statusCode = e.statusCodeOrNull()
                        )
                    )
                }
            } finally {
                if (runningJob != null && stateBindings.currentRefreshThreadJob() == runningJob) {
                    stateBindings.setRefreshThreadJob(null)
                }
            }
        }
        stateBindings.setRefreshThreadJob(nextJob)
        nextJob.start()
    }

    return ThreadScreenLoadBindings(
        startManualRefresh = startManualRefresh,
        refreshThread = refreshThread
    )
}
