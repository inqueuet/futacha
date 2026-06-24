package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import coil3.compose.LocalPlatformContext
import com.valoser.futacha.shared.ai.OnDeviceAiService
import com.valoser.futacha.shared.ai.PostModerationInput
import com.valoser.futacha.shared.ai.PostModerationResult
import com.valoser.futacha.shared.ai.ThreadSummaryInput
import com.valoser.futacha.shared.ai.createOnDeviceAiService
import com.valoser.futacha.shared.ai.normalizeThreadSummary
import com.valoser.futacha.shared.model.ThreadDisplayMode
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.lazy.LazyListState

private const val THREAD_AI_SUMMARY_UI_TIMEOUT_MS = 20_000L
private const val THREAD_AI_POST_MODERATION_BATCH_TIMEOUT_MS = 25_000L
private const val THREAD_AI_POST_MODERATION_BATCH_SIZE = 8
private const val THREAD_AI_POST_MODERATION_BATCH_DELAY_MS = 120L
private const val THREAD_AI_POST_MODERATION_UI_UPDATE_BATCH_INTERVAL = 2
private const val THREAD_AI_POST_MODERATION_START_DELAY_MS = 1_500L
private const val THREAD_AI_POST_MODERATION_SUMMARY_WAIT_DELAY_MS = 120L
private const val THREAD_AI_SUMMARY_START_DELAY_MS = 120L

