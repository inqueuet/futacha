package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.board.PosterIdLabel
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * L-B3「カタログとスレッドの行」(docs/futaber-evolution-look-2026-10-31.md §1.3): the post header as one sentence in
 * futaber's order and colours (V08・V10・V25), the そうだね colours (V09), the picture's size (V11), the read rows of the
 * catalog (V12), the replies the tabs and the history show (F27), the end of the thread (F22) and the scroll bar (V32).
 */
class FutaberRowsTest {
    private val colors = FutaberTokens.light

    private fun post(
        id: String = "325108",
        timestamp: String = "26/10/16(金)21:19:42 IP:106.73.*(enabler.ne.jp)",
        author: String? = "ひー坊",
        subject: String? = "無題",
        mail: String? = null,
        posterId: String? = null
    ) = Post(
        id = id, author = author, subject = subject, timestamp = timestamp, messageHtml = "", imageUrl = null,
        thumbnailUrl = null, mail = mail, posterId = posterId
    )

    private fun List<FutaberHeaderPart>.kinds() = map { it.kind }

    /** V08・V25: number, subject, name, [mail], date, replies, IP, n/N, ID, No. in that order, as one line of text. */
    @Test
    fun theHeaderIsOneSentenceInTheOriginalsOrder() {
        val parts = futaberPostHeaderParts(
            ordinal = 16, post = post(subject = "スレ題", mail = "sage", posterId = "abcd"),
            idLabel = PosterIdLabel("ID:abcd(1/2)", highlight = true), ipCount = "7/7", replyCount = 3
        )
        assertEquals(
            listOf(
                FutaberHeaderPartKind.Ordinal, FutaberHeaderPartKind.Subject, FutaberHeaderPartKind.Name, FutaberHeaderPartKind.Mail,
                FutaberHeaderPartKind.Date, FutaberHeaderPartKind.Replies, FutaberHeaderPartKind.Ip, FutaberHeaderPartKind.IpCount,
                FutaberHeaderPartKind.Id, FutaberHeaderPartKind.No
            ),
            parts.kinds()
        )
        assertEquals(
            "16 スレ題 ひー坊 [sage] 26/10/16(金)21:19:42 IP:106.73.*(enabler.ne.jp) 7/7 ID:abcd(1/2) No.325108",
            futaberPostHeaderDescription(parts)
        )
        // The text drawn: the same words (with places to break between any two letters) and the replies' mark between
        // the date and the IP, one inline item.
        val text = futaberPostHeaderText(parts, colors)
        assertEquals(
            "16 スレ題 ひー坊 [sage] 26/10/16(金)21:19:42   IP:106.73.*(enabler.ne.jp) 7/7 ID:abcd(1/2) No.325108",
            text.text.replace("​", "")
        )
        val inline = text.getStringAnnotations(0, text.length).filter { it.item == FUTABER_REPLY_INLINE_ID }
        assertEquals(1, inline.size, "one replies mark")
        assertTrue(text.text.substring(0, inline.single().start).replace("​", "").endsWith("21:19:42 "), "the mark follows the date")
        // "No.325108" can be broken inside, as futaber breaks it ("No.3251" / "25").
        assertTrue("N​o​.​3​2​5" in text.text)
        // The ID is marked for its tap.
        val id = text.getStringAnnotations(FUTABER_HEADER_ID_TAG, 0, text.length).single()
        assertEquals("ID:abcd(1/2)", id.item)
    }

