package com.valoser.futacha.shared.parser

import com.valoser.futacha.shared.model.Post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThreadQuoteLeadingNumberTest {
    private fun post(id: String, messageHtml: String) = Post(
        id = id,
        author = null,
        subject = null,
        timestamp = "",
        messageHtml = messageHtml,
        imageUrl = null,
        thumbnailUrl = null
    )

    @Test
    fun quotedTextStartingWithDigitsResolvesByTextWhenNoSuchPostExists() {
        val posts = ThreadHtmlParserCore.rebuildReferences(
            listOf(
                post("1000", "100円ショップで買った"),
                post("1001", "&gt;100円ショップで買った<br>いいね"),
                post("1002", "&gt;3枚目いいね")
            )
        )

        val textQuote = posts[1].quoteReferences.single()
        assertEquals(listOf("1000"), textQuote.targetPostIds)
        // No post No.3 and no matching text: not a reference to a non-existent post.
        assertTrue(posts[2].quoteReferences.isEmpty())
        assertEquals(1, posts[0].referencedCount)
    }

    @Test
    fun leadingNumberOfAnExistingPostIsStillAPostReference() {
        val posts = ThreadHtmlParserCore.rebuildReferences(
            listOf(
                post("1000", "本文"),
                post("1001", "&gt;1000<br>それな")
            )
        )

        assertEquals(listOf("1000"), posts[1].quoteReferences.single().targetPostIds)
    }
}
