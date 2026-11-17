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
import kotlin.time.TimeSource

/**
 * One budget around the entire fetch, including consuming its response body.
 *
 * Every permitted request reaches the transport even when the connectivity snapshot says
 * offline (it can lag a Wi-Fi/mobile handover). A connection failure while [isOnline] still
 * reports offline does not spend the retry budget: it waits up to [offlineGraceMillis] for the
 * network to return and otherwise fails at once. Under memory pressure ([skipOfflineWait]) it
 * fails at once, since the waiting fetch holds a scarce permit that cached images queue for.
 */
internal class RetryingImageRequestStrategy(
    private val isOnline: () -> Boolean = { true },
    private val skipOfflineWait: () -> Boolean = { false },
    private val offlineGraceMillis: Long = OFFLINE_GRACE_MILLIS,
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
                        // A cache-only miss is an answer; waiting cannot change it.
                        is ImageNotCachedException -> false
                        is HttpException -> failure.response.code in setOf(500, 502, 503, 504)
                        else -> isRetryableImageConnectionFailure(failure)
                    }
                    if (!retryable || retries >= 2) throw failure
                    if (failure !is HttpException && !awaitOnline()) throw failure
                    waitBeforeRetry(retries++, failure)
                }
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }

    /** True when the network is (back) online; false while it stays offline for the grace period. */
    private suspend fun awaitOnline(): Boolean {
        if (isOnline()) return true
        if (skipOfflineWait()) return false
        val started = TimeSource.Monotonic.markNow()
        while (started.elapsedNow().inWholeMilliseconds < offlineGraceMillis) {
            delay(OFFLINE_POLL_MILLIS.coerceAtMost(offlineGraceMillis))
            if (isOnline()) return true
        }
        return false
    }

    internal companion object {
        const val OFFLINE_GRACE_MILLIS = 1_500L
        const val OFFLINE_POLL_MILLIS = 100L
    }
}

/** The retrying strategy wired to the platform connectivity snapshot and the memory-pressure gate. */
internal fun createConnectivityAwareImageRequestStrategy(
    platformContext: coil3.PlatformContext,
    pressureGate: AdaptiveImageRequestGate,
): RetryingImageRequestStrategy {
    val connectivity = createImageConnectivityChecker(platformContext)
    return RetryingImageRequestStrategy(
        isOnline = connectivity::isOnline,
        skipOfflineWait = { pressureGate.policy().level != ImageMemoryPressureLevel.NORMAL },
    )
}

/** Android: the system's active-network snapshot. iOS: the current Network path. Desktop: always online. */
internal expect fun createImageConnectivityChecker(platformContext: coil3.PlatformContext): coil3.network.ConnectivityChecker
