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

/**
 * Catalog cells walk their preview candidates (low-quality → thumbnail →
 * original) only when the current one is missing (404/410), like the inline
 * thumbnail policy above.  Any transient failure used to download the full
 * original for every failed cell.  The tutorial board's example.com URLs can
 * never load, so they still fall through to the packaged fixture drawable.
 */
internal fun shouldAdvanceCompatCatalogPreviewCandidate(
    failedUrl: String?,
    failure: Throwable?
): Boolean =
    com.valoser.futacha.shared.ui.image.isMissingImage(failure) ||
        failedUrl.orEmpty().contains("example.com", ignoreCase = true)
