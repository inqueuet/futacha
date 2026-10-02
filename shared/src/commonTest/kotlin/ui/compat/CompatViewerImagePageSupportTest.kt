package com.valoser.futacha.shared.ui.compat

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CompatViewerImagePageSupportTest {
    @Test
    fun imageWithoutDistinctThumbnailHasNoSecondRequest() {
        val source = "https://may.2chan.net/b/src/1.jpg"
        assertNull(compatViewerThumbnailFallbackUrl(null, source))
        assertNull(compatViewerThumbnailFallbackUrl("", source))
        assertNull(compatViewerThumbnailFallbackUrl("  ", source))
        assertNull(compatViewerThumbnailFallbackUrl(source, source))
        assertEquals(
            "https://may.2chan.net/b/thumb/1s.jpg",
            compatViewerThumbnailFallbackUrl("https://may.2chan.net/b/thumb/1s.jpg", source)
        )
    }

    @Test
    fun zoomStateDescriptionMatchesExistingFormat() {
        assertEquals("拡大率 100% 位置 0,0", compatViewerZoomStateDescription(CompatViewerTransform()))
        assertEquals(
            "拡大率 600% 位置 -12,34",
            compatViewerZoomStateDescription(CompatViewerTransform(6f, Offset(-12.7f, 34.2f)))
        )
    }
}
