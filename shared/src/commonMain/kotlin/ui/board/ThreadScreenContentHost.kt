package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import coil3.compose.LocalPlatformContext
import com.valoser.futacha.shared.ai.OnDeviceAiService
import com.valoser.futacha.shared.ai.PostModerationInput
import com.valoser.futacha.shared.ai.PostModerationResult
import com.valoser.futacha.shared.ai.ThreadSummaryInput
import com.valoser.futacha.shared.ai.detectCopyPastePosts
import com.valoser.futacha.shared.ai.aiDigest
import com.valoser.futacha.shared.ai.getAiConnectionStore
import com.valoser.futacha.shared.ai.openAiSummaryText
import com.valoser.futacha.shared.ai.normalizeThreadSummary
import com.valoser.futacha.shared.ai.ModerationPostContext
import com.valoser.futacha.shared.ai.isFallbackThreadSummary
import com.valoser.futacha.shared.model.ThreadDisplayMode
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.lazy.LazyListState

private const val THREAD_AI_SUMMARY_UI_TIMEOUT_MS = 20_000L
private const val THREAD_AI_POST_MODERATION_BATCH_TIMEOUT_MS = 45_000L
private const val THREAD_AI_POST_MODERATION_BATCH_DELAY_MS = 120L
private const val THREAD_AI_POST_MODERATION_UI_UPDATE_BATCH_INTERVAL = 2
private const val THREAD_AI_POST_MODERATION_START_DELAY_MS = 1_500L
private const val THREAD_AI_POST_MODERATION_SUMMARY_WAIT_DELAY_MS = 120L
private const val THREAD_AI_SUMMARY_START_DELAY_MS = 120L

internal data class ThreadScreenContentHostBindings(
    val aiSourceBoard: String = "",
    val aiThreadTitle: String? = null,
    val uiState: ThreadUiState,
    val refreshThread: () -> Unit,
    val threadFilterBinding: ThreadFilterUiStateBinding,
    val threadDisplayMode: ThreadDisplayMode,
    val persistedSelfPostIdentifiers: List<String>,
    val ngHeaders: List<String>,
    val ngWords: List<String>,
    val ngFilteringEnabled: Boolean,
    val threadFilterCache: LinkedHashMap<ThreadFilterCacheKey, ThreadFilterResult>,
    val postTextCache: ThreadPostTextCache? = null,
    val lazyListState: LazyListState,
    val searchScrollRequest: ThreadPostScrollRequest? = null,
    val onDisplayedPostsChanged: (ThreadDisplayedPostsLayout) -> Unit = {},
    val newPostIds: Set<String> = emptySet(),
    val saidaneOverrides: Map<String, String>,
    val selfPostIdentifierSet: Set<String>,
    val postHighlightRanges: Map<Post, List<IntRange>>,
    val postOverlayState: ThreadPostOverlayState,
    val setPostOverlayState: (ThreadPostOverlayState) -> Unit,
    val onSaidaneClick: (Post) -> Unit,
    val onMediaClick: ((String, MediaType) -> Unit)?,
    val onMediaLongPress: ((Post, String, MediaType) -> Unit)?,
    val onUrlClick: (String) -> Unit,
    val onRefresh: () -> Unit,
    val isRefreshing: Boolean,
    val preferencesState: ScreenPreferencesState
)

