package com.valoser.futacha.shared.ui.image

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThumbnailGifAnimationSettingTest {
    @Test
    fun animationIsOnUnlessTurnedOff() {
        assertTrue(thumbnailGifAnimationEnabled(null))
        assertTrue(thumbnailGifAnimationEnabled("ON"))
        assertFalse(thumbnailGifAnimationEnabled("OFF"))
        assertFalse(thumbnailGifAnimationEnabled(" off "))
    }

    @Test
    fun theAnimationIsPartOfTheCacheKey() {
        val plan = HighQualityThumbnailPlan("https://may.2chan.net/b/src/1.gif", false, 100, 100, false)
        assertTrue(highQualityThumbnailMemoryKey(plan) != highQualityThumbnailMemoryKey(plan.copy(allowAnimation = true)))
    }
}
