package com.valoser.futacha.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FutachaAuditFixes20261007Test {
    // M4: the history's boardUrl already holds the thread URL.
    @Test
    fun aiHistoryThreadUrlDoesNotAppendTheThreadPathTwice() {
        assertEquals(
            "https://may.2chan.net/b/res/123.htm",
            aiHistoryThreadUrl("https://may.2chan.net/b/res/123.htm", "123")
        )
        // A plain board URL (older rows) and a damaged `.../res/123.htm/res/123.htm` both resolve.
        assertEquals("https://may.2chan.net/b/res/123.htm", aiHistoryThreadUrl("https://may.2chan.net/b/", "123"))
        assertEquals(
            "https://may.2chan.net/b/res/123.htm",
            aiHistoryThreadUrl("https://may.2chan.net/b/res/123.htm/res/123.htm", "123")
        )
        assertNull(aiHistoryThreadUrl("not a url", "123"))
        assertNull(aiHistoryThreadUrl("https://example.com/b/", "123"))
    }

    // 低: the unlock counter and cool-down use a monotonic clock and can be restored.
    @Test
    fun lockoutFollowsTheMonotonicClock() {
        var monotonic = 0L
        val tracker = AppLockAttemptTracker({ monotonic })
        repeat(APP_LOCK_MAX_FAILURES_BEFORE_WAIT - 1) { assertFalse(tracker.recordFailure()) }
        assertTrue(tracker.recordFailure())
        assertTrue(tracker.isLockedOut())
        assertEquals(APP_LOCK_FAILURE_WAIT_MILLIS, tracker.remainingMillis())
        monotonic += APP_LOCK_FAILURE_WAIT_MILLIS - 1
        assertTrue(tracker.isLockedOut())
        monotonic += 1
        assertFalse(tracker.isLockedOut())
        assertEquals(0, tracker.failedAttempts)
    }

    @Test
    fun aRestartedProcessPicksTheCounterAndRemainingWaitBackUp() {
        var monotonic = 50L
        val restored = AppLockAttemptTracker({ monotonic })
        restored.restoreOnce(AppLockAttemptState(failedAttempts = 3, lockoutRemainingMillis = 5_000L))
        assertEquals(3, restored.failedAttempts)
        assertEquals(5_000L, restored.remainingMillis())
        // Only the first restore counts, and a stored value never locks longer than one wait.
        restored.restoreOnce(AppLockAttemptState(failedAttempts = 0, lockoutRemainingMillis = 0L))
        assertEquals(3, restored.failedAttempts)

        val clamped = AppLockAttemptTracker({ monotonic })
        clamped.restoreOnce(AppLockAttemptState(0, 24L * 60L * 60L * 1000L))
        assertEquals(APP_LOCK_FAILURE_WAIT_MILLIS, clamped.remainingMillis())

        val snapshot = restored.snapshot()
        assertEquals(3, snapshot.failedAttempts)
        assertEquals(5_000L, snapshot.lockoutRemainingMillis)
        monotonic += 2_000L
        assertEquals(3_000L, restored.snapshot().lockoutRemainingMillis)
        monotonic += 3_000L
        assertFalse(restored.isLockedOut())
        assertEquals(0L, restored.snapshot().lockoutRemainingMillis)
    }

    @Test
    fun aRestoredCounterMakesTheNextFailureStartTheWait() {
        val tracker = AppLockAttemptTracker({ 0L })
        tracker.restoreOnce(AppLockAttemptState(failedAttempts = APP_LOCK_MAX_FAILURES_BEFORE_WAIT - 1))
        assertTrue(tracker.recordFailure())
        assertEquals(APP_LOCK_FAILURE_WAIT_MILLIS, tracker.remainingMillis())
    }

    @Test
    fun aRestoredCounterIsClampedBelowTheWaitThreshold() {
        val tracker = AppLockAttemptTracker({ 0L })
        tracker.restoreOnce(AppLockAttemptState(failedAttempts = 99))
        assertEquals(APP_LOCK_MAX_FAILURES_BEFORE_WAIT - 1, tracker.failedAttempts)
    }

    @Test
    fun aSuccessfulUnlockClearsTheCounter() {
        val tracker = AppLockAttemptTracker({ 0L })
        tracker.recordFailure()
        tracker.recordSuccess()
        assertEquals(0, tracker.failedAttempts)
        assertTrue(tracker.snapshot().isEmpty)
    }
}
