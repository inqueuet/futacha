package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.Post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 2026-11-12: ふたばー風もレスの中に本文のあぷ・あぷ小を並べる（他モードと同じ）。 */
class FutaberInlineApuTest {
    private fun post(html: String, image: String? = null) =
        Post(id = "1", author = null, subject = null, timestamp = "", messageHtml = html, imageUrl = image, thumbnailUrl = null)

    @Test fun everyBareUploaderFileIsListedButQuotesAndTheAttachmentAreNot() {
        val p = post("あぷ小でも<br>fu1111111.jpg fu2222222.mp4<br>&gt;fu3333333.jpg", image = "https://img.2chan.net/b/src/1.jpg")
        assertEquals(
            listOf("https://dec.2chan.net/up2/src/fu1111111.jpg", "https://dec.2chan.net/up2/src/fu2222222.mp4"),
            futaberInlineApuUrls(p)
        )
        val own = post("fu1111111.jpg", image = "https://dec.2chan.net/up2/src/fu1111111.jpg")
        assertTrue(futaberInlineApuUrls(own).isEmpty())
    }

    @Test fun theSwitchIsOnUnlessTurnedOff() {
        assertTrue(FutaberDisplaySettings.from(emptyMap()).extApuPreview)
        assertFalse(FutaberDisplaySettings.from(mapOf(FutaberSettingKeys.EXT_APU_PREVIEW to "OFF")).extApuPreview)
    }
}
