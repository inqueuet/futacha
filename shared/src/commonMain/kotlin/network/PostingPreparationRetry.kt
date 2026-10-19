package com.valoser.futacha.shared.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException

/** Only used before a posting request is built. A fresh attempt reacquires the form and scripts together. */
internal suspend fun <T : Any> withPostingPreparationReadRetry(
    timeoutMillis: Long = 30_000L,
    retryDelayMillis: Long = 150L,
    prepare: suspend () -> T
): T = withTimeoutOrNull(timeoutMillis) {
    try {
        prepare()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        if (!postingPreparationHasReadFailure(failure)) throw failure
        delay(retryDelayMillis)
        prepare()
    }
} ?: throw NetworkException("投稿準備が時間内に完了しなかったため送信していません")

internal fun postingPreparationHasReadFailure(failure: Throwable): Boolean {
    var cause: Throwable? = failure
    var readFailure = false
    repeat(8) {
        val current = cause ?: return readFailure
        if (current is CancellationException) return false
        if (current is IOException) readFailure = true
        cause = current.cause.takeUnless { it === current }
    }
    return readFailure
}
