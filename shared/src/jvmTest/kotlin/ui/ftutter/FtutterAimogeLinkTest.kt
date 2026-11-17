package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.Post
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Thread 01a1cd5d: in ふたったー風, a link written in an あいもげ post could not be pressed (its body has no `<a>`). */
class FtutterAimogeLinkTest {
    private fun post(siteUuid: String?) = Post(
        id = "1", author = null, subject = null, timestamp = "",
        messageHtml = "見て https://example.com/a", imageUrl = null, thumbnailUrl = null, siteUuid = siteUuid
    )

    @Test fun anAimogePostLinksTheUrlsInItsText() {
        val aimoge = post("01a1cd5d-1f35-7210-a568-35c674de640a")
        val anchors = ftutterAnchorTargets(aimoge.messageHtml)
        assertTrue(ftutterKeepsLink("https://example.com/a", anchors, ftutterLinksEveryUrl(aimoge, legacy = false)))
    }

    @Test fun aFutabaPostStillLinksOnlyItsAnchorsAsTheOriginal() {
        val futaba = post(null)
        val anchors = ftutterAnchorTargets(futaba.messageHtml)
        assertFalse(ftutterKeepsLink("https://example.com/a", anchors, ftutterLinksEveryUrl(futaba, legacy = false)))
        assertTrue(ftutterLinksEveryUrl(futaba, legacy = true))
    }
}
