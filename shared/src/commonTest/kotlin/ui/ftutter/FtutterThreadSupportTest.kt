package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.ui.graphics.Color
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FtutterThreadSupportTest {
    private fun post(
        id: String,
        body: String = "本文$id",
        quotes: List<String> = emptyList(),
        deleted: Boolean = false,
        soudane: String? = null,
        posterId: String? = null,
        thumb: String? = null
    ) = Post(
        id = id, author = "としあき", subject = "無題", timestamp = "26/10/13(月)12:00:00", posterId = posterId,
        messageHtml = (quotes.map { "&gt;No.$it" } + body).joinToString("<br>"),
        imageUrl = thumb?.replace("thumb", "src"), thumbnailUrl = thumb, saidaneLabel = soudane, isDeleted = deleted,
        quoteReferences = quotes.map { QuoteReference(">No.$it", listOf(it)) }
    )

    @Test
    fun theDefaultsFollowTheOriginal() {
        val s = parseFtutterThreadSettings(emptyMap())
        assertEquals(FtutterImagePosition.BOTTOM, s.imagePosition)
        assertEquals(3, s.popularReplies)
        assertEquals(10, s.popularSoudane)
        assertEquals(false, s.pullTop)
        assertEquals(true, s.pullBottom)
        assertEquals(false, s.showNewCount)
        assertEquals(true, s.showReplyCount)
        assertEquals(500.0, s.opImageMaxHeight)
        assertEquals(300.0, s.imageMaxHeight)
        // 範囲外の値は既定へ戻す
        assertEquals(3, parseFtutterThreadSettings(mapOf(FtutterKeys.POPULAR_REPLIES to "99")).popularReplies)
        assertEquals(FtutterImagePosition.LEFT, parseFtutterThreadSettings(mapOf(FtutterKeys.RES_IMAGE_POSITION to "1")).imagePosition)
    }

    @Test
    fun theHeaderColoursStepWithTheCounts() {
        assertNull(ftutterReplyCountColor(0))
        assertEquals(Color(0xFF448AFF), ftutterReplyCountColor(2))
        assertEquals(Color(0xFFE040FB), ftutterReplyCountColor(3))
        assertEquals(Color(0xFFFF4081), ftutterReplyCountColor(6))
        assertEquals(Color(0xFFFF5252), ftutterReplyCountColor(7))
        assertNull(ftutterIdCountColor(1))
        assertEquals(Color(0xFF448AFF), ftutterIdCountColor(3))
        assertEquals(Color(0xFFE040FB), ftutterIdCountColor(4))
        assertEquals(Color(0xFFFF4081), ftutterIdCountColor(7))
        assertEquals(Color(0xFFFF5252), ftutterIdCountColor(8))
        assertEquals(Color(0xFFF44336), ftutterSoudaneColor(30, Color.Black))
        assertEquals(Color(0xFFFF0000), ftutterSoudaneColor(81, Color.Black))
    }

    @Test
    fun theTreeIndentGrowsSlowerPastFourLevels() {
        assertEquals(0, ftutterIndentDp(0))
        assertEquals(60, ftutterIndentDp(4))
        assertEquals(67, ftutterIndentDp(5))
        assertEquals(74, ftutterIndentDp(6))
    }

    @Test
    fun theReadMarkSitsAfterTheLastReadPostOrBeforeTheFirst() = runBlocking {
        val posts = listOf(post("1"), post("2"), post("3"))
        val unread = ftutterBuildRows(posts, FtutterThreadSettings(), FtutterThreadFilter(), readCount = 0)
        assertEquals(FtutterRow.ReadMark, unread.first())
        val half = ftutterBuildRows(posts, FtutterThreadSettings(), FtutterThreadFilter(), readCount = 2)
        assertEquals(listOf("p0", "p1", "kokomade", "p2"), half.map { it.key })
        val all = ftutterBuildRows(posts, FtutterThreadSettings(), FtutterThreadFilter(), readCount = 3)
        assertEquals("kokomade", all.last().key)
    }

    @Test
    fun inTheTreeNewRepliesStayAfterTheReadMarkAsTheirOwnBranch() = runBlocking {
        // 0 ← 1 は既読、2 は既読の 0 への新しい返信、3 は 2 への返信。
        val posts = listOf(post("1"), post("2", quotes = listOf("1")), post("3", quotes = listOf("1")), post("4", quotes = listOf("3")))
        val rows = ftutterBuildRows(posts, FtutterThreadSettings(isTree = true), FtutterThreadFilter(), readCount = 2)
        assertEquals(listOf("p0", "p1", "kokomade", "p2", "p3"), rows.map { it.key })
        val depths = rows.filterIsInstance<FtutterRow.PostRow>().associate { it.index to it.depth }
        assertEquals(1, depths[1])
        assertEquals(0, depths[2]) // 枝の先頭を深さ0に付け直す
        assertEquals(1, depths[3])
    }

    @Test
    fun deletedPostsVanishInTheFlatViewButStayAsAReplacementRowAboveTheirRepliesInTheTree() = runBlocking {
        val posts = listOf(post("1"), post("2", deleted = true), post("3", quotes = listOf("2")), post("4", quotes = listOf("3")), post("5", deleted = true))
        // 通常表示：削除のレス本人だけを行ごと消し、返信は残す（原作）。
        val flat = ftutterBuildRows(posts, FtutterThreadSettings(), FtutterThreadFilter(), readCount = 5)
        assertTrue(flat.none { it is FtutterRow.HiddenRow })
        assertEquals(listOf(0, 2, 3), flat.filterIsInstance<FtutterRow.PostRow>().map { it.index })
        // ツリー表示：見えている返信を持つ削除のレスは置換行で残り、返信の無い削除のレス（5）は消える。
        val tree = ftutterBuildRows(posts, FtutterThreadSettings(isTree = true), FtutterThreadFilter(), readCount = 5)
        assertEquals(listOf(1), tree.filterIsInstance<FtutterRow.HiddenRow>().map { it.index })
        // 連鎖：それを引用した返信も段ごとに消える。
        val chained = ftutterBuildRows(posts, FtutterThreadSettings(deletedChain = true, isTree = true), FtutterThreadFilter(), readCount = 5)
        assertTrue(chained.none { (it as? FtutterRow.PostRow)?.index in setOf(2, 3) })
        assertTrue(chained.none { it is FtutterRow.HiddenRow })
        val shown = ftutterBuildRows(posts, FtutterThreadSettings(showDeleted = true), FtutterThreadFilter(), readCount = 5)
        assertTrue(shown.any { it is FtutterRow.PostRow && it.index == 1 })
    }

    @Test
    fun ngPostsAreHiddenLikeDeletedOnesAndTheChainFollowsReplies() = runBlocking {
        val posts = listOf(post("1"), post("2", body = "うんこ"), post("3", quotes = listOf("2")), post("4", quotes = listOf("3")))
        val matcher = FtutterNgMatcher(listOf(FtutterNgRule("ウンコ", FtutterNgType.WORD.stored)), "https://may.2chan.net/b/futaba.php")
        val ng = ftutterNgHiddenPosts(posts, matcher, chain = false)
        assertEquals(setOf(1), ng)
        val tree = ftutterBuildRows(posts, FtutterThreadSettings(isTree = true), FtutterThreadFilter(), 4, ftutterHidden(posts, FtutterThreadSettings(), ng, ngChain = false))
        assertEquals(listOf(FtutterRow.HiddenRow(1, 0, isNg = true)), tree.filterIsInstance<FtutterRow.HiddenRow>())
        val chained = ftutterHidden(posts, FtutterThreadSettings(), ng, ngChain = true)
        assertEquals(setOf(2, 3), chained.removed)
    }

    @Test
    fun popularPostsAreTheMainOnesPlusTheirConversations() = runBlocking {
        // 0 に3件の返信（本体）、5 はそうだね10（本体）、6←7←8 は3件の木（本体なしでも関連）、9 は孤立。
        val posts = listOf(
            post("1"), post("2", quotes = listOf("1")), post("3", quotes = listOf("1")), post("4", quotes = listOf("1")),
            post("5", quotes = listOf("2")), post("6", soudane = "そうだねx10"),
            post("7"), post("8", quotes = listOf("7")), post("9", quotes = listOf("8")), post("10")
        )
        val popular = ftutterPopular(posts, replyThreshold = 3, soudaneThreshold = 10)
        assertEquals(setOf(0, 5), popular.main)
        assertEquals(setOf(1, 2, 3, 4, 6, 7, 8), popular.related)
        val flat = ftutterBuildRows(posts, FtutterThreadSettings(), FtutterThreadFilter(popular = true), readCount = 10)
        assertEquals(setOf(0, 5), flat.filterIsInstance<FtutterRow.PostRow>().map { it.index }.toSet())
        val tree = ftutterBuildRows(posts, FtutterThreadSettings(isTree = true), FtutterThreadFilter(popular = true), readCount = 10)
        assertEquals((0..8).toSet(), tree.filterIsInstance<FtutterRow.PostRow>().map { it.index }.toSet())
    }

    @Test
    fun imagesOnlyAndSearchNarrowTheRows() = runBlocking {
        val posts = listOf(post("1", thumb = "https://x/b/thumb/1s.jpg"), post("2", body = "ネコ"), post("3", body = "いぬ"))
        val images = ftutterBuildRows(posts, FtutterThreadSettings(), FtutterThreadFilter(imagesOnly = true), 3)
        assertEquals(listOf(0), images.filterIsInstance<FtutterRow.PostRow>().map { it.index })
        // 全角半角・かなの種類は同一視（カタログの検索と同じ、原作との意図した違い）
        val search = ftutterBuildRows(posts, FtutterThreadSettings(), FtutterThreadFilter(query = "ねこ"), 3)
        assertEquals(listOf(1), search.filterIsInstance<FtutterRow.PostRow>().map { it.index })
    }

    @Test
    fun theConversationPopupNeedsAtLeastTwoPosts() = runBlocking {
        val posts = listOf(post("1"), post("2", quotes = listOf("1")), post("3"))
        assertEquals(listOf(0 to 0, 1 to 1), ftutterConversationOf(posts, 1))
        assertTrue(ftutterConversationOf(posts, 2).isEmpty())
    }

    @Test
    fun repliesIdsAndQuoteTargetsAreCounted() {
        val posts = listOf(
            post("1", posterId = "ID:abc"), post("2", quotes = listOf("1"), posterId = "ID:abc"),
            post("3", quotes = listOf("1"), posterId = "ID:xyz")
        )
        assertEquals(listOf(1, 2), ftutterReplyIndex(posts)["1"])
        val ids = ftutterIdCounts(posts)
        assertEquals(FtutterIdCount("abc", 2, 2), ids["2"])
        assertEquals(1, ids["3"]?.total)
        assertEquals(listOf(0), ftutterQuoteTargets(posts, 1))
        assertEquals(listOf(0), ftutterQuoteLineTargets(posts, 2, ">No.1"))
    }

    @Test
    fun theLeftLineMarksSelfRepliesAndChildren() {
        val mine = post("1")
        val reply = post("2", quotes = listOf("1"))
        assertEquals(FtutterPostMark.SELF, ftutterPostMark(mine, setOf("1"), 0))
        assertEquals(FtutterPostMark.REPLY_TO_SELF, ftutterPostMark(reply, setOf("1"), 1))
        assertEquals(FtutterPostMark.CHILD, ftutterPostMark(reply, emptySet(), 1))
        assertEquals(FtutterPostMark.NONE, ftutterPostMark(reply, emptySet(), 0))
    }

    @Test
    fun aSimpleQuoteIsShortenedOnlyWhenAsked() {
        val reply = post("2", body = "それな", quotes = listOf("1"))
        assertEquals(listOf(">No.1", "それな"), ftutterBodyLines(reply, shorten = false))
        assertEquals(listOf(">....", "それな"), ftutterBodyLines(reply, shorten = true))
    }

    @Test
    fun theRefreshNoticeCountsNewPostsAndRepliesToMe() {
        val before = listOf(post("1"), post("2"))
        val after = before + listOf(post("3", quotes = listOf("2")), post("4"))
        assertEquals("返信 1 件", ftutterRefreshNotice(before, after, setOf("2"), FtutterThreadSettings()))
        assertNull(ftutterRefreshNotice(before, after, emptySet(), FtutterThreadSettings()))
        assertEquals("新着 2 件\n返信 1 件", ftutterRefreshNotice(before, after, setOf("2"), FtutterThreadSettings(showNewCount = true)))
        assertNull(ftutterRefreshNotice(before, before, setOf("2"), FtutterThreadSettings(showNewCount = true)))
    }

    @Test
    fun theStoredReadCountIsTheRepliesReadLikeTheCatalogCountsThem() {
        // OPだけ読んだ＝返信0件読んだ。カタログの未読数（返信の数−既読）と同じ数え方。
        assertEquals(0, ftutterStoredFromPostsRead(1))
        assertEquals(1, ftutterPostsReadFromStored(0))
        assertEquals(0, ftutterStoredFromPostsRead(0))
        assertEquals(21, ftutterPostsReadFromStored(ftutterStoredFromPostsRead(21)))
    }

    @Test
    fun theReadCountNeverMovesBack() {
        assertEquals(5, ftutterReadCountAfter(5, 3))
        assertEquals(7, ftutterReadCountAfter(5, 7))
        assertEquals(2, ftutterReadCountAfter(null, 2))
    }

    @Test
    fun theEndBandSaysFallenArchivedOrTheExpiry() {
        assertEquals(FtutterEndBand("19:54頃消えます", FtutterEndState.NORMAL), ftutterEndBand("19:54頃消えます", false, false))
        assertEquals(FtutterEndState.DANGER, ftutterEndBand("もうすぐ消えます", false, false).state)
        assertEquals(FtutterEndBand("スレッドは落ちています", FtutterEndState.DANGER), ftutterEndBand("x", true, false))
        assertEquals(FtutterEndState.ARCHIVED, ftutterEndBand("x", true, true).state)
    }

    @Test
    fun urlsMailsAndThreadIdsAreReadAsTheOriginalDoes() {
        assertEquals("123", ftutterThreadIdFromUrl("https://may.2chan.net/b/res/123.htm"))
        assertNull(ftutterThreadIdFromUrl("https://may.2chan.net/b/futaba.htm"))
        assertTrue(ftutterIsThreadUrl("https://may.2chan.net/b/res/123.htm"))
        assertTrue(!ftutterIsThreadUrl("https://example.com/res/1.htm"))
        assertEquals("https://dec.2chan.net/up2/src/fu1234567.jpg", ftutterMailTarget("fu1234567.jpg"))
        assertEquals("https://dec.2chan.net/up/src/f123456.png", ftutterMailTarget("f123456.png"))
        assertEquals("http://x", ftutterMailTarget("http://x"))
        assertNull(ftutterMailTarget("sage"))
    }

    @Test
    fun theImageBoxCapsTheHeightAndOnlyFixesTheWidthWhenAsked() {
        val p = post("2", thumb = "t").copy(thumbnailWidth = 250, thumbnailHeight = 125)
        val op = ftutterImageBox(p, isOp = true, settings = FtutterThreadSettings())
        assertEquals(125f, op.maxHeight)
        assertNull(op.maxWidth)
        assertEquals(2f, op.aspect)
        val fixed = ftutterImageBox(p, isOp = false, settings = FtutterThreadSettings(imageFixedWidth = true))
        assertEquals(200f, fixed.maxWidth)
    }

    /** 監査M3：長押ししたレスは位置でなくNo.で引く。更新でレスが減った・途中に入った時も、選んだレスを指す。 */
    @Test
    fun aLongPressedPostIsFoundByNumberAfterTheListChanges() {
        val before = listOf(post("1"), post("2"), post("3"))
        val chosen = before[2].id
        // 以前は位置2のまま：レスが減ると範囲外で落ち、途中に入ると別のレスを指していた。
        val shrunk = listOf(post("1"), post("3"))
        assertEquals(1, ftutterIndexOfPost(shrunk, chosen))
        val inserted = listOf(post("1"), post("2"), post("2a"), post("3"))
        assertEquals("3", inserted[ftutterIndexOfPost(inserted, chosen)].id)
        assertEquals(-1, ftutterIndexOfPost(listOf(post("1")), chosen))
        assertEquals(-1, ftutterIndexOfPost(before, null))
    }
}