    /** The defaults are left out ("無題", "としあき"), the IP of a board without IPs and the replies when there are none. */
    @Test
    fun theHeaderLeavesOutWhatTheOriginalLeavesOut() {
        val plain = futaberPostHeaderParts(1, post(author = "としあき", timestamp = "26/10/16(金)22:37:55 IP:130.62.*(transix.jp)"), null, null, 0)
        assertEquals("1 26/10/16(金)22:37:55 IP:130.62.*(transix.jp) No.325108", futaberPostHeaderDescription(plain))
        // An IP seen once is grey; the replies are missing when there are none.
        assertNull(plain.single { it.kind == FutaberHeaderPartKind.Ip }.posterKey)
        assertTrue(plain.none { it.kind == FutaberHeaderPartKind.Replies })
        val idBoard = futaberPostHeaderParts(
            0, post(author = "としあき", subject = "無念", timestamp = "25/11/08(土)13:42:53 ID:Mua720AI", mail = "id表示", posterId = "Mua720AI"),
            PosterIdLabel("ID:Mua720AI(1/1)", false), null, 0
        )
        assertEquals("0 無念 [id表示] 25/11/08(土)13:42:53 ID:Mua720AI(1/1) No.325108", futaberPostHeaderDescription(idBoard))
        // "常に" shows the defaults too.
        assertEquals(
            "0 無題 としあき 26/10/16(金)21:19:42 IP:106.73.*(enabler.ne.jp) No.1",
            futaberPostHeaderDescription(futaberPostHeaderParts(0, post(id = "1", author = "としあき"), null, null, 0, alwaysShowTitleAndName = true))
        )
    }

