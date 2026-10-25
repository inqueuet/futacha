package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.ThreadMenuEntryId
import com.valoser.futacha.shared.model.defaultThreadMenuEntries
import com.valoser.futacha.shared.ui.util.toolbarContentWidth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThreadActionBarLayoutTest {
    // (L67) Bar widths after the 8dp side padding on 320/360/375/393/411dp screens.
    @Test fun defaultEightFitFromThreeHundredTwentyDpScreensWithSettingsPinnedAtTheRight() {
        val entries = resolveThreadActionBarEntries(defaultThreadMenuEntries())
        assertEquals(8, entries.size)
        assertEquals(ThreadMenuEntryId.Settings, entries.last().id)
        listOf(304f, 344f, 359f, 377f, 395f).forEach { width ->
            val slot = futachaThreadActionBarItemWidth(width, entries.size)
            assertTrue(slot >= 36f, "slot $slot at $width")
            assertEquals(width, slot * entries.size, 0.001f, "width $width")
            // The scrolling part beside the pinned slot holds the other seven without scrolling.
            val viewport = width - slot
            assertEquals(viewport, toolbarContentWidth(viewport, entries.size - 1,
                FUTACHA_THREAD_ACTION_BAR_MIN_ITEM_WIDTH_DP.toFloat()), 0.001f, "viewport at $width")
        }
    }

    @Test fun narrowerBarsScrollTheOthersInsteadOfSqueezingTheButtons() {
        // A 240dp screen leaves 224dp: seven 36dp slots beside the pinned one no longer fit.
        val slot = futachaThreadActionBarItemWidth(224f, 8)
        assertEquals(36f, slot)
        assertTrue(toolbarContentWidth(224f - slot, 7, 36f) > 224f - slot)
    }

    @Test fun fewerSavedItemsShareTheWidthAndAnEmptyBarDoesNotDivideByZero() {
        assertEquals(86f, futachaThreadActionBarItemWidth(344f, 4))
        assertEquals(344f, futachaThreadActionBarItemWidth(344f, 1))
        assertEquals(344f, futachaThreadActionBarItemWidth(344f, 0))
    }
}
