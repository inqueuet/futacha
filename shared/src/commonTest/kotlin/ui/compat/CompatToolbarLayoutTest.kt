package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatToolbarSurface
import com.valoser.futacha.shared.compat.compatToolbarMaster
import com.valoser.futacha.shared.ui.util.toolbarContentWidth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompatToolbarLayoutTest {
    @Test fun defaultsScrollOnPhoneWidthsWithFullSizeButtonsAndOtherReservedAtTheRight() {
        val count = compatToolbarMaster(CompatToolbarSurface.THREAD).count { it.defaultActive } + 1
        assertEquals(15, count)
        listOf(375f, 393f, 411f).forEach { width ->
            val slot = compactThreadToolbarItemWidth(width, count)
            assertEquals(40f, slot)
            val viewport = width - slot
            assertEquals(40f * (count - 1), toolbarContentWidth(viewport, count - 1, 40f))
            assertTrue(40f * (count - 1) > viewport)
        }
        assertEquals(800f / count, compactThreadToolbarItemWidth(800f, count))
    }

    @Test fun allOnScrollsRatherThanShrinkingAndTheFixedMenuKeepsItsOwnSlot() {
        val count = compatToolbarMaster(CompatToolbarSurface.THREAD).size + 1
        assertEquals(21, count)
        listOf(240f, 375f, 393f, 411f).forEach { width ->
            val menuWidth = compactThreadToolbarItemWidth(width, count)
            assertEquals(40f, menuWidth)
            assertTrue(toolbarContentWidth(width - menuWidth, count - 1, 40f) > width - menuWidth)
        }
    }

    @Test fun disabledItemsLeaveRoomForTheRemainingSavedActionsAndAnEmptyListDoesNotDivideByZero() {
        assertEquals(75f, compactThreadToolbarItemWidth(375f, 5))
        assertEquals(375f, compactThreadToolbarItemWidth(375f, 1))
        assertEquals(375f, compactThreadToolbarItemWidth(375f, 0))
    }
}
