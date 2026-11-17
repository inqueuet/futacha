package com.valoser.futacha.shared.analytics

internal inline fun <T> runAnalyticsSdkCatching(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (error: Exception) {
        Result.failure(error)
    }
}
