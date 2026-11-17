package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.analytics.analyticsCountBucket
import com.valoser.futacha.shared.analytics.analyticsSessionContextId
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.client.HttpClient
import kotlin.time.Clock
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal data class ThreadAutoSaveEffectState(
    val availability: ThreadAutoSaveAvailability,
    val page: ThreadPage? = null,
    val retryDelayMillis: Long = 0L
)

internal fun resolveThreadAutoSaveEffectState(
    page: ThreadPage?,
    expectedThreadId: String,
    isShowingOfflineCopy: Boolean,
    hasAutoSaveRepository: Boolean,
    hasHttpClient: Boolean,
    hasFileSystem: Boolean,
    isAutoSaveInProgress: Boolean,
    lastAutoSaveTimestampMillis: Long,
    nowMillis: Long,
    minIntervalMillis: Long
): ThreadAutoSaveEffectState {
    val availability = resolveThreadAutoSaveAvailability(
        pageThreadId = page?.threadId,
        expectedThreadId = expectedThreadId,
        isShowingOfflineCopy = isShowingOfflineCopy,
        hasAutoSaveRepository = hasAutoSaveRepository,
        hasHttpClient = hasHttpClient,
        hasFileSystem = hasFileSystem,
        isAutoSaveInProgress = isAutoSaveInProgress,
        lastAutoSaveTimestampMillis = lastAutoSaveTimestampMillis,
        nowMillis = nowMillis,
        minIntervalMillis = minIntervalMillis
    )
    val retryDelayMillis = if (availability == ThreadAutoSaveAvailability.Throttled) {
        remainingThreadAutoSaveDelayMillis(
            nowMillis = nowMillis,
            previousMillis = lastAutoSaveTimestampMillis,
            minIntervalMillis = minIntervalMillis
        )
    } else {
        0L
    }
    return ThreadAutoSaveEffectState(
        availability = availability,
        page = page.takeIf {
            availability == ThreadAutoSaveAvailability.Ready ||
                availability == ThreadAutoSaveAvailability.Throttled
        },
        retryDelayMillis = retryDelayMillis
    )
}

internal data class ThreadInitialScrollRestoreState(
    val shouldRestore: Boolean,
    val savedIndex: Int = 0,
    val savedOffset: Int = 0,
    val savedPostId: String? = null,
    val totalItems: Int = 0
)

internal fun resolveThreadScrollPersistenceReady(
    hasRestoredInitialScroll: Boolean,
    hasInitialHistoryEntry: Boolean,
    actualContentItemCount: Int
): Boolean = hasRestoredInitialScroll ||
    (!hasInitialHistoryEntry && actualContentItemCount > 0)

internal fun resolveThreadUrlOverrideSyncState(
    currentResolvedThreadUrlOverride: String?,
    incomingThreadUrlOverride: String?
): String? {
    return incomingThreadUrlOverride
        ?.takeIf { it.isNotBlank() }
        ?.takeIf { it != currentResolvedThreadUrlOverride }
}

internal fun resolveThreadInitialScrollRestoreState(
    hasRestoredInitialScroll: Boolean,
    entry: ThreadHistoryEntry?,
    totalItems: Int?
): ThreadInitialScrollRestoreState {
    if (hasRestoredInitialScroll || entry == null || totalItems == null) {
        return ThreadInitialScrollRestoreState(shouldRestore = false)
    }
    return ThreadInitialScrollRestoreState(
        shouldRestore = totalItems > 0,
        savedIndex = entry.lastReadItemIndex,
        savedOffset = entry.lastReadItemOffset,
        savedPostId = entry.lastReadPostId,
        totalItems = totalItems
    )
}

internal fun resolveThreadReadAloudIndexUpdate(
    currentIndex: Int,
    segmentCount: Int
): Int? {
    val normalizedIndex = normalizeReadAloudCurrentIndex(
        currentIndex = currentIndex,
        segmentCount = segmentCount
    )
    return normalizedIndex.takeIf { it != currentIndex }
}

