package com.valoser.futacha.shared.background

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackgroundRefreshSupportTest {
    @Test
    fun resolveBackgroundRefreshScheduleAction_handles_disabled_pending_and_backoff() {
        assertEquals(
            BackgroundRefreshScheduleAction.SkipDisabled,
            resolveBackgroundRefreshScheduleAction(
                enabled = false,
                hasPendingRefreshRequest = false,
                nextScheduleAllowedAtMillis = 0L,
                nowEpochMillis = 100L
            )
        )
        assertEquals(
            BackgroundRefreshScheduleAction.SkipPending,
            resolveBackgroundRefreshScheduleAction(
                enabled = true,
                hasPendingRefreshRequest = true,
                nextScheduleAllowedAtMillis = 0L,
                nowEpochMillis = 100L
            )
        )
        assertEquals(
            BackgroundRefreshScheduleAction.DelayRetry(250L),
            resolveBackgroundRefreshScheduleAction(
                enabled = true,
                hasPendingRefreshRequest = false,
                nextScheduleAllowedAtMillis = 350L,
                nowEpochMillis = 100L
            )
        )
        assertEquals(
            BackgroundRefreshScheduleAction.SubmitNow,
            resolveBackgroundRefreshScheduleAction(
                enabled = true,
                hasPendingRefreshRequest = false,
                nextScheduleAllowedAtMillis = 100L,
                nowEpochMillis = 100L
            )
        )
    }

    @Test
    fun resolveBackgroundRefreshSubmitFailureState_tracks_retry_limit() {
        val retriable = resolveBackgroundRefreshSubmitFailureState(
            failureNowEpochMillis = 1_000L,
            currentRetryAttempts = 2,
            scheduleBackoffMillis = 60_000L,
            maxRetryAttempts = 3
        )
        assertEquals(61_000L, retriable.nextScheduleAllowedAtMillis)
        assertEquals(3, retriable.nextRetryAttempts)
        assertTrue(retriable.shouldScheduleRetry)
        assertEquals(60_000L, retriable.retryDelayMillis)

        val exhausted = resolveBackgroundRefreshSubmitFailureState(
            failureNowEpochMillis = 1_000L,
            currentRetryAttempts = 3,
            scheduleBackoffMillis = 60_000L,
            maxRetryAttempts = 3
        )
        assertFalse(exhausted.shouldScheduleRetry)
        assertEquals(4, exhausted.nextRetryAttempts)
    }

    @Test
    fun shouldScheduleBackgroundRefreshRetry_requires_enabled_available_slot_and_no_active_job() {
        assertTrue(
            shouldScheduleBackgroundRefreshRetry(
                enabled = true,
                retryAttempts = 3,
                maxRetryAttempts = 3,
                hasActiveRetryJob = false
            )
        )
        assertFalse(
            shouldScheduleBackgroundRefreshRetry(
                enabled = false,
                retryAttempts = 0,
                maxRetryAttempts = 3,
                hasActiveRetryJob = false
            )
        )
        assertFalse(
            shouldScheduleBackgroundRefreshRetry(
                enabled = true,
                retryAttempts = 4,
                maxRetryAttempts = 3,
                hasActiveRetryJob = false
            )
        )
        assertFalse(
            shouldScheduleBackgroundRefreshRetry(
                enabled = true,
                retryAttempts = 0,
                maxRetryAttempts = 3,
                hasActiveRetryJob = true
            )
        )
    }

    @Test
    fun normalizeBackgroundRefreshRetryDelay_clamps_to_positive_value() {
        assertEquals(1L, normalizeBackgroundRefreshRetryDelay(0L))
        assertEquals(1L, normalizeBackgroundRefreshRetryDelay(-50L))
        assertEquals(250L, normalizeBackgroundRefreshRetryDelay(250L))
    }
}
