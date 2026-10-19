package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.parser.ThreadHtmlParserCore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.futaber.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class NestedQuoteConsumersTest {
    private fun posts(): List<Post> {
        fun post(id: String, body: String) = Post(id = id, author = null, subject = null, timestamp = "",
            messageHtml = body, imageUrl = null, thumbnailUrl = null)
        return ThreadHtmlParserCore.rebuildReferences(listOf(post("100", "元の本文"),
            post("101", ">元の本文<br>返信本文"), post("102", ">No.101<br>>>No.100<br>>>元の本文<br>>返信本文")))
    }
    @Test fun replyGraphsAndOwnMarksUseDirectEdgesWhileQuotePopupsKeepInnerLinks() = runBlocking {
        val posts = posts()
        val snapshots = posts.mapIndexed { i, p -> p.toCompatPostSnapshot(i) }
        assertTrue(compatPostQuotesOwnPost(snapshots[2], setOf("101")))
        assertFalse(compatPostQuotesOwnPost(snapshots[2], setOf("100")))
        assertEquals("no:101", compatQuoteQueryForPostLine(snapshots[2], ">>元の本文"))
        assertEquals("no:100", compatQuoteQueryForPostLine(snapshots[2], ">>No.100"))
        assertEquals(listOf("100"), futaberQuoteTargetIds(posts[2], ">>No.100"))
        assertEquals(listOf("102"), futaberReplyIndex(posts)["101"]?.map { it.id })
        assertEquals(listOf("101"), futaberReplyIndex(posts)["100"]?.map { it.id })
        assertEquals(FtutterPostMark.REPLY_TO_SELF, ftutterPostMark(posts[2], setOf("101"), 0))
        assertEquals(FtutterPostMark.NONE, ftutterPostMark(posts[2], setOf("100"), 0))
        assertEquals(listOf(1), ftutterQuoteTargets(posts, 2))
        assertEquals(listOf(0), ftutterQuoteLineTargets(posts, 2, ">>No.100"))
        val derived = buildThreadPostDerivedData(posts)
        assertEquals(listOf("102"), derived.referencedByMap["101"]?.map { it.id })
        assertEquals(listOf("101"), derived.referencedByMap["100"]?.map { it.id })
        assertEquals(derived, buildThreadPostDerivedDataCancellable(posts))
        assertEquals(listOf(0, 1, 2), buildThreadTreeNodes(posts).map { it.depth })
        assertEquals(2, buildCompatThreadDisplay(snapshots, true).depthByPostNo["102"])
        assertEquals(listOf("100", "101"), relatedThreadPosts(posts[0], posts).map { it.id })
        assertEquals(listOf("100", "101"), relatedCompatPostsIndexed("100", snapshots).map { it.postNo })
    }
    @Test fun changedDirectnessInvalidatesDerivedReplyGraphFingerprint() = runBlocking {
        val original = posts()
        val changed = original.map { post -> post.copy(quoteReferences = post.quoteReferences.map { it.copy(isDirectReply = !it.isDirectReply, replyTargetPostIds = emptyList()) }) }
        assertNotEquals(buildThreadPostListFingerprint(original), buildThreadPostListFingerprint(changed))
        assertEquals(buildThreadPostListFingerprint(changed), buildThreadPostListFingerprintCancellable(changed))
    }
    @Test fun boardPlacementIsOptInAndAcceptsBothSharedPreferenceEncodings() {
        assertFalse(boardsFromBottom(emptyMap()))
        for (value in listOf("true", "ON")) assertTrue(boardsFromBottom(mapOf(BOARDS_FROM_BOTTOM_KEY to value)))
        for (value in listOf("false", "OFF", "")) assertFalse(boardsFromBottom(mapOf(BOARDS_FROM_BOTTOM_KEY to value)))
    }
}