/**
 * Keeps the read-aloud position on the same post when the segments are
 * rebuilt (a refresh or an AI hide adds or drops segments before it). The
 * index of a Speaking/Paused session follows its segment, an idle index the
 * post it pointed at in [previousSegments]. A post that is no longer read
 * resumes at the next one that still is. Returns null when nothing changes.
 */
internal fun resolveThreadReadAloudIndexRemap(
    previousSegments: List<ReadAloudSegment>?,
    segments: List<ReadAloudSegment>,
    status: ReadAloudStatus,
    currentIndex: Int
): Int? {
    val previous = previousSegments?.takeUnless { it === segments || it.isEmpty() }
    val anchor = when (status) {
        is ReadAloudStatus.Speaking -> status.segment
        is ReadAloudStatus.Paused -> status.segment
        ReadAloudStatus.Idle -> previous?.getOrNull(currentIndex)
    }
    val mappedIndex = when {
        anchor != null -> mapReadAloudAnchorIndex(previous, segments, anchor, currentIndex)
        // Finished or past the end: stay after the last post that was read.
        previous != null && currentIndex >= previous.size -> {
            val indexByPostId = indexReadAloudSegmentsByPostId(segments)
            val lastKept = previous.asReversed().firstNotNullOfOrNull { old -> indexByPostId[old.postId] }
            lastKept?.plus(1) ?: currentIndex
        }
        else -> currentIndex
    }
    return resolveThreadReadAloudIndexUpdate(mappedIndex, segments.size)
        ?: mappedIndex.takeIf { it != currentIndex }
}

private fun mapReadAloudAnchorIndex(
    previous: List<ReadAloudSegment>?,
    segments: List<ReadAloudSegment>,
    anchor: ReadAloudSegment,
    currentIndex: Int
): Int {
    // Nothing to map onto: leave the index to the caller's clamp.
    if (segments.isEmpty()) return currentIndex
    // Already on the anchor (every segment start of a session lands here).
    if (segments.getOrNull(currentIndex)?.postId == anchor.postId) return currentIndex
    val indexByPostId = indexReadAloudSegmentsByPostId(segments)
    indexByPostId[anchor.postId]?.let { return it }
    // The anchor is no longer read: continue at the next previous segment kept.
    val anchorPosition = previous?.indexOfFirst { it.postId == anchor.postId } ?: -1
    if (anchorPosition >= 0) {
        for (position in anchorPosition + 1 until previous!!.size) {
            indexByPostId[previous[position].postId]?.let { return it }
        }
    }
    val next = segments.indexOfFirst { it.postIndex > anchor.postIndex }
    return if (next >= 0) next else segments.size
}

/** The first segment index of each post, so remapping stays O(n) on 2,000+ posts. */
private fun indexReadAloudSegmentsByPostId(segments: List<ReadAloudSegment>): Map<String, Int> {
    val indexByPostId = HashMap<String, Int>(segments.size * 2)
    segments.forEachIndexed { index, segment -> indexByPostId.getOrPut(segment.postId) { index } }
    return indexByPostId
}

internal fun resolveThreadSearchResultIndexUpdate(
    currentIndex: Int,
    matchCount: Int
): Int? {
    val normalizedIndex = normalizeThreadSearchResultIndex(
        currentIndex = currentIndex,
        matchCount = matchCount
    )
    return normalizedIndex.takeIf { it != currentIndex }
}

internal fun resolveThreadSearchQueryResetIndex(
    currentIndex: Int
): Int? {
    return 0.takeIf { currentIndex != 0 }
}

internal fun resolveThreadReplyDialogAutofillState(
    isReplyDialogVisible: Boolean,
    currentState: ThreadReplyDialogState,
    lastUsedDeleteKey: String
): ThreadReplyDialogState? {
    if (!isReplyDialogVisible) return null
    val updatedState = openThreadReplyDialog(
        state = currentState,
        lastUsedDeleteKey = lastUsedDeleteKey
    )
    return updatedState.takeIf { it != currentState }
}

