package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.network.BoardPostingCapabilities
import com.valoser.futacha.shared.util.ImageData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FutaberAttachmentTest {
    private val capabilities = BoardPostingCapabilities(
        maxFileSizeBytes = 1_000,
        supportedExtensions = setOf("jpg", "png", "gif", "webm", "mp4")
    )
    private fun file(name: String, size: Int, handwriting: Boolean = false) =
        ImageData(ByteArray(size) { 1 }, name, handwriting)

    @Test
    fun aSupportedFileWithinTheLimitIsAccepted() {
        assertEquals(FutaberAttachmentVerdict.Accept, futaberAttachmentVerdict(file("a.jpg", 500), capabilities, isReply = true))
        assertEquals(FutaberAttachmentVerdict.Accept, futaberAttachmentVerdict(file("a.PNG", 1_000), capabilities, isReply = false))
    }

    @Test
    fun anOversizedImageAsksForCompressionButAnOversizedVideoIsRefused() {
        assertEquals(FutaberAttachmentVerdict.AskCompression, futaberAttachmentVerdict(file("a.jpg", 5_000), capabilities, true))
        assertIs<FutaberAttachmentVerdict.Reject>(futaberAttachmentVerdict(file("a.webm", 5_000), capabilities, true))
    }

    @Test
    fun anUnsupportedOrEmptyOrNamelessFileIsRefusedWithAReason() {
        listOf(file("a.exe", 10), file("a.jpg", 0), file("", 10)).forEach {
            val verdict = futaberAttachmentVerdict(it, capabilities, isReply = true)
            val reject = assertIs<FutaberAttachmentVerdict.Reject>(verdict)
            assertTrue(reject.message.isNotBlank())
        }
    }

    @Test
    fun aBoardThatRefusesReplyAttachmentsStillTakesHandwritingAndNewThreads() {
        val noReplyFiles = capabilities.copy(replyAttachmentsAllowed = false)
        val refused = futaberAttachmentVerdict(file("a.jpg", 10), noReplyFiles, isReply = true)
        assertEquals(FUTABER_REPLY_ATTACHMENT_REFUSED_MESSAGE, assertIs<FutaberAttachmentVerdict.Reject>(refused).message)
        assertEquals(FutaberAttachmentVerdict.Accept, futaberAttachmentVerdict(file("a.png", 10, handwriting = true), noReplyFiles, true))
        assertEquals(FutaberAttachmentVerdict.Accept, futaberAttachmentVerdict(file("a.jpg", 10), noReplyFiles, isReply = false))
    }
}
