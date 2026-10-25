package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatToolbarSurface
import com.valoser.futacha.shared.compat.compatToolbarMaster
import com.valoser.futacha.shared.ui.util.toolbarContentWidth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompatToolbarLayoutTest {
    @Test fun defaultsFitOnPhoneWidthsWithOtherReservedAtTheRight() {
        val count = compatToolbarMaster(CompatToolbarSurface.THREAD).count { it.defaultActive } + 1
        assertEquals(15, count)
        listOf(375f, 393f, 411f).forEach { width ->
            val slot = compactThreadToolbarItemWidth(width, count)
            assertTrue(slot in 24f..28f)
            val viewport = width - slot
            assertEquals(viewport, toolbarContentWidth(viewport, count - 1, 24f))
            assertEquals(width, slot * count, 0.001f)
        }
    }

    @Test fun allOnScrollsRatherThanShrinkingAndTheFixedMenuKeepsItsOwnSlot() {
        val count = compatToolbarMaster(CompatToolbarSurface.THREAD).size + 1
        assertEquals(20, count)
        listOf(240f, 375f, 393f, 411f).forEach { width ->
            val menuWidth = compactThreadToolbarItemWidth(width, count)
            assertEquals(24f, menuWidth)
            assertTrue(toolbarContentWidth(width - menuWidth, count - 1, 24f) > width - menuWidth)
        }
    }

    @Test fun disabledItemsLeaveRoomForTheRemainingSavedActionsAndAnEmptyListDoesNotDivideByZero() {
        assertEquals(75f, compactThreadToolbarItemWidth(375f, 5))
        assertEquals(375f, compactThreadToolbarItemWidth(375f, 1))
        assertEquals(375f, compactThreadToolbarItemWidth(375f, 0))
    }
}
