package com.valoser.futacha.shared.audio.voicevox

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceInterruptionTimeoutTest {
    @Test fun missingEndHasAnExactTwentyFiveSecondLimit() = runTest {
        val paused = MutableStateFlow(true)
        val failure = CompletableDeferred<Throwable>()
        val guard = backgroundScope.launch { capture(failure) { guardVoiceInterruption(paused) } }
        runCurrent()
        advanceTimeBy(24_999); runCurrent()
        assertFalse(failure.isCompleted)
        advanceTimeBy(1); runCurrent()
        assertEquals(VOICE_INTERRUPTION_TIMEOUT_MESSAGE, failure.await().message)
        assertTrue(guard.isCompleted)
    }
    @Test fun endingAnInterruptionCancelsItsDeadlineAndTheNextOneGetsAFullBudget() = runTest {
        val paused = MutableStateFlow(true)
        val failure = CompletableDeferred<Throwable>()
        backgroundScope.launch { capture(failure) { guardVoiceInterruption(paused) } }
        runCurrent()
        advanceTimeBy(24_999)
        paused.value = false; runCurrent()
        advanceTimeBy(60_000); runCurrent()
        assertFalse(failure.isCompleted)
        paused.value = true; runCurrent()
        advanceTimeBy(24_999); runCurrent()
        assertFalse(failure.isCompleted)
        advanceTimeBy(1); runCurrent()
        assertEquals(VOICE_INTERRUPTION_TIMEOUT_MESSAGE, failure.await().message)
    }
    @Test fun duplicateBeginDoesNotExtendTheCurrentDeadline() = runTest {
        val paused = MutableStateFlow(true)
        val failure = CompletableDeferred<Throwable>()
        backgroundScope.launch { capture(failure) { guardVoiceInterruption(paused) } }
        runCurrent()
        advanceTimeBy(24_000)
        paused.value = true; runCurrent()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(VOICE_INTERRUPTION_TIMEOUT_MESSAGE, failure.await().message)
    }
    @Test fun normalPlaybackHasNoInterruptionDeadlineAndCancellationLeavesNoFailure() = runTest {
        val paused = MutableStateFlow(false)
        val failure = CompletableDeferred<Throwable>()
        val guard = backgroundScope.launch { capture(failure) { guardVoiceInterruption(paused) } }
        runCurrent()
        advanceTimeBy(600_000); runCurrent()
        assertTrue(guard.isActive)
        assertFalse(failure.isCompleted)
        guard.cancelAndJoin()
        advanceTimeBy(600_000); runCurrent()
        assertFalse(failure.isCompleted)
    }
    private suspend fun capture(failure: CompletableDeferred<Throwable>, block: suspend () -> Unit) {
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Throwable) { failure.complete(error) }
    }
}
