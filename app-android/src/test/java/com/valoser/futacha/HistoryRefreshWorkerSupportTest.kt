package com.valoser.futacha

import com.valoser.futacha.shared.network.NetworkException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class HistoryRefreshWorkerSupportTest {
    @Test
    fun isRetriableBackgroundRefreshError_matchesExpectedErrorTypes() {
        val timeoutError = runCatching {
            runBlocking {
                withTimeout(1) {
                    delay(10)
                }
            }
        }.exceptionOrNull() ?: error("timeout expected")

        assertTrue(isRetriableBackgroundRefreshError(IOException("io")))
        assertTrue(isRetriableBackgroundRefreshError(NetworkException("network")))
        assertTrue(isRetriableBackgroundRefreshError(timeoutError))
        assertFalse(isRetriableBackgroundRefreshError(IllegalArgumentException("bad input")))
    }

    @Test
    fun shouldRetryBackgroundSettingRead_retriesOnlyBeforeLimit() {
        assertTrue(shouldRetryBackgroundSettingRead(runAttemptCount = 0, maxSettingReadRetries = 3))
        assertTrue(shouldRetryBackgroundSettingRead(runAttemptCount = 2, maxSettingReadRetries = 3))
        assertFalse(shouldRetryBackgroundSettingRead(runAttemptCount = 3, maxSettingReadRetries = 3))
    }

    @Test
    fun shouldRetryBackgroundRefreshFailure_requiresRetriableErrorAndRemainingAttempts() {
        assertTrue(
            shouldRetryBackgroundRefreshFailure(
                error = IOException("io"),
                runAttemptCount = 1,
                maxRetryAttempts = 3
            )
        )
        assertFalse(
            shouldRetryBackgroundRefreshFailure(
                error = IllegalStateException("fatal"),
                runAttemptCount = 1,
                maxRetryAttempts = 3
            )
        )
        assertFalse(
            shouldRetryBackgroundRefreshFailure(
                error = IOException("io"),
                runAttemptCount = 3,
                maxRetryAttempts = 3
            )
        )
    }

    @Test
    fun shouldEnqueueImmediateBackgroundRefresh_requiresEnabledState() {
        assertFalse(
            shouldEnqueueImmediateBackgroundRefresh(
                enabled = false,
                hasObservedBackgroundToggle = false,
                lastImmediateEnqueueEpochMillis = 0L,
                nowEpochMillis = 1_000L
            )
        )
    }

    @Test
    fun shouldEnqueueImmediateBackgroundRefresh_runsWhenNeverEnqueued() {
        assertTrue(
            shouldEnqueueImmediateBackgroundRefresh(
                enabled = true,
                hasObservedBackgroundToggle = false,
                lastImmediateEnqueueEpochMillis = 0L,
                nowEpochMillis = 1_000L
            )
        )
    }

    @Test
    fun shouldEnqueueImmediateBackgroundRefresh_throttlesRecentInitialEnqueue() {
        assertFalse(
            shouldEnqueueImmediateBackgroundRefresh(
                enabled = true,
                hasObservedBackgroundToggle = false,
                lastImmediateEnqueueEpochMillis = 1_000L,
                nowEpochMillis = 1_000L + BACKGROUND_IMMEDIATE_REFRESH_MIN_INTERVAL_MILLIS - 1L
            )
        )
        assertTrue(
            shouldEnqueueImmediateBackgroundRefresh(
                enabled = true,
                hasObservedBackgroundToggle = false,
                lastImmediateEnqueueEpochMillis = 1_000L,
                nowEpochMillis = 1_000L + BACKGROUND_IMMEDIATE_REFRESH_MIN_INTERVAL_MILLIS
            )
        )
    }

    @Test
    fun shouldEnqueueImmediateBackgroundRefresh_runsAfterObservedToggle() {
        assertTrue(
            shouldEnqueueImmediateBackgroundRefresh(
                enabled = true,
                hasObservedBackgroundToggle = true,
                lastImmediateEnqueueEpochMillis = 1_000L,
                nowEpochMillis = 1_001L
            )
        )
    }

    @Test
    fun shouldEnqueueImmediateBackgroundRefresh_recoversFromClockMovingBackward() {
        assertTrue(
            shouldEnqueueImmediateBackgroundRefresh(
                enabled = true,
                hasObservedBackgroundToggle = false,
                lastImmediateEnqueueEpochMillis = 2_000L,
                nowEpochMillis = 1_000L
            )
        )
    }

    @Test
    fun hasHistoryFlushFailure_detectsPositiveFlushStageCountOnly() {
        assertTrue(HistoryRefreshWorker.hasHistoryFlushFailure(mapOf("history_flush" to 1)))
        assertFalse(HistoryRefreshWorker.hasHistoryFlushFailure(mapOf("history_flush" to 0)))
        assertFalse(HistoryRefreshWorker.hasHistoryFlushFailure(mapOf("thread_refresh" to 3)))
    }

    @Test
    fun awaitBackgroundNetworkServices_reportsReadyFailedAndTimeout() = runBlocking {
        assertEquals(
            NetworkServicesReadiness.READY,
            awaitBackgroundNetworkServices(MutableStateFlow(true), MutableStateFlow(null), 1_000L)
        )

        val lateReady = MutableStateFlow(false)
        launch {
            delay(50L)
            lateReady.value = true
        }
        assertEquals(
            NetworkServicesReadiness.READY,
            awaitBackgroundNetworkServices(lateReady, MutableStateFlow(null), 5_000L)
        )

        assertEquals(
            NetworkServicesReadiness.FAILED,
            awaitBackgroundNetworkServices(MutableStateFlow(false), MutableStateFlow("init failed"), 1_000L)
        )
        assertEquals(
            NetworkServicesReadiness.TIMED_OUT,
            awaitBackgroundNetworkServices(MutableStateFlow(false), MutableStateFlow(null), 50L)
        )
    }
}
