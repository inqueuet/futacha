package com.valoser.futacha.shared.ai

import io.ktor.http.fromHttpToGmtDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.ceil
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.TimeSource

/** Shared by all screens/services using the connection store, including after cancellation. */
internal class OpenAiRequestQueue(
    private val nowMillis: () -> Long = monotonicMillis(),
    private val waitMillis: suspend (Long) -> Unit = { delay(it) },
    private val jitterMillis: () -> Long = { Random.nextLong(100, 351) }
) {
    private val mutex = Mutex()
    private var nextRequestAt = 0L

    suspend fun <T> execute(block: suspend () -> T): T = mutex.withLock {
        val deadline = nowMillis() + 120_000L
        var retries = 0
        while (true) {
            val wait = (nextRequestAt - nowMillis()).coerceAtLeast(0)
            if (wait > 120_000L || nowMillis() + wait > deadline) {
                throw OpenAiFailure("OpenAIの待機時間中です。時間をおいて再試行してください。")
            }
            if (wait > 0) waitMillis(wait)
            nextRequestAt = nowMillis() + 334L
            try {
                return@withLock block()
            } catch (failure: OpenAiFailure) {
                if (!failure.retryableRateLimit) throw failure
                val backoff = 1_000L shl retries.coerceAtMost(6)
                val pause = maxOf(failure.retryAfterMillis ?: 0L, backoff) + jitterMillis()
                nextRequestAt = maxOf(nextRequestAt, nowMillis() + pause)
                // Keep the shared cooldown even when retries are exhausted or the caller leaves.
                if (retries++ >= 3 || nextRequestAt > deadline) throw failure
            }
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }
}

private fun monotonicMillis(): () -> Long {
    val start = TimeSource.Monotonic.markNow()
    return { start.elapsedNow().inWholeMilliseconds }
}

internal fun openAiRetryAfterMillis(value: String?, now: Long = Clock.System.now().toEpochMilliseconds()): Long? {
    val text = value?.trim() ?: return null
    text.toDoubleOrNull()?.let {
        if (it.isFinite() && it >= 0) return ceil(it * 1000).toLong().coerceAtMost(Long.MAX_VALUE / 4)
    }
    return runCatching { (text.fromHttpToGmtDate().timestamp - now).coerceAtLeast(0) }.getOrNull()
}