@OptIn(FlowPreview::class)
internal suspend fun collectThreadScrollPositionPersistence(
    listState: LazyListState,
    threadId: String,
    debounceMillis: Long = 1_000L,
    onScrollPositionPersist: (threadId: String, index: Int, offset: Int) -> Unit
) {
    snapshotFlow {
        (listState.layoutInfo.totalItemsCount > 0) to
            (listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset)
    }
        .distinctUntilChanged()
        .debounce(debounceMillis)
        .collect { (hasItems, position) ->
            if (hasItems) {
                onScrollPositionPersist(threadId, position.first, position.second)
            }
        }
}

internal fun rememberThreadAutoSaveEffectState(
    currentPageForAutoSave: ThreadPage?,
    threadId: String,
    isShowingOfflineCopy: Boolean,
    autoSaveRepository: SavedThreadRepository?,
    httpClient: HttpClient?,
    fileSystem: com.valoser.futacha.shared.util.FileSystem?,
    autoSaveJob: Job?,
    lastAutoSaveTimestampMillis: Long
): ThreadAutoSaveEffectState {
    return resolveThreadAutoSaveEffectState(
        page = currentPageForAutoSave,
        expectedThreadId = threadId,
        isShowingOfflineCopy = isShowingOfflineCopy,
        hasAutoSaveRepository = autoSaveRepository != null,
        hasHttpClient = httpClient != null,
        hasFileSystem = fileSystem != null,
        isAutoSaveInProgress = autoSaveJob?.isActive == true,
        lastAutoSaveTimestampMillis = lastAutoSaveTimestampMillis,
        nowMillis = Clock.System.now().toEpochMilliseconds(),
        minIntervalMillis = AUTO_SAVE_INTERVAL_MS
    )
}

internal fun isThreadAutoSaveReadyNow(
    page: ThreadPage?,
    threadId: String,
    isShowingOfflineCopy: Boolean,
    httpClient: HttpClient?,
    fileSystem: FileSystem?,
    lastAutoSaveTimestampMillis: Long,
    nowMillis: Long
): Boolean {
    return resolveThreadAutoSaveAvailability(
        pageThreadId = page?.threadId,
        expectedThreadId = threadId,
        isShowingOfflineCopy = isShowingOfflineCopy,
        hasAutoSaveRepository = true,
        hasHttpClient = httpClient != null,
        hasFileSystem = fileSystem != null,
        isAutoSaveInProgress = false,
        lastAutoSaveTimestampMillis = lastAutoSaveTimestampMillis,
        nowMillis = nowMillis,
        minIntervalMillis = AUTO_SAVE_INTERVAL_MS
    ) == ThreadAutoSaveAvailability.Ready
}

@Composable
internal fun ThreadUrlOverrideSyncEffect(
    threadUrlOverride: String?,
    resolvedThreadUrlOverride: String?,
    onResolvedThreadUrlOverrideChanged: (String) -> Unit
) {
    LaunchedEffect(threadUrlOverride) {
        resolveThreadUrlOverrideSyncState(
            currentResolvedThreadUrlOverride = resolvedThreadUrlOverride,
            incomingThreadUrlOverride = threadUrlOverride
        )?.let(onResolvedThreadUrlOverrideChanged)
    }
}

@Composable
internal fun ThreadReplyDialogAutofillEffect(
    isReplyDialogVisible: Boolean,
    lastUsedDeleteKey: String,
    replyDialogBinding: ThreadReplyDialogStateBinding
) {
    LaunchedEffect(isReplyDialogVisible, lastUsedDeleteKey) {
        resolveThreadReplyDialogAutofillState(
            isReplyDialogVisible = isReplyDialogVisible,
            currentState = replyDialogBinding.currentState(),
            lastUsedDeleteKey = lastUsedDeleteKey
        )?.let { updatedState ->
            replyDialogBinding.setState(updatedState)
        }
    }
}

private class ThreadReadAloudIndexedSegments {
    var segments: List<ReadAloudSegment>? = null
}

/**
 * Keeps the read-aloud index on the same post across segment rebuilds (see
 * [resolveThreadReadAloudIndexRemap]). Live values are re-read so a seek made
 * after this composition is never overwritten with a stale remap.
 */
