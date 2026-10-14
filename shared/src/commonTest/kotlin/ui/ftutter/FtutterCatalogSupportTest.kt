package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FtutterCatalogSupportTest {
    private fun item(id: String, title: String?, replies: Int = 0, board: String = "https://may.2chan.net/27/") =
        CatalogItem(id = id, threadUrl = "${board}res/$id.htm", title = title, thumbnailUrl = null, fullImageUrl = null, replyCount = replies)

    private fun board(id: String, url: String, pinned: Boolean = false) =
        BoardSummary(id = id, name = id, category = "", url = url, description = "", pinned = pinned)

    @Test
    fun theSevenOrdersAreServerSortsWithTheOriginalNumbers() {
        assertEquals(listOf("カタ", "新順", "古順", "多順", "少順", "勢順", "そ順"), FtutterOrder.entries.map { it.label })
        assertEquals((0..6).toList(), FtutterOrder.entries.map { it.stored })
        assertEquals(
            listOf(CatalogMode.Catalog, CatalogMode.New, CatalogMode.Old, CatalogMode.Many, CatalogMode.Few, CatalogMode.Momentum, CatalogMode.So),
            FtutterOrder.entries.map { it.mode }
        )
        assertEquals(listOf(null, "1", "2", "3", "4", "6", "8"), FtutterOrder.entries.map { it.mode.sortParam })
    }

    @Test
    fun theStartOrderFollowsTheDefaultOrderSetting() {
        assertEquals(FtutterOrder.CATALOG, parseFtutterCatalogSettings(emptyMap()).startOrder)
        assertEquals(FtutterOrder.SOUDANE, parseFtutterCatalogSettings(mapOf(FtutterKeys.PREVIOUS_ORDER_MODE to "6")).startOrder)
        assertEquals(
            FtutterOrder.NEW,
            parseFtutterCatalogSettings(mapOf(FtutterKeys.DEFAULT_ORDER_MODE to "1", FtutterKeys.PREVIOUS_ORDER_MODE to "6")).startOrder
        )
    }

    @Test
    fun catalogSettingsUseTheOriginalDefaultsAndRanges() {
        val d = parseFtutterCatalogSettings(emptyMap())
        assertEquals(5, d.columnsPortrait)
        assertEquals(8, d.columnsLandscape)
        assertEquals(500, d.maxThreadCount)
        assertTrue(d.sameHeight)
        assertEquals(1.2, d.maxThumbHeight)
        assertEquals(0.8, d.minThumbHeight)
        assertEquals(FtutterNumberPosition.OVERLAY, d.numberPosition)
        assertEquals(2, d.titleLines)
        assertEquals(4, d.maxTitleLetters)
        assertTrue(d.pullTop && d.pullBottom && d.kitaShorten && d.useZoom)
        val broken = parseFtutterCatalogSettings(
            mapOf(FtutterKeys.CATALOG_ROW to "0", FtutterKeys.MAX_CATALOG_THREAD_COUNT to "9999", FtutterKeys.NUMBER_ON_THUMB to "7")
        )
        assertEquals(5, broken.columnsPortrait)
        assertEquals(500, broken.maxThreadCount)
        assertEquals(FtutterNumberPosition.OVERLAY, broken.numberPosition)
    }

    @Test
    fun theKitaTitleIsShortenedOnlyWhenItIsTheBoardsDefaultTitle() {
        val settings = FtutterCatalogSettings()
        assertEquals("ｷﾀ━(ﾟ∀ﾟ)━!", ftutterCatalogTitle("ｷﾀ━━━(ﾟ∀ﾟ)━━━!!", "https://may.2chan.net/27/", settings))
        assertEquals("ｷﾀ━(ﾟ∀ﾟ)━!", ftutterCatalogTitle("ｷﾀ━━━━━━(ﾟ∀ﾟ)━━━━━━ !!!!!", "https://img.2chan.net/b/futaba.php", settings))
        // img/b's own default title is not the may one, and a title that only starts with it stays.
        assertEquals("ｷﾀ━━━(ﾟ∀ﾟ)━━━!!", ftutterCatalogTitle("ｷﾀ━━━(ﾟ∀ﾟ)━━━!!", "https://img.2chan.net/b/", settings))
        assertEquals("ｷﾀ━━━(ﾟ∀ﾟ)━━━!!です", ftutterCatalogTitle("ｷﾀ━━━(ﾟ∀ﾟ)━━━!!です", "https://may.2chan.net/27/", settings))
        assertEquals("ｷﾀ━━━(ﾟ∀ﾟ)━━━!!", ftutterCatalogTitle("ｷﾀ━━━(ﾟ∀ﾟ)━━━!!", "https://may.2chan.net/27/", settings.copy(kitaShorten = false)))
        assertEquals("骨三10", ftutterCatalogTitle("骨三10歳", "https://may.2chan.net/27/", settings.copy(limitTitleLetters = true)))
        assertEquals("", ftutterCatalogTitle("No.325098", "https://may.2chan.net/27/", settings))
    }

    @Test
    fun theMasonryAspectIsClampedToTheHeightRatios() {
        val settings = FtutterCatalogSettings()
        assertEquals(1f, ftutterThumbAspectRatio(null, 100, settings))
        assertEquals(1f, ftutterThumbAspectRatio(100, 100, settings))
        // Very tall picture: height / width capped at 1.2.
        assertEquals((1 / 1.2).toFloat(), ftutterThumbAspectRatio(100, 400, settings))
        // Very wide picture: height / width at least 0.8.
        assertEquals((1 / 0.8).toFloat(), ftutterThumbAspectRatio(400, 100, settings))
    }

    @Test
    fun hiddenThreadsAreHttpsAndDroppedWhenTheyLeaveTheirBoardsCatalog() {
        val hidden = parseFtutterHiddenThreads("http://may.2chan.net/27/res/1.htm\nhttps://may.2chan.net/27/res/2.htm\nhttps://img.2chan.net/b/res/9.htm\n")
        assertEquals(listOf("https://may.2chan.net/27/res/1.htm", "https://may.2chan.net/27/res/2.htm", "https://img.2chan.net/b/res/9.htm"), hidden)
        val pruned = ftutterPruneHiddenThreads(hidden, "https://may.2chan.net/27/futaba.php", listOf(item("2", "a")))
        assertEquals(listOf("https://may.2chan.net/27/res/2.htm", "https://img.2chan.net/b/res/9.htm"), pruned)
    }

    @Test
    fun theVisibleCatalogSearchesHidesAndCuts() {
        val items = listOf(item("1", "ねこ写真"), item("2", "ネコ写真"), item("3", "犬"), item("4", "ABC"))
        assertEquals(listOf("1", "2"), ftutterVisibleCatalog(items, "ねこ", emptySet(), 500).map { it.id })
        assertEquals(listOf("4"), ftutterVisibleCatalog(items, "abc", emptySet(), 500).map { it.id })
        assertEquals(listOf("2", "3", "4"), ftutterVisibleCatalog(items, "", setOf("https://may.2chan.net/27/res/1.htm"), 500).map { it.id })
        assertEquals(listOf("1", "2"), ftutterVisibleCatalog(items, "", emptySet(), 2).map { it.id })
    }

    @Test
    fun numbersFollowTheOriginalRules() {
        val catalog = item("7", "t", replies = 10)
        assertEquals(FtutterCatalogNumbers(10, null), ftutterCatalogNumbers(catalog, null, 3))
        val history = ThreadHistoryEntry(threadId = "7", boardId = "b", title = "t", titleImageUrl = "", boardName = "", boardUrl = "", lastVisitedEpochMillis = 0, replyCount = 15)
        // The history counts the OP: 15 posts = 14 replies, more than the catalog's 10.
        assertEquals(FtutterCatalogNumbers(14, 9), ftutterCatalogNumbers(catalog, history, 5))
        assertEquals(FtutterCatalogNumbers(14, null), ftutterCatalogNumbers(catalog, history, 14))
        assertEquals(FtutterCatalogNumbers(14, null), ftutterCatalogNumbers(catalog, history, null))
        val counts = parseFtutterReadCounts(encodeFtutterReadCounts(mapOf("https://may.2chan.net/27/res/7.htm" to 5)) + "\nbroken\n")
        assertEquals(mapOf("https://may.2chan.net/27/res/7.htm" to 5), counts)
    }

    @Test
    fun boardInputIsClassifiedLikeTheOriginal() {
        val existing = listOf(board("may27", "https://may.2chan.net/27/futaba.php"))
        assertIs<FtutterBoardInput.AllBoards>(classifyFtutterBoardInput("https://www.2chan.net/", existing))
        assertIs<FtutterBoardInput.AllBoards>(classifyFtutterBoardInput("http://www.2chan.net/bbsmenu.html", existing))
        assertIs<FtutterBoardInput.Invalid>(classifyFtutterBoardInput("may.2chan.net/b/", existing))
        assertIs<FtutterBoardInput.Invalid>(classifyFtutterBoardInput("https://example.com/b/", existing))
        // Duplicates are found after normalising (the original compared the raw text).
        assertIs<FtutterBoardInput.Invalid>(classifyFtutterBoardInput("https://may.2chan.net/27/futaba.htm", existing))
        val one = classifyFtutterBoardInput("http://img.2chan.net/b/", existing)
        assertIs<FtutterBoardInput.OneBoard>(one)
        assertTrue(one.url.startsWith("https://img.2chan.net/b/"), one.url)
    }

    @Test
    fun theBoardNameComesFromThePageTitle() {
        assertEquals("ねこ", ftutterBoardNameFromPage("""<span id="tit">ねこ＠ふたば</span>""", "https://may.2chan.net/27/"))
        assertEquals("二次元裏（img）", ftutterBoardNameFromPage("""<span id='tit'>二次元裏＠ふたば</span>""", "https://img.2chan.net/b/"))
        assertNull(ftutterBoardNameFromPage("<html></html>", "https://may.2chan.net/27/"))
    }

    @Test
    fun boardsMoveWithinTheirGroup() {
        val boards = listOf(board("a", "u", pinned = true), board("b", "u"), board("c", "u", pinned = true), board("d", "u"))
        // "c" is a favourite: one step up skips the non-favourite "b".
        assertEquals(listOf("c", "a", "b", "d"), ftutterMoveBoard(boards, "c", up = true).map { it.id })
        // "b" is not: one step down skips the favourite "c" and lands after "d".
        assertEquals(listOf("a", "c", "d", "b"), ftutterMoveBoard(boards, "b", up = false).map { it.id })
        assertEquals(boards, ftutterMoveBoard(boards, "a", up = true))
        assertEquals(listOf("a", "c", "d", "b"), ftutterMoveBoardToEnd(boards, "b", toTop = false).map { it.id })
        assertEquals(listOf("c", "a", "b", "d"), ftutterMoveBoardToEnd(boards, "c", toTop = true).map { it.id })
    }

    @Test
    fun theToolbarIsRestoredRepairedAndSized() {
        assertEquals(FTUTTER_CATALOG_TOOLBAR_DEFAULT, parseFtutterCatalogToolbar(emptyMap()))
        // Names and switches of different lengths: the original falls back to the defaults.
        assertEquals(
            FTUTTER_CATALOG_TOOLBAR_DEFAULT,
            parseFtutterCatalogToolbar(mapOf(FtutterKeys.CATALOG_TOOLBAR_ORDER to "search", FtutterKeys.CATALOG_TOOLBAR_ENABLED to "true\nfalse"))
        )
        val parsed = parseFtutterCatalogToolbar(
            mapOf(
                FtutterKeys.CATALOG_TOOLBAR_ORDER to "search\nunknown\nsearch\ndrawer",
                FtutterKeys.CATALOG_TOOLBAR_ENABLED to "true\ntrue\nfalse\nfalse"
            )
        )
        assertEquals(FtutterCatalogTool.SEARCH, parsed[0].tool)
        assertEquals(FtutterToolbarEntry(FtutterCatalogTool.DRAWER, false), parsed[1])
        assertEquals(FtutterCatalogTool.entries.size, parsed.size)
        assertEquals(FtutterCatalogTool.entries.size, parsed.map { it.tool }.toSet().size)
        val (names, switches) = encodeFtutterToolbar(parsed) { it.stored }
        assertEquals(parsed, parseFtutterToolbar(decodeFtutterList(names), decodeFtutterList(switches), FTUTTER_CATALOG_TOOLBAR_DEFAULT) { it.stored })
        // The original's width: min(45, (width - 5 - 5 - 20 - 64) / 6).
        assertEquals(45f, ftutterToolbarButtonWidth(411f, FtutterToolbarMetrics(), 6, hasSpace = true, hasOrder = true))
        assertEquals(30f, ftutterToolbarButtonWidth(274f, FtutterToolbarMetrics(), 6, hasSpace = true, hasOrder = true))
    }
}
