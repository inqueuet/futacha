package com.valoser.futacha.shared.ui.compat

import kotlin.test.Test
import kotlin.test.assertEquals

class CompatThumbnailRetryTest {
    @Test
    fun directApuSourceSharesTheDefaultCacheKeyAcrossThreadAndViewer() {
        val source = "https://example.test/source.png"
        assertEquals(
            null,
            compatThumbnailMemoryCacheKey(source, usesDirectApuSource = true, completedRetries = 0, reloadToken = 0L)
        )
        assertEquals(
            "$source#compat-42",
            compatThumbnailMemoryCacheKey(source, usesDirectApuSource = true, completedRetries = 0, reloadToken = 42L)
        )
        assertEquals(
            "$source#compat-auto-1",
            compatThumbnailMemoryCacheKey(source, usesDirectApuSource = false, completedRetries = 1, reloadToken = 0L)
        )
    }

    @Test
    fun retriesTwiceBeforeFallingBackToTheOriginalImage() {
        assertEquals(
            CompatThumbnailFailureAction.RETRY_CURRENT,
            resolveCompatThumbnailFailureAction(completedRetries = 0, hasOriginalFallback = true)
        )
        assertEquals(
            CompatThumbnailFailureAction.RETRY_CURRENT,
            resolveCompatThumbnailFailureAction(completedRetries = 1, hasOriginalFallback = true)
        )
        assertEquals(
            CompatThumbnailFailureAction.FALLBACK_TO_ORIGINAL,
            resolveCompatThumbnailFailureAction(completedRetries = 2, hasOriginalFallback = true)
        )
        assertEquals(500L, compatThumbnailRetryDelayMillis(0))
        assertEquals(1_500L, compatThumbnailRetryDelayMillis(1))
    }

    @Test
    fun originalImageFailureBecomesTerminalAfterTheBoundedRetries() {
        assertEquals(
            CompatThumbnailFailureAction.SHOW_TERMINAL_ERROR,
            resolveCompatThumbnailFailureAction(completedRetries = 2, hasOriginalFallback = false)
        )
    }
}
