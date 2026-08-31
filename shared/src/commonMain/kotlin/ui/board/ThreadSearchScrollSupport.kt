package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.valoser.futacha.shared.model.Post
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

private const val THREAD_SEARCH_SCROLL_LAYOUT_TIMEOUT_MS = 2_000L

internal data class ThreadPostScrollRequest(
    val post: Post,
    val requestId: Long
)

internal fun resolveThreadPostScrollTargetIndex(
    request: ThreadPostScrollRequest,
    displayedPosts: List<Post>,
    itemsBeforePosts: Int
): Int? {
    val identityIndex = displayedPosts.indexOfFirst { candidate -> candidate === request.post }
    val postIndex = when {
        identityIndex >= 0 -> identityIndex
        else -> displayedPosts.indexOf(request.post).takeIf { it >= 0 }
            ?: displayedPosts.indexOfFirst { candidate -> candidate.id == request.post.id }
                .takeIf { it >= 0 }
    }
    return postIndex?.let { itemsBeforePosts.coerceAtLeast(0) + it }
}

internal fun calculateThreadCenteredScrollDelta(
    viewportStartOffset: Int,
    viewportEndOffset: Int,
    itemOffset: Int,
    itemSize: Int
): Float {
    val viewportCenter = (viewportStartOffset + viewportEndOffset) / 2f
    val itemCenter = itemOffset + itemSize / 2f
    return itemCenter - viewportCenter
}

@Composable
internal fun ThreadPostScrollEffect(
    request: ThreadPostScrollRequest?,
    displayedPosts: List<Post>,
    itemsBeforePosts: Int,
    listState: LazyListState
) {
    val targetIndex = request?.let {
        resolveThreadPostScrollTargetIndex(
            request = it,
            displayedPosts = displayedPosts,
            itemsBeforePosts = itemsBeforePosts
        )
    }
    LaunchedEffect(request?.requestId, targetIndex, listState) {
        if (targetIndex == null) return@LaunchedEffect

        val isTargetIndexAvailable = withTimeoutOrNull(THREAD_SEARCH_SCROLL_LAYOUT_TIMEOUT_MS) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }
                .first { totalItemsCount -> targetIndex in 0 until totalItemsCount }
        } != null
        if (!isTargetIndexAvailable) return@LaunchedEffect

        var itemInfo = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == targetIndex }
        if (itemInfo == null) {
            listState.animateScrollToItem(targetIndex)
            itemInfo = withTimeoutOrNull(THREAD_SEARCH_SCROLL_LAYOUT_TIMEOUT_MS) {
                snapshotFlow {
                    listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.index == targetIndex }
                }.first { it != null }
            }
        }

        val resolvedItemInfo = itemInfo ?: return@LaunchedEffect
        val layoutInfo = listState.layoutInfo
        val centeredScrollDelta = calculateThreadCenteredScrollDelta(
            viewportStartOffset = layoutInfo.viewportStartOffset,
            viewportEndOffset = layoutInfo.viewportEndOffset,
            itemOffset = resolvedItemInfo.offset,
            itemSize = resolvedItemInfo.size
        )
        if (abs(centeredScrollDelta) >= 1f) {
            listState.animateScrollBy(centeredScrollDelta)
        }
    }
}
