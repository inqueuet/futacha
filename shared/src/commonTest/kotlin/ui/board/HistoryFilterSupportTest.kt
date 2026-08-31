package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryFilterSupportTest {
    @Test
    fun defaultSettingsSortByMostRecentlyVisited() {
        val history = listOf(
            entry("1", title = "古い", visitedAt = 100),
            entry("2", title = "新しい", visitedAt = 300),
            entry("3", title = "中間", visitedAt = 200)
        )

        assertEquals(
            listOf("2", "3", "1"),
            applyHistoryViewSettings(history, HistoryViewSettings.Default).map { it.threadId }
        )
    }

    @Test
    fun titleSortSupportsAscendingOrder() {
        val history = listOf(
            entry("1", title = "ねこ", visitedAt = 100),
            entry("2", title = "あひる", visitedAt = 300),
            entry("3", title = "さかな", visitedAt = 200)
        )

        val result = applyHistoryViewSettings(
            history,
            HistoryViewSettings(
                sortOption = HistorySortOption.Title,
                sortDirection = HistorySortDirection.Ascending
            )
        )

        assertEquals(listOf("2", "3", "1"), result.map { it.threadId })
    }

    @Test
    fun filtersBySelfPostLifeBoardAndTitle() {
        val target = entry(
            threadId = "1",
            title = "猫のスレ",
            visitedAt = 300,
            boardId = "img",
            hasSelfPost = true,
            lastAliveAt = 250
        )
        val history = listOf(
            target,
            entry("2", "犬のスレ", 200, boardId = "img", hasSelfPost = true, lastAliveAt = 150),
            entry("3", "猫のスレ", 100, boardId = "may", hasSelfPost = true, lastAliveAt = 90),
            entry("4", "猫のスレ", 50, boardId = "img", hasSelfPost = false, lastAliveAt = 40),
            entry("5", "猫のスレ", 25, boardId = "img", hasSelfPost = true, expired = true)
        )

        val result = applyHistoryViewSettings(
            history,
            HistoryViewSettings(
                selfPostsOnly = true,
                lifeFilter = HistoryLifeFilter.Alive,
                boardKey = "img",
                titleQuery = "猫"
            )
        )

        assertEquals(listOf(target), result)
    }

    @Test
    fun missingAliveTimestampIsAlwaysSortedLast() {
        val unknown = entry("unknown", "不明", 500)
        val olderAlive = entry("old", "古い", 100, lastAliveAt = 10)
        val newerAlive = entry("new", "新しい", 200, lastAliveAt = 20)

        val descending = applyHistoryViewSettings(
            listOf(unknown, olderAlive, newerAlive),
            HistoryViewSettings(sortOption = HistorySortOption.LastConfirmedAlive)
        )
        val ascending = applyHistoryViewSettings(
            listOf(unknown, olderAlive, newerAlive),
            HistoryViewSettings(
                sortOption = HistorySortOption.LastConfirmedAlive,
                sortDirection = HistorySortDirection.Ascending
            )
        )

        assertEquals(listOf("new", "old", "unknown"), descending.map { it.threadId })
        assertEquals(listOf("old", "new", "unknown"), ascending.map { it.threadId })
    }

    @Test
    fun defaultAndActiveSettingCountAreExplicit() {
        assertTrue(HistoryViewSettings.Default.isDefault)
        assertEquals(0, HistoryViewSettings.Default.activeSettingCount)

        val changed = HistoryViewSettings(
            selfPostsOnly = true,
            lifeFilter = HistoryLifeFilter.Expired,
            titleQuery = "猫"
        )
        assertFalse(changed.isDefault)
        assertEquals(3, changed.activeSettingCount)

        val changedSort = HistoryViewSettings(
            sortOption = HistorySortOption.Title,
            sortDirection = HistorySortDirection.Ascending
        )
        assertEquals(1, changedSort.activeSettingCount)
    }

    private fun entry(
        threadId: String,
        title: String,
        visitedAt: Long,
        boardId: String = "img",
        hasSelfPost: Boolean = false,
        lastAliveAt: Long? = null,
        expired: Boolean = false
    ) = ThreadHistoryEntry(
        threadId = threadId,
        boardId = boardId,
        title = title,
        titleImageUrl = "",
        boardName = boardId,
        boardUrl = "https://example.com/$boardId/",
        lastVisitedEpochMillis = visitedAt,
        lastConfirmedAliveEpochMillis = lastAliveAt,
        replyCount = 0,
        isAutoRefreshDisabled = expired,
        hasSelfPost = hasSelfPost
    )
}
