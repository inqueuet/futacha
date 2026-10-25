package com.valoser.futacha.shared.audio.voicevox

import kotlin.test.*
import kotlin.time.TestTimeSource
import kotlin.time.Duration.Companion.seconds

class VoicePcmPlaybackWatchdogTest {
    @Test fun aWriteThatKeepsReturningZeroHasABoundedWait() {
        val time = TestTimeSource()
        val guard = VoicePcmPlaybackWatchdog(clock = time)
        guard.observe(0, 0, false)
        time += 10.seconds
        assertFailsWith<IllegalStateException> { guard.observe(0, 0, false) }
    }
    @Test fun aFullyWrittenClipWhoseHeadNeverMovesHasABoundedWait() {
        val time = TestTimeSource()
        val guard = VoicePcmPlaybackWatchdog(clock = time)
        guard.observe(400, 0, false)
        time += 10.seconds
        assertFailsWith<IllegalStateException> { guard.observe(400, 0, false) }
    }
    @Test fun regularWritingAndConsumptionCanContinueBeyondOneTimeout() {
        val time = TestTimeSource()
        val guard = VoicePcmPlaybackWatchdog(clock = time)
        repeat(20) { index ->
            time += 9.seconds
            guard.observe(index * 10, index.toLong(), false)
        }
    }
    @Test fun focusPauseDoesNotConsumeTheStallBudgetAndResumeGetsAFullBudget() {
        val time = TestTimeSource()
        val guard = VoicePcmPlaybackWatchdog(clock = time)
        guard.observe(400, 0, false)
        time += 9.seconds
        guard.observe(400, 0, true)
        time += 60.seconds
        guard.observe(400, 0, false)
        time += 9.seconds
        guard.observe(400, 0, false)
        time += 1.seconds
        assertFailsWith<IllegalStateException> { guard.observe(400, 0, false) }
    }
}
