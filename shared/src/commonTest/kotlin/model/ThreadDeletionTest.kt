package com.valoser.futacha.shared.model

import com.valoser.futacha.shared.compat.toCompatThreadSnapshot
import com.valoser.futacha.shared.parser.ThreadHtmlParserCore
import com.valoser.futacha.shared.ui.compat.compatThreadDeletionSummary
import com.valoser.futacha.shared.ui.compat.compatDeletedNoticeRanges
import com.valoser.futacha.shared.compat.toCompatPlainText
import com.valoser.futacha.shared.ui.board.threadDeletionSummaryForPage
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ThreadDeletionTest {
    @Test
    fun futabaHtmlKeepsDeletionKindsThroughParserAndBothPresentations() = runBlocking {
        val kinds = PostDeletionKind.entries.take(4)
        val html = """
            <html><head><link rel="canonical" href="https://may.2chan.net/b/res/100.htm"></head>
            <body><div class="thre" data-res="100">
            <span class="cno">No.100</span><blockquote>スレ本文</blockquote>
            <span id=ddel>削除された記事が<span id=ddnum>6</span>件あります.<span id=ddbut>見る</span><br></span>
        """ + kinds.mapIndexed { index, kind ->
            """<table border=0><tr><td class=rtd><span class="cno">No.${101 + index}</span>
                <blockquote><font color="#ff0000">${kind.notice}</font><br>元の本文</blockquote></td></tr></table>"""
        }.joinToString("") + "</div></body></html>"
        val page = ThreadHtmlParserCore.parseThread(html, "https://may.2chan.net/b/res/100.htm")
        assertEquals(5, page.posts.size)
        val snapshot = page.toCompatThreadSnapshot("test", 1)
        val expected = "削除・隔離されたレス：6件（スレ主：1件／投稿者本人：1件／管理者：1件／隔離：1件／種類不明：2件）"
        assertEquals(expected, threadDeletionSummaryForPage(page))
        assertEquals(expected, compatThreadDeletionSummary(snapshot))
        snapshot.posts.drop(1).forEach { post ->
            assertEquals(1, compatDeletedNoticeRanges(post, post.messageHtml.toCompatPlainText()).size)
        }
    }

    @Test
    fun zeroAndUnknownTotalsAndQuotedNoticesAreHandledWithoutGuessing() {
        assertNull(threadDeletionSummary(null, emptyList()))
        assertNull(threadDeletionSummary("削除された記事が0件あります.見る", emptyList()))
        assertEquals("削除されたレス：12件（種類不明：12件）", threadDeletionSummary("削除された記事が12件あります.見る", emptyList()))
        assertNull(postDeletionKind("書き込みをした人によって削除されました", false, false))
        assertEquals(PostDeletionKind.UNKNOWN, postDeletionKind(">書き込みをした人によって削除されました", true, false))
        assertEquals("警告", threadNoticeWithoutDeletionCount("警告\n削除された記事が1件あります.見る"))
        assertEquals("削除されたレス：2件（投稿者本人：2件）", threadDeletionSummary("削除された記事が1件あります.見る", listOf(PostDeletionKind.AUTHOR, PostDeletionKind.AUTHOR)))
    }
}
