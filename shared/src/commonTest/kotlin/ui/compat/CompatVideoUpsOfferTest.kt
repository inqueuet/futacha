package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.network.defaultBoardPostingCapabilities
import com.valoser.futacha.shared.ui.futaber.FutaberAttachment
import com.valoser.futacha.shared.util.ImageData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** aimg seq63: a video the board does not take (img/b) is offered to あぷ小 instead of only being refused. */
class CompatVideoUpsOfferTest {
    private val imgB = setOf("gif", "jpg", "jpeg", "png")
    private val mayB = setOf("gif", "jpg", "jpeg", "png", "webm", "mp4")
    private fun video(name: String = "clip.mp4", size: Int = 1_000_000) = ImageData(ByteArray(size) { 1 }, name)

    @Test
    fun anMp4OnImgIsOfferedToUps() {
        val offer = compatVideoUpsOffer(video(), imgB, isAimoge = false)
        assertIs<CompatVideoUpsOffer.Offer>(offer)
        assertTrue("あぷ小へ上げて、本文にファイル名を入れますか？" in offer.message)
    }

    @Test
    fun aBoardThatTakesTheVideoAndAimogeAndPicturesAreLeftAlone() {
        assertNull(compatVideoUpsOffer(video(), mayB, isAimoge = false))
        assertNull(compatVideoUpsOffer(video("clip.mov"), emptySet(), isAimoge = true))
        assertNull(compatVideoUpsOffer(ImageData(ByteArray(10), "a.webp"), imgB, isAimoge = false))
    }

    @Test
    fun aReplyThatTakesNoAttachmentStillOffersUps() {
        assertIs<CompatVideoUpsOffer.Offer>(compatVideoUpsOffer(video(), mayB, isAimoge = false, attachmentAllowed = false))
    }

    @Test
    fun overUpsLimitIsRefusedWithAReason() {
        val offer = compatVideoUpsOffer(video(size = COMPAT_UPS_VIDEO_MAX_BYTES + 1), imgB, isAimoge = false)
        assertIs<CompatVideoUpsOffer.Refuse>(offer)
        assertTrue("3000KB" in offer.message)
    }

    @Test
    fun theUploadAddsTheNameToTheBody() = runTest {
        var body = "本文"
        val uploaded = mutableListOf<String>()
        val message = uploadCompatVideoToUps(video(), { uploaded += it.fileName; "fu12345.mp4" }) { body = appendCompatPostText(body, it) }
        assertEquals(listOf("clip.mp4"), uploaded)
        assertEquals("本文\nfu12345.mp4", body)
        assertTrue("fu12345.mp4" in message)
        assertTrue("上げられませんでした" in uploadCompatVideoToUps(video(), { error("x") }) { body = "changed" })
        assertEquals("本文\nfu12345.mp4", body)
    }

    @Test
    fun theFutaberAndFtutterAttachmentAsksInsteadOfRefusing() {
        val attachment = FutaberAttachment(CoroutineScope(Dispatchers.Unconfined))
        attachment.offer(video(), defaultBoardPostingCapabilities("https://img.2chan.net/b/futaba.php"), isReply = false)
        assertNotNull(attachment.pendingUps)
        assertNull(attachment.error)
        assertNull(attachment.image)
    }
}
