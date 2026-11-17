package com.valoser.futacha.shared.mix

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.ui.board.mixFailureText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNull

class MixFailureMessageTest {
    @Test fun partialFailuresIdentifyTheBoardAndTheReason() {
        val may = "https://may.2chan.net/b/"
        val aimoge = "https://nijiurachan.net/b/aimg/"
        val boards = listOf(BoardSummary("may", "may", "", may, ""),
            BoardSummary("aimg", "あいもげ", "", aimoge, ""))
        val text = mixFailureText(mapOf(may to MixFetchFailure.TIMEOUT),
            mapOf(aimoge to MixSkipReason.SITE_PAUSED), boards)!!
        assertContains(text, "may：時間切れ")
        assertContains(text, "あいもげ：休止中")
        assertNull(mixFailureText(emptyMap(), emptyMap(), boards))
    }
}
