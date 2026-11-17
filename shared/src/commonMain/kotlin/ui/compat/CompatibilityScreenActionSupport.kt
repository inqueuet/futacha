package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal const val COMPAT_ACTION_TIMEOUT_MESSAGE = "タイムアウトしました"

/**
 * True when [this] is an internal `withTimeout` firing while the calling
 * coroutine is still active. That is a failure to report to the user; only a
 * cancellation of the caller itself must propagate (otherwise spinners and
 * "送信中…" states stay up with no message).
 */
internal suspend fun Throwable.isCompatInternalTimeout(): Boolean =
    this is TimeoutCancellationException && currentCoroutineContext().isActive

/** Converts an internal timeout into an ordinary failure; rethrows real cancellation. */
internal suspend fun CancellationException.compatTimeoutFailureOrThrow(
    message: String = COMPAT_ACTION_TIMEOUT_MESSAGE
): Throwable {
    if (!isCompatInternalTimeout()) throw this
    return IllegalStateException(message, this)
}

/** Keep recoverable I/O failures inside the screen that started the action. */
internal fun CoroutineScope.launchCompatScreenAction(
    tag: String,
    onFailure: (Throwable) -> Unit,
    block: suspend CoroutineScope.() -> Unit
): Job = launch {
    try {
        block()
    } catch (cancelled: CancellationException) {
        val timeout = cancelled.compatTimeoutFailureOrThrow()
        Logger.e(tag, "Screen action timed out", timeout)
        onFailure(timeout)
    } catch (failure: Throwable) {
        Logger.e(tag, "Screen action failed", failure)
        onFailure(failure)
    }
}
