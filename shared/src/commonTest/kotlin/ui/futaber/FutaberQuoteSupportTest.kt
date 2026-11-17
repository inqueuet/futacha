package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FutaberQuoteSupportTest {
    private fun post(id: String, vararg refs: QuoteReference) = Post(
        id = id, author = null, subject = null, timestamp = "", messageHtml = "",
        imageUrl = null, thumbnailUrl = null, quoteReferences = refs.toList()
    )

    private val posts = listOf(
        post("100"),
        post("101", QuoteReference(">元の文", listOf("100"))),
        post("102", QuoteReference(">元の文", listOf("100")), QuoteReference(">返事", listOf("101"))),
        post("103", QuoteReference(">一行目\n>二行目", listOf("101", "102")))
    )

    @Test
    fun aQuotedLineResolvesToTheTargetsOfItsReference() {
        assertEquals(listOf("100"), futaberQuoteTargetIds(posts[1], ">元の文"))
        assertEquals(listOf("100"), futaberQuoteTargetIds(posts[1], "  >元の文  "))
        assertEquals(listOf("101"), futaberQuoteTargetIds(posts[2], ">返事"))
        assertTrue(futaberQuoteTargetIds(posts[1], ">知らない行").isEmpty())
        assertTrue(futaberQuoteTargetIds(posts[1], "").isEmpty())
    }

    @Test
    fun anyLineOfACopiedBlockResolvesToTheBlocksTargets() {
        assertEquals(listOf("101", "102"), futaberQuoteTargetIds(posts[3], ">一行目"))
        assertEquals(listOf("101", "102"), futaberQuoteTargetIds(posts[3], ">二行目"))
    }

    @Test
    fun theReplyIndexListsEachQuotingPostOnceInDisplayOrder() {
        val index = futaberReplyIndex(posts)
        assertEquals(listOf("101", "102"), index["100"]?.map { it.id })
        assertEquals(listOf("102", "103"), index["101"]?.map { it.id })
        assertEquals(listOf("103"), index["102"]?.map { it.id })
        assertTrue("103" !in index)
        assertEquals(2, futaberReplyCount(posts[0], index))
        assertEquals(0, futaberReplyCount(posts[3], index))
    }

    @Test
    fun aPostNeverRepliesToItself() {
        val selfQuote = post("200", QuoteReference(">自分", listOf("200", "100")))
        val index = futaberReplyIndex(listOf(post("100"), selfQuote))
        assertTrue("200" !in index)
        assertEquals(listOf("200"), index["100"]?.map { it.id })
    }

    @Test
    fun postsByIdKeepThreadOrderSkipMissingAndDropDuplicates() {
        assertEquals(listOf("100", "102"), futaberPostsById(posts, listOf("102", "100", "999", "102")).map { it.id })
        assertTrue(futaberPostsById(posts, emptyList()).isEmpty())
    }

    @Test
    fun theBubbleSitsAboveTheLineWhenItFitsElseBelow() {
        val low = FutaberAnchor(x = 100f, top = 900f, bottom = 940f)
        val above = futaberBubblePlacement(low, cardHeight = 200f, minTop = 80f, maxBottom = 1000f, gap = 10f)
        assertEquals(FutaberBubblePlacement(690f, pointerOnTop = false), above)
        // Near the top there is no room above, so it goes below.
        val high = FutaberAnchor(x = 100f, top = 150f, bottom = 190f)
        val below = futaberBubblePlacement(high, cardHeight = 200f, minTop = 80f, maxBottom = 1000f, gap = 10f)
        assertEquals(FutaberBubblePlacement(200f, pointerOnTop = true), below)
    }

    @Test
    fun aTallBubbleStaysInsideTheAvailableSpace() {
        val middle = FutaberAnchor(x = 100f, top = 500f, bottom = 540f)
        val result = futaberBubblePlacement(middle, cardHeight = 900f, minTop = 80f, maxBottom = 1000f, gap = 10f)
        assertTrue(result.top >= 80f)
        assertTrue(result.top + 900f <= 1000f + 0.5f || result.top == 80f)
    }
}
