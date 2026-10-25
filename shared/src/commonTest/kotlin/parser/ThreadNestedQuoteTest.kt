package com.valoser.futacha.shared.parser

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.ui.board.ThreadOpeningSummaryTracker
import kotlinx.serialization.json.Json
import kotlin.test.*

class ThreadNestedQuoteTest {
    private fun post(id: String, body: String) = Post(id = id, author = null, subject = null,
        timestamp = "", messageHtml = body, imageUrl = null, thumbnailUrl = null)
    private fun resolve(body: String) = ThreadHtmlParserCore.rebuildReferences(listOf(
        post("100", "元の本文"), post("101", ">元の本文<br>返信本文"), post("102", body)))
    private fun assertParent(body: String) {
        val posts = resolve(body)
        assertEquals(setOf("101"), posts.last().quoteReferences.filter { it.isDirectReply }.flatMap { it.targetPostIds }.toSet())
        assertEquals(1, posts[1].referencedCount)
        assertEquals(1, posts[0].referencedCount) // 101 only; 102 is a reply to 101.
        assertEquals(1, ThreadOpeningSummaryTracker(2).accept(posts, setOf("101"))?.repliesToSelf)
        assertEquals(0, ThreadOpeningSummaryTracker(2).accept(posts, setOf("100"))?.repliesToSelf)
    }
    @Test fun nestedMultilineQuoteResolvesToThePostContainingBothLines() =
        assertParent(">>元の本文<br>>返信本文<br>さらに返信")
    @Test fun nestedSingleLineUsesItsImmediateQuotedSource() = assertParent(">>元の本文<br>さらに返信")
    @Test fun explicitNestedAnchorRemainsTappableWithoutNotifyingItsAncestor() {
        assertParent(">No.101<br>>>No.100<br>>返信本文<br>さらに返信")
        val nested = resolve(">No.101<br>>>No.100<br>>返信本文").last().quoteReferences.first { "100" in it.targetPostIds }
        assertFalse(nested.isDirectReply)
    }
    @Test fun copiedNumberOnlyAnchorRepliesToItsImmediateSource() {
        val posts = ThreadHtmlParserCore.rebuildReferences(listOf(post("100", "元の本文"),
            post("101", ">No.100"), post("102", ">>No.100")))
        val reference = posts.last().quoteReferences.single()
        assertEquals(listOf("100"), reference.targetPostIds)
        assertFalse(reference.isDirectReply)
        assertEquals(listOf("101"), reference.replyTargetPostIds)
        assertEquals(1, ThreadOpeningSummaryTracker(2).accept(posts, setOf("101"))?.repliesToSelf)
        assertEquals(0, ThreadOpeningSummaryTracker(2).accept(posts, setOf("100"))?.repliesToSelf)
        assertEquals(1, posts[0].referencedCount)
        assertEquals(1, posts[1].referencedCount)
    }
    /**
     * L85: an anchor deeper than a later, shallower line of its quote block is a nested quote, not a direct reply. A
     * 2ch-style ">>100" right before ">本文" stays a direct reply (the decision for L85).
     */
    @Test fun anAnchorAboveAShallowerLineOfItsBlockIsNested() {
        val nested = resolve(">>No.100<br>>返信本文<br>さらに返信").last().quoteReferences.first { "100" in it.targetPostIds }
        assertFalse(nested.isDirectReply)
        assertTrue(nested.replyTargetPostIds.isEmpty())
        assertParent(">>No.100<br>>返信本文<br>さらに返信")
        val shortcut = resolve(">>100<br>>返信本文<br>さらに返信").last().quoteReferences.first { "100" in it.targetPostIds }
        assertTrue(shortcut.isDirectReply)
        // The shallow line first, as before: the deeper anchor is nested.
        assertFalse(resolve(">返信本文<br>>>No.100<br>さらに").last().quoteReferences.first { "100" in it.targetPostIds }.isDirectReply)
    }

    @Test fun standaloneDoubleMarkerNumberStillMeansAnExplicitAnchor() {
        assertEquals(listOf("100"), resolve(">>100").last().quoteReferences.single().targetPostIds)
        assertTrue(resolve(">>100").last().quoteReferences.single().isDirectReply)
    }
    @Test fun fullWidthAndMixedMarkersRetainTheirDepth() = assertParent("＞>元の本文<br>＞返信本文<br>返信")
    @Test fun unknownNestedTextDoesNotFallBackToAnAncestor() {
        assertTrue(resolve(">>>元の本文").last().quoteReferences.isEmpty())
    }
    @Test fun nestedExplicitIdAndMediaLinksAreNotDirectReplies() {
        val posts = ThreadHtmlParserCore.rebuildReferences(listOf(
            post("100", "元").copy(posterId = "ID:abc123", imageUrl = "https://may.2chan.net/b/src/1234567890123.jpg"),
            post("101", "親"), post("102", ">No.101<br>>>ID:abc123<br>>>1234567890123.jpg")))
        assertEquals(setOf("101"), posts.last().quoteReferences.filter { it.isDirectReply }.flatMap { it.targetPostIds }.toSet())
        assertTrue(posts.last().quoteReferences.filterNot { it.isDirectReply }.any { "100" in it.targetPostIds })
    }
    @Test fun oldSavedReferencesDecodeAsDirectAndNewIndirectReferencesRoundTrip() {
        assertTrue(Json.decodeFromString<QuoteReference>("""{"text":">No.1","targetPostIds":["1"]}""").isDirectReply)
        val ref = QuoteReference(">>No.1", listOf("1"), false)
        assertEquals(ref, Json.decodeFromString<QuoteReference>(Json.encodeToString(QuoteReference.serializer(), ref)))
    }
}
