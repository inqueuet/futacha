package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.Post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** H2：引用の結び付けを原作の規則に（固定データA：原作は1↩4、3↩1、6↩3）。 */
class FtutterQuotesTest {
    private fun q(text: String) = "<font color=\"#789922\">&gt;$text</font>"

    private fun post(id: String, html: String, image: String? = null) = Post(
        id = id, author = "としあき", subject = "無題", timestamp = "26/08/25(火)18:30:24", messageHtml = html,
        imageUrl = image, thumbnailUrl = null
    )

    /** 固定データAの引用に関わるレス（No.900000〜900008と、複数行の引用）。 */
    private val fixtureA = listOf(
        post("900000", "OPの本文<br>二行目", "https://may.2chan.net/27/src/1787650196754.jpg"),
        post("900001", "一番目の本文です"),
        post("900002", "${q("No.900001")}<br>番号で引用"),
        post("900003", "${q("一番目の本文です")}<br>本文で引用"),
        post("900004", "${q("一番目の本文です")}<br><font color=\"#789922\">&gt;&gt;一番目の本文です</font><br>二重引用"),
        post("900005", "${q("1787650224907.jpg")}<br>ファイル名で引用"),
        post("900006", "添付あり", "https://may.2chan.net/27/src/1787650224907.jpg"),
        post("900007", "${q("1787650224907.jpg")}<br>添付のファイル名で引用"),
        post("900008", "${q("No.900008")}<br>自分自身と後のレスを引用"),
        post("900009", "${q("一行目の引用")}<br>${q("二行目の引用")}<br>複数行の引用 見つからない"),
        post("900010", "${q("添付あり")}<br>${q("一番目の本文です")}<br>複数引用"),
    )

    private fun replyCounts(posts: List<Post>): Map<Int, Int> {
        val replies = ftutterReplyIndex(ftutterRelinkQuotes(posts))
        return posts.indices.associateWith { replies[posts[it].id]?.size ?: 0 }.filterValues { it > 0 }
    }

    @Test
    fun theReplyCountsAreTheOriginals() {
        assertEquals(mapOf(1 to 4, 3 to 1, 6 to 3), replyCounts(fixtureA))
    }

    @Test
    fun eachQuoteLineGoesWhereTheOriginalGoes() {
        val linked = ftutterRelinkQuotes(fixtureA)
        fun line(index: Int, text: String) = ftutterQuoteLineTargets(linked, index, text)
        assertEquals(listOf(1), line(2, ">No.900001"))
        assertEquals(listOf(1), line(3, ">一番目の本文です"))
        // 二重引用：1行目はレス1、`>>`の行は「>一番目の本文です」を含むレス3。
        assertEquals(listOf(1), line(4, ">一番目の本文です"))
        assertEquals(listOf(3), line(4, ">>一番目の本文です"))
        // 添付のファイル名は後のレスも対象。
        assertEquals(listOf(6), line(5, ">1787650224907.jpg"))
        assertEquals(listOf(6), line(7, ">1787650224907.jpg"))
        // 2行の塊は行ごとに別々に結ぶ。
        assertEquals(listOf(6), line(10, ">添付あり"))
        assertEquals(listOf(1), line(10, ">一番目の本文です"))
    }

    @Test
    fun selfQuotesUnfoundQuotesAndTheOpsQuotesStayUnlinked() {
        val linked = ftutterRelinkQuotes(fixtureA)
        assertTrue(ftutterQuoteLineTargets(linked, 8, ">No.900008").isEmpty())
        assertTrue(ftutterQuoteLineTargets(linked, 9, ">一行目の引用").isEmpty())
        assertTrue(ftutterQuoteLineTargets(linked, 9, ">二行目の引用").isEmpty())
        val opQuoting = listOf(post("1", "${q("あとの本文")}<br>OP"), post("2", "あとの本文"))
        assertTrue(ftutterRelinkQuotes(opQuoting)[0].quoteReferences.all { it.targetPostIds.isEmpty() })
    }

