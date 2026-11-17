package com.valoser.futacha.shared.ui

import kotlin.time.TimeSource

/** Wrong-password counter and cool-down of the start lock, as it can be persisted. */
internal data class AppLockAttemptState(
    val failedAttempts: Int = 0,
    /**
     * Cool-down still to wait, in milliseconds; 0 when none. Stored as a remaining duration, not
     * as a wall-clock deadline, so neither setting the clock back (to stretch the wait) nor
     * forward (to skip it) changes it.
     */
    val lockoutRemainingMillis: Long = 0L
) {
    val isEmpty: Boolean get() = failedAttempts == 0 && lockoutRemainingMillis == 0L
}

internal const val APP_LOCK_MAX_FAILURES_BEFORE_WAIT = 5
internal const val APP_LOCK_FAILURE_WAIT_MILLIS = 15_000L

/**
 * Tracks failed unlock attempts for the whole process, not for one screen instance. The previous
 * per-screen `rememberSaveable` state was lost when the app was force-stopped, and its cool-down
 * ran on the wall clock, so moving the clock back skipped the wait. The cool-down here counts
 * monotonic time only; a persisted [AppLockAttemptState] carries the remaining wait as a
 * duration (clamped, so a stored value cannot lock the app for longer than one wait).
 */
internal class AppLockAttemptTracker(
    private val monotonicNowMillis: () -> Long = defaultMonotonicNowMillis
) {
    var failedAttempts: Int = 0
        private set
    private var lockoutEndMonotonicMillis: Long? = null
    private var restored = false

    fun remainingMillis(): Long {
        val end = lockoutEndMonotonicMillis ?: return 0L
        val remaining = end - monotonicNowMillis()
        if (remaining <= 0L) {
            lockoutEndMonotonicMillis = null
            return 0L
        }
        return remaining.coerceAtMost(APP_LOCK_FAILURE_WAIT_MILLIS)
    }

    fun isLockedOut(): Boolean = remainingMillis() > 0L

    /** Records a wrong password. Returns true when it started a cool-down. */
    fun recordFailure(): Boolean {
        failedAttempts += 1
        if (failedAttempts >= APP_LOCK_MAX_FAILURES_BEFORE_WAIT) {
            failedAttempts = 0
            lockoutEndMonotonicMillis = monotonicNowMillis() + APP_LOCK_FAILURE_WAIT_MILLIS
            return true
        }
        return false
    }

    fun recordSuccess() {
        failedAttempts = 0
        lockoutEndMonotonicMillis = null
    }

    /**
     * Adopts a persisted state once per process, and only while nothing was recorded yet: a
     * fresh process after a force-stop picks the counter and the remaining wait back up.
     */
    fun restoreOnce(state: AppLockAttemptState) {
        if (restored) return
        restored = true
        if (failedAttempts != 0 || lockoutEndMonotonicMillis != null) return
        failedAttempts = state.failedAttempts.coerceIn(0, APP_LOCK_MAX_FAILURES_BEFORE_WAIT - 1)
        val remaining = state.lockoutRemainingMillis.coerceIn(0L, APP_LOCK_FAILURE_WAIT_MILLIS)
        if (remaining > 0L) {
            lockoutEndMonotonicMillis = monotonicNowMillis() + remaining
        }
    }

    fun snapshot(): AppLockAttemptState {
        return AppLockAttemptState(
            failedAttempts = failedAttempts,
            lockoutRemainingMillis = remainingMillis()
        )
    }

    companion object {
        /** Shared by every lock screen of this process. */
        val Process = AppLockAttemptTracker()
    }
}

private val monotonicOrigin = TimeSource.Monotonic.markNow()
private val defaultMonotonicNowMillis: () -> Long = { monotonicOrigin.elapsedNow().inWholeMilliseconds }
