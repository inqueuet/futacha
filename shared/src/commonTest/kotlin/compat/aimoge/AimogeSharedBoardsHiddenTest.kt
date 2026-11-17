package com.valoser.futacha.shared.compat.aimoge

import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.shared.SharedBoardSide
import com.valoser.futacha.shared.compat.shared.SharedBoardsRecord
import com.valoser.futacha.shared.compat.shared.planSharedBoards
import com.valoser.futacha.shared.compat.storeCanonicalBoardUrlOf
import com.valoser.futacha.shared.model.BoardSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** AM-B5: an あいもげ board in the compatibility store is hidden, never deleted, while あいもげ is off. */
class AimogeSharedBoardsHiddenTest {
    private val aUrl = "https://nijiurachan.net/b/aimoge/"
    private val aBoard = CompatBoard(compatBoardKey(aUrl), "あいもげ", aUrl, aUrl, 1)
    private val fUrl = "https://may.2chan.net/b/"
    private val fSummary = BoardSummary("b", "may", "", "${fUrl}futaba.php", "")
    private val fBoard = CompatBoard(compatBoardKey(fUrl), "may", fUrl, "${fUrl}futaba.php", 0)

    @Test
    fun theStoreCheckAcceptsTheAimogeShapeWhetherOnOrOff() {
        withAimoge(enabled = false) { assertEquals(aUrl, storeCanonicalBoardUrlOf(aUrl)) }
        withAimoge(enabled = true) { assertEquals(aUrl, storeCanonicalBoardUrlOf(aUrl)) }
        assertNull(storeCanonicalBoardUrlOf("https://nijiurachan.net/b/aimoge/thread/0193b2c4-1a2b-7c3d-8e4f-5a6b7c8d9e0f"))
        assertEquals(fUrl, storeCanonicalBoardUrlOf("${fUrl}futaba.php"))
    }

    @Test
    fun whileOffTheBoardIsNeitherDeletedNorShown() = withAimoge(enabled = false) {
        val record = SharedBoardsRecord(SharedBoardSide.APP_STATE, listOf(fBoard.key, aBoard.key), emptyMap())
        for (side in SharedBoardSide.entries) {
            var current = record
            repeat(3) {
                val plan = planSharedBoards(side, listOf(fSummary), listOf(fBoard, aBoard), current, false, 10_000_000_000L)
                assertTrue(aBoard.key !in plan.compatDeletes, "$side deleted the hidden board")
                assertTrue(plan.addToAppState.none { it.id == aBoard.key }, "$side showed the hidden board")
                assertTrue(plan.compatAfter.none { it.key == aBoard.key }, "$side ordered the hidden board into the list")
                assertTrue(aBoard.key !in plan.finalRecord.seen && aBoard.key !in plan.finalRecord.tombstones)
                current = plan.finalRecord
            }
        }
    }

    @Test
    fun onceOnTheSameBoardIsShownAgain() {
        val record = withAimoge(enabled = false) {
            planSharedBoards(
                SharedBoardSide.APP_STATE, listOf(fSummary), listOf(fBoard, aBoard),
                SharedBoardsRecord(SharedBoardSide.APP_STATE, listOf(fBoard.key, aBoard.key), emptyMap()), false, 1L
            ).finalRecord
        }
        withAimoge(enabled = true) {
            val plan = planSharedBoards(SharedBoardSide.APP_STATE, listOf(fSummary), listOf(fBoard, aBoard), record, false, 2L)
            assertTrue(aBoard.key !in plan.compatDeletes)
            assertEquals(listOf(aBoard.key), plan.addToAppState.map { it.id })
            // The compatibility side also keeps it.
            val compatPlan = planSharedBoards(SharedBoardSide.COMPAT, listOf(fSummary), listOf(fBoard, aBoard), record, false, 2L)
            assertTrue(aBoard.key !in compatPlan.compatDeletes && compatPlan.compatAfter.any { it.key == aBoard.key })
        }
    }
}
