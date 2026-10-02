package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.ui.AiCommandHoldDecision
import com.valoser.futacha.shared.ui.FutachaAppLockHolder
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class CompatPlatformAiCommandGateTest {
    private fun open(vararg parameters: Pair<String, String>) =
        FutachaAiCommand(FutachaAiAction.OpenThreadFromUrl, parameters.toMap(), source = "platform")

    @Test
    fun threadUrlOutsideFutabaIsRejectedWithTheModernMessage() {
        assertNull(compatPlatformAiThreadUrlRejection(open()))
        assertNull(compatPlatformAiThreadUrlRejection(open("url" to "https://may.2chan.net/b/res/123.htm")))
        assertNull(compatPlatformAiThreadUrlRejection(open("threadId" to "123")))
        assertEquals(
            COMPAT_AI_UNTRUSTED_THREAD_URL_MESSAGE,
            compatPlatformAiThreadUrlRejection(open("url" to "https://evil.example/b/res/123.htm"))
        )
        // User info hides the real host from a naive suffix check.
        assertEquals(
            COMPAT_AI_UNTRUSTED_THREAD_URL_MESSAGE,
            compatPlatformAiThreadUrlRejection(open("url" to "https://may.2chan.net@evil.example/b/res/1.htm"))
        )
        assertEquals(
            COMPAT_AI_UNTRUSTED_THREAD_URL_MESSAGE,
            compatPlatformAiThreadUrlRejection(open("url" to "https://evil.example@may.2chan.net/b/res/1.htm"))
        )
    }

    @Test
    fun arrivalIsKeptPerDeliveryAcrossEffectRestarts() = runBlocking<Unit> {
        val tracker = CompatPlatformAiArrivalTracker()
        val command = open("threadId" to "1")
        val first = tracker.arrivalOf(command)
        delay(2L)
        // ValueTimeMark is a value class: compare values, not identity.
        assertEquals(first, tracker.arrivalOf(command))
        // An equal command delivered again is a new delivery.
        val again = tracker.arrivalOf(command.copy())
        assertTrue(again > first)
    }

    @Test
    fun commandIsHeldUntilTheUnlock() = runBlocking<Unit> {
        val holder = FutachaAppLockHolder()
        holder.setContentVisible(true)
        val arrivedAt = TimeSource.Monotonic.markNow()
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { holdCompatPlatformAiCommand(arrivedAt, holder) }
        yield()
        assertFalse(waiting.isCompleted)
        delay(2L)
        holder.unlockSession()
        assertEquals(AiCommandHoldDecision.Run, waiting.await())
    }

    @Test
    fun commandHeldBehindTheLockForAMinuteIsDropped() = runBlocking<Unit> {
        val holder = FutachaAppLockHolder()
        holder.setContentVisible(true)
        val arrivedAt = TimeSource.Monotonic.markNow() - 61.seconds
        holder.unlockSession()
        assertEquals(AiCommandHoldDecision.DropHeldByLock, holdCompatPlatformAiCommand(arrivedAt, holder))

        // Without a password the same age is not a lock hold.
        val noPassword = FutachaAppLockHolder().apply {
            openSessionWithoutLock()
            setContentVisible(true)
        }
        assertEquals(AiCommandHoldDecision.Run, holdCompatPlatformAiCommand(arrivedAt, noPassword))
    }
}
