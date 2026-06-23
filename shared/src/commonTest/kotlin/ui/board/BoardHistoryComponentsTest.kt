package com.valoser.futacha.shared.ui.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoardHistoryComponentsTest {
    @Test
    fun historyGrowthWarning_isShownFromOneHundredEntries() {
        assertFalse(shouldShowHistoryDrawerGrowthWarning(99))
        assertTrue(shouldShowHistoryDrawerGrowthWarning(100))
        assertTrue(shouldShowHistoryDrawerGrowthWarning(101))
    }

    @Test
    fun historyGrowthWarningText_mentionsGrowthAndCurrentCount() {
        assertEquals("履歴が増えています", buildHistoryDrawerGrowthWarningTitle(100))
        assertEquals(
            "現在の履歴は100件です。表示や保存が重くなる場合があります。",
            buildHistoryDrawerGrowthWarningBody(100)
        )
        assertEquals(
            "現在の履歴は0件です。表示や保存が重くなる場合があります。",
            buildHistoryDrawerGrowthWarningBody(-1)
        )
    }
}
