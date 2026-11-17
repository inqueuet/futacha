package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.util.ImageData
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class CompatPostImageCompressionJvmTest {
    @Test
    fun fittingPngIsSentLosslesslyWithoutTextChunks() = runBlocking {
        val source = withTextChunk(png(transparentLeftHalf(40, 20)))
        val result = compressCompatPostImage(ImageData(source, "clip.PNG"), 1024 * 1024).getOrThrow()
        assertEquals("clip.png", result.fileName)
        assertEquals(CompatPostImageFormat.PNG, detectCompatPostImageFormat(result.bytes))
        assertTrue("secret" !in result.bytes.decodeToString(), "tEXt metadata must be removed")
        val decoded = ImageIO.read(ByteArrayInputStream(result.bytes))
        assertEquals(0, decoded.getRGB(5, 10) ushr 24, "transparency must survive")
    }

    @Test
    fun gifBytesAreKeptSoAnimationSurvives() = runBlocking {
        val gif = ByteArrayOutputStream().also { ImageIO.write(transparentLeftHalf(8, 8), "gif", it) }.toByteArray()
        val result = compressCompatPostImage(ImageData(gif, "anim.gif"), 1024 * 1024).getOrThrow()
        assertEquals("anim.gif", result.fileName)
        assertContentEquals(gif, result.bytes)
    }

    @Test
    fun transparentImageAboveTheLimitIsFlattenedOnWhite() = runBlocking {
        val image = transparentLeftHalf(256, 256)
        val random = Random(7)
        for (y in 0 until 256) for (x in 128 until 256) image.setRGB(x, y, (0xff shl 24) or random.nextInt(0x1000000))
        val source = png(image)
        val limit = source.size / 3
        val result = compressCompatPostImage(ImageData(source, "noise.v2.png"), limit).getOrThrow()
        assertTrue(result.bytes.size <= limit)
        assertEquals("noise.v2.jpg", result.fileName)
        val pixel = Color(ImageIO.read(ByteArrayInputStream(result.bytes)).getRGB(4, 4))
        assertTrue(pixel.red > 230 && pixel.green > 230 && pixel.blue > 230, "transparent area became $pixel")
    }

    @Test
    fun portraitJpegWithOrientation6IsRotatedUpright() = runBlocking {
        // Raw sensor image is landscape: red left half, blue right half. Orientation 6 = rotate 90° clockwise.
        val source = withExifOrientation(jpeg(redLeftBlueRight(80, 40)), 6)
        assertEquals(6, readCompatPostEncodedOrigin(source))
        val result = compressCompatPostImage(ImageData(source, "photo.JPG"), 1024 * 1024).getOrThrow()
        assertEquals("photo.jpg", result.fileName)
        val decoded = ImageIO.read(ByteArrayInputStream(result.bytes))
        assertEquals(40, decoded.width)
        assertEquals(80, decoded.height)
        assertRed(Color(decoded.getRGB(20, 10)), "top must be the raw left (red) side")
        assertBlue(Color(decoded.getRGB(20, 70)), "bottom must be the raw right (blue) side")
        assertEquals(1, readCompatPostEncodedOrigin(result.bytes), "output must not carry an orientation to apply twice")
    }

    @Test
    fun jpegWithOrientation3IsTurnedUpsideDown() = runBlocking {
        val source = withExifOrientation(jpeg(redLeftBlueRight(80, 40)), 3)
        assertEquals(3, readCompatPostEncodedOrigin(source))
        val decoded = ImageIO.read(ByteArrayInputStream(
            compressCompatPostImage(ImageData(source, "photo.jpeg"), 1024 * 1024).getOrThrow().bytes))
        assertEquals(80, decoded.width)
        assertEquals(40, decoded.height)
        assertBlue(Color(decoded.getRGB(10, 20)), "left must be the raw right (blue) side")
        assertRed(Color(decoded.getRGB(70, 20)), "right must be the raw left (red) side")
    }

    @Test
    fun uprightJpegKeepsItsLayout() = runBlocking {
        val source = withExifOrientation(jpeg(redLeftBlueRight(80, 40)), 1)
        val decoded = ImageIO.read(ByteArrayInputStream(
            compressCompatPostImage(ImageData(source, "photo.jpg"), 1024 * 1024).getOrThrow().bytes))
        assertEquals(80, decoded.width)
        assertRed(Color(decoded.getRGB(10, 20)), "left stays red")
        assertBlue(Color(decoded.getRGB(70, 20)), "right stays blue")
    }

    @Test
    fun everyExifOrientationMapsRawCornersUpright() {
        // Raw 3x2 pixel corners -> where EXIF says they belong once displayed upright.
        val expected = mapOf(
            1 to listOf(0.0 to 0.0, 3.0 to 2.0), 2 to listOf(3.0 to 0.0, 0.0 to 2.0),
            3 to listOf(3.0 to 2.0, 0.0 to 0.0), 4 to listOf(0.0 to 2.0, 3.0 to 0.0),
            5 to listOf(0.0 to 0.0, 2.0 to 3.0), 6 to listOf(2.0 to 0.0, 0.0 to 3.0),
            7 to listOf(2.0 to 3.0, 0.0 to 0.0), 8 to listOf(0.0 to 3.0, 2.0 to 0.0)
        )
        expected.forEach { (exif, corners) ->
            val transform = compatPostOrientationTransform(compatExifOrientationTransform(exif), 3.0, 2.0)
            val origin = transform.transform(java.awt.geom.Point2D.Double(0.0, 0.0), null)
            val far = transform.transform(java.awt.geom.Point2D.Double(3.0, 2.0), null)
            assertEquals(corners[0], (origin.x + 0.0) to (origin.y + 0.0), "orientation $exif origin")
            assertEquals(corners[1], (far.x + 0.0) to (far.y + 0.0), "orientation $exif far corner")
        }
    }

    private fun redLeftBlueRight(width: Int, height: Int): BufferedImage =
        BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).apply {
            for (y in 0 until height) for (x in 0 until width) setRGB(x, y, if (x < width / 2) 0xff0000 else 0x0000ff)
        }

    private fun jpeg(image: BufferedImage): ByteArray =
        ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()

    /** Inserts a big-endian EXIF APP1 segment holding only the Orientation tag after SOI/APP0. */
    private fun withExifOrientation(jpeg: ByteArray, orientation: Int): ByteArray {
        val tiff = byteArrayOf(
            'M'.code.toByte(), 'M'.code.toByte(), 0, 42, 0, 0, 0, 8, // header, IFD0 at 8
            0, 1, // one entry
            0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, orientation.toByte(), 0, 0, // Orientation SHORT
            0, 0, 0, 0 // no next IFD
        )
        val payload = "Exif\u0000\u0000".encodeToByteArray() + tiff
        val length = payload.size + 2
        val segment = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length ushr 8).toByte(), length.toByte()) + payload
        // ImageIO writes SOI followed by a JFIF APP0 segment.
        val app0End = if ((jpeg[3].toInt() and 0xFF) == 0xE0) 4 + (((jpeg[4].toInt() and 0xFF) shl 8) or (jpeg[5].toInt() and 0xFF)) else 2
        return jpeg.copyOfRange(0, app0End) + segment + jpeg.copyOfRange(app0End, jpeg.size)
    }

    private fun assertRed(color: Color, message: String) =
        assertTrue(color.red > 180 && color.blue < 80, "$message: $color")

    private fun assertBlue(color: Color, message: String) =
        assertTrue(color.blue > 180 && color.red < 80, "$message: $color")

    private fun transparentLeftHalf(width: Int, height: Int): BufferedImage =
        BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply {
            for (y in 0 until height) for (x in width / 2 until width) setRGB(x, y, 0xffff0000.toInt())
        }

    private fun png(image: BufferedImage): ByteArray =
        ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()

    /** Inserts a tEXt chunk right after IHDR (8-byte signature + 25-byte IHDR chunk). */
    private fun withTextChunk(png: ByteArray): ByteArray {
        val payload = "Comment\u0000secret".encodeToByteArray()
        val chunk = ByteArray(12 + payload.size)
        chunk[3] = payload.size.toByte()
        "tEXt".encodeToByteArray().copyInto(chunk, 4)
        payload.copyInto(chunk, 8)
        val crc = java.util.zip.CRC32().apply { update(chunk, 4, 4 + payload.size) }.value
        for (index in 0 until 4) chunk[8 + payload.size + index] = (crc ushr (24 - index * 8)).toByte()
        return png.copyOfRange(0, 33) + chunk + png.copyOfRange(33, png.size)
    }
}
