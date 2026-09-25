package com.valoser.futacha

import com.valoser.futacha.shared.network.NetworkException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

internal const val BACKGROUND_IMMEDIATE_REFRESH_MIN_INTERVAL_MILLIS = 15L * 60L * 1000L

internal fun isRetriableBackgroundRefreshError(error: Throwable): Boolean {
    return when (error) {
        is IOException,
        is NetworkException,
        is TimeoutCancellationException -> true
        else -> false
    }
}

internal fun shouldRetryBackgroundSettingRead(
    runAttemptCount: Int,
    maxSettingReadRetries: Int = 3
): Boolean {
    return runAttemptCount < maxSettingReadRetries
}

internal fun shouldRetryBackgroundRefreshFailure(
    error: Throwable,
    runAttemptCount: Int,
    maxRetryAttempts: Int = 3
): Boolean {
    return isRetriableBackgroundRefreshError(error) && runAttemptCount < maxRetryAttempts
}

internal fun shouldEnqueueImmediateBackgroundRefresh(
    enabled: Boolean,
    hasObservedBackgroundToggle: Boolean,
    lastImmediateEnqueueEpochMillis: Long,
    nowEpochMillis: Long,
    minIntervalMillis: Long = BACKGROUND_IMMEDIATE_REFRESH_MIN_INTERVAL_MILLIS
): Boolean {
    if (!enabled) return false
    if (hasObservedBackgroundToggle) return true
    if (lastImmediateEnqueueEpochMillis <= 0L) return true
    if (minIntervalMillis <= 0L) return true
    val elapsedMillis = nowEpochMillis - lastImmediateEnqueueEpochMillis
    return elapsedMillis < 0L || elapsedMillis >= minIntervalMillis
}

internal enum class NetworkServicesReadiness { READY, FAILED, TIMED_OUT }

/**
 * WorkManager can start the worker in a fresh process before the application
 * finished creating its HTTP client and repositories asynchronously; reading
 * them earlier throws. Wait for either outcome, bounded by [timeoutMillis].
 */
internal suspend fun awaitBackgroundNetworkServices(
    ready: StateFlow<Boolean>,
    error: StateFlow<String?>,
    timeoutMillis: Long
): NetworkServicesReadiness {
    val outcome = withTimeoutOrNull(timeoutMillis) {
        combine(ready, error) { isReady, failure -> isReady to failure }
            .first { (isReady, failure) -> isReady || failure != null }
    } ?: return NetworkServicesReadiness.TIMED_OUT
    return if (outcome.first) NetworkServicesReadiness.READY else NetworkServicesReadiness.FAILED
}
