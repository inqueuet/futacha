package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertEquals

class CompatNewReplyNavigationTest {
    private val posts = (0..9).map { CompatPostSnapshot(position = it, postNo = "$it", timestamp = "", messageHtml = "body") }

    @Test fun bottomStopsAtExistingMarkerThenContinuesToFooter() {
        val notice = CompatNewReplyNotice(4, 6)
        assertEquals(6, resolveCompatThreadBottomScrollIndex(posts, notice, 0, 10))
        assertEquals(10, resolveCompatThreadBottomScrollIndex(posts, notice, 6, 10))
        assertEquals(10, resolveCompatThreadBottomScrollIndex(posts, notice, 8, 10))
    }

    @Test fun bottomUsesFilteredMarkerAndHandlesNoVisibleNewReplies() {
        val notice = CompatNewReplyNotice(4, 6)
        assertEquals(2, resolveCompatThreadBottomScrollIndex(listOf(posts[0], posts[2], posts[8]), notice, 0, 3))
        assertEquals(1, resolveCompatThreadBottomScrollIndex(posts.take(2), notice, 0, 1))
        assertEquals(10, resolveCompatThreadBottomScrollIndex(posts, null, 0, 10))
        assertEquals(0, resolveCompatThreadBottomScrollIndex(emptyList(), notice, 0, -1))
    }
}
