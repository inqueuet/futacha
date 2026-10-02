package com.valoser.futacha.shared.ui.board

import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoardHistoryComponentsTest {
    @Test
    fun historyGrowthWarning_isShownFromOneHundredEntries() {
        assertFalse(shouldShowHistoryDrawerGrowthWarning(99))
        assertTrue(shouldShowHistoryDrawerGrowthWarning(100))
        assertTrue(shouldShowHistoryDrawerGrowthWarning(101))
    }

    @Test
    fun historyGrowthWarningText_mentionsGrowthAndCurrentCount() {
        assertEquals("履歴が増えています", buildHistoryDrawerGrowthWarningTitle(100))
        assertEquals(
            "現在の履歴は100件です。表示や保存が重くなる場合があります。",
            buildHistoryDrawerGrowthWarningBody(100)
        )
        assertEquals(
            "現在の履歴は0件です。表示や保存が重くなる場合があります。",
            buildHistoryDrawerGrowthWarningBody(-1)
        )
    }

    @Test
    fun closedDrawerKeepsItsLastListUntilItIsShownAgain() {
        fun entry(id: String) = com.valoser.futacha.shared.model.ThreadHistoryEntry(
            threadId = id, boardId = "b", title = id, titleImageUrl = "", boardName = "b",
            boardUrl = "https://may.2chan.net/b/futaba.php", lastVisitedEpochMillis = 1L, replyCount = 1
        )
        val holder = HistoryDrawerSnapshotHolder()
        val first = listOf(entry("1"))
        val second = listOf(entry("1"), entry("2"))

        assertSame(first, holder.resolve(first, visible = false)) // nothing shown yet
        assertSame(first, holder.resolve(second, visible = false))
        assertSame(second, holder.resolve(second, visible = true))
    }

    @Test
    fun closedDrawerDoesNotBuildItsViewInComposition() {
        fun entry(id: String) = com.valoser.futacha.shared.model.ThreadHistoryEntry(
            threadId = id, boardId = "b", title = id, titleImageUrl = "", boardName = "b",
            boardUrl = "https://may.2chan.net/b/futaba.php", lastVisitedEpochMillis = 1L, replyCount = 1
        )
        val history = listOf(entry("1"), entry("2"))
        val settings = HistoryViewSettings.Default
        var builds = 0
        val build = { h: List<com.valoser.futacha.shared.model.ThreadHistoryEntry>, s: HistoryViewSettings ->
            builds += 1
            buildHistoryDrawerView(h, s)
        }
        val cache = HistoryDrawerViewCache()

        // Closed with no background result yet: an empty placeholder, no main-thread build.
        val closed = resolveHistoryDrawerView(null, cache, history, settings, isVisible = false, build = build)
        assertEquals(0, builds)
        assertTrue(closed.displayedHistory.isEmpty())

        // Opened before the background build finished: built once in place, never shown empty.
        val opened = resolveHistoryDrawerView(null, cache, history, settings, isVisible = true, build = build)
        assertEquals(1, builds)
        assertEquals(history, opened.displayedHistory)
        assertSame(opened, resolveHistoryDrawerView(null, cache, history, settings, isVisible = true, build = build))
        assertEquals(1, builds)

        // The background result always wins and needs no build.
        val produced = buildHistoryDrawerView(history, settings)
        assertSame(produced, resolveHistoryDrawerView(produced, HistoryDrawerViewCache(), history, settings,
            isVisible = true, build = build))
        assertEquals(1, builds)
    }
}
