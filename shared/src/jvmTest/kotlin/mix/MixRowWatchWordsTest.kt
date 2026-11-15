package com.valoser.futacha.shared.mix

import com.valoser.futacha.shared.state.boardSettingKeys
import com.valoser.futacha.shared.state.resolveEffectiveWatchWordsForBoard
import kotlin.test.Test
import kotlin.test.assertEquals

/** Each MIX row takes its own board's watch words; a board without its own list falls back to the global one. */
class MixRowWatchWordsTest {
    private val may = "https://may.2chan.net/b/"
    private val img = "https://img.2chan.net/b/"

    @Test fun eachBoardUsesItsOwnWordsAndOthersFallBack() {
        val mayKeys = boardSettingKeys("may", may)
        val imgKeys = boardSettingKeys("img", img)
        val map = mapOf(mayKeys!!.key to listOf("猫"))
        val global = listOf("全体")
        assertEquals(listOf("猫"), resolveEffectiveWatchWordsForBoard(global, map, mayKeys))
        assertEquals(global, resolveEffectiveWatchWordsForBoard(global, map, imgKeys))
    }
}
