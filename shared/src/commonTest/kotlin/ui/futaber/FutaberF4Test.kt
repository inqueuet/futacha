package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** F-B4: board pin, close fallen tabs, open by URL and the catalog volume keys (their rules and switches). */
class FutaberF4Test {
    private fun board(id: String) = BoardSummary(id, "板$id", "", "https://x/$id/", "")
    private fun entry(b: String, t: String, fallen: Boolean) = ThreadHistoryEntry(
        threadId = t, boardId = b, title = "題$t", titleImageUrl = "", boardName = "n",
        boardUrl = "https://x/$b/", lastVisitedEpochMillis = 1L, replyCount = 3, isAutoRefreshDisabled = fallen
    )

    @Test
    fun pinningOnlyFlipsThatBoardAndKeepsTheOrder() {
        val boards = listOf(board("a"), board("b"), board("c"))
        val pinned = futaberTogglePin(boards, "b")
        assertEquals(listOf("a", "b", "c"), pinned.map { it.id })
        assertEquals(listOf(false, true, false), pinned.map { it.pinned })
        assertEquals(boards, futaberTogglePin(pinned, "b"))
        assertEquals(boards, futaberTogglePin(boards, "zzz"))
    }

    @Test
    fun theFallenTabsAreThoseTheHistoryMarks() {
        val keys = listOf(FutaberTabKey("a", "1"), FutaberTabKey("a", "2"), FutaberTabKey("b", "3"))
        val history = listOf(entry("a", "1", false), entry("a", "2", true), entry("b", "3", true))
        val fallen = futaberFallenTabKeys(keys, history)
        assertEquals(setOf(FutaberTabKey("a", "2"), FutaberTabKey("b", "3")), fallen)
        assertEquals(listOf(FutaberTabKey("a", "1")), futaberRemoveFallenTabs(keys, fallen))
    }

    @Test
    fun theSwitchesDefaultAsDecided() {
        val d = FutaberDisplaySettings.from(emptyMap())
        assertTrue(d.boardPin && d.extOpenUrl && d.extTabsCloseFallen)
        assertFalse(d.extCatalogVolumeKey)
        val o = FutaberDisplaySettings.from(mapOf(
            "compat.futaber.ext.boardPin" to "OFF", "compat.futaber.ext.openUrl" to "OFF",
            "compat.futaber.ext.tabsCloseFallen" to "OFF", "compat.futaber.ext.catalogVolumeKey" to "ON"
        ))
        assertFalse(o.boardPin || o.extOpenUrl || o.extTabsCloseFallen)
        assertTrue(o.extCatalogVolumeKey)
    }
}
