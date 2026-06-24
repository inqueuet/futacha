package com.valoser.futacha.shared.ui.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThreadScreenAdSupportTest {
    @Test
    fun shouldCreateThreadBannerAd_requiresStableVisibilityDelay() {
        assertFalse(
            shouldCreateThreadBannerAd(
                stableVisibleElapsedMillis = THREAD_SCREEN_BANNER_AD_STABLE_VISIBILITY_DELAY_MS - 1
            )
        )
        assertTrue(
            shouldCreateThreadBannerAd(
                stableVisibleElapsedMillis = THREAD_SCREEN_BANNER_AD_STABLE_VISIBILITY_DELAY_MS
            )
        )
        assertTrue(
            shouldCreateThreadBannerAd(
                stableVisibleElapsedMillis = THREAD_SCREEN_BANNER_AD_STABLE_VISIBILITY_DELAY_MS + 1
            )
        )
    }

    @Test
    fun threadBannerAdHeightMatchesStandardBannerHeight() {
        assertEquals(50, THREAD_SCREEN_BANNER_AD_HEIGHT_DP)
    }
}
