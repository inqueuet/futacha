package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import coil3.compose.LocalPlatformContext
import com.valoser.futacha.shared.ai.PostModerationInput
import com.valoser.futacha.shared.ai.PostModerationResult
import com.valoser.futacha.shared.ai.ThreadSummaryInput
import com.valoser.futacha.shared.ai.createOnDeviceAiService
import com.valoser.futacha.shared.model.ThreadDisplayMode
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.LazyListState

internal data class ThreadScreenContentHostBindings(
    val uiState: ThreadUiState,
    val refreshThread: () -> Unit,
    val threadFilterBinding: ThreadFilterUiStateBinding,
    val threadDisplayMode: ThreadDisplayMode,
    val persistedSelfPostIdentifiers: List<String>,
    val ngHeaders: List<String>,
    val ngWords: List<String>,
    val ngFilteringEnabled: Boolean,
    val threadFilterCache: LinkedHashMap<ThreadFilterCacheKey, ThreadPage>,
    val lazyListState: LazyListState,
    val saidaneOverrides: Map<String, String>,
    val selfPostIdentifierSet: Set<String>,
    val postHighlightRanges: Map<String, List<IntRange>>,
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
                        buildThreadPostListFingerprint(state.page.posts)
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
            val cachedFilteredPage = remember(filterCacheKey, hasNgFilters, hasThreadFilters) {
                if (!hasNgFilters && !hasThreadFilters) {
                    null
                } else {
                    bindings.threadFilterCache[filterCacheKey]
                }
            }
            val filteredPage by produceState(
                initialValue = cachedFilteredPage ?: state.page,
                key1 = filterCacheKey
            ) {
                bindings.threadFilterCache[filterCacheKey]?.let {
                    value = it
                    return@produceState
                }
                if (!hasNgFilters && !hasThreadFilters) {
                    value = state.page
                    return@produceState
                }
                if (threadFilterComputationState.criteria.options.contains(ThreadFilterOption.Keyword)) {
                    delay(THREAD_FILTER_DEBOUNCE_MILLIS)
                }
                value = withContext(AppDispatchers.parsing) {
                    val hasNgWordFilters = hasNgFilters && bindings.ngWords.any { it.isNotBlank() }
                    val hasThreadLowerBodyFilters = threadFilterComputationState.criteria.options.any {
                        it == ThreadFilterOption.Url || it == ThreadFilterOption.Keyword
                    }
                    val precomputedLowerBodyByPostId = if (hasNgWordFilters || hasThreadLowerBodyFilters) {
                        buildLowerBodyByPostId(state.page.posts)
                    } else {
                        emptyMap()
                    }
                    val ngFiltered = applyNgFilters(
                        page = state.page,
                        ngHeaders = bindings.ngHeaders,
                        ngWords = bindings.ngWords,
                        enabled = hasNgFilters,
                        precomputedLowerBodyByPostId = precomputedLowerBodyByPostId
                    )
                    applyThreadFilters(
                        page = ngFiltered,
                        criteria = threadFilterComputationState.criteria,
                        precomputedLowerBodyByPostId = precomputedLowerBodyByPostId
                    )
                }
                if (bindings.threadFilterCache.size >= THREAD_FILTER_CACHE_MAX_ENTRIES) {
                    val iterator = bindings.threadFilterCache.entries.iterator()
                    if (iterator.hasNext()) {
                        iterator.next()
                        iterator.remove()
                    }
                }
                bindings.threadFilterCache[filterCacheKey] = value
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
            val threadSummaryCache = remember { linkedMapOf<ThreadAiCacheKey, ThreadSummaryUiState.Ready>() }
            val threadPostModerationCache = remember { linkedMapOf<ThreadAiCacheKey, List<PostModerationResult>>() }
            val aiSourcePosts = remember(state.page) { resolveThreadAiSourcePosts(state.page) }
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
            val summaryState by produceState<ThreadSummaryUiState?>(
                initialValue = resolveInitialThreadSummaryUiState(shouldShowThreadSummary),
                key1 = shouldShowThreadSummary,
                key2 = aiCacheKey
            ) {
                if (!shouldShowThreadSummary) {
                    value = null
                    return@produceState
                }
                threadSummaryCache[aiCacheKey]?.let {
                    value = it
                    return@produceState
                }
                value = ThreadSummaryUiState.Loading
                delay(120L)
                val summaryInput = ThreadSummaryInput(
                    threadId = state.page.threadId,
                    title = null,
                    posts = aiSourcePosts
                )
                value = aiService.summarizeThread(summaryInput).fold(
                    onSuccess = {
                        ThreadSummaryUiState.Ready(it).also { readyState ->
                            putThreadAiCacheEntry(threadSummaryCache, aiCacheKey, readyState)
                        }
                    },
                    onFailure = {
                        ThreadSummaryUiState.Unavailable(
                            it.message ?: "スレ要約を生成できませんでした。"
                        )
                    }
                )
            }
            val aiPostModerationResults by produceState<List<PostModerationResult>>(
                initialValue = emptyList(),
                key1 = shouldApplyAiPostFilter,
                key2 = aiCacheKey
            ) {
                if (!shouldApplyAiPostFilter) {
                    value = emptyList()
                    return@produceState
                }
                threadPostModerationCache[aiCacheKey]?.let {
                    value = it
                    return@produceState
                }
                val input = PostModerationInput(
                    threadId = state.page.threadId,
                    posts = aiSourcePosts
                )
                value = aiService.classifyPosts(input)
                    .getOrDefault(emptyList())
                    .also {
                        putThreadAiCacheEntry(threadPostModerationCache, aiCacheKey, it)
                    }
            }
            val aiHiddenPostState = remember(
                filteredPage.posts,
                aiPostModerationResults,
                bindings.selfPostIdentifierSet
            ) {
                resolveAiHiddenPostState(
                    posts = filteredPage.posts,
                    moderationResults = aiPostModerationResults,
                    selfPostIdentifiers = bindings.selfPostIdentifierSet
                )
            }
            when (bindings.threadDisplayMode) {
                ThreadDisplayMode.Flat -> ThreadContent(
                    page = filteredPage,
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
                    modifier = modifier.fillMaxSize()
                )

                ThreadDisplayMode.Tree -> ThreadTreeContent(
                    page = filteredPage,
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
                    modifier = modifier.fillMaxSize()
                )
            }
        }
    }
}
