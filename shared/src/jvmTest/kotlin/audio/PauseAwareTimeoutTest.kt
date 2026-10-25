package com.valoser.futacha.shared.audio

import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** L117: a reading paused for the audio focus (this app's video) does not run out of time while it waits. */
class PauseAwareTimeoutTest {
    @Test fun timePausedIsNotCounted() = runTest {
        var paused = true
        val result = withPauseAwareTimeout(1_000L, isPaused = { paused }, onTimeout = { IOException("timed out") }) {
            delay(60_000L) // a long video
            paused = false
            delay(500L)
            "spoken"
        }
        assertEquals("spoken", result)
    }

    @Test fun timeHeardStillRunsOut() = runTest {
        assertFailsWith<IOException> {
            withPauseAwareTimeout(1_000L, isPaused = { false }, onTimeout = { IOException("timed out") }) {
                delay(60_000L)
            }
        }
    }
}
