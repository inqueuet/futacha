package com.valoser.futacha.shared.util

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/** Result wrapper for suspend work which never converts structured cancellation into failure. */
suspend inline fun <T> runSuspendCatchingPreservingCancellation(
    crossinline block: suspend () -> T
): Result<T> = try {
    Result.success(block())
} catch (timeout: TimeoutCancellationException) {
    // A child withTimeout expiration is an operation failure while the
    // caller remains active. Parent cancellation must still propagate.
    if (!currentCoroutineContext().isActive) throw timeout
    Result.failure(timeout)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}
