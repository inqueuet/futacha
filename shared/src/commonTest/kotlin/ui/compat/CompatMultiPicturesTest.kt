package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.repo.aimoge.AimogePendingExtras
import com.valoser.futacha.shared.util.ImageData
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompatMultiPicturesTest {
    private fun img(n: Int) = ImageData(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), n.toByte()), "p$n.jpg")

    private val AIMOGE = "https://nijiurachan.net/b/ai2/"
    @kotlin.test.BeforeTest fun on() { com.valoser.futacha.shared.compat.AimogeAvailability.sitesEnabled = true }
    @AfterTest fun clean() { AimogePendingExtras.clearAll(); com.valoser.futacha.shared.compat.AimogeAvailability.sitesEnabled = false }

    @Test
    fun onFutabaTheFirstIsAttachedAndTheRestGoToApuSmallInOrder() = runTest {
        var attached: ImageData? = null
        val uploaded = mutableListOf<String>()
        var body = "本文"
        val message = applyCompatPickedPictures(
            listOf(img(1), img(2), img(3)), current = null, attachmentAllowed = true, isAimoge = false, boardUrl = "",
            setAttachment = { attached = it }, upload = { uploaded += it.fileName; "fu${uploaded.size}.jpg" },
            appendName = { body = appendCompatPostText(body, it) }
        )
        assertEquals("p1.jpg", attached?.fileName)
        assertEquals(listOf("p2.jpg", "p3.jpg"), uploaded)
        assertEquals("本文\nfu1.jpg\nfu2.jpg", body)
        assertTrue("2枚をあぷ小" in message, message)
    }

    @Test
    fun withAnAttachmentAlreadyOrOnAReplyThatTakesNoneEveryPictureGoesToApuSmall() = runTest {
        val split = splitCompatPictures(listOf(img(1), img(2)), current = img(9), attachmentAllowed = true, isAimoge = false)
        assertNull(split.attachment)
        assertEquals(2, split.uploads.size)
        val noSlot = splitCompatPictures(listOf(img(1)), current = null, attachmentAllowed = false, isAimoge = false)
        assertEquals(1, noSlot.uploads.size)
        val over = splitCompatPictures(List(12) { img(it) }, current = null, attachmentAllowed = true, isAimoge = false)
        assertEquals(9, over.uploads.size)
        assertEquals(2, over.dropped)
    }

    @Test
    fun aFailedUploadKeepsTheOthers() = runTest {
        var body = ""
        val message = applyCompatPickedPictures(
            listOf(img(1), img(2), img(3)), null, true, false, "", {}, upload = { if (it.fileName == "p2.jpg") error("x") else "fu.jpg" },
            appendName = { body = appendCompatPostText(body, it) }
        )
        assertEquals("fu.jpg", body)
        assertTrue("1枚はあぷ小へ上げられません" in message, message)
    }

    @Test
    fun onAimogeTheRestAreHeldWithTheFirstAndNothingIsUploaded() = runTest {
        var attached: ImageData? = null
        val message = applyCompatPickedPictures(
            listOf(img(1), img(2), img(3), img(4)), null, true, isAimoge = true, boardUrl = AIMOGE,
            setAttachment = { attached = it }, upload = { error("never") }, appendName = { error("never") }
        )
        assertEquals("p1.jpg", attached?.fileName)
        assertEquals(listOf("p2.jpg", "p3.jpg"), AimogePendingExtras.of(AIMOGE).map { it.second })
        assertTrue(AimogePendingExtras.of("https://nijiurachan.net/b/other/").isEmpty(), "only the board they were picked on")
        assertTrue("合わせて3枚" in message && "1枚は数の上限" in message, message)
    }
}
