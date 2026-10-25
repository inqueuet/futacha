package com.valoser.futacha.shared.ui.compat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** L3: the settings sample explains only a real time limit; a stop from elsewhere is not a timeout. */
class SpeechSampleCancellationMessageTest {
    @Test fun aRealTimeLimitIsExplained() = runBlocking {
        val timeout = try { withTimeout(1) { awaitCancellation() } } catch (cancelled: CancellationException) { cancelled }
        assertEquals("試聴がタイムアウトしました", speechSampleCancellationMessage(timeout))
    }

    @Test fun aStopFromTheNotificationShowsNothing() {
        assertNull(speechSampleCancellationMessage(CancellationException("stopped from the notification")))
    }
}
