package com.valoser.futacha.shared

import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull

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

internal suspend fun awaitIosBackgroundRepositoryClose(
    closeJob: Job,
    timeoutMillis: Long
): Boolean {
    val completed = withTimeoutOrNull(timeoutMillis.coerceAtLeast(1L)) {
        closeJob.join()
        true
    } ?: false
    if (!completed) {
        closeJob.cancel()
    }
    return completed
}

/**
 * The value to store as the last-read change log version at iOS startup, or
 * null to keep what is stored. An explicit `-commonUsedVersion` launch argument
 * (UI tests) always wins; otherwise the NSUserDefaults value is migrated only
 * when nothing was stored yet. Previously a stored value made later test
 * launches ignore the argument.
 */
internal fun resolveCommonUsedVersionToStore(
    stored: String?,
    defaultsValue: String?,
    explicitArgument: String?
): String? = when {
    !explicitArgument.isNullOrBlank() -> explicitArgument.takeIf { it != stored }
    stored == null -> defaultsValue?.takeIf { it.isNotBlank() }
    else -> null
}