@Composable
internal fun ThreadReadAloudIndexEffect(
    segments: List<ReadAloudSegment>,
    currentStatus: () -> ReadAloudStatus,
    currentIndex: () -> Int,
    onCurrentReadAloudIndexChanged: (Int) -> Unit
) {
    val indexed = remember { ThreadReadAloudIndexedSegments() }
    val status = currentStatus()
    val index = currentIndex()
    LaunchedEffect(segments, status, index) {
        // Empty idle segments (closed read-aloud controls, or every post
        // hidden) hold no position: keep the index and the last list as the
        // remap source, so the position returns to its post with them (U4-4).
        if (segments.isEmpty() && status == ReadAloudStatus.Idle) return@LaunchedEffect
        val previous = indexed.segments
        indexed.segments = segments
        if (currentStatus() != status || currentIndex() != index) return@LaunchedEffect
        resolveThreadReadAloudIndexRemap(previous, segments, status, index)
            ?.let(onCurrentReadAloudIndexChanged)
    }
}


/**
 * Whether an initial restore may be marked complete. While a provisional page
 * (a local copy shown ahead of the network) lacks the saved post, the restore
 * must run again on the real page instead of persisting a wrong position.
 */
internal fun shouldCompleteThreadInitialScrollRestore(
    savedPostId: String?,
    displayedPostsLayout: ThreadDisplayedPostsLayout,
    isProvisionalContent: Boolean
): Boolean {
    if (!isProvisionalContent || savedPostId == null) return true
    return displayedPostsLayout.posts.any { it.id == savedPostId }
}

/** The layout a provisional restore already scrolled to (see [ThreadInitialScrollRestoreEffect]). */
internal class ThreadProvisionalScrollRestoreHolder {
    var restoredLayout: ThreadDisplayedPostsLayout? = null
}

@Composable
internal fun ThreadInitialScrollRestoreEffect(
    threadId: String,
    restoreState: ThreadInitialScrollRestoreState,
    lazyListState: LazyListState,
    displayedPostsLayout: ThreadDisplayedPostsLayout,
    onRestoreCompleted: () -> Unit,
    onRestoreFailed: (String, Throwable?) -> Unit,
    /** A local copy is on screen while the network load still runs. */
    isProvisionalContent: Boolean = false
) {
    val provisionalRestore = remember(threadId) { ThreadProvisionalScrollRestoreHolder() }
    LaunchedEffect(threadId, restoreState, displayedPostsLayout, isProvisionalContent) {
        if (!restoreState.shouldRestore) return@LaunchedEffect
        if (restoreState.savedPostId != null && displayedPostsLayout.posts.isEmpty()) {
            return@LaunchedEffect
        }
        if (isProvisionalContent && provisionalRestore.restoredLayout != null &&
            !shouldCompleteThreadInitialScrollRestore(restoreState.savedPostId,
                displayedPostsLayout, isProvisionalContent = true)) {
            return@LaunchedEffect
        }
        if (!isProvisionalContent && provisionalRestore.restoredLayout == displayedPostsLayout) {
            // The load ended without a different page (e.g. only the local copy
            // exists); the position applied to it stands. Do not scroll again.
            provisionalRestore.restoredLayout = null
            onRestoreCompleted()
            return@LaunchedEffect
        }
        // The predicted content count can become available before LazyColumn
        // has installed its actual items. Wait for the real layout instead of
        // treating a pre-layout scrollToItem failure as a completed restore.
        var restored = false
        for (attempt in 0 until 3) {
            val actualTotalItems = snapshotFlow { lazyListState.layoutInfo.totalItemsCount }
                .first { it > 0 }
            restored = restoreThreadScrollPositionSafely(
                listState = lazyListState,
                savedIndex = restoreState.savedIndex,
                savedOffset = restoreState.savedOffset,
                savedPostId = restoreState.savedPostId,
                displayedPostsLayout = displayedPostsLayout,
                // Filtering/NG can make the rendered list shorter than the
                // predicted page count. Clamp against what LazyColumn
                // actually installed so scrollToItem cannot target a missing
                // row.
                totalItems = actualTotalItems,
                onFailure = onRestoreFailed
            )
            if (restored) break
            delay(16L)
        }
        if (restored) {
            if (
                shouldCompleteThreadInitialScrollRestore(
                    savedPostId = restoreState.savedPostId,
                    displayedPostsLayout = displayedPostsLayout,
                    isProvisionalContent = isProvisionalContent
                )
            ) {
                provisionalRestore.restoredLayout = null
                onRestoreCompleted()
            } else {
                provisionalRestore.restoredLayout = displayedPostsLayout
            }
        }
    }
}

