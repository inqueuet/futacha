package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class ThreadTreeContentSupportTest {
    @Test
    fun deeplyNestedQuotesAreFlattenedWithoutRecursiveStackGrowth() = runBlocking {
        val posts = (0 until 4_000).map { index ->
            Post(
                id = index.toString(),
                author = null,
                subject = null,
                timestamp = "",
                messageHtml = "body",
                imageUrl = null,
                thumbnailUrl = null,
                quoteReferences = if (index == 0) emptyList() else {
                    listOf(QuoteReference(">>${index - 1}", listOf((index - 1).toString())))
                }
            )
        }

        val nodes = buildThreadTreeNodes(posts)

        assertEquals(posts.map(Post::id), nodes.map { it.post.id })
        assertEquals(12, nodes.last().depth)
    }
}
