package com.valoser.futacha.shared.ui.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatScrollIndicatorTest {
    @Test
    fun aDragGoesToTheSamePlaceInTheList() {
        assertEquals(0, compatScrollIndicatorTarget(0f, 1000f, 200))
        assertEquals(99, compatScrollIndicatorTarget(500f, 1000f, 200))
        assertEquals(199, compatScrollIndicatorTarget(1000f, 1000f, 200))
        assertEquals(199, compatScrollIndicatorTarget(1500f, 1000f, 200))
        assertEquals(0, compatScrollIndicatorTarget(-50f, 1000f, 200))
        assertEquals(0, compatScrollIndicatorTarget(300f, 1000f, 0))
    }

    @Test
    fun theFastScrollBarTakesThePlaceOfThePlainOne() {
        assertTrue(compatShowsScrollIndicator(indicatorOn = true, fastScrollOn = false))
        assertFalse(compatShowsScrollIndicator(indicatorOn = true, fastScrollOn = true))
        assertFalse(compatShowsScrollIndicator(indicatorOn = false, fastScrollOn = false))
    }
}
