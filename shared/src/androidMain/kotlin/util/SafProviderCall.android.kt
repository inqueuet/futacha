package com.valoser.futacha.shared.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resumeWithException

// Some Binder providers ignore thread interruption. Keep their work outside
// the waiting caller. Each call gets its own (cached) thread, so a provider
// that never answers holds only the threads of the calls made to it and
// cannot use up a shared pool that every other SAF operation waits on.
// Threads still held by unanswered calls are capped: past the cap a new call
// fails at once instead of adding threads without bound (G-2).
internal const val MAX_SAF_PROVIDER_CALLS_IN_FLIGHT = 64
private val inFlightSafProviderCalls = AtomicInteger()
private val safProviderThreadCount = AtomicInteger()
private val safProviderCalls = CoroutineScope(
    SupervisorJob() + ThreadPoolExecutor(
        0, Int.MAX_VALUE, 30L, TimeUnit.SECONDS, SynchronousQueue()
    ) { runnable ->
        Thread(runnable, "futacha-saf-${safProviderThreadCount.incrementAndGet()}").apply { isDaemon = true }
    }.asCoroutineDispatcher()
)

/**
 * Runs [block] on a dedicated SAF worker and waits for it without being held
 * by it: cancelling the caller interrupts the worker and returns at once, and
 * a [Closeable] the abandoned worker still returns is closed. The caller's
 * save batch listing ([SafTreeIndex]) is carried over, so lookups inside the
 * block reuse and update the batch's folder listings.
 */
internal suspend fun <T> awaitSafProviderCall(block: suspend () -> T): T =
    suspendCancellableCoroutine { continuation ->
        if (inFlightSafProviderCalls.incrementAndGet() > MAX_SAF_PROVIDER_CALLS_IN_FLIGHT) {
            inFlightSafProviderCalls.decrementAndGet()
            throw SaveProviderTimeoutException(
                "保存先プロバイダが応答しない処理が多すぎます。しばらく待つか、別の保存先を試してください。"
            )
        }
        val callerIndex = continuation.context[SafTreeIndex]
        val task = safProviderCalls.launch(callerIndex ?: EmptyCoroutineContext) {
            try {
                val result = block()
                continuation.resume(result, onCancellation = { _, value, _ ->
                    if (value is Closeable) safProviderCalls.launch { runCatching { value.close() } }
                })
            } catch (failure: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(failure)
            }
        }
        // Released only when the worker really returns, not when the caller gives up.
        task.invokeOnCompletion { inFlightSafProviderCalls.decrementAndGet() }
        continuation.invokeOnCancellation { task.cancel() }
    }

/** SAF calls whose worker has not returned yet (including abandoned, hung ones). */
internal fun safProviderCallsInFlight(): Int = inFlightSafProviderCalls.get()
