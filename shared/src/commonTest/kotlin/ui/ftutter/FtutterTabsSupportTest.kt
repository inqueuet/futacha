package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

class FtutterTabsSupportTest {
    private fun url(n: Int) = "https://may.2chan.net/27/res/$n.htm"

    @Test
    fun anOpenedThreadGoesFirstButAKnownTabStaysWhereItIs() {
        assertEquals(listOf(url(3), url(1), url(2)), ftutterPushTab(listOf(url(1), url(2)), url(3)))
        assertEquals(listOf(url(1), url(2)), ftutterPushTab(listOf(url(1), url(2)), url(2)))
    }

    @Test
    fun closingAllAndUndoingOneByOneRestoresTheOrder() {
        // 原作の実測：16タブを全て閉じ、Undoを16回押すと元の順に戻る。
        val tabs = (1..16).map(::url)
        val closed = ftutterCloseMany(tabs, tabs, emptySet())
        assertEquals(emptyList(), closed.urls)
        var current = closed.urls
        closed.closed.reversed().forEach { current = ftutterRestoreTab(current, it) }
        assertEquals(tabs, current)
    }

    @Test
    fun bulkClosingKeepsProtectedTabs() {
        val tabs = listOf(url(1), url(2), url(3))
        val result = ftutterCloseMany(tabs, tabs, setOf(url(2)))
        assertEquals(listOf(url(2)), result.urls)
        // 先頭から閉じる：url(1)は0番、残った [2,3] の url(3) は1番で記録する。
        assertEquals(listOf(0, 1), result.closed.map { it.index })
    }

    @Test
    fun aRestoredTabGoesBackToItsPlaceOrTheEnd() {
        assertEquals(listOf(url(1), url(9), url(2)), ftutterRestoreTab(listOf(url(1), url(2)), FtutterClosedTab(url(9), 1)))
        assertEquals(listOf(url(1), url(9)), ftutterRestoreTab(listOf(url(1)), FtutterClosedTab(url(9), 5)))
        assertEquals(listOf(url(9)), ftutterRestoreTab(listOf(url(9)), FtutterClosedTab(url(9), 0)))
    }

    @Test
    fun theTabsAfterOneFollowTheOrderShown() {
        assertEquals(listOf(url(3), url(4)), ftutterTabsAfter(listOf(url(1), url(2), url(3), url(4)), url(2)))
        assertEquals(emptyList(), ftutterTabsAfter(listOf(url(1)), url(5)))
    }

    @Test
    fun theStoredTabsAreReadSafely() {
        val tabs = parseFtutterTabs(mapOf(
            FtutterKeys.TABS to "http://may.2chan.net/27/res/1.htm\n\n${url(1)}\n${url(2)}",
            FtutterKeys.OPENING to url(9)
        ))
        // http は https にそろえ、重複と空行は落とす。開いているURLがタブに無ければ先頭。
        assertEquals(listOf(url(1), url(2)), tabs.urls)
        assertEquals(url(1), tabs.opening)
        assertEquals(setOf(url(1)), parseFtutterProtected(mapOf(FtutterKeys.PROTECTED to "http://may.2chan.net/27/res/1.htm")))
    }

    private fun entry(n: Int, replies: Int, title: String = "スレ$n", self: Boolean = false) = ThreadHistoryEntry(
        threadId = "$n", boardId = "may27", title = title, titleImageUrl = "", boardName = "ねこ", boardUrl = url(n),
        lastVisitedEpochMillis = 0, replyCount = replies, hasSelfPost = self
    )

    @Test
    fun theDrawerListsHistoryTabsOrTabsInHistoryOrder() {
        val history = listOf(url(3), url(1), url(2))
        val tabs = listOf(url(1), url(2))
        assertEquals(history, ftutterDrawerUrls(FtutterDrawerContent.HISTORY, history, tabs))
        assertEquals(tabs, ftutterDrawerUrls(FtutterDrawerContent.TABS, history, tabs))
        assertEquals(listOf(url(1), url(2)), ftutterDrawerUrls(FtutterDrawerContent.TABS_BY_ACCESS, listOf(url(2), url(1)), tabs).reversed())
    }

    /** 再監査M7：2万件の履歴と多いタブでも、「タブ（更新順）」の行はすぐ作れる（以前はリストで数億回の比較）。 */
    @Test
    fun drawerRowsForAHugeHistoryAreQuick() {
        val history = (1..20_000).map(::url)
        val tabs = (10_001..30_000).map(::url)
        val entries = history.associateWith { u -> entry(u.substringAfterLast("/").removeSuffix(".htm").toInt(), 3) }
        val started = kotlin.time.TimeSource.Monotonic.markNow()
        val rows = ftutterDrawerRows(
            FtutterDrawerContent.TABS_BY_ACCESS, history, tabs, setOf(url(10_001)), entries, emptyMap(), FtutterDrawerFilter(), null
        )
        val took = started.elapsedNow()
        assertEquals(20_000, rows.size)
        assertEquals(url(10_001), rows.first().url)
        assertTrue(rows.first().protected && rows.first().isTab)
        assertEquals(entries[url(10_001)], rows.first().entry)
        assertEquals(url(30_000), rows.last().url)
        assertTrue(took < kotlin.time.Duration.parse("200ms"), "took $took")
    }