@Composable
internal fun ThreadSearchIndexEffects(
    searchQuery: String,
    threadId: String,
    searchMatches: List<ThreadSearchMatch>,
    currentSearchResultIndex: Int,
    onCurrentSearchResultIndexChanged: (Int) -> Unit
) {
    LaunchedEffect(searchQuery, threadId) {
        resolveThreadSearchQueryResetIndex(
            currentIndex = currentSearchResultIndex
        )?.let(onCurrentSearchResultIndexChanged)
    }
    LaunchedEffect(searchMatches) {
        resolveThreadSearchResultIndexUpdate(
            currentIndex = currentSearchResultIndex,
            matchCount = searchMatches.size
        )?.let(onCurrentSearchResultIndexChanged)
    }
}

@Composable
internal fun ThreadScrollPersistenceEffect(
    threadId: String,
    lazyListState: LazyListState,
    displayedPostsLayout: ThreadDisplayedPostsLayout,
    onScrollPositionPersist: (threadId: String, index: Int, offset: Int, postId: String?) -> Unit,
    onScrollPositionPersistImmediately: (threadId: String, index: Int, offset: Int, postId: String?) -> Unit,
    scrollPersistenceReady: Boolean
) {
    val latestImmediatePersist = rememberUpdatedState(onScrollPositionPersistImmediately)
    val latestPersist = rememberUpdatedState(onScrollPositionPersist)
    val latestPersistenceReady = rememberUpdatedState(scrollPersistenceReady)
    val latestDisplayedPostsLayout = rememberUpdatedState(displayedPostsLayout)
    DisposableEffect(threadId, lazyListState) {
        onDispose {
            // The debounce collector is cancelled together with this screen.
            // Flush the last visible position before that happens so a quick
            // thread/history/tab switch cannot lose the user's reading point.
            if (latestPersistenceReady.value && lazyListState.layoutInfo.totalItemsCount > 0) {
                latestImmediatePersist.value(
                    threadId,
                    lazyListState.firstVisibleItemIndex,
                    lazyListState.firstVisibleItemScrollOffset,
                    resolveVisibleThreadPostId(
                        lazyListState.firstVisibleItemIndex,
                        latestDisplayedPostsLayout.value
                    )
                )
            }
        }
    }
    LaunchedEffect(threadId, lazyListState, scrollPersistenceReady) {
        if (!scrollPersistenceReady) return@LaunchedEffect
        // The collector outlives recompositions: filter, tree or summary-row
        // changes replace the layout without restarting it, so resolve the post
        // id against the latest layout rather than the one captured at start.
        collectThreadScrollPositionPersistence(
            listState = lazyListState,
            threadId = threadId,
            onScrollPositionPersist = createThreadScrollPersistHandler(
                layoutProvider = { latestDisplayedPostsLayout.value },
                persist = { persistedThreadId, index, offset, postId ->
                    latestPersist.value(persistedThreadId, index, offset, postId)
                }
            )
        )
    }
}

/**
 * Captures meaningful reading progress rather than every pixel of a scroll. The
 * target is anonymous for the current process and the post text is never read.
 */
