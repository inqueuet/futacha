package com.valoser.futacha.shared.util

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/*
 * Ktor 3.6.1 can report a connection lost in the middle of a response body as
 * `CancellationException("Receive failed")` with the transport's exception
 * (ClosedByteChannelException -> IOException, a Darwin NSError, a timeout ...)
 * in its causes. The app treats a CancellationException as "the caller gave up"
 * and rethrows it, so a cut post / deletion / vote never reached the
 * "結果を確認できませんでした" message, and retries and error messages stopped
 * silently. The read must fail with the transport exception, as with Ktor 3.6.0.
 */

/**
 * The first cause of [error] that is not a cancellation, or null when the whole
 * chain is cancellation (a real cancel of the read).
 */
internal fun transportCauseOf(error: Throwable): Throwable? {
    var current: Throwable? = error
    var depth = 0
    while (current != null && depth < 16) {
        if (current !is CancellationException) return current
        val next = current.cause
        if (next === current) return null
        current = next
        depth++
    }
    return null
}

/**
 * Rethrows [cancelled] when it is a real cancellation of this coroutine or has no
 * transport failure behind it; otherwise the body broke off, and the transport's
 * own exception is thrown (Ktor's cancellation is kept as suppressed). Callers then
 * see exactly what Ktor 3.6.0 gave them: the same type, message and timeout kind.
 */
suspend fun rethrowResponseBodyCancellation(cancelled: CancellationException): Nothing {
    if (!currentCoroutineContext().isActive) throw cancelled
    val transport = transportCauseOf(cancelled) ?: throw cancelled
    if (transport !== cancelled) runCatching { transport.addSuppressed(cancelled) }
    throw transport
}

/**
 * Runs a read of a response body (or a call that reads it whole, like client.get);
 * a body cut off by the transport fails with the transport exception instead of
 * looking like the caller's cancellation. A real cancellation propagates unchanged.
 */
suspend inline fun <T> readResponseBody(crossinline block: suspend () -> T): T = try {
    block()
} catch (cancelled: CancellationException) {
    rethrowResponseBodyCancellation(cancelled)
}
