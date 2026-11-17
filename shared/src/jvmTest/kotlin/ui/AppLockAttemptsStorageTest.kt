package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.compat.compatBackupExportPreferences
import com.valoser.futacha.shared.compat.compatBackupRestorePreferences
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppLockAttemptsStorageTest {
    private class FakePreference(var value: String? = null) {
        var writes = 0
        val storage = AppLockAttemptsStorage(
            read = { value },
            write = { value = it; writes += 1 }
        )
    }

    @Test
    fun theKeyAndFormatAreStable() {
        assertEquals("compat.appLock.attempts", APP_LOCK_ATTEMPTS_PREFERENCE_KEY)
        assertEquals("3,5000", encodeAppLockAttemptState(AppLockAttemptState(3, 5_000L)))
        assertNull(encodeAppLockAttemptState(AppLockAttemptState()))
        assertEquals(AppLockAttemptState(3, 5_000L), decodeAppLockAttemptState("3,5000"))
    }

    @Test
    fun outOfRangeValuesAreClampedAndBrokenValuesAreEmpty() {
        assertEquals(
            AppLockAttemptState(APP_LOCK_MAX_FAILURES_BEFORE_WAIT - 1, APP_LOCK_FAILURE_WAIT_MILLIS),
            decodeAppLockAttemptState("99,86400000")
        )
        assertEquals(AppLockAttemptState(0, 0L), decodeAppLockAttemptState("-3,-10"))
        for (broken in listOf(null, "", "  ", "abc", "1", "1,2,3", "x,5", "2,y", "1.5,2", ",")) {
            assertTrue(decodeAppLockAttemptState(broken).isEmpty, "broken value: $broken")
        }
        // The encoder never writes more than the decoder accepts.
        assertEquals("4,15000", encodeAppLockAttemptState(AppLockAttemptState(50, Long.MAX_VALUE)))
    }

    @Test
    fun theStateRoundTripsThroughTheStorage() = runBlocking<Unit> {
        val pref = FakePreference()
        assertTrue(pref.storage.save(AppLockAttemptState(2, 0L)))
        assertEquals("2,0", pref.value)
        assertEquals(AppLockAttemptState(2, 0L), pref.storage.load())
        assertTrue(pref.storage.save(AppLockAttemptState(0, 9_000L)))
        assertEquals(AppLockAttemptState(0, 9_000L), AppLockAttemptsStorage({ pref.value }, {}).load())
    }

    @Test
    fun aRestartKeepsTheCounterAndTheRemainingWait() = runBlocking<Unit> {
        var monotonic = 0L
        val pref = FakePreference()
        val first = AppLockAttemptTracker({ monotonic })
        repeat(APP_LOCK_MAX_FAILURES_BEFORE_WAIT) { first.recordFailure() }
        pref.storage.save(first.snapshot())
        monotonic += 4_000L // the wait runs on; the stored remaining wait is what was written

        // "Restart": a new tracker and a new storage over the same preference.
        monotonic = 1_000_000L
        val second = AppLockAttemptTracker({ monotonic })
        val reloaded = AppLockAttemptsStorage({ pref.value }, { pref.value = it })
        second.restoreOnce(reloaded.load())
        assertTrue(second.isLockedOut())
        assertEquals(APP_LOCK_FAILURE_WAIT_MILLIS, second.remainingMillis())

        // Waiting it out and persisting that clears the key, so the next launch does not wait again.
        monotonic += APP_LOCK_FAILURE_WAIT_MILLIS
        assertFalse(second.isLockedOut())
        reloaded.save(second.snapshot())
        assertNull(pref.value)
    }

    @Test
    fun aRestoredCounterMakesTheNextFailureStartTheWait() = runBlocking<Unit> {
        val pref = FakePreference("${APP_LOCK_MAX_FAILURES_BEFORE_WAIT - 1},0")
        val tracker = AppLockAttemptTracker({ 0L })
        tracker.restoreOnce(pref.storage.load())
        assertTrue(tracker.recordFailure())
        assertEquals(APP_LOCK_FAILURE_WAIT_MILLIS, tracker.remainingMillis())
    }

    @Test
    fun aSuccessfulUnlockRemovesTheKey() = runBlocking<Unit> {
        val pref = FakePreference("3,0")
        pref.storage.load()
        val tracker = AppLockAttemptTracker({ 0L })
        tracker.restoreOnce(AppLockAttemptState(3, 0L))
        tracker.recordSuccess()
        assertTrue(pref.storage.save(tracker.snapshot()))
        assertNull(pref.value)
    }

    @Test
    fun anUnchangedOrEmptyStateIsNotWrittenAgain() = runBlocking<Unit> {
        val pref = FakePreference("2,0")
        pref.storage.load()
        assertFalse(pref.storage.save(AppLockAttemptState(2, 0L)))
        assertEquals(0, pref.writes)

        assertTrue(pref.storage.save(AppLockAttemptState(3, 0L)))
        assertFalse(pref.storage.save(AppLockAttemptState(3, 0L)))
        assertEquals(1, pref.writes)

        // Empty over empty: nothing to write (also on a fresh install).
        val fresh = FakePreference()
        fresh.storage.load()
        assertFalse(fresh.storage.save(AppLockAttemptState()))
        assertEquals(0, fresh.writes)
        // Empty over a stored value removes it exactly once.
        assertTrue(pref.storage.save(AppLockAttemptState()))
        assertFalse(pref.storage.save(AppLockAttemptState()))
        assertNull(pref.value)
    }

    @Test
    fun aGarbageStoredValueIsReplacedWhenTheStateIsSaved() = runBlocking<Unit> {
        val pref = FakePreference("garbage")
        assertTrue(pref.storage.load().isEmpty)
        assertTrue(pref.storage.save(AppLockAttemptState()))
        assertNull(pref.value)
    }

    @Test
    fun aFailingStoreNeverBreaksTheLockScreen() = runBlocking<Unit> {
        val storage = AppLockAttemptsStorage(
            read = { error("unreadable") },
            write = { error("unwritable") }
        )
        assertTrue(storage.load().isEmpty)
        assertFalse(storage.save(AppLockAttemptState(1, 0L)))
    }

    @Test
    fun withoutAStoreTheCounterStaysInTheProcessOnly() {
        // No storage: the screen gets the defaults (loaded, empty) and a no-op writer.
        val tracker = AppLockAttemptTracker({ 0L })
        tracker.restoreOnce(AppLockAttemptState())
        tracker.recordFailure()
        assertEquals(1, tracker.failedAttempts)
    }

    @Test
    fun theAttemptsAreNotPartOfSettingsBackups() {
        val prefs = mapOf(APP_LOCK_ATTEMPTS_PREFERENCE_KEY to "4,9000", "compat.other" to "1")
        assertEquals(mapOf("compat.other" to "1"), compatBackupExportPreferences(prefs))
        assertEquals(mapOf("compat.other" to "1"), compatBackupRestorePreferences(prefs))
    }
}
