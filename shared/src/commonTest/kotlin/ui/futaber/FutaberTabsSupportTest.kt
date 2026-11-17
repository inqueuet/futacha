package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FutaberTabsSupportTest {
    private fun key(b: String, t: String) = FutaberTabKey(b, t)
    private fun entry(b: String, t: String, title: String = "題$t", replies: Int = 3) = ThreadHistoryEntry(
        threadId = t, boardId = b, title = title, titleImageUrl = "img$t.jpg", boardName = "n",
        boardUrl = "https://x/$b/", lastVisitedEpochMillis = 1L, replyCount = replies
    )
    private val boards = listOf(
        BoardSummary("a", "A", "", "https://x/a/", ""),
        BoardSummary("b", "B", "", "https://x/b/", "")
    )

    @Test
    fun tabsSurviveEncodingAndAnyJunkReadsAsNoTabs() {
        val keys = listOf(key("a", "1"), key("b", "22"))
        assertEquals(keys, decodeFutaberTabs(encodeFutaberTabs(keys)))
        assertTrue(decodeFutaberTabs(null).isEmpty())
        assertTrue(decodeFutaberTabs("").isEmpty())
        assertTrue(decodeFutaberTabs("not json").isEmpty())
        assertTrue(decodeFutaberTabs("""["noseparator","a\n"]""").isEmpty())
    }

    @Test
    fun duplicateStoredKeysAndAnOverlongListAreNormalised() {
        val many = (1..60).map { key("a", it.toString()) }
        assertEquals(60, decodeFutaberTabs(encodeFutaberTabs(many)).size, "plan B8a: no 50-tab cap")
        assertEquals(listOf(key("a", "1")), decodeFutaberTabs(encodeFutaberTabs(listOf(key("a", "1"), key("a", "1")))))
    }

    @Test
    fun toggleAddsAtTheEndAndRemoves() {
        val one = futaberToggleTab(emptyList(), key("a", "1"))
        assertEquals(listOf(key("a", "1")), one)
        assertEquals(listOf(key("a", "1"), key("a", "2")), futaberToggleTab(one, key("a", "2")))
        assertTrue(futaberToggleTab(one, key("a", "1")).isEmpty())
        assertEquals(listOf(key("a", "2")), futaberRemoveTab(listOf(key("a", "1"), key("a", "2")), key("a", "1")))
    }

    @Test
    fun tabViewsComeFromHistoryInTabOrder() {
        val history = listOf(entry("a", "1", "一", 5), entry("b", "2", "", 0))
        val views = futaberTabViews(listOf(key("b", "2"), key("a", "1"), key("a", "404")), history)
        assertEquals(listOf("(無題)", "一"), views.map { it.title })
        assertEquals(listOf(0, 5), views.map { it.replyCount })
        assertEquals("img1.jpg", views[1].thumbnailUrl)
    }

    @Test
    fun aHistoryRowOpensOnlyWhileItsBoardIsRegistered() {
        val ref = futaberRefForHistory(entry("a", "1", "題", 7), boards)
        assertEquals(FutaberThreadRef("a", "1", "題", "img1.jpg", 7), ref)
        assertNull(futaberRefForHistory(entry("gone", "1"), boards))
        val legacy = entry("", "1").copy(boardUrl = "https://x/b/")
        assertEquals("b", futaberRefForHistory(legacy, boards)?.boardId)
    }

    @Test
    fun theTabSheetActionsKeepTheRightTabs() {
        val keys = listOf(key("a", "1"), key("a", "2"), key("b", "3"))
        assertEquals(listOf(key("a", "1"), key("a", "2")), futaberRemoveTabsToTheRight(keys, key("a", "2")))
        assertEquals(keys, futaberRemoveTabsToTheRight(keys, key("b", "3")))
        assertEquals(keys, futaberRemoveTabsToTheRight(keys, key("z", "9")))
        assertEquals(listOf(key("a", "2")), futaberKeepOnlyTab(keys, key("a", "2")))
        assertEquals(keys, futaberKeepOnlyTab(keys, key("z", "9")))
        assertEquals(listOf(key("a", "1"), key("b", "3")), futaberRemoveFallenTabs(keys, setOf(key("a", "2"))))
    }

    @Test
    fun aThreadTheRefreshFoundGoneIsMarkedFallenInItsTab() {
        val fallenEntry = entry("a", "1").copy(isAutoRefreshDisabled = true)
        val views = futaberTabViews(listOf(key("a", "1"), key("a", "2")), listOf(fallenEntry, entry("a", "2")))
        assertEquals(listOf(true, false), views.map { it.fallen })
    }
}
