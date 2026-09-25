@file:OptIn(coil3.annotation.ExperimentalCoilApi::class)
package com.valoser.futacha.shared.ui.compat

import coil3.network.HttpException
import coil3.network.NetworkResponse
import kotlin.test.*

class CompatThumbnailRetryTest {
    @Test fun directApuSourceSharesTheDefaultCacheKeyAcrossThreadAndViewer() {
        val source = "https://example.test/source.png"
        assertNull(compatThumbnailMemoryCacheKey(source, true, 0, 0L))
        assertEquals("$source#compat-42", compatThumbnailMemoryCacheKey(source, true, 0, 42L))
        assertEquals("$source#compat-auto-1", compatThumbnailMemoryCacheKey(source, false, 1, 0L))
    }
    @Test fun missingThumbnailFallsBackWithoutRepeatingTheSameUrl() {
        for (status in listOf(404, 410)) {
            assertEquals(CompatThumbnailFailureAction.FALLBACK_TO_ORIGINAL,
                resolveCompatThumbnailFailureAction(0, true, HttpException(NetworkResponse(code = status))))
        }
    }
    @Test fun transportFailuresDoNotMultiplyRetriesOrDownloadTheOriginal() {
        for (status in listOf(401, 403, 429, 500, 503)) {
            assertEquals(CompatThumbnailFailureAction.SHOW_TERMINAL_ERROR,
                resolveCompatThumbnailFailureAction(0, true, HttpException(NetworkResponse(code = status))))
        }
        assertEquals(CompatThumbnailFailureAction.SHOW_TERMINAL_ERROR,
            resolveCompatThumbnailFailureAction(0, true, IllegalStateException("transport already exhausted")))
    }
    @Test fun catalogCellFallsBackToOriginalOnlyForMissingPreview() {
        val thumb = "https://img.2chan.net/b/thumb/1s.jpg"
        for (status in listOf(404, 410)) {
            assertTrue(shouldAdvanceCompatCatalogPreviewCandidate(thumb, HttpException(NetworkResponse(code = status))))
        }
        for (status in listOf(403, 429, 500, 503)) {
            assertFalse(shouldAdvanceCompatCatalogPreviewCandidate(thumb, HttpException(NetworkResponse(code = status))))
        }
        assertFalse(shouldAdvanceCompatCatalogPreviewCandidate(thumb, IllegalStateException("timeout")))
        assertFalse(shouldAdvanceCompatCatalogPreviewCandidate(thumb, null))
        // The tutorial fixture's unreachable host still reaches the packaged drawable.
        assertTrue(shouldAdvanceCompatCatalogPreviewCandidate("https://example.com/thumb/1s.jpg", IllegalStateException("dns")))
    }
    @Test fun originalImageFailureBecomesTerminalAfterTheBoundedRetries() {
        assertEquals(CompatThumbnailFailureAction.SHOW_TERMINAL_ERROR,
            resolveCompatThumbnailFailureAction(2, false, HttpException(NetworkResponse(code = 404))))
    }
}
