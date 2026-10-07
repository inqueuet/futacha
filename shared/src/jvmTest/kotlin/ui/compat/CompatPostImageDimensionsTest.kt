package com.valoser.futacha.shared.ui.compat

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CompatPostImageDimensionsTest {
    private fun encode(width: Int, height: Int, format: String): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val output = ByteArrayOutputStream()
        check(ImageIO.write(image, format, output))
        return output.toByteArray()
    }

    @Test
    fun dimensionsComeFromThePngAndJpegHeaders() {
        assertEquals(300 to 200, compatPostImageDimensions(encode(300, 200, "png")))
        assertEquals(64 to 128, compatPostImageDimensions(encode(64, 128, "jpg")))
    }

    @Test
    fun aspectRatioIsWidthOverHeight() {
        assertEquals(1.5f, compatPostImageAspectRatio(encode(300, 200, "png")))
        assertEquals(0.5f, compatPostImageAspectRatio(encode(64, 128, "png")))
    }

    @Test
    fun emptyOrNonImageBytesGiveNothing() {
        assertNull(compatPostImageDimensions(ByteArray(0)))
        assertNull(compatPostImageDimensions("not an image".encodeToByteArray()))
        assertNull(compatPostImageAspectRatio("not an image".encodeToByteArray()))
    }

    @Test
    fun aTruncatedBodyStillReportsItsHeaderSize() {
        // Only the header is read, so a file cut off after it still has its dimensions.
        val png = encode(40, 30, "png")
        assertEquals(40 to 30, compatPostImageDimensions(png.copyOf(64)))
    }
}
