package com.valoser.futacha.shared.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EpochTimeSupportTest {
    @Test
    fun elapsed_saturatesAndNeverBecomesNegative() {
        assertEquals(50L, safeEpochElapsedMillis(150L, 100L))
        assertEquals(0L, safeEpochElapsedMillis(50L, 100L))
        assertEquals(Long.MAX_VALUE, safeEpochElapsedMillis(Long.MAX_VALUE, Long.MIN_VALUE))
    }

    @Test
    fun intervalHelpersInvalidateOnClockRollback() {
        assertTrue(hasEpochIntervalElapsed(50L, 100L, 1_000L))
        assertFalse(isWithinEpochInterval(50L, 100L, 1_000L))
        assertTrue(isWithinEpochInterval(150L, 100L, 50L))
        assertTrue(hasEpochDurationExceeded(50L, 100L, 1_000L))
        assertFalse(hasEpochDurationExceeded(150L, 100L, 50L))
    }

    @Test
    fun subtract_saturatesAtLongMinimum() {
        assertEquals(Long.MIN_VALUE, saturatingEpochSubtract(Long.MIN_VALUE + 1L, 10L))
        assertEquals(90L, saturatingEpochSubtract(100L, 10L))
        assertEquals(Long.MAX_VALUE, saturatingEpochAdd(Long.MAX_VALUE - 1L, 10L))
        assertEquals(110L, saturatingEpochAdd(100L, 10L))
    }
}
