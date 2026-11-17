package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.BoardSummary
import kotlin.test.Test
import kotlin.test.assertEquals

/** ふたちゃの板の並び替え：1回のドラッグで何行も動く（スレ1447418590「ドラッグした時固まる」）。 */
class BoardDragStepsTest {
    private val boards = (1..5).map { BoardSummary("b$it", "B$it", "", "https://may.2chan.net/b$it/", "") }

    @Test fun aLongDragCrossesSeveralRowsAndKeepsTheRest() {
        val (order, left) = boardDragSteps(boards, "b1", 3.2f * 100f, 100f)
        assertEquals(listOf("b2", "b3", "b4", "b1", "b5"), order.map { it.id })
        assertEquals(20f, left, 0.01f, "rest of the drag")
    }

    @Test fun upwardsAndAtTheEnds() {
        assertEquals(listOf("b5", "b1", "b2", "b3", "b4"), boardDragSteps(boards, "b5", -900f, 100f).first.map { it.id })
        assertEquals(boards, boardDragSteps(boards, "b1", -900f, 100f).first)
        assertEquals(boards to 40f, boardDragSteps(boards, "b2", 40f, 100f))
    }
}