    @Test
    fun theDrawerFiltersUnreadWrittenAndTitle() {
        val entries = mapOf(url(1) to entry(1, 11), url(2) to entry(2, 3, title = "ネコ", self = true))
        val urls = listOf(url(1), url(2), url(3))
        // url(1)：返信10件、既読4件 → 未読6。url(2)：返信2件、既読2件 → 未読なし。
        val read = mapOf(url(1) to 4, url(2) to 2)
        assertEquals(listOf(url(1)), ftutterFilterDrawer(urls, entries, read, emptySet(), FtutterDrawerFilter(unreadOnly = true)))
        assertEquals(listOf(url(2)), ftutterFilterDrawer(urls, entries, read, emptySet(), FtutterDrawerFilter(writtenOnly = true)))
        assertEquals(listOf(url(2)), ftutterFilterDrawer(urls, entries, read, emptySet(), FtutterDrawerFilter(query = "ねこ")))
        assertEquals(urls, ftutterFilterDrawer(urls, entries, read, emptySet(), FtutterDrawerFilter()))
        assertEquals(FtutterTabNumbers(10, 6), ftutterTabNumbers(entries[url(1)], 4))
        assertEquals(FtutterTabNumbers(10, null), ftutterTabNumbers(entries[url(1)], null))
    }

    @Test
    fun deletingHistoryPicksEveryRowOfTheChosenThreads() {
        // 同じスレの行が2つあっても両方消す。板を決められない行は選ばれない。
        val unknown = entry(9, 1).copy(boardUrl = "https://may.2chan.net/27/futaba.php", boardId = "other")
        val history = listOf(entry(1, 1), entry(2, 1), entry(1, 5), unknown)
        assertEquals(listOf(entry(1, 1), entry(1, 5)), ftutterHistoryEntriesFor(history, emptyList(), setOf(url(1))))
        assertEquals(emptyList(), ftutterHistoryEntriesFor(history, emptyList(), emptySet()))
    }

    @Test
    fun deletingAllHistoryKeepsOnlyProtectedThreads() {
        val unknown = entry(9, 1).copy(boardUrl = "https://may.2chan.net/27/futaba.php", boardId = "other")
        val history = listOf(entry(1, 1), entry(2, 1), unknown)
        assertEquals(listOf(entry(1, 1), unknown), ftutterHistoryEntriesExcept(history, emptyList(), setOf(url(2))))
        assertEquals(history, ftutterHistoryEntriesExcept(history, emptyList(), emptySet()))
    }

    @Test
    fun historyRowsAreMatchedByTheirThreadUrl() {
        val board = BoardSummary(id = "may27", name = "ねこ", category = "", url = "https://may.2chan.net/27/futaba.php", description = "")
        val byBoard = entry(5, 1).copy(boardUrl = "https://may.2chan.net/27/futaba.php")
        assertEquals(url(5), ftutterHistoryThreadUrl(byBoard, listOf(board)))
        assertEquals(url(1), ftutterHistoryThreadUrl(entry(1, 1).copy(boardUrl = "http://may.2chan.net/27/res/1.htm"), emptyList()))
        assertNull(ftutterHistoryThreadUrl(byBoard.copy(boardId = "other"), listOf(board)))
    }

    @Test
    fun aThreadUrlFindsItsBoard() {
        val may = BoardSummary(id = "may27", name = "ねこ", category = "", url = "https://may.2chan.net/27/futaba.php", description = "")
        val tutorial = BoardSummary(id = "t", name = "チュートリアル", category = "", url = "https://www.example.com/t/futaba.php", description = "")
        val boards = listOf(may, tutorial)
        assertEquals(may, ftutterBoardForUrl(boards, "http://MAY.2chan.net/27/res/123.htm"))
        assertEquals(tutorial, ftutterBoardForUrl(boards, "https://www.example.com/t/res/1364612020.htm"))
        assertEquals(may, ftutterBoardForUrl(boards, "https://may.2chan.net/27/futaba.php?mode=cat"))
        assertNull(ftutterBoardForUrl(boards, "https://may.2chan.net/b/res/1.htm"))
    }

    @Test
    fun closingTheOpenTabMovesToTheTabInItsPlace() {
        val before = listOf(url(1), url(2), url(3))
        assertEquals(url(3), ftutterNextOpening(before, listOf(url(1), url(3)), url(2)))
        assertEquals(url(2), ftutterNextOpening(before, listOf(url(1), url(2)), url(3)))
        assertNull(ftutterNextOpening(listOf(url(1)), emptyList(), url(1)))
    }