internal data class ThreadScreenContentHostBindings(
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
            val cachedFilteredPage = remember(filterCacheKey, hasNgFilters, hasThreadFilters, state.page) {
                if (!hasNgFilters && !hasThreadFilters) {
                    null
                } else {
                    bindings.threadFilterCache[filterCacheKey]?.toThreadPage(state.page)
                }
            }
            var lastVisibleFilteredPage by remember(state.page) { mutableStateOf(state.page) }
            val filteredPage by produceState(
                initialValue = cachedFilteredPage ?: lastVisibleFilteredPage,
                key1 = filterCacheKey
            ) {
                bindings.threadFilterCache[filterCacheKey]?.let { cachedResult ->
                    value = cachedResult.toThreadPage(state.page)
                    lastVisibleFilteredPage = value
                    return@produceState
                }
                if (!hasNgFilters && !hasThreadFilters) {
                    value = state.page
                    lastVisibleFilteredPage = value
                    return@produceState
                }
                if (threadFilterComputationState.criteria.options.contains(ThreadFilterOption.Keyword)) {
                    delay(THREAD_FILTER_DEBOUNCE_MILLIS)
                }
                val filterResult = withContext(AppDispatchers.parsing) {
                    val hasNgWordFilters = hasNgFilters && bindings.ngWords.any { it.isNotBlank() }
                    val hasThreadLowerBodyFilters = threadFilterComputationState.criteria.options.any {
                        it == ThreadFilterOption.Url || it == ThreadFilterOption.Keyword
                    }
                    val precomputedLowerBodyByPost = if (hasNgWordFilters || hasThreadLowerBodyFilters) {
                        buildLowerBodyByPost(state.page.posts, bindings.postTextCache)
                    } else {
                        emptyMap()
                    }
                    applyThreadFilterResult(
                        page = state.page,
                        criteria = threadFilterComputationState.criteria,
                        ngHeaders = bindings.ngHeaders,
                        ngWords = bindings.ngWords,
                        ngEnabled = hasNgFilters,
                        precomputedLowerBodyByPost = precomputedLowerBodyByPost
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
                value = filterResult.toThreadPage(state.page)
                lastVisibleFilteredPage = value
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
            val aiService = remember(platformContext) { createOnDeviceAiService(platformContext) }
            val aiInferenceMutex = remember { Mutex() }
            val threadSummaryCache = remember { linkedMapOf<ThreadSummaryCacheKey, ThreadSummaryUiState.Ready>() }
            val threadPostModerationCache = remember { linkedMapOf<ThreadPostModerationCacheKey, PostModerationResult>() }
            val aiSourcePosts = remember(state.page) { resolveThreadAiSourcePosts(state.page) }
            val aiPostModerationSourcePosts = remember(aiSourcePosts) {
                resolveThreadAiPostModerationSourcePosts(aiSourcePosts)
            }
            val aiCacheKey = remember(
                state.page.threadId,
                postsFingerprint,
                bindings.preferencesState.aiAvailability.providerLabel
            ) {
                ThreadAiCacheKey(
                    threadId = state.page.threadId,
                    postsFingerprint = postsFingerprint,
                    providerLabel = bindings.preferencesState.aiAvailability.providerLabel
                )
            }
            val threadSummaryCacheKey = remember(
                state.page.threadId,
                bindings.preferencesState.aiAvailability.providerLabel
            ) {
                buildThreadSummaryCacheKey(
                    threadId = state.page.threadId,
                    providerLabel = bindings.preferencesState.aiAvailability.providerLabel
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
                    title = null,
                    posts = aiSourcePosts
                )
                val summaryResult = runThreadAiInferenceWithTimeout(
                    timeoutMillis = THREAD_AI_SUMMARY_UI_TIMEOUT_MS,
                    aiInferenceMutex = aiInferenceMutex,
                    aiService = aiService
                ) {
                    aiService.summarizeThread(summaryInput)
                }
                value = summaryResult?.fold(
                    onSuccess = {
                        ThreadSummaryUiState.Ready(normalizeThreadSummary(it)).also { readyState ->
                            putBoundedAiCacheEntry(
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
            val aiPostModerationUiState by produceState(
                initialValue = AiPostModerationUiState(isEnabled = shouldApplyAiPostFilter),
                key1 = shouldApplyAiPostFilter,
                key2 = aiCacheKey,
                key3 = shouldShowThreadSummary
            ) {
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
                val input = PostModerationInput(
                    threadId = state.page.threadId,
                    posts = aiPostModerationSourcePosts
                )
                val cachedResults = linkedMapOf<String, PostModerationResult>()
                val uncachedPosts = mutableListOf<Post>()
                input.posts.forEach { post ->
                    val postCacheKey = buildThreadPostModerationCacheKey(
                        threadId = input.threadId,
                        post = post,
                        providerLabel = bindings.preferencesState.aiAvailability.providerLabel
                    )
                    val cachedResult = threadPostModerationCache[postCacheKey]
                    if (cachedResult != null) {
                        cachedResults[post.id] = cachedResult
                    } else {
                        uncachedPosts += post
                    }
                }
                if (uncachedPosts.isEmpty()) {
                    value = AiPostModerationUiState(
                        isEnabled = true,
                        isRunning = false,
                        processedPosts = input.posts.size,
                        totalPosts = input.posts.size,
                        results = cachedResults.values.toList()
                    )
                    return@produceState
                }
                val postBatches = withContext(AppDispatchers.parsing) {
                    uncachedPosts.chunked(THREAD_AI_POST_MODERATION_BATCH_SIZE)
                }
                val mergedModeration = linkedMapOf<String, PostModerationResult>().apply {
                    putAll(cachedResults)
                }
                var publishedModeration = cachedResults.values.toList()
                var processedPosts = cachedResults.size
                var failedBatchCount = 0
                value = AiPostModerationUiState(
                    isEnabled = true,
                    isRunning = postBatches.isNotEmpty(),
                    processedPosts = processedPosts,
                    totalPosts = input.posts.size,
                    results = publishedModeration
                )
                delay(THREAD_AI_POST_MODERATION_START_DELAY_MS)
                postBatches.forEachIndexed { index, posts ->
                    val batchInput = input.copy(posts = posts)
                    val moderationResult = runThreadAiInferenceWithTimeout(
                        timeoutMillis = THREAD_AI_POST_MODERATION_BATCH_TIMEOUT_MS,
                        aiInferenceMutex = aiInferenceMutex,
                        aiService = aiService
                    ) {
                        aiService.classifyPosts(batchInput)
                    }
                    val moderation = moderationResult?.getOrNull()
                    processedPosts += posts.size
                    if (moderationResult == null || moderation == null) {
                        failedBatchCount += 1
                    } else {
                        moderation.forEach { result ->
                            mergedModeration[result.postId] = result
                            posts.firstOrNull { it.id == result.postId }?.let { post ->
                                putBoundedAiCacheEntry(
                                    cache = threadPostModerationCache,
                                    key = buildThreadPostModerationCacheKey(
                                        threadId = input.threadId,
                                        post = post,
                                        providerLabel = bindings.preferencesState.aiAvailability.providerLabel
                                    ),
                                    value = result,
                                    maxEntries = THREAD_AI_POST_MODERATION_CACHE_MAX_ENTRIES
                                )
                            }
                        }
                    }
                    val shouldPublishBatch = shouldPublishAiPostModerationBatch(
                        index = index,
                        lastIndex = postBatches.lastIndex,
                        didFail = moderationResult == null || moderation == null
                    )
                    if (shouldPublishBatch) {
                        publishedModeration = mergedModeration.values.toList()
                        value = AiPostModerationUiState(
                            isEnabled = true,
                            isRunning = index != postBatches.lastIndex,
                            processedPosts = processedPosts,
                            totalPosts = input.posts.size,
                            failedBatchCount = failedBatchCount,
                            results = publishedModeration
                        )
                    }
                    if (index != postBatches.lastIndex) {
                        delay(THREAD_AI_POST_MODERATION_BATCH_DELAY_MS)
                    }
                }
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
                key2 = aiPostModerationUiState.results
            ) {
                val resolutionContext = aiHiddenPostResolutionContext
                value = if (resolutionContext == null) {
                    AiHiddenPostState()
                } else {
                    withContext(AppDispatchers.parsing) {
                        resolveAiHiddenPostState(
                            context = resolutionContext,
                            moderationResults = aiPostModerationUiState.results
                        )
                    }
                }
            }
            when (bindings.threadDisplayMode) {
                ThreadDisplayMode.Flat -> ThreadContent(
                    page = filteredPage,
                    embeddedHtml = state.embeddedHtml,
                    summaryState = summaryState,
                    aiPostModerationUiState = aiPostModerationUiState,
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
                    modifier = modifier.fillMaxSize()
                )

                ThreadDisplayMode.Tree -> ThreadTreeContent(
                    page = filteredPage,
                    embeddedHtml = state.embeddedHtml,
                    summaryState = summaryState,
                    aiPostModerationUiState = aiPostModerationUiState,
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
    val didLock = withTimeoutOrNull(timeoutMillis) {
        aiInferenceMutex.lock()
        true
    } ?: false
    if (!didLock) {
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
