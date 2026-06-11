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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.model.EmbeddedHtmlContent
import com.valoser.futacha.shared.model.EmbeddedHtmlPlacement
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.withContext

internal data class ThreadTreeNode(
    val post: Post,
    val depth: Int
)

internal fun buildThreadTreeNodes(posts: List<Post>): List<ThreadTreeNode> {
    if (posts.isEmpty()) return emptyList()
    val orderIndex = posts.mapIndexed { index, post -> post.id to index }.toMap()
    val postIndex = posts.associateBy { it.id }
    val parentByPostId = mutableMapOf<String, String?>()
    val childrenByParentId = linkedMapOf<String, MutableList<Post>>()

    posts.forEach { post ->
        val primaryParentId = post.quoteReferences
            .asSequence()
            .flatMap { it.targetPostIds.asSequence() }
            .mapNotNull { targetId ->
                val target = postIndex[targetId] ?: return@mapNotNull null
                val targetIndex = orderIndex[target.id] ?: return@mapNotNull null
                val postOrder = orderIndex[post.id] ?: return@mapNotNull null
                if (target.id == post.id || targetIndex >= postOrder) {
                    null
                } else {
                    target.id
                }
            }
            .firstOrNull()
        parentByPostId[post.id] = primaryParentId
        if (primaryParentId != null) {
            childrenByParentId.getOrPut(primaryParentId) { mutableListOf() }.add(post)
        }
    }

    val roots = posts.filter { parentByPostId[it.id] == null }
    val visited = linkedSetOf<String>()
    val result = mutableListOf<ThreadTreeNode>()

    fun visit(post: Post, depth: Int) {
        if (!visited.add(post.id)) return
        result += ThreadTreeNode(post = post, depth = depth)
        childrenByParentId[post.id]
            ?.sortedBy { child -> orderIndex[child.id] ?: Int.MAX_VALUE }
            ?.forEach { child ->
                visit(child, (depth + 1).coerceAtMost(12))
            }
    }

    roots.forEach { visit(it, 0) }
    posts.forEach { post ->
        if (post.id !in visited) {
            visit(post, 0)
        }
    }
    return result
}

@Composable
internal fun ThreadTreeContent(
    page: ThreadPage,
    embeddedHtml: List<EmbeddedHtmlContent>,
    summaryState: ThreadSummaryUiState?,
    aiPostModerationUiState: AiPostModerationUiState = AiPostModerationUiState(),
    aiHiddenPostIds: Set<String> = emptySet(),
    aiHiddenPostReasons: Map<String, String> = emptyMap(),
    listState: LazyListState,
    saidaneOverrides: Map<String, String>,
    selfPostIdentifiers: Set<String> = emptySet(),
    searchHighlightRanges: Map<String, List<IntRange>>,
    onPostLongPress: (Post) -> Unit,
    onQuoteRequestedForPost: (Post) -> Unit,
    onSaidaneClick: (Post) -> Unit,
    onMediaClick: ((String, MediaType) -> Unit)? = null,
    onMediaLongPress: ((Post, String, MediaType) -> Unit)? = null,
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
    val treeNodes by produceState(
        initialValue = emptyList<ThreadTreeNode>(),
        key1 = page.posts
    ) {
        value = withContext(AppDispatchers.parsing) {
            buildThreadTreeNodes(page.posts)
        }
    }
    val posterIdLabels = derivedPostData.posterIdLabels
    val postIndex = derivedPostData.postIndex
    val referencedByMap = derivedPostData.referencedByMap
    val postsByPosterId = derivedPostData.postsByPosterId
    var quotePreviewState by remember(page.posts) { mutableStateOf<QuotePreviewState?>(null) }
    val revealedAiHiddenPostIds = remember(page.threadId, aiHiddenPostIds) { mutableStateListOf<String>() }
    val edgeSwipeRefreshBinding = rememberEdgeSwipeRefreshBinding(
        listState = listState,
        isRefreshing = isRefreshing,
        animationLabel = "threadTreeOverscroll"
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
                    top = if (summaryState != null) 0.dp else 8.dp,
                    bottom = 24.dp
                )
            ) {
                summaryState?.let { state ->
                    item(key = "thread-tree-summary") {
                        ThreadSummaryCard(
                            state = state,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (aiPostModerationUiState.isEnabled) {
                    item(key = "thread-tree-ai-post-moderation-progress") {
                        AiPostModerationProgressCard(
                            state = aiPostModerationUiState,
                            hiddenPostCount = aiHiddenPostIds.size,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (embeddedHtml.any { it.placement == EmbeddedHtmlPlacement.Header }) {
                    item(key = "thread-tree-embedded-html-header") {
                        EmbeddedHtmlSection(
                            snippets = embeddedHtml,
                            placement = EmbeddedHtmlPlacement.Header,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                page.deletedNotice?.takeIf { it.isNotBlank() }?.let { notice ->
                    item(key = "thread-tree-notice") {
                        ThreadNoticeCard(message = notice)
                    }
                }
                if (aiHiddenPostIds.any { it !in revealedAiHiddenPostIds }) {
                    item(key = "thread-tree-ai-hidden-posts-summary") {
                        AiHiddenPostsSummaryCard(
                            onRevealAll = {
                                revealedAiHiddenPostIds.addAll(
                                    aiHiddenPostIds.filter { it !in revealedAiHiddenPostIds }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                itemsIndexed(
                    items = treeNodes,
                    key = { _, node -> "tree-${node.post.id}" }
                ) { index, node ->
                    val post = node.post
                    val isSelfPost = selfPostIdentifiers.contains(post.id.trim())
                    val isAiHidden = post.id in aiHiddenPostIds && post.id !in revealedAiHiddenPostIds
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
                        onMediaLongPress = onMediaLongPress,
                        onPostLongPress = onPostLongPress
                    )
                    val indent = (node.depth * 18).coerceAtMost(108).dp
                    if (isAiHidden) {
                        AiHiddenPostPlaceholder(
                            postId = post.id,
                            reason = aiHiddenPostReasons[post.id],
                            onReveal = { revealedAiHiddenPostIds.add(post.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = indent)
                        )
                    } else {
                        ThreadPostCard(
                            post = post,
                            isOp = post.id == page.posts.firstOrNull()?.id,
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
                            onMediaLongPress = postCardCallbacks.onMediaLongPress,
                            onLongPress = postCardCallbacks.onLongPress,
                            onAiHideAgain = if (post.id in aiHiddenPostIds) {
                                { revealedAiHiddenPostIds.remove(post.id) }
                            } else {
                                null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = indent)
                        )
                    }
                    if (index != treeNodes.lastIndex) {
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
                    item(key = "thread-tree-expires-label") {
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
                if (embeddedHtml.any { it.placement == EmbeddedHtmlPlacement.Footer }) {
                    item(key = "thread-tree-embedded-html-footer") {
                        EmbeddedHtmlSection(
                            snippets = embeddedHtml,
                            placement = EmbeddedHtmlPlacement.Footer,
                            modifier = Modifier.fillMaxWidth()
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
