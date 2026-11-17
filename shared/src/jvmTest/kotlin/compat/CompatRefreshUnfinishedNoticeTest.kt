package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.desktop.desktopCompatHistoryRefreshNotice
import com.valoser.futacha.shared.ui.board.futachaCheckAllTabsNotice
import com.valoser.futacha.shared.ui.compat.compatRefreshAllTabsDoneNotice
import com.valoser.futacha.shared.ui.compat.compatWatchCheckNowNotice
import com.valoser.futacha.shared.ui.compat.compatWatchPatrolNotice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Re-audit L81: steps cut off by the time are not failures, but every screen other than the iOS manual refresh told
 * "失敗0件" (and "巡回完了") as if everything had been checked.
 */
class CompatRefreshUnfinishedNoticeTest {
    private val unfinishedNote = "時間内に確認しきれなかった分は次回確認します"

    private fun notices(result: CompatBackgroundRefreshResult): List<String> = listOf(
        desktopCompatHistoryRefreshNotice(result),
        compatRefreshAllTabsDoneNotice(result),
        compatWatchPatrolNotice(result),
        compatWatchCheckNowNotice(result),
        futachaCheckAllTabsNotice(result)
    )

    @Test
    fun everyToldResultSaysWhatTheTimeLeftUnchecked() {
        notices(CompatBackgroundRefreshResult(updatedTabs = 1, unfinished = 2)).forEach { notice ->
            assertTrue(notice.contains(unfinishedNote), notice)
        }
        assertFalse(compatWatchPatrolNotice(CompatBackgroundRefreshResult(unfinished = 1)).contains("巡回完了"))
    }

    @Test
    fun aRunThatFinishedTellsWhatItDidBefore() {
        val finished = CompatBackgroundRefreshResult(updatedTabs = 3, deadTabs = 1, failures = 2)
        notices(finished).forEach { notice -> assertFalse(notice.contains(unfinishedNote), notice) }
        assertEquals("履歴を更新しました（更新 3件、終了 1件、失敗 2件）", desktopCompatHistoryRefreshNotice(finished))
        assertEquals("巡回完了：新着0件／失敗2件", compatWatchPatrolNotice(finished))
        assertEquals("新たに保存 0件／失敗 2件", compatWatchCheckNowNotice(finished))
        assertEquals("更新3件・落ち1件・失敗2件", futachaCheckAllTabsNotice(finished))
    }
}
