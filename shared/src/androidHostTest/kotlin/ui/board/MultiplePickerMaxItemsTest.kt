package com.valoser.futacha.shared.ui.board

import kotlin.test.Test
import kotlin.test.assertEquals

class MultiplePickerMaxItemsTest {
    @Test
    fun theLimitIsTheModesCountWithinTheSystemLimit() {
        assertEquals(10, multiplePickerMaxItems(10, systemLimit = 100))
        assertEquals(3, multiplePickerMaxItems(3, systemLimit = 100))
        assertEquals(5, multiplePickerMaxItems(10, systemLimit = 5))
        // The photo picker refuses 1, so a single slot still opens it with 2.
        assertEquals(2, multiplePickerMaxItems(1, systemLimit = 100))
    }
}
