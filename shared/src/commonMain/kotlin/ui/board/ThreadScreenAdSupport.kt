package com.valoser.futacha.shared.ui.board

internal const val THREAD_SCREEN_BANNER_AD_STABLE_VISIBILITY_DELAY_MS = 1_200L
internal const val THREAD_SCREEN_BANNER_AD_HEIGHT_DP = 50

internal fun shouldCreateThreadBannerAd(
    stableVisibleElapsedMillis: Long,
    requiredDelayMillis: Long = THREAD_SCREEN_BANNER_AD_STABLE_VISIBILITY_DELAY_MS
): Boolean {
    return stableVisibleElapsedMillis >= requiredDelayMillis.coerceAtLeast(0L)
}
