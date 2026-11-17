package com.valoser.futacha.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class FutachaAppLockSupportTest {
    @Test
    fun remainingLockoutTimeHandlesClockExtremesWithoutOverflow() {
        assertEquals(0L, appLockRemainingMillis(100L, 100L))
        assertEquals(1_001L, appLockRemainingMillis(2_001L, 1_000L))
        assertEquals(2L, appLockRemainingSeconds(2_001L, 1_000L))
        assertEquals(
            Long.MAX_VALUE,
            appLockRemainingMillis(Long.MAX_VALUE, Long.MIN_VALUE)
        )
        assertEquals(9_223_372_036_854_776L, appLockRemainingSeconds(Long.MAX_VALUE, Long.MIN_VALUE))
    }
}