@Composable
internal fun ThreadReadProgressObservationEffect(
    threadId: String,
    boardUrl: String,
    totalPostCount: Int,
    lazyListState: LazyListState
) {
    LaunchedEffect(threadId, boardUrl, totalPostCount, lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemIndex }
            .map { index -> (index.coerceAtLeast(0) / THREAD_READ_PROGRESS_STEP) * THREAD_READ_PROGRESS_STEP }
            .distinctUntilChanged()
            .collect { startIndex ->
                AnalyticsTracker.event(
                    "thread_read_progress",
                    mapOf(
                        "thread_context" to analyticsSessionContextId("thread", boardUrl, threadId),
                        "visible_post_range" to "${startIndex}_${startIndex + THREAD_READ_PROGRESS_STEP - 1}",
                        "total_post_count_bucket" to analyticsCountBucket(totalPostCount)
                    )
                )
            }
    }
}

private const val THREAD_READ_PROGRESS_STEP = 20

@Composable
internal fun ThreadAutoSaveLaunchEffect(
    threadId: String,
    currentPageForAutoSave: ThreadPage?,
    isShowingOfflineCopy: Boolean,
    httpClient: HttpClient?,
    fileSystem: FileSystem?,
    autoSaveEffectState: ThreadAutoSaveEffectState,
    lastAutoSaveTimestampMillis: Long,
    onStartAutoSave: (ThreadPage) -> Unit
) {
    val currentOnStartAutoSave = rememberUpdatedState(onStartAutoSave)
    val latestPageForAutoSave = rememberUpdatedState(currentPageForAutoSave)
    val latestLastAutoSaveTimestampMillis = rememberUpdatedState(lastAutoSaveTimestampMillis)
    val startupWindow = remember(threadId) { ThreadAutoSaveStartupWindowHolder() }
    // Keyed on the availability and page, not the whole state: its retry delay
    // is recomputed from the clock on every recomposition, which restarted this
    // effect (and its throttle wait) on each frame of scrolling or loading.
    LaunchedEffect(
        threadId,
        isShowingOfflineCopy,
        httpClient,
        fileSystem,
        autoSaveEffectState.availability,
        autoSaveEffectState.page,
        lastAutoSaveTimestampMillis
    ) {
        when (autoSaveEffectState.availability) {
            ThreadAutoSaveAvailability.Ready -> {
                val windowStart = startupWindow.startedAtMillis
                    ?: Clock.System.now().toEpochMilliseconds().also { startupWindow.startedAtMillis = it }
                awaitThreadAutoSaveStartupWindow(lastAutoSaveTimestampMillis, windowStartedAtMillis = windowStart)
                val page = latestPageForAutoSave.value ?: autoSaveEffectState.page
                if (
                    isThreadAutoSaveReadyNow(
                        page = page,
                        threadId = threadId,
                        isShowingOfflineCopy = isShowingOfflineCopy,
                        httpClient = httpClient,
                        fileSystem = fileSystem,
                        lastAutoSaveTimestampMillis = latestLastAutoSaveTimestampMillis.value,
                        nowMillis = Clock.System.now().toEpochMilliseconds()
                    )
                ) {
                    page?.let { targetPage ->
                        currentOnStartAutoSave.value(targetPage)
                    }
                }
            }
            ThreadAutoSaveAvailability.Throttled -> {
                delay(autoSaveEffectState.retryDelayMillis.coerceAtLeast(1_000L))
                val page = latestPageForAutoSave.value
                if (
                    isThreadAutoSaveReadyNow(
                        page = page,
                        threadId = threadId,
                        isShowingOfflineCopy = isShowingOfflineCopy,
                        httpClient = httpClient,
                        fileSystem = fileSystem,
                        lastAutoSaveTimestampMillis = latestLastAutoSaveTimestampMillis.value,
                        nowMillis = Clock.System.now().toEpochMilliseconds()
                    )
                ) {
                    page?.let { targetPage ->
                        currentOnStartAutoSave.value(targetPage)
                    }
                }
            }
            ThreadAutoSaveAvailability.InProgress,
            ThreadAutoSaveAvailability.MissingPage,
            ThreadAutoSaveAvailability.MissingDependencies,
            ThreadAutoSaveAvailability.ThreadMismatch,
            ThreadAutoSaveAvailability.OfflineCopy -> Unit
        }
    }
}
