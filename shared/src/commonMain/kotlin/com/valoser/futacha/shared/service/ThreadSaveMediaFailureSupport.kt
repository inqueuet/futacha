package com.valoser.futacha.shared.service

internal class ThreadSaveMediaDownloadFailure(
    message: String,
    val retryable: Boolean,
    cause: Throwable? = null
) : Exception(message, cause)

internal fun isThreadSaveMediaDownloadRetryable(error: Throwable?): Boolean {
    var current = error
    while (current != null) {
        if (current is ThreadSaveMediaDownloadFailure) {
            return current.retryable
        }
        current = current.cause
    }
    return true
}

internal fun isThreadSaveMediaHttpStatusRetryable(statusCode: Int): Boolean {
    return when (statusCode) {
        408, 425, 429 -> true
        in 400..499 -> false
        else -> true
    }
}