@Composable
internal fun ThreadScreenContentHost(
    bindings: ThreadScreenContentHostBindings,
    modifier: Modifier = Modifier
) {
    when (val state = bindings.uiState) {
        ThreadUiState.Loading -> ThreadLoading(modifier = modifier.fillMaxSize())
        is ThreadUiState.Error -> ThreadError(
            message = state.message,
            modifier = modifier.fillMaxSize(),
            onRetry = bindings.refreshThread
        )

        is ThreadUiState.Success -> {
            val threadFilterUiState = bindings.threadFilterBinding.currentState()
            val threadFilterComputationState = remember(
                threadFilterUiState,
                bindings.persistedSelfPostIdentifiers,
                bindings.ngHeaders,
                bindings.ngWords,
                bindings.ngFilteringEnabled
            ) {
                resolveThreadFilterComputationState(
                    uiState = threadFilterUiState,
                    selfPostIdentifiers = bindings.persistedSelfPostIdentifiers,
                    ngHeaders = bindings.ngHeaders,
                    ngWords = bindings.ngWords,
                    ngFilteringEnabled = bindings.ngFilteringEnabled
                )
            }
            val hasNgFilters = threadFilterComputationState.hasNgFilters
            val hasThreadFilters = threadFilterComputationState.hasThreadFilters
            val shouldShowThreadSummary = isThreadSummaryFeatureEnabled(bindings.preferencesState)
            val shouldApplyAiPostFilter = isAiPostFilterFeatureEnabled(bindings.preferencesState)
            OpenAiLimitNotice(shouldApplyAiPostFilter)
            val shouldComputeFullPostFingerprint = shouldComputeFullThreadPostFingerprint(
                shouldComputeForThreadFilters = threadFilterComputationState.shouldComputeFullPostFingerprint,
                shouldShowThreadSummary = shouldShowThreadSummary,
                shouldApplyAiPostFilter = shouldApplyAiPostFilter
            )
            val postsFingerprint by produceState(
                initialValue = buildLightweightThreadPostListFingerprint(state.page.posts),
                key1 = state.page.posts,
                key2 = shouldComputeFullPostFingerprint
            ) {
                value = if (shouldComputeFullPostFingerprint) {
                    withContext(AppDispatchers.parsing) {
                        buildThreadPostListFingerprintCancellable(state.page.posts)
                    }
                } else {
                    buildLightweightThreadPostListFingerprint(state.page.posts)
                }
            }
            val filterCacheKey = remember(
                postsFingerprint,
                hasNgFilters,
                bindings.ngHeaders,
                bindings.ngWords,
                threadFilterComputationState
            ) {
                buildThreadFilterCacheKey(
                    postsFingerprint = postsFingerprint,
                    computationState = threadFilterComputationState,
                    ngHeaders = bindings.ngHeaders,
                    ngWords = bindings.ngWords
                )
            }
            val filtersActive = hasNgFilters || hasThreadFilters
            val cachedFilterOutcome = remember(filterCacheKey, filtersActive, state.page) {
                if (!filtersActive) {
                    null
                } else {
                    bindings.threadFilterCache[filterCacheKey]?.let { cached ->
                        ThreadFilterOutcome(filterCacheKey, cached, state.page.posts.size)
                    }
                }
            }
            // Only which posts survive is cached here, never the page itself: a
            // refresh with the same reply count keeps the same cache key but brings
            // new bodies, deletions, saidane counts and expiry, which must show.
            val filterOutcome by produceState(
                initialValue = cachedFilterOutcome,
                key1 = filterCacheKey
            ) {
                bindings.threadFilterCache[filterCacheKey]?.let { cachedResult ->
                    value = ThreadFilterOutcome(filterCacheKey, cachedResult, state.page.posts.size)
                    return@produceState
                }
                if (!filtersActive) {
                    value = null
                    return@produceState
                }
                if (threadFilterComputationState.criteria.options.contains(ThreadFilterOption.Keyword)) {
                    delay(THREAD_FILTER_DEBOUNCE_MILLIS)
                }
                val sourcePage = state.page
                val filterResult = withContext(AppDispatchers.parsing) {
                    val hasNgWordFilters = hasNgFilters && bindings.ngWords.any { it.isNotBlank() }
                    val hasThreadLowerBodyFilters = threadFilterComputationState.criteria.options.any {
                        it == ThreadFilterOption.Url || it == ThreadFilterOption.Keyword
                    }
                    val precomputedLowerBodyByPost = if (hasNgWordFilters || hasThreadLowerBodyFilters) {
                        buildLowerBodyByPost(sourcePage.posts, bindings.postTextCache)
                    } else {
                        emptyMap()
                    }
                    applyThreadFilterResultCancellable(
                        page = sourcePage,
                        criteria = threadFilterComputationState.criteria,
                        ngHeaders = bindings.ngHeaders,
                        ngWords = bindings.ngWords,
                        ngEnabled = hasNgFilters,
                        precomputedLowerBodyByPost = precomputedLowerBodyByPost,
                        textCache = bindings.postTextCache
                    )
                }
                if (bindings.threadFilterCache.size >= THREAD_FILTER_CACHE_MAX_ENTRIES) {
                    val iterator = bindings.threadFilterCache.entries.iterator()
                    if (iterator.hasNext()) {
                        iterator.next()
                        iterator.remove()
                    }
                }
                bindings.threadFilterCache[filterCacheKey] = filterResult
                value = ThreadFilterOutcome(filterCacheKey, filterResult, sourcePage.posts.size)
            }
            val lastFilteredPage = remember { ThreadFilteredPageHolder() }
            val normallyFilteredPage = remember(state.page, filterOutcome, filterCacheKey, filtersActive) {
                resolveNormallyFilteredThreadPage(
                    page = state.page,
                    outcome = filterOutcome,
                    currentKey = filterCacheKey,
                    filtersActive = filtersActive,
                    previous = lastFilteredPage.page
                ).also { lastFilteredPage.page = it }
            }
            val filteredPage = rememberFutachaFilteredThreadPage(state.page, normallyFilteredPage,
                bindings.ngHeaders, bindings.ngWords, bindings.postTextCache)
            if (filteredPage == null) {
                // Shared NG is still being applied; never show posts it will hide.
                ThreadLoading(modifier = modifier.fillMaxSize())
                return
            }
            val onPostLongPress: (Post) -> Unit = { post ->
                bindings.setPostOverlayState(
                    openThreadPostActionOverlay(
                        currentState = bindings.postOverlayState,
                        post = post
                    )
                )
            }
            val onQuoteRequestedForPost: (Post) -> Unit = { post ->
                bindings.setPostOverlayState(
                    openThreadQuoteOverlay(
                        currentState = bindings.postOverlayState,
                        post = post
                    )
                )
            }
            val platformContext = LocalPlatformContext.current
            val aiService = rememberSelectedAiService(platformContext)
            // An API key retry in the settings resumes moderation paused by that key's failure.
            val aiKeyRetries = remember(platformContext) { getAiConnectionStore(platformContext).keyRetries.drop(1) }
            val aiInferenceMutex = remember { Mutex() }
            val threadSummaryCache = remember { linkedMapOf<ThreadSummaryCacheKey, ThreadSummaryUiState.Ready>() }
            val threadPostModerationCache = remember { linkedMapOf<ThreadPostModerationCacheKey, PostModerationResult>() }
            val aiSourcePosts = remember(state.page) { resolveThreadAiSourcePosts(state.page) }
            // Every post's body is stripped to build this; nothing reads it while AI filtering is off.
            val aiPostModerationSourcePosts = remember(aiSourcePosts, shouldApplyAiPostFilter) {
                if (shouldApplyAiPostFilter) resolveThreadAiPostModerationSourcePosts(aiSourcePosts) else emptyList()
            }
            var moderationDisplayedPosts by remember(bindings.aiSourceBoard, state.page.threadId, bindings.threadDisplayMode) {
                mutableStateOf<List<Post>>(emptyList())
            }
            val moderationOrderedIds = remember(moderationDisplayedPosts, shouldApplyAiPostFilter) {
                if (shouldApplyAiPostFilter) moderationDisplayedPosts.map { it.id } else emptyList()
            }
            val moderationItemKeys = remember(moderationDisplayedPosts, bindings.threadDisplayMode, shouldApplyAiPostFilter) {
                if (shouldApplyAiPostFilter) {
                    val prefix = if (bindings.threadDisplayMode == ThreadDisplayMode.Tree) "tree-post" else "thread-post"
                    buildThreadPostLazyListKeys(moderationDisplayedPosts, prefix).zip(moderationOrderedIds).toMap()
                } else {
                    emptyMap()
                }
            }
            val nearbyModerationIds = rememberModerationViewport(
                bindings.lazyListState, moderationOrderedIds, moderationItemKeys, shouldApplyAiPostFilter)
            val externalSummaryFingerprint = remember(aiSourcePosts, bindings.aiThreadTitle, aiService.externalSummary) {
                if (aiService.externalSummary) aiDigest(openAiSummaryText(ThreadSummaryInput(state.page.threadId, bindings.aiThreadTitle, aiSourcePosts))) else ""
            }
            // Moderation input only: a そうだね, image or unchanged refresh must not restart the pass.
            val aiCacheKey = remember(
                bindings.aiSourceBoard,
                state.page.threadId,
                aiPostModerationSourcePosts,
                aiService.configurationKey
            ) {
                buildThreadAiModerationCacheKey(
                    threadId = "${bindings.aiSourceBoard}/${state.page.threadId}",
                    moderationSourcePosts = aiPostModerationSourcePosts,
                    providerLabel = aiService.configurationKey
                )
            }
            val threadSummaryCacheKey = remember(
                bindings.aiSourceBoard,
                state.page.threadId,
                aiService.configurationKey,
                externalSummaryFingerprint,
                state.page.isTruncated
            ) {
                buildThreadSummaryCacheKey(
                    threadId = "${bindings.aiSourceBoard}/${state.page.threadId}",
                    providerLabel = "${aiService.configurationKey}:$externalSummaryFingerprint:${state.page.isTruncated}"
                )
            }
            val summaryState by produceState<ThreadSummaryUiState?>(
                initialValue = resolveInitialThreadSummaryUiState(shouldShowThreadSummary),
                key1 = shouldShowThreadSummary,
                key2 = threadSummaryCacheKey
            ) {
                if (!shouldShowThreadSummary) {
                    value = null
                    return@produceState
                }
                threadSummaryCache[threadSummaryCacheKey]?.let {
                    value = it
                    return@produceState
                }
                value = ThreadSummaryUiState.Loading
                delay(THREAD_AI_SUMMARY_START_DELAY_MS)
                val summaryInput = ThreadSummaryInput(
                    threadId = state.page.threadId,
                    title = bindings.aiThreadTitle,
                    posts = aiSourcePosts,
                    sourceKey = "${bindings.aiSourceBoard}/${state.page.threadId}",
                    isTruncated = state.page.isTruncated
                )
                val summaryResult = runThreadAiInferenceWithTimeout(
                    timeoutMillis = if (aiService.externalSummary) 300_000L else THREAD_AI_SUMMARY_UI_TIMEOUT_MS,
                    aiInferenceMutex = aiInferenceMutex,
                    aiService = aiService
                ) {
                    aiService.summarizeThread(summaryInput)
                }
                value = summaryResult?.fold(
                    onSuccess = {
                        ThreadSummaryUiState.Ready(if (aiService.externalSummary) it else normalizeThreadSummary(it)).also { readyState ->
                            // An extract-only fallback is shown but not kept, so it is regenerated later.
                            if (!it.isFallbackThreadSummary) putBoundedAiCacheEntry(
                                cache = threadSummaryCache,
                                key = threadSummaryCacheKey,
                                value = readyState,
                                maxEntries = THREAD_AI_CACHE_MAX_ENTRIES
                            )
                        }
                    },
                    onFailure = {
                        ThreadSummaryUiState.Unavailable(
                            it.message ?: "スレ要約を生成できませんでした。"
                        )
                    }
                ) ?: ThreadSummaryUiState.Unavailable("スレ要約がタイムアウトしました。")
            }
            val latestSummaryState = rememberUpdatedState(summaryState)
            val externalModerationCache = remember(aiService, bindings.aiSourceBoard, state.page.threadId) {
                linkedMapOf<String, Pair<String, PostModerationResult>>()
            }
            val aiModerationCacheMutex = remember { Mutex() }
            // Device-only undecided answers: bounded re-inference for this thread screen.
            // Recreated when moderation is turned off and on, which re-judges posts whose retries were used up.
            val moderationRetryBudget = remember(aiService, bindings.aiSourceBoard, state.page.threadId, shouldApplyAiPostFilter) {
                ModerationRetryBudget()
            }
            // The viewport is not a key: the worker follows it, so scrolling never cancels a sent batch.
            val aiPostModerationUiState by produceState(
                initialValue = AiPostModerationUiState(isEnabled = shouldApplyAiPostFilter),
                key1 = shouldApplyAiPostFilter,
                key2 = aiCacheKey,
                key3 = Pair(shouldShowThreadSummary, bindings.aiThreadTitle)
            ) {
                if (!shouldApplyAiPostFilter) {
                    value = AiPostModerationUiState(isEnabled = false)
                    return@produceState
                }
                while (
                    shouldDeferAiPostModeration(
                        shouldApplyAiPostFilter = shouldApplyAiPostFilter,
                        shouldShowThreadSummary = shouldShowThreadSummary,
                        summaryState = latestSummaryState.value
                    )
                ) {
                    value = AiPostModerationUiState(isEnabled = shouldApplyAiPostFilter)
                    if (!shouldApplyAiPostFilter) {
                        return@produceState
                    }
                    delay(THREAD_AI_POST_MODERATION_SUMMARY_WAIT_DELAY_MS)
                }
                val threadId = state.page.threadId
                val sourcePosts = aiPostModerationSourcePosts
                val external = aiService.externalModeration
                val hybrid = aiService.hybridModeration
                val providerLabel = aiService.configurationKey
                // Per-post context and cache keys cover every post; build them off the main thread.
                val prepared = aiModerationCacheMutex.withLock {
                    withContext(AppDispatchers.parsing) {
                        prepareThreadAiModeration(
                            cacheThreadId = aiCacheKey.threadId,
                            title = bindings.aiThreadTitle,
                            sourcePosts = sourcePosts,
                            external = external,
                            hybrid = hybrid,
                            providerLabel = providerLabel,
                            localCache = threadPostModerationCache,
                            externalCache = externalModerationCache
                        )
                    }
                }
                val results = prepared.results
                var pendingCount = 0
                var failedBatchCount = 0
                var errorMessage: String? = null
                fun publish(isRunning: Boolean) {
                    value = AiPostModerationUiState(
                        isEnabled = true,
                        isRunning = isRunning,
                        processedPosts = results.size,
                        totalPosts = results.size + pendingCount,
                        failedBatchCount = failedBatchCount,
                        results = results.values.toList(),
                        errorMessage = errorMessage
                    )
                }
                publish(isRunning = false)
                runViewportModeration(
                    viewport = snapshotFlow { nearbyModerationIds.value },
                    startDelayMillis = if (external) 0L else THREAD_AI_POST_MODERATION_START_DELAY_MS,
                    batchDelayMillis = if (external) 0L else THREAD_AI_POST_MODERATION_BATCH_DELAY_MS,
                    retryDelayMillis = if (external && !hybrid) 0L else THREAD_AI_POST_MODERATION_START_DELAY_MS,
                    retryBudget = if (external) null else moderationRetryBudget,
                    retryKey = { post -> prepared.cacheKeys[post.id] ?: post.id },
                    onViewportChanged = {
                        failedBatchCount = 0
                        errorMessage = null
                    },
                    resume = aiKeyRetries,
                    nextBatch = { nearby, attempted ->
                        // External decisions may be partial and are retried; local decisions are final.
                        val pending = sourcePosts.asSequence().filter { post ->
                            post.id !in attempted && (nearby == null || post.id in nearby) &&
                                if (external) results[post.id]?.isComplete != true else post.id !in results
                        }.take(32).toList()
                        pendingCount = pending.size
                        val batch = when {
                            pending.isEmpty() -> null
                            external && !hybrid -> PostModerationInput(threadId, pending)
                            else -> withContext(AppDispatchers.parsing) {
                                prepared.context.batches(threadId, pending).firstOrNull()
                            }
                        }
                        publish(isRunning = batch != null)
                        batch
                    },
                    runBatch = { batchInput ->
                        val targetIds = batchInput.posts.map { it.id }.toSet()
                        val accepted = linkedMapOf<String, PostModerationResult>()
                        fun publishResults(classified: List<PostModerationResult>) {
                            classified.filter { it.postId in targetIds }.forEach { result ->
                                accepted[result.postId] = result
                                results[result.postId] = result
                            }
                            publish(isRunning = true)
                        }
                        var response: Result<List<PostModerationResult>>? = null
                        try {
                            response = runThreadAiInferenceWithTimeout(
                                timeoutMillis = if (external) 150_000L else THREAD_AI_POST_MODERATION_BATCH_TIMEOUT_MS,
                                aiInferenceMutex = aiInferenceMutex,
                                aiService = aiService
                            ) {
                                withContext(ModerationPostContext(prepared.context)) {
                                    if (external) aiService.classifyPosts(batchInput, ::publishResults)
                                    else aiService.classifyPosts(batchInput)
                                }
                            }
                            response?.getOrNull()?.let(::publishResults)
                        } finally {
                            if (accepted.isNotEmpty()) withContext(NonCancellable) {
                                aiModerationCacheMutex.withLock {
                                    commitThreadAiModeration(prepared, accepted, sourcePosts.size,
                                        threadPostModerationCache, externalModerationCache)
                                }
                            }
                        }
                        val finished = response
                        if (!external && finished?.isSuccess == true) batchInput.posts.forEach { post ->
                            if (post.id !in accepted) moderationRetryBudget.record(prepared.cacheKeys[post.id] ?: post.id)
                        }
                        if (finished?.isSuccess != true || batchInput.posts.any { it.id !in accepted }) {
                            failedBatchCount += 1
                        }
                        errorMessage = if (finished == null) "判定がタイムアウトしました。"
                            else finished.exceptionOrNull()?.message ?: errorMessage
                        publish(isRunning = true)
                        !(external && finished?.isSuccess != true)
                    }
                )
            }
            val copyPasteResults by produceState<List<PostModerationResult>>(
                initialValue = emptyList(), key1 = state.page.posts, key2 = shouldApplyAiPostFilter
            ) {
                value = if (shouldApplyAiPostFilter) withContext(AppDispatchers.parsing) {
                    detectCopyPastePosts(state.page.posts)
                } else emptyList()
            }
            val combinedModerationResults = remember(aiPostModerationUiState.results, copyPasteResults, shouldApplyAiPostFilter) {
                if (shouldApplyAiPostFilter) aiPostModerationUiState.results + copyPasteResults else emptyList()
            }
            val aiHiddenPostResolutionContext by produceState<AiHiddenPostResolutionContext?>(
                initialValue = null,
                key1 = filteredPage.posts,
                key2 = bindings.selfPostIdentifierSet
            ) {
                value = withContext(AppDispatchers.parsing) {
                    buildAiHiddenPostResolutionContext(
                        posts = filteredPage.posts,
                        selfPostIdentifiers = bindings.selfPostIdentifierSet
                    )
                }
            }
            val aiHiddenPostState by produceState(
                initialValue = AiHiddenPostState(),
                key1 = aiHiddenPostResolutionContext,
                key2 = combinedModerationResults,
                key3 = aiService.automaticallyHideModeratedPosts
            ) {
                val resolutionContext = aiHiddenPostResolutionContext
                value = if (resolutionContext == null) {
                    AiHiddenPostState()
                } else {
                    withContext(AppDispatchers.parsing) {
                        resolveAiHiddenPostState(
                            context = resolutionContext,
                            moderationResults = combinedModerationResults,
                            automaticallyHide = aiService.automaticallyHideModeratedPosts
                        )
                    }
                }
            }
            val deletionSummary = remember(state.page) { threadDeletionSummaryForPage(state.page) }
            when (bindings.threadDisplayMode) {
                ThreadDisplayMode.Flat -> ThreadContent(
                    page = filteredPage,
                    deletionSummary = deletionSummary,
                    originalPostId = state.page.posts.firstOrNull()?.id,
                    embeddedHtml = state.embeddedHtml,
                    summaryState = summaryState,
                    aiHiddenPostIds = aiHiddenPostState.postIds,
                    aiHiddenPostReasons = aiHiddenPostState.reasons,
                    listState = bindings.lazyListState,
                    saidaneOverrides = bindings.saidaneOverrides,
                    selfPostIdentifiers = bindings.selfPostIdentifierSet,
                    searchHighlightRanges = bindings.postHighlightRanges,
                    onPostLongPress = onPostLongPress,
                    onQuoteRequestedForPost = onQuoteRequestedForPost,
                    onSaidaneClick = bindings.onSaidaneClick,
                    onMediaClick = bindings.onMediaClick,
                    onMediaLongPress = bindings.onMediaLongPress,
                    onUrlClick = bindings.onUrlClick,
                    onRefresh = bindings.onRefresh,
                    isRefreshing = bindings.isRefreshing,
                    bodyTextSize = bindings.preferencesState.threadBodyTextSize,
                    postImageSize = bindings.preferencesState.threadPostImageSize,
                    compactHeader = bindings.preferencesState.isCompactThreadHeaderEnabled,
                    searchScrollRequest = bindings.searchScrollRequest,
                    onDisplayedPostsChanged = {
                        moderationDisplayedPosts = it.posts
                        bindings.onDisplayedPostsChanged(it)
                    },
                    newPostIds = bindings.newPostIds,
                    modifier = modifier.fillMaxSize()
                )

                ThreadDisplayMode.Tree -> ThreadTreeContent(
                    page = filteredPage,
                    deletionSummary = deletionSummary,
                    originalPostId = state.page.posts.firstOrNull()?.id,
                    embeddedHtml = state.embeddedHtml,
                    summaryState = summaryState,
                    aiHiddenPostIds = aiHiddenPostState.postIds,
                    aiHiddenPostReasons = aiHiddenPostState.reasons,
                    listState = bindings.lazyListState,
                    saidaneOverrides = bindings.saidaneOverrides,
                    selfPostIdentifiers = bindings.selfPostIdentifierSet,
                    searchHighlightRanges = bindings.postHighlightRanges,
                    onPostLongPress = onPostLongPress,
                    onQuoteRequestedForPost = onQuoteRequestedForPost,
                    onSaidaneClick = bindings.onSaidaneClick,
                    onMediaClick = bindings.onMediaClick,
                    onMediaLongPress = bindings.onMediaLongPress,
                    onUrlClick = bindings.onUrlClick,
                    onRefresh = bindings.onRefresh,
                    isRefreshing = bindings.isRefreshing,
                    bodyTextSize = bindings.preferencesState.threadBodyTextSize,
                    postImageSize = bindings.preferencesState.threadPostImageSize,
                    compactHeader = bindings.preferencesState.isCompactThreadHeaderEnabled,
                    searchScrollRequest = bindings.searchScrollRequest,
                    onDisplayedPostsChanged = {
                        moderationDisplayedPosts = it.posts
                        bindings.onDisplayedPostsChanged(it)
                    },
                    newPostIds = bindings.newPostIds,
                    modifier = modifier.fillMaxSize()
                )
            }
        }
    }
}

