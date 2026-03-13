package com.valoser.futacha

import com.valoser.futacha.shared.network.NetworkException
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException

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
