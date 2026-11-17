package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** F-B3: thread features from the other modes (saidane threshold, popular, conversation, NG chain, menu entries). */
class FutaberF3Test {
    private fun post(id: String, saidane: String? = null, quotes: List<String> = emptyList()) = Post(
        id = id, author = null, subject = null, timestamp = "", messageHtml = "本文$id",
        imageUrl = null, thumbnailUrl = null, saidaneLabel = saidane, quoteReferences = quotes.map { QuoteReference(">>$it", listOf(it)) }
    )

    @Test
    fun theSaidaneThresholdMovesTheExtractLine() {
        val p = post("1", saidane = "そうだねx4")
        assertTrue(futaberMatchesExtract(p, FutaberExtract.Saidane, emptySet(), null, 3))
        assertFalse(futaberMatchesExtract(p, FutaberExtract.Saidane, emptySet(), null, 5))
    }

    @Test
    fun theNgChainHidesRepliesToHiddenPostsThroughOthers() {
        val posts = listOf(post("1"), post("2", quotes = listOf("1")), post("3", quotes = listOf("2")), post("4"))
        val index = futaberReplyIndex(posts)
        assertEquals(setOf("1", "2", "3"), futaberNgChain(setOf("1"), index))
        assertEquals(setOf("4"), futaberNgChain(setOf("4"), index))
        assertEquals(emptySet(), futaberNgChain(emptySet(), index))
    }

    @Test
    fun aConversationIsTheQuoteTreeAndASinglePostHasNone() {
        val posts = listOf(post("1"), post("2", quotes = listOf("1")), post("3", quotes = listOf("2")), post("9"))
        assertEquals(listOf("1", "2", "3"), futaberConversationOf(posts[2], posts).map { it.id })
        assertTrue(futaberConversationOf(posts[3], posts).size < 2)
    }

    @Test
    fun theNewSwitchesReadTheirKeys() {
        val d = FutaberDisplaySettings.from(emptyMap())
        assertFalse(d.extNgChain); assertFalse(d.extOpenAtTop)
        val on = FutaberDisplaySettings.from(mapOf("compat.futaber.ext.ngChain" to "ON", "compat.futaber.ext.openAtTop" to "ON"))
        assertTrue(on.extNgChain && on.extOpenAtTop)
    }

    @Test
    fun theMenuEntriesFollowWhatTheHostCanDo() {
        val full = futaberEvolutionMenuItems({}, false, {}, { }, false, {})
        assertEquals(listOf("privacy", "prev-tab", "next-tab", "tree"), full.map { it.id })
        val bare = futaberEvolutionMenuItems({}, null, {}, null, true, {})
        assertEquals(listOf("tree"), bare.map { it.id })
        assertEquals("通常表示", bare.single().label)
    }
}
