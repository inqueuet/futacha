package com.valoser.futacha

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchSyncManagerCommandAgeTest {
    @Test
    fun queuedPlaybackCommandStaysRelevantOnlyWithinTheUiCommandMaxAge() {
        val now = 10_000_000L
        assertEquals(false, isWithinWatchUiCommandMaxAge(queuedElapsedMillis = Long.MIN_VALUE, nowElapsedMillis = now))
        assertEquals(true, isWithinWatchUiCommandMaxAge(queuedElapsedMillis = now - 1_000L, nowElapsedMillis = now))
        assertEquals(
            true,
            isWithinWatchUiCommandMaxAge(queuedElapsedMillis = now - WATCH_UI_COMMAND_MAX_AGE_MILLIS, nowElapsedMillis = now)
        )
        assertEquals(
            false,
            isWithinWatchUiCommandMaxAge(queuedElapsedMillis = now - WATCH_UI_COMMAND_MAX_AGE_MILLIS - 1L, nowElapsedMillis = now)
        )
        // A value from before a reboot (elapsed clock reset) is not trusted.
        assertEquals(false, isWithinWatchUiCommandMaxAge(queuedElapsedMillis = now + 5L, nowElapsedMillis = now))
    }
}
