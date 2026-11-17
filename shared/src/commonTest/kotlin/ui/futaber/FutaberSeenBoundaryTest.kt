package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.BoardSummary
import kotlin.test.*

class FutaberSeenBoundaryTest {
    private val board = BoardSummary("may", "板", "検証", "https://may.2chan.net/b/", "")
    @Test fun originalThreeFieldValuesRemainReadableAndNewBoundariesSurvivePersistence() {
        val legacy = encodeFutaberSeenCounts(listOf(FutaberSeenCount(board.id, "100", 4)))
        assertEquals(4, futaberSeenCountForBoard(legacy, board, "100"))
        val updated = futaberSeenCountsAfterForBoard(legacy, board, "100", 4, "104")!!
        assertEquals("104", futaberSeenBoundaryForBoard(updated, board, "100")?.lastPostId)
        assertNull(futaberSeenCountsAfterForBoard(updated, board, "100", 4, "104"))
        assertEquals(4, futaberSeenCountForBoard(updated, board, "100"))
    }
}
