package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppStateHistorySelfPostSupportTest {
    @Test
    fun selfPostPlanUpdatesOnlyMatchingBoardAndThread() {
        val target = entry(threadId = "10", boardId = "img")
        val sameNumberOtherBoard = entry(threadId = "10", boardId = "may")

        val plan = resolveAppStateHistorySelfPostPlan(
            currentHistory = listOf(target, sameNumberOtherBoard),
            threadId = "10",
            boardId = "img",
            postedAtMillis = 1234L
        )

        assertNotNull(plan)
        assertTrue(plan.updatedHistory[0].hasSelfPost)
        assertEquals(1234L, plan.updatedHistory[0].lastSelfPostEpochMillis)
        assertFalse(plan.updatedHistory[1].hasSelfPost)
    }

    @Test
    fun selfPostPlanReturnsNullWhenHistoryDoesNotContainThread() {
        assertNull(
            resolveAppStateHistorySelfPostPlan(
                currentHistory = listOf(entry(threadId = "10", boardId = "img")),
                threadId = "99",
                boardId = "img",
                postedAtMillis = 1234L
            )
        )
    }

    private fun entry(threadId: String, boardId: String) = ThreadHistoryEntry(
        threadId = threadId,
        boardId = boardId,
        title = "title",
        titleImageUrl = "",
        boardName = boardId,
        boardUrl = "https://example.com/$boardId/",
        lastVisitedEpochMillis = 1L,
        replyCount = 0
    )
}
