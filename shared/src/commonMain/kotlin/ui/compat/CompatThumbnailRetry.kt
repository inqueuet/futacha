package com.valoser.futacha.shared.ui.compat

internal const val COMPAT_THUMBNAIL_LOADING_INDICATOR_DELAY_MILLIS = 180L

internal const val COMPAT_INLINE_THUMBNAIL_MAX_RETRIES = 2

private val COMPAT_INLINE_THUMBNAIL_RETRY_DELAYS_MILLIS = longArrayOf(500L, 1_500L)

internal enum class CompatThumbnailFailureAction {
    RETRY_CURRENT,
    FALLBACK_TO_ORIGINAL,
    SHOW_TERMINAL_ERROR
}

internal fun compatThumbnailRetryDelayMillis(completedRetries: Int): Long =
    COMPAT_INLINE_THUMBNAIL_RETRY_DELAYS_MILLIS[
        completedRetries.coerceIn(0, COMPAT_INLINE_THUMBNAIL_RETRY_DELAYS_MILLIS.lastIndex)
    ]

internal fun compatThumbnailMemoryCacheKey(
    previewUrl: String,
    usesDirectApuSource: Boolean,
    completedRetries: Int,
    reloadToken: Long
): String? {
    if (usesDirectApuSource && reloadToken == 0L) return null
    val suffix = reloadToken.takeIf { it != 0L } ?: "auto-$completedRetries"
    return "$previewUrl#compat-$suffix"
}

internal fun resolveCompatThumbnailFailureAction(
    completedRetries: Int,
    hasOriginalFallback: Boolean,
    failure: Throwable? = null
): CompatThumbnailFailureAction =
    if (hasOriginalFallback && com.valoser.futacha.shared.ui.image.isMissingImage(failure)) {
        CompatThumbnailFailureAction.FALLBACK_TO_ORIGINAL
    } else {
        // HTTP retries are owned by the transport. UI retries multiplied them
        // and could retry a permanently cached error without reaching the server.
        CompatThumbnailFailureAction.SHOW_TERMINAL_ERROR
    }
