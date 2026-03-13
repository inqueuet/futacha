package com.valoser.futacha

import com.valoser.futacha.shared.network.NetworkException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
    fun shouldRetryBackgroundRefreshTimeout_retriesOnlyBeforeLimit() {
        assertTrue(shouldRetryBackgroundRefreshTimeout(runAttemptCount = 0, maxTimeoutRetries = 2))
        assertTrue(shouldRetryBackgroundRefreshTimeout(runAttemptCount = 1, maxTimeoutRetries = 2))
        assertFalse(shouldRetryBackgroundRefreshTimeout(runAttemptCount = 2, maxTimeoutRetries = 2))
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
}
