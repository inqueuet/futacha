package com.valoser.futacha

import com.valoser.futacha.shared.network.NetworkException
import kotlinx.coroutines.TimeoutCancellationException
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

internal fun shouldRetryBackgroundRefreshTimeout(
    runAttemptCount: Int,
    maxTimeoutRetries: Int = 2
): Boolean {
    return runAttemptCount < maxTimeoutRetries
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
