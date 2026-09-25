package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.lazy.LazyListState
import com.valoser.futacha.shared.model.Post
import kotlinx.coroutines.CancellationException

internal data class ThreadDisplayedPostsLayout(
    val posts: List<Post> = emptyList(),
    val itemsBeforePosts: Int = 0,
    /** Posts currently collapsed by the AI post filter (not revealed by the user). */
    val collapsedPostIds: Set<String> = emptySet()
)

internal data class ThreadScrollRestoreTarget(
    val index: Int,
    val offset: Int
)

/** Kept for one open thread; failed loads and equal refreshes leave its new-post boundary intact. */
internal class ThreadNewPostTracker(private val initialKnownPostCount: Int? = null) {
    private var knownPostIds: Set<String>? = null
    private var awaitingHistoryBaseline = initialKnownPostCount != null
    var newPostIds: Set<String> = emptySet()
        private set

    fun onPostsLoaded(posts: List<Post>) {
        val currentIds = posts.mapTo(mutableSetOf()) { it.id }
        val knownIds = knownPostIds
        val addedIds = when {
            awaitingHistoryBaseline -> {
                val count = initialKnownPostCount!!.coerceAtLeast(0)
                if (posts.size >= count) awaitingHistoryBaseline = false
                posts.drop(count).mapTo(mutableSetOf()) { it.id }
            }
            knownIds == null -> emptySet()
            else -> currentIds - knownIds
        }
        newPostIds = if (addedIds.isNotEmpty()) addedIds else newPostIds.intersect(currentIds)
        knownPostIds = knownIds.orEmpty() + currentIds
    }
}

internal fun resolveThreadBottomScrollTarget(
    layout: ThreadDisplayedPostsLayout,
    newPostIds: Set<String>,
    firstVisibleItemIndex: Int,
    totalItems: Int
): Int? {
    if (totalItems <= 0) return null
    val newPostIndex = layout.posts.indexOfFirst { it.id in newPostIds }
    val newItemIndex = if (newPostIndex >= 0) layout.itemsBeforePosts.coerceAtLeast(0) + newPostIndex else -1
    return if (newItemIndex in (firstVisibleItemIndex + 1) until totalItems) {
        newItemIndex
    } else {
        totalItems - 1
    }
}

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

internal fun createThreadScrollPersistHandler(
    layoutProvider: () -> ThreadDisplayedPostsLayout,
    persist: (threadId: String, index: Int, offset: Int, postId: String?) -> Unit
): (threadId: String, index: Int, offset: Int) -> Unit = { threadId, index, offset ->
    persist(threadId, index, offset, resolveVisibleThreadPostId(index, layoutProvider()))
}

internal fun resolveVisibleThreadPostId(
    visibleItemIndex: Int,
    layout: ThreadDisplayedPostsLayout
): String? = layout.posts.getOrNull(visibleItemIndex - layout.itemsBeforePosts)?.id
