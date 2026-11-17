package com.valoser.futacha.shared.ui.futaber

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** App Store review 2026-11-16: あぷ小のリンクが外部ブラウザで開いた。画像と動画はビューアで開く。 */
class FutaberApuLinkTest {
    @Test
    fun apuPicturesAndVideosOpenInTheViewerAndOtherLinksDoNot() {
        assertTrue(futaberOpensApuInViewer("https://dec.2chan.net/up2/src/fu1234567.jpg"))
        assertTrue(futaberOpensApuInViewer("https://dec.2chan.net/up2/src/fu1234568.webm"))
        assertFalse(futaberOpensApuInViewer("https://dec.2chan.net/up2/src/fu1234569.zip"))
        assertFalse(futaberOpensApuInViewer("https://example.com/a.jpg"))
    }
}
