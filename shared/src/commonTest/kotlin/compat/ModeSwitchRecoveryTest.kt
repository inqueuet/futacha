package com.valoser.futacha.shared.compat

import kotlinx.coroutines.runBlocking
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ModeSwitchRecoveryTest {
    @Test
    fun successfulRecoveryReturnsItsResult() = runBlocking {
        assertEquals(
            ExperienceProfile.TOSHIAKI_COMPAT,
            recoverInterruptedModeSwitch { ExperienceProfile.TOSHIAKI_COMPAT }
        )
    }

    @Test
    fun failedRecoveryIsReportedAsNotRolledBack() = runBlocking {
        val commitFailure = IllegalStateException("commit failed")

        val reported = assertFailsWith<ModeSwitchRecoveryException> {
            recoverInterruptedModeSwitch<ExperienceProfile> { throw commitFailure }
        }

        // Previously the raw failure reached the dialog without a suppressed exception,
        // so it claimed "現在のモードのまま使用できます" although nothing was rolled back (M4-1).
        assertSame(commitFailure, reported.cause)
        val message = modeSwitchFailureMessage(reported)
        assertTrue(message.startsWith("アプリを再起動してください。"), message)
        assertFalse(message.contains("現在のモードのまま"), message)
        assertTrue(message.contains("前回のモード切替を完了できませんでした"), message)
    }

    @Test
    fun recoveryFailureIsNotWrappedTwice() = runBlocking {
        val first = ModeSwitchRecoveryException(IllegalStateException("commit failed"))

        val reported = assertFailsWith<ModeSwitchRecoveryException> {
            recoverInterruptedModeSwitch<Unit> { throw first }
        }

        assertSame(first, reported)
    }

    @Test
    fun cancellationIsNotARecoveryFailure() {
        val cancelled = CancellationException("left the screen")

        val thrown = assertFailsWith<CancellationException> {
            runBlocking { recoverInterruptedModeSwitch<Unit> { throw cancelled } }
        }

        assertEquals(CancellationException::class, thrown::class)
        assertEquals("left the screen", thrown.message)
    }
}