private suspend fun <T> runThreadAiInferenceWithTimeout(
    timeoutMillis: Long,
    aiInferenceMutex: Mutex,
    aiService: OnDeviceAiService,
    block: suspend () -> T
): T? {
    // Tracked outside the timeout: it can fire after lock() returned, making
    // withTimeoutOrNull return null while this coroutine holds the mutex,
    // which would block every later AI request on this screen.
    var locked = false
    val didLock = try {
        withTimeoutOrNull(timeoutMillis) {
            aiInferenceMutex.lock()
            locked = true
            true
        } ?: false
    } catch (e: CancellationException) {
        if (locked) aiInferenceMutex.unlock()
        throw e
    }
    if (!didLock) {
        if (locked) aiInferenceMutex.unlock()
        return null
    }
    return try {
        val result = withTimeoutOrNull(timeoutMillis) {
            ThreadAiInferenceTimeoutResult(
                value = withContext(AppDispatchers.io) {
                    // Platform AI calls must cooperate with cancellation. If they stop doing so,
                    // cancelActiveRequests() is the escape hatch before another request waits here.
                    block()
                }
            )
        }
        if (result == null) {
            aiService.cancelActiveRequests()
        }
        result?.value
    } finally {
        aiInferenceMutex.unlock()
    }
}

private class ThreadAiInferenceTimeoutResult<out T>(
    val value: T
)

internal fun shouldDeferAiPostModeration(
    shouldApplyAiPostFilter: Boolean,
    shouldShowThreadSummary: Boolean,
    summaryState: ThreadSummaryUiState?
): Boolean {
    if (!shouldApplyAiPostFilter) return true
    return shouldShowThreadSummary && summaryState == ThreadSummaryUiState.Loading
}

internal fun shouldPublishAiPostModerationBatch(
    index: Int,
    lastIndex: Int,
    didFail: Boolean
): Boolean {
    if (index >= lastIndex) return true
    if (didFail) return true
    return (index + 1) % THREAD_AI_POST_MODERATION_UI_UPDATE_BATCH_INTERVAL == 0
}
