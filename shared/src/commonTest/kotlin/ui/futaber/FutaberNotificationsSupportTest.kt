package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.CompatWatchResult
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.model.BoardSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FutaberNotificationsSupportTest {
    private val may = BoardSummary(id = "may-b", name = "may/b", category = "", url = "https://may.2chan.net/b/futaba.php", description = "")
    private val img = BoardSummary(id = "img-b", name = "img/b", category = "", url = "https://img.2chan.net/b/futaba.php", description = "")

    private fun result(boardUrl: String, thread: String, keyword: String = "猫", active: Boolean = true, title: String = "猫のスレ") =
        CompatWatchResult(
            history = CompatHistoryEntry(
                canonicalUrl = "${boardUrl}res/$thread.htm", originalUrl = "${boardUrl}res/$thread.htm",
                boardKey = compatBoardKey(boardUrl), boardName = "板", threadNo = thread, title = title,
                thumbnailUrl = "https://t/$thread.jpg", replyCount = 12, contentUpdatedAtEpochMillis = 100L
            ),
            keyword = keyword, insertedAtEpochMillis = 50L, active = active
        )

    @Test
    fun onlyTheResultRowsAreWatched() {
        val prefs = mapOf(
            "compat.watcher.result.0" to "{}", "compat.watcher.result.7" to "{}",
            "compat.watcher.rules" to "[]", "compat.futaber.tabs" to "[]"
        )
        assertEquals(setOf("compat.watcher.result.0", "compat.watcher.result.7"), futaberWatchResultSlots(prefs).keys)
        assertTrue(futaberWatchResultSlots(emptyMap()).isEmpty())
    }

    @Test
    fun aResultFoundByAnotherModeOpensOnTheMatchingBoard() {
        val ref = futaberRefForWatchResult(result("https://may.2chan.net/b/", "555"), listOf(img, may))
        assertEquals("may-b", ref?.boardId)
        assertEquals("555", ref?.threadId)
        assertEquals("猫のスレ", ref?.title)
        assertEquals("https://t/555.jpg", ref?.thumbnailUrl)
        assertEquals(12, ref?.replyCount)
    }

    @Test
    fun aResultOfAnUnregisteredBoardCannotOpen() {
        assertNull(futaberRefForWatchResult(result("https://may.2chan.net/b/", "1"), listOf(img)))
        assertEquals("(無題)", futaberRefForWatchResult(result("https://may.2chan.net/b/", "1", title = ""), listOf(may))?.title)
    }

    @Test
    fun theSubtitleNamesTheKeywordAndMarksGoneOrUnregisteredThreads() {
        val live = result("https://may.2chan.net/b/", "1")
        assertEquals("「猫」  板", futaberWatchSubtitle(live, openable = true))
        assertEquals("「猫」  板（落ちました）", futaberWatchSubtitle(live.copy(active = false), openable = true))
        assertEquals("「猫」  板（板が未登録）", futaberWatchSubtitle(live, openable = false))
    }

    @Test
    fun theStatusSaysWhyTheListMayBeEmpty() {
        val rule = """[{"word":"猫"}]"""
        assertEquals("キーワードが登録されていません。キーワード管理で追加できます", futaberNotificationsStatus(emptyMap()))
        assertEquals("自動巡回が無効です。キーワード管理で有効にできます", futaberNotificationsStatus(mapOf("compat.watcher.enabled" to "OFF", "compat.watcher.rules" to rule)))
        assertEquals("キーワードに一致したスレッド", futaberNotificationsStatus(mapOf("compat.watcher.rules" to rule)))
        // Words that are all disabled count as none.
        assertEquals(
            "キーワードが登録されていません。キーワード管理で追加できます",
            futaberNotificationsStatus(mapOf("compat.watcher.rules" to """[{"word":"猫","enabled":false}]"""))
        )
    }
}