    /** §8.1 (L-B2's handoff): the number blue, the subject red, the name green (futaber #327800), the mail futaber's blue. */
    @Test
    fun theHeaderTakesTheOriginalsColours() {
        val parts = futaberPostHeaderParts(
            0, post(subject = "スレ題", mail = "sage", posterId = "abcd"), PosterIdLabel("ID:abcd(1/2)", true), "1/2", 1
        )
        val text = futaberPostHeaderText(parts, colors)
        fun colourOf(word: String) = text.spanStyles.first { range ->
            text.text.substring(range.start, range.end).replace("​", "") == word
        }.item.color
        assertEquals(colors.ordinal, colourOf("0"))
        assertEquals(colors.accent, colourOf("スレ題"))
        assertEquals(colors.name, colourOf("ひー坊"))
        assertEquals(colors.mail, colourOf("[sage]"))
        assertEquals(colors.meta, colourOf("26/10/16(金)21:19:42"))
        assertEquals(colors.meta, colourOf("No.325108"))
        // An IP that posts again, its n/N and an ID take the poster's colour.
        val ip = "IP:106.73.*(enabler.ne.jp)"
        assertEquals(futaberPosterColor(ip, false), colourOf(ip))
        assertEquals(futaberPosterColor(ip, false), colourOf("1/2"))
        assertEquals(futaberPosterColor("abcd", false), colourOf("ID:abcd(1/2)"))
        assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, text.spanStyles.first { range ->
            text.text.substring(range.start, range.end).replace("​", "") == "ID:abcd(1/2)"
        }.item.fontWeight)
        // V10: the replies in the link colour.
        assertEquals(colors.link, futaberHeaderPartColor(parts.single { it.kind == FutaberHeaderPartKind.Replies }, colors))
        // futaber's name green and its "[sage]" blue are kept as they are (both 4.5:1 or more on white).
        assertEquals(androidx.compose.ui.graphics.Color(0xFF327800), colors.name)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF2F70E1), colors.mail)
    }

    /**
     * §8.1's handoff: the poster colours start with futaber's (green #50A04F, blue #4078F1, red #E35549, made darker in
     * their hue to 4.5:1; the dark list with its #00757C made lighter), the rest stay so more posters differ.
     */
    @Test
    fun thePosterColoursStartWithTheOriginals() {
        val light = futaberPosterColors(isDark = false)
        assertEquals(
            listOf(0xFF376F37, 0xFF335FC0, 0xFFAC4037).map { androidx.compose.ui.graphics.Color(it) },
            light.take(3)
        )
        // Same hue as futaber's: green, blue and red stay the strongest channel.
        assertTrue(light[0].green > light[0].red && light[0].green > light[0].blue)
        assertTrue(light[1].blue > light[1].red && light[1].blue > light[1].green)
        assertTrue(light[2].red > light[2].green && light[2].red > light[2].blue)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF0098A1), futaberPosterColors(isDark = true).first())
        assertEquals(8, light.toSet().size)
        assertEquals(8, futaberPosterColors(isDark = true).toSet().size)
    }

    /** V09: ×5 and more red, below that the yellow (made readable); 3 and 4 are provisional (low) until L-B0. */
    @Test
    fun saidaneIsRedFromFiveAndYellowBelow() {
        listOf(FutaberTokens.light, FutaberTokens.dark, FutaberTokens.futaba).forEach { c ->
            listOf(1, 2, 3, 4).forEach { assertEquals(c.saidaneLow, futaberSaidaneColor(it, c), "×$it") }
            listOf(5, 6, 45, 999).forEach { assertEquals(c.saidaneHigh, futaberSaidaneColor(it, c), "×$it") }
        }
        assertEquals(androidx.compose.ui.graphics.Color(0xFF936F00), FutaberTokens.light.saidaneLow)
        assertTrue(FutaberTokens.light.saidaneLow != FutaberTokens.light.saidaneHigh)
    }

    /**
     * V11 (provisional rule): the picture at 0.7 of its thumbnail, as every attachment in the futaber captures is
     * (250×188 → 175×132, 251×209 → 176×146, 189×250 → 132×175, 230×250 → 161×175).
     */
    @Test
    fun aPictureIsSevenTenthsOfItsThumbnail() {
        fun assertSize(w: Float, h: Float, size: FutaberAttachmentSize) {
            assertTrue(abs(size.width - w) < 0.6f && abs(size.height - h) < 0.6f, "expected ${w}x$h, was ${size.width}x${size.height}")
        }
        assertSize(175f, 132f, futaberAttachmentSize(250, 188))
        assertSize(176f, 146f, futaberAttachmentSize(251, 209))
        assertSize(132f, 175f, futaberAttachmentSize(189, 250))
        assertSize(161f, 175f, futaberAttachmentSize(230, 250))
        // No size on the page: a square of the largest thumbnail.
        assertSize(175f, 175f, futaberAttachmentSize(null, null))
        assertSize(175f, 175f, futaberAttachmentSize(0, 188))
        // The ratio is a parameter (画像を小さくする, the coming ratio setting C21).
        assertSize(52.5f, 39.5f, futaberAttachmentSize(250, 188, 0.21f))
        // A very long thumbnail keeps at most 4:1.
        val long = futaberAttachmentSize(250, 20)
        assertEquals(4f, long.width / long.height, 0.01f)
    }

    private val board = BoardSummary(id = "may27", name = "猫", category = "", url = "https://may.2chan.net/27/futaba.php", description = "")

    private fun entry(threadId: String, boardId: String, boardUrl: String) = ThreadHistoryEntry(
        threadId = threadId, boardId = boardId, title = "t", titleImageUrl = "", boardName = "猫", boardUrl = boardUrl,
        lastVisitedEpochMillis = 1L, replyCount = 3
    )

    /** V12: the threads of the board in the shared history, whichever mode wrote the row. */
    @Test
    fun theReadRowsAreTheBoardsThreadsInTheSharedHistory() {
        val history = listOf(
            // ふたばー風 / ふたちゃ: the board's id and the thread's own address.
            entry("100", "may27", "https://may.2chan.net/27/res/100.htm"),
            // としあき(仮): the shared board key as the id and the board's address.
            entry("200", "compat_board_may_27", "https://may.2chan.net/27/"),
            // Another mode's id with the thread's address (http, upper case host).
            entry("300", "other", "http://MAY.2chan.net/27/res/300.htm"),
            // Other boards: the same number elsewhere, and a board whose path starts the same.
            entry("400", "img_b", "https://img.2chan.net/b/res/400.htm"),
            entry("500", "may273", "https://may.2chan.net/273/res/500.htm"),
            entry("", "may27", "https://may.2chan.net/27/res/.htm")
        )
        assertEquals(setOf("100", "200", "300"), futaberReadThreadIds(history, board))
        // The tutorial board (not an official address): its own id only.
        val tutorial = BoardSummary(id = "tutorial", name = "チュートリアル", category = "", url = "https://www.example.com/t/", description = "")
        assertEquals(
            setOf("1"),
            futaberReadThreadIds(listOf(entry("1", "tutorial", "https://www.example.com/t/res/1.htm"), entry("2", "x", "https://www.example.com/t/res/2.htm")), tutorial)
        )
        assertEquals(emptySet(), futaberReadThreadIds(emptyList(), board))
    }

    /** V27: futaber's 40 row (the wide one provisional), and a read row says so to a screen reader. */
    @Test
    fun aCatalogRowIsFortyHighAndSaysWhenItWasRead() {
        assertEquals(40, futaberCatalogRowHeightDp(wide = false))
        assertEquals(56, futaberCatalogRowHeightDp(wide = true))
        assertEquals(4 + 40 + 12, FUTABER_CATALOG_ROW_THUMB_START_DP + FUTABER_CATALOG_ROW_DP + FUTABER_CATALOG_ROW_TITLE_GAP_DP)
        assertEquals("本日は雨、2レス", futaberCatalogItemDescription("本日は雨", 2, read = false))
        assertEquals("本日は雨、2レス、既読", futaberCatalogItemDescription("本日は雨", 2, read = true))
    }

    /** F27: the tabs and the history show replies (the stored posts less the first); nothing stored changes. */
    @Test
    fun theTabsAndTheHistoryShowRepliesNotPosts() {
        assertEquals(19, futaberShownReplyCount(20))
        assertEquals(0, futaberShownReplyCount(1))
        assertEquals(0, futaberShownReplyCount(0))
        assertEquals(0, futaberShownReplyCount(-3))
        val stored = entry("100", "may27", "https://may.2chan.net/27/res/100.htm").copy(replyCount = 20)
        val tab = futaberTabViews(listOf(FutaberTabKey("may27", "100")), listOf(stored)).single()
        assertEquals(20, tab.replyCount, "the tab keeps the stored count; only the shown number is one less")
        assertEquals(19, futaberShownReplyCount(tab.replyCount))
    }

    /** F22: the end of the thread shows the page's "…頃消えます"; nothing when the page has none. */
    @Test
    fun theThreadEndsWithTheExpiryOfThePage() {
        val page = ThreadPage(threadId = "1", boardTitle = null, expiresAtLabel = " 28年5月頃消えます ", deletedNotice = null, posts = emptyList())
        assertEquals("28年5月頃消えます", futaberThreadEndLabel(page))
        assertNull(futaberThreadEndLabel(page.copy(expiresAtLabel = "  ")))
        assertNull(futaberThreadEndLabel(page.copy(expiresAtLabel = null)))
        assertNull(futaberThreadEndLabel(null))
        // The tutorial board's page (the one the device tests open) has one, and its first post a mail field.
        val tutorial = runBlocking { FakeBoardRepository().getThread("https://www.example.com/t/", "1364612020") }
        assertEquals("19:54頃消えます", futaberThreadEndLabel(tutorial))
        assertEquals("id表示", tutorial.posts.first().mail)
    }

    /** V32: the bar is as long as the share on screen and as far down as the list is scrolled; none when nothing scrolls. */
    @Test
    fun theScrollBarFollowsTheList() {
        fun thumb(first: Int, offset: Float = 0f, atStart: Boolean = false, atEnd: Boolean = false) =
            futaberScrollThumb(1000f, totalUnits = 100, firstUnit = first, firstUnitOffset = offset, averageUnitSize = 100f, atStart = atStart, atEnd = atEnd, minThumb = 36f)
        val top = assertNotNull(thumb(0, atStart = true))
        assertEquals(0f, top.top)
        assertEquals(100f, top.height, 0.01f) // 1000 of 10,000
        val middle = assertNotNull(thumb(45))
        assertEquals((1000f - 100f) * 0.5f, middle.top, 0.5f)
        val end = assertNotNull(thumb(90, atEnd = true))
        assertEquals(900f, end.top, 0.01f)
        // A long list: the bar keeps a length a finger can see.
        val long = assertNotNull(futaberScrollThumb(1000f, 10_000, 0, 0f, 100f, atStart = true, atEnd = false, minThumb = 36f))
        assertEquals(36f, long.height)
        // Nothing to scroll, or nothing measured yet.
        assertNull(futaberScrollThumb(1000f, 5, 0, 0f, 100f, atStart = true, atEnd = true, minThumb = 36f))
        assertNull(futaberScrollThumb(0f, 100, 0, 0f, 100f, atStart = true, atEnd = false, minThumb = 36f))
        assertNull(futaberScrollThumb(1000f, 100, 0, 0f, 0f, atStart = true, atEnd = false, minThumb = 36f))
    }
}
