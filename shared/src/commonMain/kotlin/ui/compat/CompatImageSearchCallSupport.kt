package com.valoser.futacha.shared.ui.compat

import kotlinx.coroutines.TimeoutCancellationException

internal class CompatImageSearchTimeoutException(cause: Throwable) :
    IllegalStateException("image search timeout", cause)

/**
 * Runs an image-search request for a UI caller that shows a "送信中…" status.
 *
 * The search helpers bound themselves with withTimeout, whose
 * TimeoutCancellationException is a CancellationException: they rethrow it,
 * and a launched UI coroutine then ends silently with the status stuck. Turn
 * that internal timeout into an ordinary failure. Cancellation of the caller
 * itself is a plain CancellationException and still propagates.
 */
internal suspend fun <T> runCompatImageSearchCall(block: suspend () -> Result<T>): Result<T> = try {
    block()
} catch (timeout: TimeoutCancellationException) {
    Result.failure(CompatImageSearchTimeoutException(timeout))
}
