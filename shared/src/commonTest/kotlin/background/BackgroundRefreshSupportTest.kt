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
    fun backgroundRefreshTiming_saturatesCorruptExtremeValues() {
        assertEquals(
            BackgroundRefreshScheduleAction.DelayRetry(Long.MAX_VALUE),
            resolveBackgroundRefreshScheduleAction(
                enabled = true,
                hasPendingRefreshRequest = false,
                nextScheduleAllowedAtMillis = Long.MAX_VALUE,
                nowEpochMillis = Long.MIN_VALUE
            )
        )

        val state = resolveBackgroundRefreshSubmitFailureState(
            failureNowEpochMillis = Long.MAX_VALUE,
            currentRetryAttempts = Int.MAX_VALUE,
            scheduleBackoffMillis = Long.MAX_VALUE,
            maxRetryAttempts = 12
        )
        assertEquals(Long.MAX_VALUE, state.nextScheduleAllowedAtMillis)
        assertEquals(Int.MAX_VALUE, state.nextRetryAttempts)
        assertFalse(state.shouldScheduleRetry)
        assertEquals(Long.MAX_VALUE, state.retryDelayMillis)
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

    @Test
    fun backgroundRefresh_submitsAppRefreshAndProcessingUntilEachIsPending() {
        val all = BackgroundRefreshTaskKind.entries.toSet()
        // BGAppRefresh is what iOS runs periodically; it must be submitted, not only BGProcessing.
        assertEquals(
            listOf(BackgroundRefreshTaskKind.APP_REFRESH, BackgroundRefreshTaskKind.PROCESSING),
            resolveBackgroundRefreshKindsToSubmit(permittedKinds = all, pendingKinds = emptySet())
        )
        // After the app refresh task ran, only it is submitted again.
        assertEquals(
            listOf(BackgroundRefreshTaskKind.APP_REFRESH),
            resolveBackgroundRefreshKindsToSubmit(all, pendingKinds = setOf(BackgroundRefreshTaskKind.PROCESSING))
        )
        assertTrue(resolveBackgroundRefreshKindsToSubmit(all, pendingKinds = all).isEmpty())
        // An identifier missing from Info.plist is never submitted.
        assertEquals(
            listOf(BackgroundRefreshTaskKind.PROCESSING),
            resolveBackgroundRefreshKindsToSubmit(setOf(BackgroundRefreshTaskKind.PROCESSING), emptySet())
        )
        // The processing id is unchanged so requests from earlier versions are still handled.
        assertEquals(BackgroundRefreshTaskKind.PROCESSING, BackgroundRefreshTaskKind.fromIdentifier("com.valoser.futacha.refresh"))
        assertEquals(BackgroundRefreshTaskKind.APP_REFRESH, BackgroundRefreshTaskKind.fromIdentifier("com.valoser.futacha.appRefresh"))
    }

    @Test
    fun backgroundRefreshPlan_processingKeepsTheLongRun() {
        val plan = iosBackgroundRefreshPlanFor(BackgroundRefreshTaskKind.PROCESSING)
        assertEquals(9 * 60 * 1000L, plan.totalTimeoutMillis)
        assertEquals(IosBackgroundRefreshStage.SHARED_FEATURES, plan.stageOrder.first())
        assertEquals(40, plan.maxThreadsPerRun)
        assertEquals(90_000L, plan.autoSaveBudgetMillis)
        assertEquals(2, plan.maxAutoSavesPerRun)
        assertEquals(3 * 60 * 1000L, plan.sharedFeaturesBudgetMillis)
        // No stage cap of its own and the refresher's defaults, as before.
        assertEquals(null, plan.historyTimeoutMillis)
        assertEquals(null, plan.historyRunBudgetMillis)
        assertEquals(null, plan.threadFetchTimeoutMillis)
        assertEquals(null, plan.watchAlertTimeoutMillis)
        assertEquals(null, plan.archiveReportTimeoutMillis)
        assertEquals(null, plan.compatRefreshBudgetMillis)
        assertEquals(null, plan.compatUpdateBudgetMillis)
    }

    @Test
    fun backgroundRefreshPlan_shortRunsGiveEveryStageTimeWithinTheirWindow() {
        // BGAppRefresh and the Watch background task get about 30 s (H4-1).
        listOf(
            iosBackgroundRefreshPlanFor(BackgroundRefreshTaskKind.APP_REFRESH) to 25_000L,
            IOS_WATCH_REFRESH_PLAN to 20_000L
        ).forEach { (plan, window) ->
            assertTrue(plan.totalTimeoutMillis <= window)
            assertEquals(IosBackgroundRefreshStage.entries.toSet(), plan.stageOrder.toSet())
            assertEquals(
                listOf(IosBackgroundRefreshStage.HISTORY, IosBackgroundRefreshStage.WATCH_ALERTS),
                plan.stageOrder.take(2)
            )
            assertTrue(requireNotNull(plan.futachaStageLimitsMillis()) < plan.totalTimeoutMillis)
            assertTrue(requireNotNull(plan.compatStageLimitsMillis()) < plan.totalTimeoutMillis)
            val historyBudget = requireNotNull(plan.historyRunBudgetMillis)
            val fetchTimeout = requireNotNull(plan.threadFetchTimeoutMillis)
            // Fetches can start, and the refresher's own budget ends before its cap.
            assertTrue(historyBudget > fetchTimeout)
            assertTrue(requireNotNull(plan.historyTimeoutMillis) > maxOf(historyBudget, plan.autoSaveBudgetMillis))
            val compatBudget = requireNotNull(plan.compatRefreshBudgetMillis)
            assertTrue(
                requireNotNull(plan.compatUpdateBudgetMillis) + plan.compatExistenceBudgetMillis +
                    plan.compatWatchCheckBudgetMillis < compatBudget,
                "the watch-word phase keeps time after the capped phases"
            )
            assertTrue(plan.sharedFeaturesBudgetMillis > 0L)
        }
    }
}
