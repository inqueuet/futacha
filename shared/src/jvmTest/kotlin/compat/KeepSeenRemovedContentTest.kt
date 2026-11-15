package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KeepSeenRemovedContentTest {
    @Test
    fun realDeletionMarkupKeepsSeenBodyAcrossRepeatedRefreshes() = kotlinx.coroutines.runBlocking {
        val html = """
            <div class="thre" data-res="1"><span class="cno">No.1</span><blockquote>OP</blockquote></div>
            <table border=0 class=deleted><tr><td class=rtd><span class="cno">No.2</span><blockquote><font color="#ff0000">書き込みをした人によって削除されました</font></blockquote></td></tr></table>
        """.trimIndent()
        val fetched = com.valoser.futacha.shared.parser.ThreadHtmlParserCore.parseThread(html).toCompatThreadSnapshot("k", 2)
        var current = snap(post("1", "OP"), post("2", "既読の本文", "seen.mp4"))
        repeat(3) {
            current = keepSeenRemovedCompatContent(current, fetched)
            val kept = current.posts.first { it.postNo == "2" }
            assertEquals("既読の本文", kept.messageHtml)
            assertEquals("seen.mp4", kept.imageUrl)
            assertTrue(kept.isDeleted)
        }
    }

    @Test
    fun emptyDeletedResponsesKeepSeenContentAcrossRepeatedRefreshes() {
        val removed = snap(post("2", "", deleted = true))
        var current = snap(post("2", "既読の本文", "seen.jpg"))
        repeat(3) {
            current = keepSeenRemovedCompatContent(current, removed)
            assertEquals("既読の本文", current.posts.single().messageHtml)
            assertEquals("seen.jpg", current.posts.single().imageUrl)
        }
    }

    @Test
    fun noticesAndRedactedCopiesAreNotRecoveredAsOriginalBodies() {
        val notice = com.valoser.futacha.shared.model.PostDeletionKind.AUTHOR.notice
        val removed = snap(post("2", "", deleted = true))
        assertSame(removed, keepSeenRemovedCompatContent(snap(post("2", notice, deleted = true)), removed))
        assertSame(removed, keepSeenRemovedCompatContent(
            snap(post("2", "hidden", "hidden.jpg", deleted = true).copy(isContentRedacted = true)), removed
        ))
        val hasVisibleBody = snap(post("2", "サーバーに残っている本文", deleted = true))
        assertSame(hasVisibleBody, keepSeenRemovedCompatContent(snap(post("2", "以前の本文")), hasVisibleBody))
    }

    private fun post(no: String, body: String, image: String? = null, deleted: Boolean = false) =
        CompatPostSnapshot(position = no.toInt(), postNo = no, timestamp = "t", messageHtml = body,
            imageUrl = image, thumbnailUrl = image?.let { "$it.thumb" }, isDeleted = deleted)

    private fun snap(vararg posts: CompatPostSnapshot) =
        CompatThreadSnapshot(tabKey = "k", revision = 1, fetchedAtEpochMillis = 1, posts = posts.toList())

    @Test
    fun aDeletedPostKeepsTheBodyAndPictureSeenBefore() {
        val before = snap(post("1", "op"), post("2", "見た本文", "a.jpg"))
        val after = snap(post("1", "op"), post("2", "", null, deleted = true))
        val kept = keepSeenRemovedCompatContent(before, after).posts[1]
        assertEquals("見た本文", kept.messageHtml)
        assertEquals("a.jpg", kept.imageUrl)
        assertEquals("a.jpg.thumb", kept.thumbnailUrl)
        assertTrue(kept.isDeleted)
    }

    @Test
    fun nothingChangesForAimogeOrUnseenOrLivePosts() {
        val before = snap(post("1", "op", "a.jpg"))
        val after = snap(post("1", "", null, deleted = true), post("2", "", null, deleted = true))
        assertSame(after, keepSeenRemovedCompatContent(before, after, serverDropsRemovedPosts = true))
        assertSame(after, keepSeenRemovedCompatContent(null, after))
        val result = keepSeenRemovedCompatContent(before, after)
        assertEquals("", result.posts[1].messageHtml)
        assertEquals(2, result.posts.size)
        val same = snap(post("1", "op", "a.jpg"))
        assertSame(same, keepSeenRemovedCompatContent(before, same))
    }
}
