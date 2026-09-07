@file:OptIn(coil3.annotation.ExperimentalCoilApi::class)
package com.valoser.futacha.shared.ui.image

import coil3.fetch.FetchResult
import coil3.network.ConcurrentRequestStrategy
import coil3.network.DeDupeConcurrentRequestStrategy
import coil3.network.HttpException
import com.valoser.futacha.shared.network.isRetryableImageConnectionFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.random.Random

/** One budget around the entire fetch, including consuming its response body. */
internal class RetryingImageRequestStrategy(
    private val waitBeforeRetry: suspend (Int, Throwable) -> Unit = { retry, failure ->
        val serverDelay = (failure as? HttpException)?.response?.headers?.get("Retry-After")
            ?.toLongOrNull()?.coerceIn(0, 90)?.times(1000)
        delay(maxOf(serverDelay ?: 0, (1000L shl retry) + Random.nextLong(1000)))
    },
) : ConcurrentRequestStrategy {
    private val dedupe = DeDupeConcurrentRequestStrategy()

    override suspend fun apply(key: String, block: suspend () -> FetchResult): FetchResult =
        dedupe.apply(key) {
            var retries = 0
            while (true) {
                try {
                    return@apply block()
                } catch (failure: Exception) {
                    val retryable = failure !is CancellationException && when (failure) {
                        is HttpException -> failure.response.code in setOf(500, 502, 503, 504)
                        else -> isRetryableImageConnectionFailure(failure)
                    }
                    if (!retryable || retries >= 2) throw failure
                    waitBeforeRetry(retries++, failure)
                }
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }
}
