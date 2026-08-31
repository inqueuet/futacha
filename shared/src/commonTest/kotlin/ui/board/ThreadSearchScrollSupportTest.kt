package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThreadSearchScrollSupportTest {
    @Test
    fun resolveTargetUsesDisplayedOrderAndItemsBeforePosts() {
        val first = post("1")
        val second = post("2")
        val target = post("3")

        assertEquals(
            5,
            resolveThreadPostScrollTargetIndex(
                request = ThreadPostScrollRequest(post = target, requestId = 1L),
                displayedPosts = listOf(second, target, first),
                itemsBeforePosts = 4
            )
        )
    }

    @Test
    fun resolveTargetUsesPostIdentityWhenIdsAreDuplicated() {
        val firstDuplicate = post("10", message = "first")
        val targetDuplicate = post("10", message = "target")

        assertEquals(
            2,
            resolveThreadPostScrollTargetIndex(
                request = ThreadPostScrollRequest(post = targetDuplicate, requestId = 2L),
                displayedPosts = listOf(firstDuplicate, targetDuplicate),
                itemsBeforePosts = 1
            )
        )
    }

    @Test
    fun resolveTargetReturnsNullWhenPostIsFilteredOut() {
        assertNull(
            resolveThreadPostScrollTargetIndex(
                request = ThreadPostScrollRequest(post = post("3"), requestId = 3L),
                displayedPosts = listOf(post("1"), post("2")),
                itemsBeforePosts = 2
            )
        )
    }

    @Test
    fun centeredScrollDeltaMovesItemCenterToViewportCenter() {
        assertEquals(
            200f,
            calculateThreadCenteredScrollDelta(
                viewportStartOffset = 0,
                viewportEndOffset = 1000,
                itemOffset = 600,
                itemSize = 200
            )
        )
        assertEquals(
            -300f,
            calculateThreadCenteredScrollDelta(
                viewportStartOffset = 100,
                viewportEndOffset = 900,
                itemOffset = 100,
                itemSize = 200
            )
        )
    }

    private fun post(id: String, message: String = "body") = Post(
        id = id,
        author = null,
        subject = null,
        timestamp = "",
        messageHtml = message,
        imageUrl = null,
        thumbnailUrl = null
    )
}
