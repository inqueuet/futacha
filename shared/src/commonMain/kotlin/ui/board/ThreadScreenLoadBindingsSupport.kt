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
import kotlinx.coroutines.withContext
import com.valoser.futacha.shared.util.AppDispatchers
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
    val currentUiState: () -> ThreadUiState = { ThreadUiState.Loading },
    /** Whether the page on screen is a local copy (or another page auto-save must not write). */
    val currentIsShowingOfflineCopy: () -> Boolean = { false }
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
        if (result.usedOffline || result.fromArchive) return
        val supplemented = loadRunnerCallbacks.supplementRemote(result)
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        if (supplemented.page != result.page) {
            uiCallbacks.onInitialLoadSuccess(buildThreadInitialLoadUiOutcome(
                page = supplemented.page, embeddedHtml = supplemented.embeddedHtml,
                history = currentHistory(), threadId = threadId, threadTitle = threadTitle,
                board = board, overrideThreadUrl = supplemented.nextThreadUrlOverride, usedOffline = false,
                fromArchive = supplemented.fromArchive
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
        if (result.fromArchive) {
            // An archive copy of a dead thread can hold fewer posts than the
            // page on screen; keep those instead of replacing them.
            val visible = stateBindings.currentUiState() as? ThreadUiState.Success
            val visibleIsLocal = stateBindings.currentIsShowingOfflineCopy()
            val reconciled = withContext(AppDispatchers.parsing) {
                reconcileArchiveLoadWithVisiblePage(result, threadId, visible?.page, visibleIsLocal)
            }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            return reconciled to true
        }
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
                // The offline flag keeps auto-save away from a local copy; it
                // changes only together with the page that replaces it (C2).
                val loadResult = PerformanceTracker.measureSuspend(
                    traceName = "thread_manual_refresh",
                    attributes = mapOf(
                        "feature" to "thread",
                        "source" to "manual",
                        "board_kind" to analyticsBoardKind(board.url)
                    )
                ) {
                    performThreadLoadWithOfflineFallback(
                        // A refresh failure must retain the page already on
                        // screen, including replies newer than the saved copy.
                        config = loadRunnerConfig.copy(allowOfflineFallback =
                            loadRunnerConfig.allowOfflineFallback &&
                                stateBindings.currentUiState() !is ThreadUiState.Success),
                        callbacks = loadRunnerCallbacks
                    )
                }
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
                    stateBindings.setResolvedThreadUrlOverride(shownResult.nextThreadUrlOverride)
                    stateBindings.setIsShowingOfflineCopy(shownResult.usedOffline)
                    uiCallbacks.onManualRefreshSuccess(
                        buildThreadManualRefreshUiOutcome(
                            page = shownResult.page,
                            embeddedHtml = shownResult.embeddedHtml,
                            history = currentHistory(),
                            threadId = threadId,
                            threadTitle = threadTitle,
                            board = board,
                            overrideThreadUrl = shownResult.nextThreadUrlOverride,
                            usedOffline = shownResult.usedOffline,
                            fromArchive = shownResult.fromArchive
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
                // With a page on screen the flag must follow that page (C2).
                if (isInitialLoad) stateBindings.setIsShowingOfflineCopy(false)
                val localStaleResult = if (isInitialLoad) loadThreadLocalStalePageIfAvailable(
                    config = loadRunnerConfig,
                    callbacks = loadRunnerCallbacks
                ) else null
                // Show the local copy first. Automatic reopening can be disabled;
                // explicit refreshes and posting still use the network.
                val activeLoadRunnerConfig = when {
                    localStaleResult != null ->
                        loadRunnerConfig.copy(preferOfflineFallbackAfterLocalStale = true)
                    // A reload with a page on screen (after a reply, auto-scroll or
                    // an AI command) keeps that page on failure, as a manual refresh
                    // does: an older auto-save copy must not replace newer replies.
                    !isInitialLoad -> loadRunnerConfig.copy(allowOfflineFallback = false)
                    else -> loadRunnerConfig
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
                    if (localStaleResult.page.posts.isNotEmpty() && !loadRunnerCallbacks.reloadOnOpenEnabled()) return@launch
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
                            usedOffline = shownResult.usedOffline,
                            fromArchive = shownResult.fromArchive
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
                    if (stateBindings.currentUiState() is ThreadUiState.Success) {
                        // A reload after a reply, auto-scroll or a local copy keeps
                        // the page on screen, as a manual refresh does (C3).
                        uiCallbacks.onManualRefreshFailure(
                            buildThreadManualRefreshFailureUiOutcome(
                                error = e,
                                statusCode = e.statusCodeOrNull()
                            )
                        )
                    } else {
                        uiCallbacks.onInitialLoadFailure(
                            buildThreadInitialLoadFailureUiOutcome(
                                error = e,
                                statusCode = e.statusCodeOrNull()
                            )
                        )
                    }
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