    @Test
    fun theNearestEarlierPostWinsAndNoNumberBeatsText() {
        val posts = listOf(
            post("1", "OP"),
            post("2", "同じ文"),
            post("3", "同じ文"),
            post("4", "No.2"),
            post("5", "${q("同じ文")}<br>${q("No.2")}"),
        )
        val linked = ftutterRelinkQuotes(posts)
        assertEquals(listOf(2), ftutterQuoteLineTargets(linked, 4, ">同じ文"))
        // 「No.2」は本文に同じ文字のあるレス3ではなく、No.2のレス1へ。
        assertEquals(listOf(1), ftutterQuoteLineTargets(linked, 4, ">No.2"))
    }

    @Test
    fun idAndIpQuotesAreNotBoundToAPost() {
        val posts = listOf(post("1", "OP"), post("2", "本文"), post("3", "${q("ID:abcd1234")}<br>IDの引用"))
        assertTrue(ftutterRelinkQuotes(posts)[2].quoteReferences.all { it.targetPostIds.isEmpty() })
    }

    @Test
    fun anIpQuoteGoesToTheFirstPostWithThatIpAndAnIdQuoteToAllOfThem() {
        fun p(id: String, stamp: String, html: String) = Post(id = id, author = null, subject = null, timestamp = stamp,
            messageHtml = html, imageUrl = null, thumbnailUrl = null)
        val ip = "26/08/25(火)18:30:24 IP:133.149.*(aitai.ne.jp)"
        val posts = listOf(
            p("1", ip, "OP"), p("2", ip, "二"), p("3", "26/08/25(火)18:31:00 ID:abcd1234", "ID一"),
            p("4", "26/08/25(火)18:31:10 ID:abcd1234", "ID二"),
            p("5", ip, "${q("IP:133.149.*(aitai.ne.jp)")}<br>IPの引用"),
            p("6", ip, "${q("ID:abcd1234")}<br>IDの引用"),
        )
        val linked = ftutterRelinkQuotes(posts)
        assertEquals(listOf(0), ftutterQuoteLineTargets(linked, 4, ">IP:133.149.*(aitai.ne.jp)"))
        assertEquals(listOf(2, 3), ftutterQuoteLineTargets(linked, 5, ">ID:abcd1234"))
    }

    @Test
    fun theFirstPopupSitsAboveTheTapAndTheRestUnderTheBar() {
        // 1枚目：押した所(1000)のすぐ上。収まらなければバーの下(200)。
        assertEquals(700f, ftutterPopupTop(0, 1000f, 300, 200f, pinTop = false, legacy = false))
        assertEquals(200f, ftutterPopupTop(0, 300f, 300, 200f, pinTop = false, legacy = false))
        // 返信数の一覧（M1）と2枚目から（M84）はバーの下。
        assertEquals(200f, ftutterPopupTop(0, 1000f, 300, 200f, pinTop = true, legacy = false))
        assertEquals(200f, ftutterPopupTop(1, 1000f, 300, 200f, pinTop = false, legacy = false))
        // 従来の動き：どれも押した所のすぐ上。
        assertEquals(700f, ftutterPopupTop(1, 1000f, 300, 200f, pinTop = true, legacy = true))
    }

    @Test
    fun theLegacyPopupSwitchIsOffByDefault() {
        assertEquals(false, ftutterLegacyPopups(emptyMap()))
    }

    @Test
    fun relinkingWhatIsAlreadyLinkedKeepsTheSameList() {
        val posts = listOf(
            Post("1", author = null, subject = null, timestamp = "", messageHtml = "本文", imageUrl = null, thumbnailUrl = null),
            Post("2", author = null, subject = null, timestamp = "", messageHtml = "&gt;本文<br>返信", imageUrl = null, thumbnailUrl = null)
        )
        val linked = ftutterRelinkQuotes(posts)
        assertTrue(linked !== posts)
        assertTrue(ftutterRelinkQuotes(linked) === linked)
    }
}
