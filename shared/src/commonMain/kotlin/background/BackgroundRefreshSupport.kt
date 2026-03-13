package com.valoser.futacha.shared.background

internal sealed interface BackgroundRefreshScheduleAction {
    data object SkipDisabled : BackgroundRefreshScheduleAction
    data object SkipPending : BackgroundRefreshScheduleAction
    data class DelayRetry(val delayMillis: Long) : BackgroundRefreshScheduleAction
    data object SubmitNow : BackgroundRefreshScheduleAction
}

internal data class BackgroundRefreshSubmitFailureState(
    val nextScheduleAllowedAtMillis: Long,
    val nextRetryAttempts: Int,
    val shouldScheduleRetry: Boolean,
    val retryDelayMillis: Long
)

internal fun resolveBackgroundRefreshScheduleAction(
    enabled: Boolean,
    hasPendingRefreshRequest: Boolean,
    nextScheduleAllowedAtMillis: Long,
    nowEpochMillis: Long
): BackgroundRefreshScheduleAction {
    if (!enabled) {
        return BackgroundRefreshScheduleAction.SkipDisabled
    }
    if (hasPendingRefreshRequest) {
        return BackgroundRefreshScheduleAction.SkipPending
    }

    val remainingBackoff = nextScheduleAllowedAtMillis - nowEpochMillis
    return if (remainingBackoff > 0L) {
        BackgroundRefreshScheduleAction.DelayRetry(remainingBackoff)
    } else {
        BackgroundRefreshScheduleAction.SubmitNow
    }
}

internal fun resolveBackgroundRefreshSubmitFailureState(
    failureNowEpochMillis: Long,
    currentRetryAttempts: Int,
    scheduleBackoffMillis: Long,
    maxRetryAttempts: Int
): BackgroundRefreshSubmitFailureState {
    val nextRetryAttempts = currentRetryAttempts + 1
    return BackgroundRefreshSubmitFailureState(
        nextScheduleAllowedAtMillis = failureNowEpochMillis + scheduleBackoffMillis,
        nextRetryAttempts = nextRetryAttempts,
        shouldScheduleRetry = nextRetryAttempts <= maxRetryAttempts,
        retryDelayMillis = scheduleBackoffMillis
    )
}

internal fun shouldScheduleBackgroundRefreshRetry(
    enabled: Boolean,
    retryAttempts: Int,
    maxRetryAttempts: Int,
    hasActiveRetryJob: Boolean
): Boolean {
    if (!enabled) {
        return false
    }
    if (retryAttempts > maxRetryAttempts) {
        return false
    }
    return !hasActiveRetryJob
}

internal fun normalizeBackgroundRefreshRetryDelay(delayMillis: Long): Long =
    delayMillis.coerceAtLeast(1L)
