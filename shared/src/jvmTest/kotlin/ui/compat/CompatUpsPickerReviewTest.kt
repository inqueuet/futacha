package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.MAX_PICKED_IMAGE_BYTES
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Review 2026-11-11: 「imgのスレにあぷ小で画像を張り付けようとしても『添付ファイルが読み込めません』」.
 * The あぷ小 pickers of ふたちゃ・ふたばー風・ふたったー風 read with the 8,192,000-byte default, so a phone
 * photo over that was refused while reading.  All four modes must read up to the posting limit, and a
 * picture over 3000KB must be shrunk instead of refused.
 */
class CompatUpsPickerReviewTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    @Test
    fun everyUpsPickerReadsMoreThanAPhonePhoto() {
        assertTrue(COMPAT_UPS_PICKER_MAX_BYTES > MAX_PICKED_IMAGE_BYTES)
        assertTrue(COMPAT_UPS_PICKER_MAX_BYTES >= 20_000_000L)
        val files = listOf(
            "shared/src/commonMain/kotlin/ui/board/FutachaPostToolbar.kt",
            "shared/src/commonMain/kotlin/ui/compat/CompatPostScreen.kt",
            "shared/src/commonMain/kotlin/ui/futaber/FutaberParityB2.kt",
            "shared/src/commonMain/kotlin/ui/ftutter/FtutterPost.kt",
        )
        files.forEach { path ->
            val source = File(root, path).readText()
            assertTrue("COMPAT_UPS_PICKER_MAX_BYTES" in source, "$path: あぷ小 picker uses the default read limit")
            assertTrue("prepareCompatUpsUpload" in source, "$path: an oversized picture is refused instead of shrunk")
        }
    }

    @Test
    fun aSmallPictureIsUploadedAsItIs() = runBlocking {
        val picture = ImageData(jpeg(64, 64), "small.jpg")
        val prepared = prepareCompatUpsUpload(picture).getOrThrow()
        assertFalse(prepared.shrunk)
        assertTrue(prepared.image.bytes.contentEquals(picture.bytes))
    }

    @Test
    fun aPhotoOverThreeMegabytesIsShrunkToFit() = runBlocking {
        val big = jpeg(3000, 2400)
        assertTrue(big.size > 3_000_000, "fixture must exceed the あぷ小 limit (${big.size})")
        val prepared = prepareCompatUpsUpload(ImageData(big, "photo.jpg")).getOrThrow()
        assertTrue(prepared.shrunk)
        assertTrue(isCompatUpsUploadSizeAllowed(prepared.image.bytes.size))
    }

    @Test
    fun anOversizedGifIsStillRefused() = runBlocking {
        val gif = ByteArray(3_100_000) { 0x47 }
        assertTrue(prepareCompatUpsUpload(ImageData(gif, "anime.gif")).isFailure)
        assertEquals("あぷ小の3000KBに収まるよう画像を縮小しました", COMPAT_UPS_SHRUNK_NOTICE)
    }

    private fun jpeg(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val random = Random(7)
        for (y in 0 until height) for (x in 0 until width) image.setRGB(x, y, random.nextInt(0x1000000))
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val param = writer.defaultWriteParam.apply {
            compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
            compressionQuality = 1f
        }
        val out = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(out).use { stream ->
            writer.output = stream
            writer.write(null, javax.imageio.IIOImage(image, null, null), param)
        }
        writer.dispose()
        return out.toByteArray()
    }
}
