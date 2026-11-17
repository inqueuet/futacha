package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * C4-1/E4-1: a command forwarded to a screen expires by its real age (time
 * spent stopped or locked counts) and one that waited behind the lock follows
 * the 60 s rule of the reception. Before, the 90 s timer started only when the
 * effect first ran (after an Android stop: at the next resume, hours later)
 * and restarted from zero after every unlock.
 */
class FutachaAiScreenCommandSupervisionTest {
    private val command = FutachaAiCommand(FutachaAiAction.StartThreadReadAloud, source = "wear-os")

    private fun unlockedHolder() = FutachaAppLockHolder().apply {
        openSessionWithoutLock()
        setContentVisible(true)
    }

    @Test
    fun decisionUsesTheAgeAndTheLockHoldRule() {
        assertEquals(AiCommandHoldDecision.Run, resolveAiScreenCommandHoldDecision(89_000L, wasHeldByLock = false))
        assertEquals(AiCommandHoldDecision.DropExpired, resolveAiScreenCommandHoldDecision(90_001L, wasHeldByLock = false))
        assertEquals(AiCommandHoldDecision.Run, resolveAiScreenCommandHoldDecision(59_000L, wasHeldByLock = true))
        assertEquals(AiCommandHoldDecision.DropHeldByLock, resolveAiScreenCommandHoldDecision(60_001L, wasHeldByLock = true))
    }

    @Test
    fun commandForwardedLongAgoIsDroppedWithoutReachingTheScreen() = runBlocking {
        // Forwarded while the activity was stopped; the effect first runs at the resume.
        var released = false
        val decision = superviseAiScreenCommand(
            forwardedAt = TimeSource.Monotonic.markNow() - 3_600.seconds,
            appLock = unlockedHolder(),
            onRelease = { released = true }
        )
        assertEquals(AiCommandHoldDecision.DropExpired, decision)
        assertFalse(released)
        assertEquals(buildAiScreenCommandExpiredMessage(command), buildAiScreenCommandDropMessage(command, decision))
    }

    @Test
    fun commandIsNotReleasedWhileLockedAndIsDroppedWhenHeldTooLong() = runBlocking {
        val holder = unlockedHolder()
        val forwardedAt = TimeSource.Monotonic.markNow() - 70.seconds
        holder.lockSession()
        var released = false
        val supervision = async(start = CoroutineStart.UNDISPATCHED) {
            superviseAiScreenCommand(forwardedAt, holder, onRelease = { released = true })
        }
        yield()
        assertFalse(supervision.isCompleted, "must wait for the unlock")
        assertFalse(released)
        holder.unlockSession()
        assertEquals(AiCommandHoldDecision.DropHeldByLock, supervision.await())
        assertFalse(released)
        assertEquals(
            buildAiCommandHeldByLockMessage(command),
            buildAiScreenCommandDropMessage(command, AiCommandHoldDecision.DropHeldByLock)
        )
    }

    @Test
    fun commandUnlockedSoonIsReleasedAndExpiresLater() = runBlocking {
        val holder = unlockedHolder()
        val forwardedAt = TimeSource.Monotonic.markNow() - 10.seconds
        holder.lockSession()
        var released = false
        val supervision = async(start = CoroutineStart.UNDISPATCHED) {
            superviseAiScreenCommand(forwardedAt, holder, onRelease = { released = true }, expiryMillis = 10_300L)
        }
        yield()
        assertFalse(released)
        holder.unlockSession()
        assertEquals(AiCommandHoldDecision.DropExpired, supervision.await())
        assertTrue(released, "a command unlocked within the limit reaches the screen")
    }
}
