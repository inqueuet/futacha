package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.withFrameNanos
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
    val onUrlClick: (String) -> Unit,
    val onRefresh: () -> Unit,
    val isRefreshing: Boolean
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
            val postsFingerprint by produceState(
                initialValue = buildLightweightThreadPostListFingerprint(state.page.posts),
                key1 = state.page.posts,
                key2 = threadFilterComputationState.shouldComputeFullPostFingerprint
            ) {
                value = if (threadFilterComputationState.shouldComputeFullPostFingerprint) {
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
            ThreadContent(
                page = filteredPage,
                embeddedHtml = state.embeddedHtml,
                listState = bindings.lazyListState,
                saidaneOverrides = bindings.saidaneOverrides,
                selfPostIdentifiers = bindings.selfPostIdentifierSet,
                searchHighlightRanges = bindings.postHighlightRanges,
                onPostLongPress = { post ->
                    bindings.setPostOverlayState(
                        openThreadPostActionOverlay(
                            currentState = bindings.postOverlayState,
                            post = post
                        )
                    )
                },
                onQuoteRequestedForPost = { post ->
                    bindings.setPostOverlayState(
                        openThreadQuoteOverlay(
                            currentState = bindings.postOverlayState,
                            post = post
                        )
                    )
                },
                onSaidaneClick = bindings.onSaidaneClick,
                onMediaClick = bindings.onMediaClick,
                onUrlClick = bindings.onUrlClick,
                onRefresh = bindings.onRefresh,
                isRefreshing = bindings.isRefreshing,
                modifier = modifier.fillMaxSize()
            )
        }
    }
}
