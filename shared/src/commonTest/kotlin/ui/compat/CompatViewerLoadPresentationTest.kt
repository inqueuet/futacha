package com.valoser.futacha.shared.ui.compat

import kotlin.test.Test
import kotlin.test.assertEquals

class CompatViewerLoadPresentationTest {
    @Test
    fun sourceFailureWaitsForThumbnailBeforeShowingTerminalError() {
        assertEquals(
            CompatViewerLoadPresentation.LOADING,
            resolveCompatViewerLoadPresentation(
                hasSource = true,
                sourceReady = false,
                sourceFailed = true,
                hasThumbnailFallback = true,
                thumbnailReady = false,
                thumbnailFailed = false
            )
        )
        assertEquals(
            CompatViewerLoadPresentation.THUMBNAIL_FALLBACK,
            resolveCompatViewerLoadPresentation(true, false, true, true, true, false)
        )
        assertEquals(
            CompatViewerLoadPresentation.ERROR,
            resolveCompatViewerLoadPresentation(true, false, true, true, false, true)
        )
        assertEquals(
            CompatViewerLoadPresentation.ERROR,
            resolveCompatViewerLoadPresentation(true, false, true, false, false, false)
        )
    }
}
