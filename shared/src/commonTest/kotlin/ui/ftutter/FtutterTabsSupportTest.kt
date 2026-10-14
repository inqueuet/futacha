package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
