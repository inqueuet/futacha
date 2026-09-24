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
}
