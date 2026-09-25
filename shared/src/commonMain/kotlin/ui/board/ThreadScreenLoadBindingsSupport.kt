package com.valoser.futacha.shared.ui.board

import kotlinx.coroutines.ensureActive

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
    val setIsShowingOfflineCopy: (Boolean) -> Unit,
    val currentUiState: () -> ThreadUiState = { ThreadUiState.Loading }
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
    currentHistory: () -> List<ThreadHistoryEntry> = { history },
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
    suspend fun supplementVisiblePage(result: ThreadLoadExecutionResult) {
        if (result.usedOffline) return
        val supplemented = loadRunnerCallbacks.supplementRemote(result)
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        if (supplemented.page != result.page) {
            uiCallbacks.onInitialLoadSuccess(buildThreadInitialLoadUiOutcome(
                page = supplemented.page, embeddedHtml = supplemented.embeddedHtml,
                history = currentHistory(), threadId = threadId, threadTitle = threadTitle,
                board = board, overrideThreadUrl = supplemented.nextThreadUrlOverride, usedOffline = false
            ))
        }
    }
    /**
     * Supplements before display when the fresh page lacks posts already on
     * screen (archive-only replies). Showing it first would make those posts
     * vanish and reappear, moving the reader's scroll position. Returns the
     * page to show and whether the supplement already ran.
     */
    suspend fun supplementBeforeDisplayIfDropping(
        result: ThreadLoadExecutionResult
    ): Pair<ThreadLoadExecutionResult, Boolean> {
        if (result.usedOffline) return result to false
        val visiblePosts = (stateBindings.currentUiState() as? ThreadUiState.Success)?.page?.posts
            ?: return result to false
        val freshIds = result.page.posts.mapTo(HashSet()) { it.id }
        if (visiblePosts.all { it.id in freshIds }) return result to false
        val supplemented = loadRunnerCallbacks.supplementRemote(result)
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        return supplemented to true
    }
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
                val (shownResult, alreadySupplemented) = supplementBeforeDisplayIfDropping(loadResult)
                if (isActive) {
                    uiCallbacks.onManualRefreshSuccess(
                        buildThreadManualRefreshUiOutcome(
                            page = shownResult.page,
                            embeddedHtml = shownResult.embeddedHtml,
                            history = currentHistory(),
                            threadId = threadId,
                            threadTitle = threadTitle,
                            board = board,
                            overrideThreadUrl = shownResult.nextThreadUrlOverride,
                            usedOffline = shownResult.usedOffline
                        ),
                        savedIndex,
                        savedOffset
                    )
                }
                if (!alreadySupplemented) supplementVisiblePage(loadResult)
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
            val isInitialLoad = stateBindings.currentUiState() !is ThreadUiState.Success
            if (isInitialLoad) stateBindings.setUiState(ThreadUiState.Loading)
            try {
                stateBindings.setIsShowingOfflineCopy(false)
                val localStaleResult = if (isInitialLoad) loadThreadLocalStalePageIfAvailable(
                    config = loadRunnerConfig,
                    callbacks = loadRunnerCallbacks
                ) else null
                // Show the local copy at once, but always continue to the network:
                // a local copy alone would hide new replies and dead threads.
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
                            history = currentHistory(),
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
                // A local copy on screen may contain archive-only replies.
                val (shownResult, alreadySupplemented) = supplementBeforeDisplayIfDropping(loadResult)
                stateBindings.setResolvedThreadUrlOverride(shownResult.nextThreadUrlOverride)
                stateBindings.setIsShowingOfflineCopy(shownResult.usedOffline)
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
                            page = shownResult.page,
                            embeddedHtml = shownResult.embeddedHtml,
                            history = currentHistory(),
                            threadId = threadId,
                            threadTitle = threadTitle,
                            board = board,
                            overrideThreadUrl = shownResult.nextThreadUrlOverride,
                            usedOffline = shownResult.usedOffline
                        )
                    )
                }
                if (!alreadySupplemented) supplementVisiblePage(loadResult)
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
