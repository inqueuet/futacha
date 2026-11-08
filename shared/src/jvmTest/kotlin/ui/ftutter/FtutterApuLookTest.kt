package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.Post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FtutterApuLookTest {
    private fun post(image: String?, thumb: String?) = Post(id = "1", author = null, subject = null, timestamp = "",
        messageHtml = "", imageUrl = image, thumbnailUrl = thumb)

    @Test
    fun theHeaderShowsOnlyARealAttachmentsFileNameLikeTheOriginal() {
        // 本文の「fu…」は共有の解析が添付の無いレスの画像として拾うが、原作は見出しに出さない。
        val uploader = post("https://dec.2chan.net/up2/src/fu7511312.jpg", null)
        assertNull(ftutterHeaderFileName(uploader, legacy = false))
        assertEquals("fu7511312.jpg", ftutterHeaderFileName(uploader, legacy = true))
        val attached = post("https://img.2chan.net/b/src/1485171088000.jpg", "https://img.2chan.net/b/thumb/1485171088000s.jpg")
        assertEquals("1485171088000.jpg", ftutterHeaderFileName(attached, legacy = false))
    }

    @Test
    fun aQuotedApuFileNameGetsNoThumbnailLikeTheOriginal() {
        val own = "なんなんだコイツ<br>fu7511354.jpg"
        val quoted = "<font color=\"#789922\">&gt;fu7511312.jpg</font><br>ハンティーはいいやつだったよな"
        assertEquals(listOf("https://dec.2chan.net/up2/src/fu7511354.jpg"), ftutterApuImages(own, skipQuotes = true))
        assertEquals(emptyList(), ftutterApuImages(quoted, skipQuotes = true))
        assertEquals(1, ftutterApuImages(quoted).size)
    }
}
