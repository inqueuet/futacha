package com.valoser.futacha.shared.ui

import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MixNavigationStateTest {
    private val scope = SaverScope { true }

    @Test
    fun mixIdSurvivesSaveAndRestore() {
        val saver = FutachaNavigationState.Saver
        val saved = with(saver) { scope.save(selectFutachaMix(FutachaNavigationState(), "mix-1")) }!!
        assertEquals("mix-1", saver.restore(saved)!!.selectedMixId)
    }

    @Test
    fun oldEightValueListsRestoreWithoutMix() {
        val old = listOf<Any?>("b", "t", "title", 3, null, null, false, "name")
        val restored = FutachaNavigationState.Saver.restore(old)!!
        assertNull(restored.selectedMixId)
        assertEquals("b", restored.selectedBoardId)
    }

    @Test
    fun exitClearsMixAndSelectingClearsBoard() {
        val shown = selectFutachaMix(FutachaNavigationState(selectedBoardId = "b"), "m")
        assertNull(shown.selectedBoardId)
        assertNull(exitFutachaMix(shown).selectedMixId)
    }
}
