package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.Test
import kotlin.test.assertEquals

/** 2026-11-12: ふたったー風のレスの中のあぷ小は、動画も並べ（あぷ小に …s.jpg は無いので、動画そのものから最初のコマを出す）、画像だけの保存は変えない。 */
class FtutterApuMediaTest {
    @Test fun videosAreListedWithTheUploadersThumbnail() {
        val html = "fu1234567.jpg fu1234568.mp4 fu1234569.webm"
        assertEquals(listOf("https://dec.2chan.net/up2/src/fu1234567.jpg", "https://dec.2chan.net/up2/src/fu1234568.mp4",
            "https://dec.2chan.net/up2/src/fu1234569.webm"), ftutterApuMedia(html))
        assertEquals(listOf("https://dec.2chan.net/up2/src/fu1234567.jpg"), ftutterApuImages(html))
        assertEquals("https://dec.2chan.net/up2/src/fu1234568.mp4", ftutterApuPreviewUrl("https://dec.2chan.net/up2/src/fu1234568.mp4"))
        assertEquals("https://dec.2chan.net/up2/src/fu1234567.jpg", ftutterApuPreviewUrl("https://dec.2chan.net/up2/src/fu1234567.jpg"))
    }
    @Test fun quotedVideosStayOut() {
        assertEquals(emptyList(), ftutterApuMedia("&gt;fu1234568.mp4", skipQuotes = true))
    }
}
