package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.ai.PostModerationResult
import com.valoser.futacha.shared.model.Post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThreadAiPostFilterSupportTest {
    @Test
    fun resolveAiHiddenPostStateSkipsOpSelfPostsAndUnknownPostIds() {
        val posts = listOf(
            post("1"),
            post("2"),
            post("3")
        )
        val state = resolveAiHiddenPostState(
            posts = posts,
            moderationResults = listOf(
                PostModerationResult(postId = "1", shouldHide = true, reason = "OP"),
                PostModerationResult(postId = "2", shouldHide = true, reason = "自分"),
                PostModerationResult(postId = "3", shouldHide = true, reason = "連投"),
                PostModerationResult(postId = "missing", shouldHide = true, reason = "不明")
            ),
            selfPostIdentifiers = setOf("2")
        )

        assertEquals(setOf("3"), state.postIds)
        assertEquals(mapOf("3" to "連投"), state.reasons)
    }

    @Test
    fun resolveAiHiddenPostStateKeepsOnlyHideResults() {
        val state = resolveAiHiddenPostState(
            posts = listOf(post("1"), post("2")),
            moderationResults = listOf(
                PostModerationResult(postId = "2", shouldHide = false, reason = "通常")
            )
        )

        assertEquals(emptySet(), state.postIds)
        assertEquals(emptyMap(), state.reasons)
    }

    @Test
    fun resolveAiHiddenPostStateNormalizesLongMultilineReasons() {
        val state = resolveAiHiddenPostState(
            posts = listOf(post("1"), post("2")),
            moderationResults = listOf(
                PostModerationResult(
                    postId = "2",
                    shouldHide = true,
                    reason = "連投\n".repeat(60)
                )
            )
        )
        val reason = state.reasons.getValue("2")

        assertFalse(reason.contains('\n'))
        assertEquals(80, reason.length)
        assertEquals('…', reason.last())
    }

    @Test
    fun resolveAiHiddenPostStateKeepsAllAiHiddenPostsExceptOp() {
        val posts = (1..50).map { post(it.toString()) }
        val results = (2..50).map { index ->
            PostModerationResult(
                postId = index.toString(),
                shouldHide = true,
                reason = "spam"
            )
        }

        val state = resolveAiHiddenPostState(
            posts = posts,
            moderationResults = results
        )

        assertEquals(49, state.postIds.size)
        assertEquals((2..50).map { it.toString() }.toSet(), state.postIds)
    }

    @Test
    fun resolveAiHiddenPostLimitAllowsEveryNonOpPost() {
        assertEquals(0, resolveAiHiddenPostLimit(1))
        assertEquals(1, resolveAiHiddenPostLimit(2))
        assertEquals(19, resolveAiHiddenPostLimit(20))
        assertEquals(99, resolveAiHiddenPostLimit(100))
    }

    @Test
    fun shouldDeferAiPostModeration_waitsForSummaryLoadingOnly() {
        assertTrue(
            shouldDeferAiPostModeration(
                shouldApplyAiPostFilter = false,
                shouldShowThreadSummary = false,
                summaryState = null
            )
        )
        assertTrue(
            shouldDeferAiPostModeration(
                shouldApplyAiPostFilter = true,
                shouldShowThreadSummary = true,
                summaryState = ThreadSummaryUiState.Loading
            )
        )
        assertFalse(
            shouldDeferAiPostModeration(
                shouldApplyAiPostFilter = true,
                shouldShowThreadSummary = true,
                summaryState = ThreadSummaryUiState.Unavailable("timeout")
            )
        )
        assertFalse(
            shouldDeferAiPostModeration(
                shouldApplyAiPostFilter = true,
                shouldShowThreadSummary = false,
                summaryState = ThreadSummaryUiState.Loading
            )
        )
    }

    private fun post(id: String): Post {
        return Post(
            id = id,
            author = null,
            subject = null,
            timestamp = "",
            messageHtml = "body",
            imageUrl = null,
            thumbnailUrl = null
        )
    }
}
