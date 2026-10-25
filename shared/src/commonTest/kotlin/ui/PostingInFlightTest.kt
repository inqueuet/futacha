package com.valoser.futacha.shared.ui

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** L30: what is waited for is every post being sent, and a wait ends when the last one does. */
class PostingInFlightTest {
    @Test fun awaitIdleWaitsForEverySendToEnd() = runBlocking {
        val tracker = PostingInFlight()
        withTimeout(1_000) { tracker.awaitIdle() }
        tracker.begin(); tracker.begin()
        val waiting = async { tracker.awaitIdle(); true }
        repeat(5) { yield() }
        assertFalse(waiting.isCompleted)
        tracker.end()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        repeat(5) { yield() }
        assertFalse(waiting.isCompleted)
        tracker.end()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        assertTrue(withTimeout(1_000) { waiting.await() })
        tracker.end()
        assertEquals(0, tracker.count)
    }
}
