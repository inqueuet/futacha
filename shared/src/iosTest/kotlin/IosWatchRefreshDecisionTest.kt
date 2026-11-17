package com.valoser.futacha.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class IosWatchRefreshDecisionTest {
    @Test
    fun refreshIsThrottledWithinTwoMinutesOfTheLastStart() {
        val interval = IOS_WATCH_REFRESH_MIN_INTERVAL
        assertEquals(2.minutes, interval)
        assertEquals(IosWatchRefreshDecision.Start, resolveIosWatchRefreshDecision(false, null, interval))
        assertEquals(IosWatchRefreshDecision.Throttled, resolveIosWatchRefreshDecision(false, 30.seconds, interval))
        assertEquals(IosWatchRefreshDecision.Start, resolveIosWatchRefreshDecision(false, 2.minutes, interval))
        assertEquals(IosWatchRefreshDecision.CoalesceIntoRunning, resolveIosWatchRefreshDecision(true, 5.minutes, interval))
        assertEquals("refreshThrottled", IosWatchCommandOutcome.RefreshThrottled.wireValue)
    }
}
