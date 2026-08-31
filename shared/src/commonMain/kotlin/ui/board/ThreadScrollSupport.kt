package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.lazy.LazyListState
import com.valoser.futacha.shared.model.Post
import kotlinx.coroutines.CancellationException

internal data class ThreadDisplayedPostsLayout(
    val posts: List<Post> = emptyList(),
    val itemsBeforePosts: Int = 0
)

internal data class ThreadScrollRestoreTarget(
    val index: Int,
    val offset: Int
)

internal fun resolveThreadScrollRestoreTarget(
    savedIndex: Int,
    savedOffset: Int,
    totalItems: Int,
    savedPostId: String? = null,
    displayedPostsLayout: ThreadDisplayedPostsLayout? = null
): ThreadScrollRestoreTarget? {
    if (totalItems <= 0) return null
    val stablePostIndex = savedPostId
        ?.let { id -> displayedPostsLayout?.posts?.indexOfFirst { it.id == id } }
        ?.takeIf { it >= 0 }
        ?.let { displayedPostsLayout!!.itemsBeforePosts.coerceAtLeast(0) + it }
    return ThreadScrollRestoreTarget(
        index = (stablePostIndex ?: savedIndex).coerceIn(0, totalItems - 1),
        offset = savedOffset.coerceAtLeast(0)
    )
}

internal fun buildThreadScrollRestoreFailureMessage(
    index: Int,
    offset: Int,
    error: Throwable
): String {
    return "Failed to restore scroll position index=$index offset=$offset: ${error.message}"
}

internal suspend fun restoreThreadScrollPositionSafely(
    listState: LazyListState,
    savedIndex: Int,
    savedOffset: Int,
    totalItems: Int,
    savedPostId: String? = null,
    displayedPostsLayout: ThreadDisplayedPostsLayout? = null,
    onFailure: (String, Throwable) -> Unit = { _, _ -> }
): Boolean {
    val target = resolveThreadScrollRestoreTarget(
        savedIndex = savedIndex,
        savedOffset = savedOffset,
        totalItems = totalItems,
        savedPostId = savedPostId,
        displayedPostsLayout = displayedPostsLayout
    ) ?: return false
    return try {
        listState.scrollToItem(target.index, target.offset)
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        onFailure(buildThreadScrollRestoreFailureMessage(target.index, target.offset, error), error)
        false
    }
}

internal fun resolveVisibleThreadPostId(
    visibleItemIndex: Int,
    layout: ThreadDisplayedPostsLayout
): String? = layout.posts.getOrNull(visibleItemIndex - layout.itemsBeforePosts)?.id
