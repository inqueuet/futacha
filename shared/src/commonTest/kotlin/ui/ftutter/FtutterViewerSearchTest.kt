package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FtutterViewerSearchTest {
    @Test fun onlyTheKnownServicesTopAddressIsAccepted() {
        assertEquals(FtutterViewerSearchEngine.ASCII2D, ftutterViewerSearchEngineFor("https://ascii2d.net/"))
        assertEquals(FtutterViewerSearchEngine.ASCII2D, ftutterViewerSearchEngineFor(" https://ascii2d.net "))
        // 原作で断られたアドレス。
        assertNull(ftutterViewerSearchEngineFor("https://ascii2d.net/search/url/"))
        assertNull(ftutterViewerSearchEngineFor("https://example.com/s?u="))
        assertNull(ftutterViewerSearchEngineFor(""))
    }

    @Test fun addedEnginesAreSavedByIdAndGoogleGetsTheEncodedImage() {
        val saved = encodeFtutterViewerSearchEngines(listOf(FtutterViewerSearchEngine.ASCII2D, FtutterViewerSearchEngine.ASCII2D))
        assertEquals("ascii2d", saved)
        assertEquals(listOf(FtutterViewerSearchEngine.ASCII2D), parseFtutterViewerSearchEngines(mapOf(FTUTTER_VIEWER_SEARCH_ENGINES_KEY to "x,ascii2d")))
        assertEquals(
            "https://www.google.com/searchbyimage?sbisrc=chrome&image_url=https%3A%2F%2Fmay.2chan.net%2Fb%2Fsrc%2F1.jpg",
            ftutterGoogleImageSearchUrl("https://may.2chan.net/b/src/1.jpg")
        )
    }
}
