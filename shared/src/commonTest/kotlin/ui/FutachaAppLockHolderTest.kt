package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class FutachaAppLockHolderTest {
    @Test
    fun lockIsReadableImmediatelyWithoutComposition() {
        val holder = FutachaAppLockHolder()
        assertFalse(holder.isUnlocked)
        holder.unlockSession()
        // The content is not shown until composition publishes it.
        assertFalse(holder.isUnlocked)
        holder.setContentVisible(true)
        assertTrue(holder.isUnlocked)

        // ON_STOP while recomposition is paused: the content stays "visible"
        // for composition, but command paths must see the lock at once (C-1).
        holder.lockSession()
        assertFalse(holder.isUnlocked)
        assertFalse(holder.sessionUnlocked.value)
    }

    @Test
    fun awaitUnlockedHoldsUntilTheUnlock() = runBlocking<Unit> {
        val holder = FutachaAppLockHolder()
        holder.setContentVisible(true)
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { holder.awaitUnlocked(); true }
        yield()
        assertFalse(waiting.isCompleted)
        holder.unlockSession()
        assertTrue(waiting.await())
    }

    @Test
    fun onlyAPasswordUnlockAfterArrivalCountsAsHeldByLock() = runBlocking<Unit> {
        val holder = FutachaAppLockHolder()
        val beforeStart = TimeSource.Monotonic.markNow()
        delay(2L)
        holder.openSessionWithoutLock()
        assertFalse(holder.wasHeldByLock(beforeStart))

        val beforeUnlock = TimeSource.Monotonic.markNow()
        delay(2L)
        holder.lockSession()
        holder.unlockSession()
        assertTrue(holder.wasHeldByLock(beforeUnlock))
        assertFalse(holder.wasHeldByLock(TimeSource.Monotonic.markNow()))
    }

    @Test
    fun heldCommandsExpireAfterTheLockTtl() {
        assertEquals(
            AiCommandHoldDecision.Run,
            resolveAiCommandHoldDecision(ageMillis = 59_000L, maxAgeMillis = null, wasHeldByLock = true)
        )
        assertEquals(
            AiCommandHoldDecision.DropHeldByLock,
            resolveAiCommandHoldDecision(ageMillis = 61_000L, maxAgeMillis = null, wasHeldByLock = true)
        )
        // Without a lock wait an old command (e.g. a cold-start deep link) still runs.
        assertEquals(
            AiCommandHoldDecision.Run,
            resolveAiCommandHoldDecision(ageMillis = 600_000L, maxAgeMillis = null, wasHeldByLock = false)
        )
        // A sender's own limit wins (watch taps).
        assertEquals(
            AiCommandHoldDecision.DropExpired,
            resolveAiCommandHoldDecision(ageMillis = 31_000L, maxAgeMillis = 30_000L, wasHeldByLock = false)
        )
    }

    @Test
    fun screenCommandWaitCoversASlowThreadLoad() {
        assertTrue(AI_SCREEN_COMMAND_EXPIRY_MILLIS >= 75_000L)
        assertTrue(
            buildAiScreenCommandExpiredMessage(FutachaAiCommand(FutachaAiAction.StartThreadReadAloud))
                .contains(FutachaAiAction.StartThreadReadAloud.label)
        )
    }
}
