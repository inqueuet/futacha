package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.compat.CompatPostSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class FtutterGalleryFitTest {
    private fun post(w: Int?, h: Int?) = CompatPostSnapshot(position = 0, postNo = "1", timestamp = "", messageHtml = "",
        thumbnailWidth = w, thumbnailHeight = h)

    @Test
    fun aCellFitsTheWholeImageAndTheRowTakesItsTallestImageLikeTheOriginal() {
        // 横長（250×125）は幅いっぱいで高さ0.5、縦長（125×250）は高さが幅と同じ（1.0）で上限。
        val wide = post(250, 125)
        val tall = post(125, 250)
        val unknown = post(null, null)
        assertEquals(2f, ftutterGalleryImageRatio(wide))
        assertEquals(0.5f, ftutterGalleryImageRatio(tall))
        assertEquals(1f, ftutterGalleryImageRatio(unknown))
        // 1行目は横長だけ → 0.5、2行目に縦長が入れば1.0。
        val media = listOf(wide, wide, tall, wide)
        assertEquals(0.5f, ftutterGalleryRowHeightFraction(media, 0, 2))
        assertEquals(0.5f, ftutterGalleryRowHeightFraction(media, 1, 2))
        assertEquals(1f, ftutterGalleryRowHeightFraction(media, 3, 2))
        // 最後の半端な行も、その行の画像だけで決める。
        assertEquals(0.5f, ftutterGalleryRowHeightFraction(listOf(tall, wide, wide), 2, 2))
    }

    @Test
    fun theLegacySwitchIsOffByDefault() {
        assertEquals(false, parseFtutterMediaSettings(emptyMap()).legacyGallerySquare)
        assertEquals(true, parseFtutterMediaSettings(mapOf(FTUTTER_LEGACY_GALLERY_SQUARE_KEY to "true")).legacyGallerySquare)
    }
}
