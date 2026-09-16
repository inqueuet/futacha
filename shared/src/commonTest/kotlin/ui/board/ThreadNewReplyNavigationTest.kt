package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThreadNewReplyNavigationTest {
    @Test fun firstVisitHasNoMarkerAndRefreshFindsNewRepliesByIdentity() {
        val tracker = ThreadNewPostTracker()
        tracker.onPostsLoaded(posts(1, 2, 3))
        assertTrue(tracker.newPostIds.isEmpty())
        tracker.onPostsLoaded(posts(1, 3, 4)) // deletion must not hide the appended reply
        assertEquals(setOf("4"), tracker.newPostIds)
        tracker.onPostsLoaded(posts(1, 3, 4))
        assertEquals(setOf("4"), tracker.newPostIds)
        tracker.onPostsLoaded(posts(1, 2, 3, 4, 5)) // restored old reply is not new
        assertEquals(setOf("5"), tracker.newPostIds)
    }

    @Test fun reopeningUsesHistoryCountAndWaitsThroughShorterCachedCopies() {
        val tracker = ThreadNewPostTracker(initialKnownPostCount = 3)
        tracker.onPostsLoaded(posts(1, 2))
        assertTrue(tracker.newPostIds.isEmpty())
        tracker.onPostsLoaded(posts(1, 2, 3, 4, 5))
        assertEquals(setOf("4", "5"), tracker.newPostIds)
    }

    @Test fun emptyAndRemovedRepliesCannotLeaveAnInvalidNewBoundary() {
        val tracker = ThreadNewPostTracker()
        tracker.onPostsLoaded(posts(1))
        tracker.onPostsLoaded(posts(1, 2))
        tracker.onPostsLoaded(emptyList())
        assertTrue(tracker.newPostIds.isEmpty())
        tracker.onPostsLoaded(posts(1, 2))
        assertTrue(tracker.newPostIds.isEmpty())
        assertTrue(ThreadNewPostTracker().newPostIds.isEmpty())
    }

    @Test fun bottomStopsAtNewReplyThenContinuesToFooter() {
        val layout = ThreadDisplayedPostsLayout(posts(1, 2, 3, 4, 5), itemsBeforePosts = 2)
        assertEquals(5, resolveThreadBottomScrollTarget(layout, setOf("4", "5"), 0, 8))
        assertEquals(7, resolveThreadBottomScrollTarget(layout, setOf("4", "5"), 5, 8))
        assertEquals(7, resolveThreadBottomScrollTarget(layout, setOf("4", "5"), 6, 8))
    }

    @Test fun bottomUsesFilteredAndTreeDisplayOrderAndSkipsHiddenNewReplies() {
        val layout = ThreadDisplayedPostsLayout(posts(1, 5, 2), itemsBeforePosts = 1)
        assertEquals(2, resolveThreadBottomScrollTarget(layout, setOf("4", "5"), 0, 5))
        assertEquals(4, resolveThreadBottomScrollTarget(layout, setOf("4"), 0, 5))
        assertEquals(4, resolveThreadBottomScrollTarget(layout, emptySet(), 0, 5))
    }

    @Test fun bottomHandlesEmptyListsAndStaleLayoutsWithoutInvalidIndex() {
        assertNull(resolveThreadBottomScrollTarget(ThreadDisplayedPostsLayout(), emptySet(), 0, 0))
        assertEquals(0, resolveThreadBottomScrollTarget(ThreadDisplayedPostsLayout(), emptySet(), 0, 1))
        assertEquals(1, resolveThreadBottomScrollTarget(ThreadDisplayedPostsLayout(posts(1, 2), 5), setOf("2"), 0, 2))
    }

    private fun posts(vararg ids: Int) = ids.map { id ->
        Post(id.toString(), author = null, subject = null, timestamp = "", messageHtml = "body", imageUrl = null, thumbnailUrl = null)
    }
}
