package com.valoser.futacha.shared.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException

/**
 * The whole preparation (the agent, the form GET, the official scripts and the WebView, each bounded
 * by its own 30 s, plus the one retry). One shared 30 s cut a slow line that was still progressing;
 * 75 s is about what the stages allowed before they shared a budget (L99, the decision).
 */
internal const val POSTING_PREPARATION_TIMEOUT_MILLIS = 75_000L

/** Only used before a posting request is built. A fresh attempt reacquires the form and scripts together. */
internal suspend fun <T : Any> withPostingPreparationReadRetry(
    timeoutMillis: Long = POSTING_PREPARATION_TIMEOUT_MILLIS,
    retryDelayMillis: Long = 150L,
    prepare: suspend () -> T
): T {
    var firstFailure: Exception? = null
    return withTimeoutOrNull(timeoutMillis) {
        try {
            prepare()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (!postingPreparationHasReadFailure(failure)) throw failure
            firstFailure = failure
            delay(retryDelayMillis)
            prepare()
        }
    } ?: throw firstFailure.let { first ->
        // A retry that runs out of time keeps why the first attempt failed (L99).
        if (first == null) NetworkException("投稿準備が時間内に完了しなかったため送信していません")
        else NetworkException("投稿準備が時間内に完了しなかったため送信していません（最初の失敗: ${first.message}）", cause = first)
    }
}

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
