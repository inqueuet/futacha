package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.model.Post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What the side-by-side run with futaber on the iPhone showed (docs/thread-feedback-1444136530-2026-10-27.md §10):
 * the post header's order and IP count, the picture's file name, the viewer's caption and the catalog's title.
 */
class FutaberDeviceComparisonTest {
    private fun post(id: String, timestamp: String, imageUrl: String? = null) = Post(
        id = id, author = null, subject = null, timestamp = timestamp, messageHtml = "", imageUrl = imageUrl, thumbnailUrl = null
    )

    /**
     * The header as the text a reader sees, the replies' mark written "💬N" where it is drawn (L-B3 V08: the header is one
     * text, so its order is checked on that text rather than on separate pieces).
     */
    private fun headerLine(ordinal: Int, post: Post, ipCount: String? = null, replies: Int = 0, id: String? = null): String {
        val parts = futaberPostHeaderParts(
            ordinal, post, id?.let { com.valoser.futacha.shared.ui.board.PosterIdLabel(it, false) }, ipCount, replies
        )
        val text = futaberPostHeaderText(parts, FutaberTokens.light)
        val mark = text.getStringAnnotations(0, text.length).firstOrNull { it.item == FUTABER_REPLY_INLINE_ID }
        val shown = if (mark == null) text.text else text.text.replaceRange(mark.start, mark.end, "💬$replies")
        return shown.replace("\u200B", "")
    }

    @Test
    fun theHeaderSplitsTheDateFromTheIpSoTheReplyCountGoesBetween() {
        assertEquals(
            "26/10/16(金)21:19:42" to "IP:106.73.*(enabler.ne.jp)",
            futaberTimestampAndIp("26/10/16(金)21:19:42 IP:106.73.*(enabler.ne.jp)")
        )
        // A board without IPs: the ID is drawn on its own, so nothing is left after the date.
        assertEquals("25/11/08(土)13:42:53" to null, futaberTimestampAndIp("25/11/08(土)13:42:53 ID:Mua720AI"))
        assertEquals("26/10/16(金)21:19:42" to null, futaberTimestampAndIp("26/10/16(金)21:19:42"))
        // futaber's post 0 (P/out-thread/30-01-thread.txt): "0 ひ―坊 26/10/16(金)21:19:42 💬1 IP:… 1/2 No.325108".
        val first = Post(
            id = "325108", author = "ひー坊", subject = "無題", timestamp = "26/10/16(金)21:19:42 IP:106.73.*(enabler.ne.jp)",
            messageHtml = "", imageUrl = null, thumbnailUrl = null
        )
        assertEquals(
            "0 ひー坊 26/10/16(金)21:19:42 💬1 IP:106.73.*(enabler.ne.jp) 1/2 No.325108",
            headerLine(0, first, ipCount = "1/2", replies = 1)
        )
        // Post 16 of the same thread: the mail field in brackets after the name's place ("16 [sage] …"), no replies.
        assertEquals(
            "16 [sage] 26/10/25(日)17:59:39 IP:14.9.*(enabler.ne.jp) 7/7 No.325150",
            headerLine(
                16, first.copy(id = "325150", author = "としあき", mail = "sage", timestamp = "26/10/25(日)17:59:39 IP:14.9.*(enabler.ne.jp)"),
                ipCount = "7/7"
            )
        )
        // An ID board: the ID after the date (and the replies), before No.
        assertEquals(
            "10 無念 25/11/08(土)13:44:59 💬3 ID:SZ3HceEk(1/1) No.1364612475",
            headerLine(
                10, first.copy(id = "1364612475", author = "としあき", subject = "無念", timestamp = "25/11/08(土)13:44:59 ID:SZ3HceEk"),
                replies = 3, id = "ID:SZ3HceEk(1/1)"
            )
        )
    }

    @Test
    fun anIpThatPostsAgainIsCountedAndOneSeenOnceIsNot() {
        val posts = listOf(
            post("1", "26/10/16(金)21:19:42 IP:106.73.*(enabler.ne.jp)"),
            post("2", "26/10/16(金)22:37:55 IP:130.62.*(transix.jp)"),
            post("3", "26/10/17(土)10:24:38 IP:106.73.*(enabler.ne.jp)"),
            post("4", "26/10/17(土)11:00:00")
        )
        assertEquals(mapOf("1" to "1/2", "3" to "2/2"), futaberIpCountLabels(posts))
        assertEquals(emptyMap(), futaberIpCountLabels(posts.take(2)))
    }

    @Test
    fun thePicturesFileNameIsTheLastPartOfItsAddress() {
        assertEquals("1792153182260.jpg", futaberImageFileName(post("1", "", "https://may.2chan.net/27/src/1792153182260.jpg")))
        assertEquals("a.webm", futaberImageFileName(post("1", "", "https://may.2chan.net/b/src/a.webm?x=1")))
        assertNull(futaberImageFileName(post("1", "")))
    }

    @Test
    fun theViewerCaptionFollowsTheOriginalsOrder() {
        val shown = CompatPostSnapshot(
            position = 0, postNo = "325108", author = "ひー坊", subject = "無題",
            timestamp = "26/10/16(金)21:19:42 IP:106.73.*(enabler.ne.jp)", messageHtml = ""
        )
        assertEquals(
            "0 ひー坊 26/10/16(金)21:19:42" to "IP:106.73.*(enabler.ne.jp) 1/2 No.325108",
            futaberViewerHeaderParts(shown, ipCount = "1/2")
        )
        // The default name is left out unless the setting shows it always.
        assertEquals(
            "0 26/10/16(金)21:19:42" to "IP:106.73.*(enabler.ne.jp) No.325108",
            futaberViewerHeaderParts(shown.copy(author = "としあき"))
        )
        assertEquals(
            "0 無題  としあき 26/10/16(金)21:19:42" to "IP:106.73.*(enabler.ne.jp) No.325108",
            futaberViewerHeaderParts(shown.copy(author = "としあき"), alwaysShowTitleAndName = true)
        )
    }

    @Test
    fun theCatalogTitleDropsTheBoardsPage() {
        assertEquals("may.2chan.net/27", futaberCatalogHeading("https://may.2chan.net/27/futaba.php"))
        assertEquals("may.2chan.net/27", futaberCatalogHeading("https://may.2chan.net/27/futaba.htm"))
        assertEquals("may.2chan.net/b", futaberCatalogHeading("https://may.2chan.net/b/"))
        assertEquals("www.example.com/t", futaberCatalogHeading("https://www.example.com/t/"))
    }
}
