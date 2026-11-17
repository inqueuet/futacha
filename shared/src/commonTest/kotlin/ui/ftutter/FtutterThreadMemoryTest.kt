package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.*
import kotlin.test.*

class FtutterThreadMemoryTest {
    private val url = "https://may.2chan.net/b/res/100.htm"
    private fun post(id: String) = Post(id, author = null, subject = null, timestamp = "", messageHtml = "body$id", imageUrl = null, thumbnailUrl = null)
    private val old = ThreadPage("100", null, null, null, listOf(post("100")))
    private val entry = ThreadHistoryEntry("100", "board", "title", "", "board", url, 1, 1)

    // The extension 「スレッドを先頭から開く」: only an opening from the catalog, the history or a link asks for the top, and
    // only once, so coming back to the thread (from settings) or moving between tabs keeps the place.
    @Test fun anOpeningAsksForTheTopOnceAndATabMoveDoesNot() {
        val memory = FtutterThreadMemory()
        memory.open(url, refreshOnOpen = true, startAtTop = true)
        assertTrue(memory.takeStartAtTop(url))
        assertFalse(memory.takeStartAtTop(url))
        memory.open(url, refreshOnOpen = false, startAtTop = false)
        assertFalse(memory.takeStartAtTop(url))
        memory.open(url, refreshOnOpen = true, startAtTop = true)
        memory.open(url, refreshOnOpen = false)
        assertFalse(memory.takeStartAtTop(url), "a tab move after an opening cancels its request")
        memory.open(url, refreshOnOpen = true, startAtTop = true)
        memory.remove(url)
        assertFalse(memory.takeStartAtTop(url))
    }
    @Test fun theOpenAtTopExtensionIsOffUnlessTurnedOn() {
        assertFalse(FtutterExtension.OPEN_AT_TOP in parseFtutterExtensions(emptyMap()).enabled)
        assertTrue(FtutterExtension.OPEN_AT_TOP in parseFtutterExtensions(mapOf(FtutterExtension.OPEN_AT_TOP.key to "true")).enabled)
        assertEquals("compat.ftutter.ext.openAtTop", FtutterExtension.OPEN_AT_TOP.key)
    }
    @Test fun backgroundUnreadCountRejectsTheOlderPage() {
        val memory = FtutterThreadMemory().apply { put(url, old); open(url, false) }
        assertSame(old, memory.page(url))
        assertTrue(ftutterThreadPageIsStale(old, entry.copy(replyCount = 2)))
    }
    @Test fun anUnchangedTabKeepsItsPageAndDoesNotRequireNetwork() {
        val memory = FtutterThreadMemory().apply { put(url, old); open(url, false) }
        assertSame(old, memory.page(url))
        assertFalse(memory.takeRefresh(url))
    }
    @Test fun allTabsRefreshReplacesThePageForBothTabsAndHistoryRevisits() {
        val fresh = old.copy(posts = old.posts + post("101"))
        val memory = FtutterThreadMemory().apply { put(url, old); put(url, fresh); open(url, false) }
        assertSame(fresh, memory.page(url))
        memory.open(url, false)
        assertSame(fresh, memory.page(url))
    }
    @Test fun confirmedDeathKeepsTheLastReadablePageEvenIfBackgroundCountIsHigher() {
        val history = entry.copy(replyCount = 9, isAutoRefreshDisabled = true, threadStatus = ThreadStatus(isDead = true))
        val memory = FtutterThreadMemory().apply { put(url, old) }
        assertTrue(ftutterHistoryIsFallen(history))
        assertSame(old, memory.page(url))
        assertFalse(ftutterThreadPageIsStale(old, history))
    }
    @Test fun eitherConfirmedDeathFlagRestoresTheArchiveAction() {
        assertTrue(ftutterHistoryIsFallen(entry.copy(threadStatus = ThreadStatus(isDead = true))))
        assertTrue(ftutterHistoryIsFallen(entry.copy(isAutoRefreshDisabled = true)))
        assertFalse(ftutterHistoryIsFallen(entry))
        assertFalse(ftutterHistoryIsFallen(null))
    }
    @Test fun liveRefreshClearsBothDeathFlagsWithoutChangingHistoryOrderOrReadBoundary() {
        val fallen = entry.copy(isAutoRefreshDisabled = true, threadStatus = ThreadStatus(isDead = true, isIsolated = true),
            lastReadPostId = "100", lastViewedSnapshot = ThreadViewedSnapshot(1, "100", 7))
        val other = fallen.copy(boardId = "other")
        val result = ftutterHistoryAfterLiveLoad(listOf(other, fallen), "board", "100", old.posts + post("101"), 123)
        assertSame(other, result[0])
        val alive = result[1]
        assertEquals(2, alive.replyCount)
        assertFalse(ftutterHistoryIsFallen(alive))
        assertEquals(123L, alive.lastConfirmedAliveEpochMillis)
        assertEquals(fallen.lastReadPostId, alive.lastReadPostId)
        assertEquals(fallen.lastViewedSnapshot, alive.lastViewedSnapshot)
        assertEquals(fallen.lastVisitedEpochMillis, alive.lastVisitedEpochMillis)
        assertTrue(alive.threadStatus!!.isIsolated)
    }

    /** L18：以前の落ちの記録（isAutoRefreshDisabled だけ）も、拡張の表示で「落ち」になる。 */
    @Test fun eitherFallenRecordIsShownAsFallen() {
        val legacy = entry.copy(isAutoRefreshDisabled = true, threadStatus = null)
        assertEquals("落ち", com.valoser.futacha.shared.ui.board.threadStatusText(ftutterDisplayStatus(legacy)))
        assertEquals("落ち", com.valoser.futacha.shared.ui.board.threadStatusBadgeText(ftutterDisplayStatus(legacy)))
        val current = entry.copy(threadStatus = ThreadStatus(isDead = true))
        assertEquals("落ち", com.valoser.futacha.shared.ui.board.threadStatusText(ftutterDisplayStatus(current)))
        val isolatedAndLegacy = entry.copy(isAutoRefreshDisabled = true, threadStatus = ThreadStatus(isIsolated = true))
        assertEquals("隔離・落ち", com.valoser.futacha.shared.ui.board.threadStatusText(ftutterDisplayStatus(isolatedAndLegacy)))
        assertEquals("", com.valoser.futacha.shared.ui.board.threadStatusText(ftutterDisplayStatus(entry)))
        assertNull(ftutterDisplayStatus(null))
    }
}
