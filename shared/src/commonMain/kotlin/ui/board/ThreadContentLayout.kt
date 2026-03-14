package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ThreadContent(
    page: ThreadPage,
    listState: LazyListState,
    saidaneOverrides: Map<String, String>,
    selfPostIdentifiers: Set<String> = emptySet(),
    searchHighlightRanges: Map<String, List<IntRange>>,
    onPostLongPress: (Post) -> Unit,
    onQuoteRequestedForPost: (Post) -> Unit,
    onSaidaneClick: (Post) -> Unit,
    onMediaClick: ((String, MediaType) -> Unit)? = null,
    onUrlClick: (String) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val derivedPostData by produceState(
        initialValue = ThreadPostDerivedData(),
        key1 = page.posts
    ) {
        value = withContext(AppDispatchers.parsing) {
            buildThreadPostDerivedData(page.posts)
        }
    }
    val posterIdLabels = derivedPostData.posterIdLabels
    val postIndex = derivedPostData.postIndex
    val referencedByMap = derivedPostData.referencedByMap
    val postsByPosterId = derivedPostData.postsByPosterId
    var quotePreviewState by remember(page.posts) { mutableStateOf<QuotePreviewState?>(null) }
    val edgeSwipeRefreshBinding = rememberEdgeSwipeRefreshBinding(
        listState = listState,
        isRefreshing = isRefreshing,
        animationLabel = "threadOverscroll"
    )
    val isScrolling = listState.isScrollInProgress
    val showQuotePreview = buildThreadScreenQuotePreviewPresenter(
        isScrolling = { isScrolling },
        posterIdLabels = posterIdLabels,
        setState = { quotePreviewState = it }
    )

    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, edgeSwipeRefreshBinding.visualState.overscrollOffset.toInt()) }
                    .edgeSwipeRefresh(
                        isRefreshing = isRefreshing,
                        isAtTop = edgeSwipeRefreshBinding.edgeState.isAtTop,
                        isAtBottom = edgeSwipeRefreshBinding.edgeState.isAtBottom,
                        maxOverscrollPx = edgeSwipeRefreshBinding.metrics.maxOverscrollPx,
                        refreshTriggerPx = edgeSwipeRefreshBinding.metrics.refreshTriggerPx,
                        onOverscrollTargetChanged = edgeSwipeRefreshBinding.visualState::onOverscrollTargetChanged,
                        onRefresh = onRefresh
                    ),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = 24.dp
                )
            ) {
                page.deletedNotice?.takeIf { it.isNotBlank() }?.let { notice ->
                    item(key = "thread-notice") {
                        ThreadNoticeCard(message = notice)
                    }
                }
                itemsIndexed(
                    items = page.posts,
                    key = { _, post -> post.id }
                ) { index, post ->
                    val isSelfPost = selfPostIdentifiers.contains(post.id.trim())
                    val normalizedPosterId = normalizePosterIdValue(post.posterId)
                    val postCardCallbacks = buildThreadScreenPostCardCallbacks(
                        post = post,
                        normalizedPosterId = normalizedPosterId,
                        postIndex = postIndex,
                        referencedByMap = referencedByMap,
                        postsByPosterId = postsByPosterId,
                        quotePreviewState = quotePreviewState,
                        onShowQuotePreview = showQuotePreview,
                        onQuoteRequestedForPost = onQuoteRequestedForPost,
                        onSaidaneClick = onSaidaneClick,
                        onMediaClick = onMediaClick,
                        onPostLongPress = onPostLongPress
                    )
                    ThreadPostCard(
                        post = post,
                        isOp = index == 0,
                        isSelfPost = isSelfPost,
                        posterIdLabel = posterIdLabels[post.id],
                        posterIdValue = normalizedPosterId,
                        saidaneLabelOverride = saidaneOverrides[post.id],
                        highlightRanges = searchHighlightRanges[post.id] ?: emptyList(),
                        onQuoteClick = postCardCallbacks.onQuoteClick,
                        onUrlClick = onUrlClick,
                        onQuoteRequested = postCardCallbacks.onQuoteRequested,
                        onPosterIdClick = postCardCallbacks.onPosterIdClick,
                        onReferencedByClick = postCardCallbacks.onReferencedByClick,
                        onSaidaneClick = postCardCallbacks.onSaidaneClick,
                        onMediaClick = postCardCallbacks.onMediaClick,
                        onLongPress = postCardCallbacks.onLongPress,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (index != page.posts.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                        )
                    }
                }
                page.expiresAtLabel?.takeIf { it.isNotBlank() }?.let { footerLabel ->
                    item(key = "thread-expires-label") {
                        Text(
                            text = footerLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 16.dp)
                        )
                    }
                }
            }

            ThreadScrollbar(
                listState = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(vertical = 12.dp, horizontal = 4.dp)
            )
        }
    }
    quotePreviewState?.let { state ->
        val quotePreviewCallbacks = buildThreadScreenQuotePreviewCallbacks(
            postIndex = postIndex,
            onDismiss = { quotePreviewState = null },
            onShowQuotePreview = showQuotePreview
        )
        QuotePreviewDialog(
            state = state,
            onDismiss = quotePreviewCallbacks.onDismiss,
            onMediaClick = onMediaClick,
            onUrlClick = onUrlClick,
            onQuoteClick = quotePreviewCallbacks.onQuoteClick
        )
    }
}
