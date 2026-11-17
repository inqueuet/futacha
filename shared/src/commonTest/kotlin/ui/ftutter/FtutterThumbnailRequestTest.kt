package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.*

class FtutterThumbnailRequestTest {
    private val thumb = "https://may.2chan.net/b/thumb/100s.jpg"
    private val full = "https://may.2chan.net/b/src/100.jpg"

    @Test fun videoOriginalsAreNeverImageCandidatesForAnyQualityOrNetwork() {
        for (extension in listOf("webm", "mp4", "WEBM", "MP4")) {
            val video = "https://may.2chan.net/b/src/100.$extension?download=1#video"
            for (quality in 0..2) for (wifi in listOf(false, true)) {
                assertEquals(FtutterThumbnailSources(thumb, null),
                    ftutterThumbnailSources(thumb, video, quality, wifi), "$extension quality=$quality wifi=$wifi")
            }
        }
    }
    @Test fun staticOriginalsKeepTheConfiguredWifiAndAlwaysHighQualityPolicies() {
        assertEquals(FtutterThumbnailSources(full, thumb), ftutterThumbnailSources(thumb, full, 1, true))
        assertEquals(FtutterThumbnailSources(full, thumb), ftutterThumbnailSources(thumb, full, 2, false))
        assertEquals(FtutterThumbnailSources(thumb, null), ftutterThumbnailSources(thumb, full, 1, false))
        assertEquals(FtutterThumbnailSources(thumb, null), ftutterThumbnailSources(thumb, full, 0, true))
    }
    @Test fun catalogQualityStillSelectsStaticCatalogOrThreadThumbnails() {
        val cat = thumb.replace("/thumb/", "/cat/")
        assertEquals(FtutterThumbnailSources(thumb, cat), ftutterThumbnailSources(cat, full, 2, true, catalog = true))
        assertEquals(FtutterThumbnailSources(cat, null), ftutterThumbnailSources(thumb, full, 0, true, catalog = true))
    }
    @Test fun aMissingVideoThumbnailDoesNotTurnTheVideoIntoAnImageRequest() {
        val video = "https://may.2chan.net/b/src/100.webm"
        assertEquals(FtutterThumbnailSources(null, null), ftutterThumbnailSources(null, video, 2, true))
        assertEquals(FtutterThumbnailSources(null, null), ftutterThumbnailSources(video, video, 2, true))
    }
    @Test fun legacyFutabaHttpUrlsRemainHttpsForPrimaryAndFallback() {
        assertEquals(FtutterThumbnailSources(full, thumb),
            ftutterThumbnailSources(thumb.replace("https:", "http:"), full.replace("https:", "http:"), 2, true))
    }
}
