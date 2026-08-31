package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.analytics.PerformanceTracker
import com.valoser.futacha.shared.analytics.analyticsBoardKind
import com.valoser.futacha.shared.analytics.analyticsCountBucket
import com.valoser.futacha.shared.analytics.analyticsSessionContextId
import com.valoser.futacha.shared.analytics.analyticsTextHasUrl
import com.valoser.futacha.shared.analytics.analyticsTextLengthBucket
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
    val analyticsContext = mapOf(
        "board_kind" to analyticsBoardKind(board.url),
        "board_context" to analyticsSessionContextId("board", board.id, board.url),
        "thread_context" to analyticsSessionContextId("thread", board.url, threadId),
        "title_length_bucket" to analyticsTextLengthBucket(threadTitle),
        "title_has_url" to analyticsTextHasUrl(threadTitle)
    )
    val startManualRefresh: (Int, Int) -> Unit = { savedIndex, savedOffset ->
        AnalyticsTracker.event(
            "thread_refresh_started",
            analyticsContext + mapOf("source" to "manual")
        )
        val requestGeneration = stateBindings.currentManualRefreshGeneration() + 1L
        stateBindings.setManualRefreshGeneration(requestGeneration)
        stateBindings.setIsRefreshing(true)
        stateBindings.currentRefreshThreadJob()?.cancel()
        val nextJob = coroutineScope.launch(start = CoroutineStart.LAZY) {
            val runningJob = coroutineContext[Job]
            try {
                stateBindings.setIsShowingOfflineCopy(false)
                val loadResult = PerformanceTracker.measureSuspend(
                    traceName = "thread_manual_refresh",
                    attributes = mapOf(
                        "feature" to "thread",
                        "source" to "manual",
                        "board_kind" to analyticsBoardKind(board.url)
                    )
                ) {
                    performThreadLoadWithOfflineFallback(
                        config = loadRunnerConfig,
                        callbacks = loadRunnerCallbacks
                    )
                }
                stateBindings.setResolvedThreadUrlOverride(loadResult.nextThreadUrlOverride)
                stateBindings.setIsShowingOfflineCopy(loadResult.usedOffline)
                AnalyticsTracker.event(
                    "thread_refresh_result",
                    analyticsContext + mapOf(
                        "source" to "manual",
                        "result" to "success",
                        "used_offline" to loadResult.usedOffline.toString(),
                        "post_count_bucket" to analyticsCountBucket(loadResult.page.posts.size)
                    )
                )
                if (isActive) {
                    uiCallbacks.onManualRefreshSuccess(
                        buildThreadManualRefreshUiOutcome(
                            page = loadResult.page,
                            embeddedHtml = loadResult.embeddedHtml,
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
                AnalyticsTracker.event(
                    "thread_refresh_result",
                    analyticsContext + mapOf(
                        "source" to "manual",
                        "result" to "failure",
                        "error_type" to (e::class.simpleName ?: "unknown")
                    )
                )
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
        AnalyticsTracker.event(
            "thread_load_started",
            analyticsContext
        )
        stateBindings.currentRefreshThreadJob()?.cancel()
        val nextJob = coroutineScope.launch(start = CoroutineStart.LAZY) {
            val runningJob = coroutineContext[Job]
            stateBindings.setUiState(ThreadUiState.Loading)
            try {
                stateBindings.setIsShowingOfflineCopy(false)
                val localStaleResult = loadThreadLocalStalePageIfAvailable(
                    config = loadRunnerConfig,
                    callbacks = loadRunnerCallbacks
                )
                val activeLoadRunnerConfig = if (localStaleResult != null) {
                    loadRunnerConfig.copy(preferOfflineFallbackAfterLocalStale = true)
                } else {
                    loadRunnerConfig
                }
                if (localStaleResult != null && isActive) {
                    stateBindings.setResolvedThreadUrlOverride(localStaleResult.nextThreadUrlOverride)
                    stateBindings.setIsShowingOfflineCopy(true)
                    uiCallbacks.onInitialLoadSuccess(
                        buildThreadInitialLoadUiOutcome(
                            page = localStaleResult.page,
                            embeddedHtml = localStaleResult.embeddedHtml,
                            history = history,
                            threadId = threadId,
                            threadTitle = threadTitle,
                            board = board,
                            overrideThreadUrl = localStaleResult.nextThreadUrlOverride,
                            usedOffline = true
                        )
                    )
                }
                val loadResult = PerformanceTracker.measureSuspend(
                    traceName = "thread_initial_load",
                    attributes = mapOf(
                        "feature" to "thread",
                        "source" to "initial",
                        "board_kind" to analyticsBoardKind(board.url),
                        "had_local_stale" to (localStaleResult != null).toString()
                    )
                ) {
                    performThreadLoadWithOfflineFallback(
                        config = activeLoadRunnerConfig,
                        callbacks = loadRunnerCallbacks
                    )
                }
                stateBindings.setResolvedThreadUrlOverride(loadResult.nextThreadUrlOverride)
                stateBindings.setIsShowingOfflineCopy(loadResult.usedOffline)
                AnalyticsTracker.event(
                    "thread_load_result",
                    analyticsContext + mapOf(
                        "result" to "success",
                        "used_offline" to loadResult.usedOffline.toString(),
                        "post_count_bucket" to analyticsCountBucket(loadResult.page.posts.size)
                    )
                )
                if (isActive) {
                    uiCallbacks.onInitialLoadSuccess(
                        buildThreadInitialLoadUiOutcome(
                            page = loadResult.page,
                            embeddedHtml = loadResult.embeddedHtml,
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
                AnalyticsTracker.event(
                    "thread_load_result",
                    analyticsContext + mapOf(
                        "result" to "failure",
                        "error_type" to (e::class.simpleName ?: "unknown")
                    )
                )
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
