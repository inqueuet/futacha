package com.valoser.futacha.shared.ai

import io.ktor.http.fromHttpToGmtDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.ceil
import kotlin.random.Random
import kotlin.time.Clock

/** Shared by all screens/services using the connection store, including after cancellation. */
internal class OpenAiRequestQueue(
    internal val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val waitMillis: suspend (Long) -> Unit = { delay(it) },
    private val jitterMillis: () -> Long = { Random.nextLong(100, 351) }
) {
    private val mutex = Mutex()
    private var nextRequestAt = 0L
    private var lastObservedAt = Long.MIN_VALUE

    /** Wall clock; a backwards jump shifts the pacing deadline instead of extending it. */
    private fun clock(): Long {
        val time = nowMillis()
        if (lastObservedAt != Long.MIN_VALUE && time < lastObservedAt && nextRequestAt > 0L) {
            nextRequestAt = (nextRequestAt + (time - lastObservedAt)).coerceAtLeast(0L)
        }
        lastObservedAt = time
        return time
    }

    internal suspend fun waitFor(millis: Long) = waitMillis(millis)

    suspend fun <T> execute(estimatedTokens: Int = 0, usage: OpenAiUsageTracker? = null,
        localLimitsOnly: Boolean = false, retryRateLimits: Boolean = true, block: suspend () -> T): T = mutex.withLock {
        val deadline = clock() + 120_000L
        var retries = 0
        while (true) {
            val wait = maxOf((nextRequestAt - clock()).coerceAtLeast(0), usage?.requiredWait(estimatedTokens, localLimitsOnly) ?: 0)
            if (wait > 120_000L || clock() + wait > deadline) {
                throw OpenAiFailure("OpenAIの待機時間中です。時間をおいて再試行してください。")
            }
            if (wait > 0) waitMillis(wait)
            nextRequestAt = clock() + 334L
            try {
                usage?.attempt(estimatedTokens)
                return@withLock block()
            } catch (failure: OpenAiFailure) {
                if (!retryRateLimits || !failure.retryableRateLimit) throw failure
                val backoff = 1_000L shl retries.coerceAtMost(6)
                val pause = maxOf(failure.retryAfterMillis ?: 0L, backoff) + jitterMillis()
                nextRequestAt = maxOf(nextRequestAt, clock() + pause)
                usage?.cooldown(pause, if (failure.retryAfterMillis != null) "server_retry" else "backoff")
                // Keep the shared cooldown even when retries are exhausted or the caller leaves.
                if (retries++ >= 3 || nextRequestAt > deadline) throw failure
            }
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }
}

internal fun openAiRetryAfterMillis(value: String?, now: Long = Clock.System.now().toEpochMilliseconds()): Long? {
    val text = value?.trim() ?: return null
    text.toDoubleOrNull()?.let {
        if (it.isFinite() && it >= 0) return ceil(it * 1000).toLong().coerceAtMost(Long.MAX_VALUE / 4)
    }
    return runCatching { (text.fromHttpToGmtDate().timestamp - now).coerceAtLeast(0) }.getOrNull()
}
