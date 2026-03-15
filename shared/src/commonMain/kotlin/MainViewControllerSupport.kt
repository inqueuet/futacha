package com.valoser.futacha.shared

internal data class IosBackgroundRefreshFlowRetryState(
    val shouldRetry: Boolean,
    val backoffMillis: Long?
)

internal fun calculateIosBackgroundRefreshFlowBackoffMillis(attempt: Long): Long =
    (1_000L shl attempt.toInt().coerceAtMost(5)).coerceAtMost(30_000L)

internal fun resolveIosBackgroundRefreshFlowRetryState(
    attempt: Long,
    maxRetries: Long
): IosBackgroundRefreshFlowRetryState {
    val shouldRetry = attempt < maxRetries
    return IosBackgroundRefreshFlowRetryState(
        shouldRetry = shouldRetry,
        backoffMillis = if (shouldRetry) calculateIosBackgroundRefreshFlowBackoffMillis(attempt) else null
    )
}