    @Test
    fun theTabSettingsDefaultsFollowTheOriginal() {
        val s = parseFtutterTabSettings(emptyMap())
        assertEquals(false, s.showTab)
        assertEquals(false, s.tabOnTop)
        assertEquals(40, s.tabHeight)
        assertEquals(60, s.tabWidth)
        assertEquals(true, s.backWhenNoTab)
        assertEquals(FtutterDrawerSide.LEFT, s.drawerSide)
        assertEquals(FtutterDismissDirection.CLOSE_TO_RIGHT, s.dismissDirection)
        assertEquals(FtutterDrawerContent.HISTORY, s.drawerContent)
        assertEquals(FtutterDismissDirection.NONE, parseFtutterTabSettings(mapOf(FtutterKeys.TAB_DISMISS_DIRECTION to "3")).dismissDirection)
    }

    /** 監査M8：離した時の並べ替えは、離した時の並びから位置を引く。 */
    @Test
    fun aTabDragMovesByTheWidthFromWhereTheTabIsNow() {
        val urls = listOf("a", "b", "c")
        assertEquals(1 to 2, ftutterTabDragMove(urls, "b", 60f, 60f))
        assertEquals(1 to 0, ftutterTabDragMove(urls, "b", -70f, 60f))
        assertEquals(2 to 0, ftutterTabDragMove(urls, "c", -600f, 60f))
        // 幅の3分の1より動かしていない、または動いた先が同じなら、長押しのメニュー。
        assertNull(ftutterTabDragMove(urls, "b", 15f, 60f))
        assertNull(ftutterTabDragMove(urls, "c", 120f, 60f))
        // 組み直しで並びが変わっても、今の位置から動かす。消えたタブは何もしない。
        assertEquals(0 to 1, ftutterTabDragMove(listOf("b", "a", "c"), "b", 60f, 60f))
        assertNull(ftutterTabDragMove(listOf("a", "c"), "b", 60f, 60f))
    }

    /** 監査M9：板を表で引いても、以前（行ごとに板を探す）と同じ表になる。 */
    @Test
    fun theHistoryIndexByUrlIsTheSameWithTheBoardTable() {
        val boards = (0 until 50).map { BoardSummary("b$it", "板$it", "", "https://may.2chan.net/$it/futaba.php", "") } +
            BoardSummary("b0", "重複", "", "https://img.2chan.net/x/futaba.php", "")
        val history = (0 until 2000).map { i ->
            ThreadHistoryEntry(
                threadId = "${100000 + i}", boardId = if (i % 7 == 0) "missing" else "b${i % 50}", title = "t$i", titleImageUrl = "",
                boardName = "", boardUrl = if (i % 3 == 0) "https://may.2chan.net/${i % 50}/res/${100000 + i}.htm" else "",
                lastVisitedEpochMillis = 0L, replyCount = 0
            )
        } + ThreadHistoryEntry("100003", "b3", "dup", "", "", "", 0L, 0)
        val expected = LinkedHashMap<String, ThreadHistoryEntry>()
        history.forEach { entry -> ftutterHistoryThreadUrl(entry, boards)?.let { if (it !in expected) expected[it] = entry } }
        val actual = ftutterHistoryByUrl(history, boards)
        assertEquals(expected.toList(), actual.toList())
    }

    /** 監査M11：先に作った正規化した題名で絞っても、以前（1文字ごとに全件を正規化）と同じ行になる。 */
    @Test
    fun theDrawerSearchWithPrecomputedTitlesMatchesTheOldOne() {
        val titles = listOf("ねこ写真", "ネコ写真", "ＮＥＫＯ", "犬スレ", "abc", "")
        val entries = LinkedHashMap<String, ThreadHistoryEntry>()
        (0 until 60).forEach { i ->
            val url = "https://may.2chan.net/b/res/${1000 + i}.htm"
            entries[url] = ThreadHistoryEntry("${1000 + i}", "b", titles[i % titles.size], "", "", url, 0L, i % 5)
        }
        val urls = entries.keys.toList() + "https://may.2chan.net/b/res/9999.htm"
        val keys = ftutterDrawerTitleKeys(entries)
        for (query in listOf("", "ねこ", "neko", "写真", "ABC", "zzz")) {
            val filter = FtutterDrawerFilter(query = query)
            assertEquals(ftutterFilterDrawer(urls, entries, emptyMap(), emptySet(), filter), ftutterFilterDrawer(urls, entries, emptyMap(), emptySet(), filter, keys), query)
        }
    }

    /** 監査L7：同じ文の通知でも、出すたびに数え直す。消す時（null）は数えない。 */
    @Test
    fun theSameNoticeTwiceCountsTwice() {
        val holder = FtutterNoticeHolder()
        var notice by holder
        notice = "書き込みました"
        val first = holder.serial
        notice = "書き込みました"
        assertEquals(first + 1, holder.serial)
        notice = null
        assertEquals(first + 1, holder.serial)
        assertNull(notice)
    }
}
