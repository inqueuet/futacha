package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.network.defaultBoardPostingCapabilities
import com.valoser.futacha.shared.ui.compat.compressCompatPostImage
import com.valoser.futacha.shared.util.ImageData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** L-B8 (C43): 「JPG圧縮」 draws a picture again as JPEG; 「高品質」 keeps its own format (as before, and as the other modes). */
class FutaberPostJpegTest {
    private fun transparentPng(): ByteArray {
        val image = BufferedImage(32, 24, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 24) for (x in 0 until 32) image.setRGB(x, y, if (x < 16) 0x00000000 else 0xFF2050A0.toInt())
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    private fun ByteArray.isJpeg() = size > 3 && this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte()
    private fun ByteArray.isPng() = size > 8 && this[0] == 0x89.toByte() && this[1] == 0x50.toByte()

    @Test fun forcedJpegFlattensTheTransparencyOntoWhiteAndTheDefaultKeepsThePng() = runBlocking {
        val png = ImageData(transparentPng(), "picture.png")
        val kept = compressCompatPostImage(png, 8 * 1024 * 1024).getOrThrow()
        assertTrue(kept.bytes.isPng(), "the default keeps the PNG (the other modes)")
        assertEquals("picture.png", kept.fileName)
        val jpeg = compressCompatPostImage(png, 8 * 1024 * 1024, forceJpeg = true).getOrThrow()
        assertTrue(jpeg.bytes.isJpeg())
        assertEquals("picture.jpg", jpeg.fileName)
        val decoded = ImageIO.read(jpeg.bytes.inputStream())
        assertEquals(32, decoded.width)
        // The transparent half is white, not black.
        val rgb = decoded.getRGB(4, 4) and 0xFFFFFF
        assertTrue(((rgb shr 16) and 0xFF) > 240 && ((rgb shr 8) and 0xFF) > 240 && (rgb and 0xFF) > 240, Integer.toHexString(rgb))
    }

    @Test fun theAttachmentRedrawsAsJpegOnlyUnderJpgCompression() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val capabilities = defaultBoardPostingCapabilities("https://may.2chan.net/b/")
            val high = FutaberAttachment(scope)
            high.offer(ImageData(transparentPng(), "a.png"), capabilities, isReply = false)
            withTimeout(10_000) { while (high.processing || high.image == null) delay(20) }
            assertTrue(high.image!!.bytes.isPng())
            val jpeg = FutaberAttachment(scope).apply { quality = FutaberImageQuality.Jpeg }
            jpeg.offer(ImageData(transparentPng(), "a.png"), capabilities, isReply = false)
            withTimeout(10_000) { while (jpeg.processing || jpeg.image == null) delay(20) }
            assertTrue(jpeg.image!!.bytes.isJpeg())
            assertEquals("a.jpg", jpeg.image!!.fileName)
        } finally {
            scope.cancel()
        }
    }
}
