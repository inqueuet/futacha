package com.valoser.futacha.shared.ui.compat

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CompatPostImageMetadataSupportTest {
    @Test
    fun exifOrientationMapsToUprightTransform() {
        assertEquals(CompatImageOrientation(0, false), compatExifOrientationTransform(1))
        assertEquals(CompatImageOrientation(0, true), compatExifOrientationTransform(2))
        assertEquals(CompatImageOrientation(180, false), compatExifOrientationTransform(3))
        assertEquals(CompatImageOrientation(180, true), compatExifOrientationTransform(4))
        assertEquals(CompatImageOrientation(90, true), compatExifOrientationTransform(5))
        assertEquals(CompatImageOrientation(90, false), compatExifOrientationTransform(6))
        assertEquals(CompatImageOrientation(270, true), compatExifOrientationTransform(7))
        assertEquals(CompatImageOrientation(270, false), compatExifOrientationTransform(8))
        assertTrue(compatExifOrientationTransform(0).isIdentity)
        assertTrue(compatExifOrientationTransform(99).isIdentity)
    }

    @Test
    fun detectsFormatsFromMagicBytes() {
        assertEquals(CompatPostImageFormat.JPEG, detectCompatPostImageFormat(byteArrayOf(-1, -40, -1, -32)))
        assertEquals(CompatPostImageFormat.PNG, detectCompatPostImageFormat(png(chunk("IEND", ByteArray(0)))))
        assertEquals(CompatPostImageFormat.GIF, detectCompatPostImageFormat("GIF89a....".encodeToByteArray()))
        assertEquals(CompatPostImageFormat.WEBP, detectCompatPostImageFormat(webp(riffChunk("VP8L", ByteArray(5)))))
        assertEquals(CompatPostImageFormat.OTHER, detectCompatPostImageFormat("....ftypheic".encodeToByteArray()))
        assertEquals(CompatPostImageFormat.OTHER, detectCompatPostImageFormat(ByteArray(0)))
    }

    @Test
    fun pngStripDropsTextExifAndTrailingBytesButKeepsAnimationAndAlphaChunks() {
        val ihdr = chunk("IHDR", ByteArray(13) { 1 })
        val actl = chunk("acTL", ByteArray(8) { 2 })
        val trns = chunk("tRNS", ByteArray(3) { 3 })
        val idat = chunk("IDAT", ByteArray(10) { 4 })
        val iend = chunk("IEND", ByteArray(0))
        val source = png(
            ihdr, chunk("eXIf", "GPS".encodeToByteArray()), actl, chunk("tEXt", "Comment\u0000secret".encodeToByteArray()),
            trns, chunk("iTXt", ByteArray(4)), chunk("zTXt", ByteArray(4)), chunk("tIME", ByteArray(7)), idat, iend
        ) + "trailer".encodeToByteArray()
        assertContentEquals(png(ihdr, actl, trns, idat, iend), stripCompatPngMetadataChunks(source))
    }

    @Test
    fun pngStripRejectsTruncatedChunks() {
        val truncated = png(chunk("IHDR", ByteArray(13))).copyOf(20)
        assertNull(stripCompatPngMetadataChunks(truncated))
        // Missing IEND is malformed too.
        assertNull(stripCompatPngMetadataChunks(png(chunk("IHDR", ByteArray(13)))))
    }

    @Test
    fun webpStripDropsExifXmpChunksClearsFlagsAndRewritesRiffSize() {
        val vp8xFlags = 0x10 or 0x08 or 0x04 or 0x02 // alpha, EXIF, XMP, animation
        val vp8x = ByteArray(10).also { it[0] = vp8xFlags.toByte() }
        val anim = riffChunk("ANIM", ByteArray(6) { 5 })
        val anmf = riffChunk("ANMF", ByteArray(17) { 6 }) // odd size, padded
        val source = webp(
            riffChunk("VP8X", vp8x), anim, anmf,
            riffChunk("EXIF", "GPS".encodeToByteArray()), riffChunk("XMP ", ByteArray(4))
        )
        val expectedVp8x = vp8x.copyOf().also { it[0] = (0x10 or 0x02).toByte() }
        val expected = webp(riffChunk("VP8X", expectedVp8x), anim, anmf)
        val stripped = stripCompatWebpMetadataChunks(source)
        assertContentEquals(expected, stripped)
        assertEquals(stripped!!.size - 8, littleEndian(stripped, 4))
    }

    @Test
    fun webpStripRejectsChunkPastRiffEnd() {
        val source = webp(riffChunk("VP8L", ByteArray(8)))
        source[16] = 100 // chunk size beyond the container
        assertNull(stripCompatWebpMetadataChunks(source))
    }

    @Test
    fun losslessSanitizeKeepsGifAndOnlyAppliesToUprightImagesWithinLimit() {
        val gif = "GIF89a-animated-frames".encodeToByteArray()
        val upright = CompatImageOrientation(0, false)
        assertSame(gif, compatPostLosslessSanitizedImage(gif, CompatPostImageFormat.GIF, upright, gif.size))
        assertNull(compatPostLosslessSanitizedImage(gif, CompatPostImageFormat.GIF, upright, gif.size - 1))
        val pngBytes = png(chunk("IHDR", ByteArray(13)), chunk("tEXt", ByteArray(4)), chunk("IEND", ByteArray(0)))
        assertFalse(compatPostLosslessSanitizedImage(pngBytes, CompatPostImageFormat.PNG, upright, Int.MAX_VALUE)
            .contentEquals(pngBytes))
        // A rotated PNG/WebP must be re-encoded with the rotation applied.
        assertNull(compatPostLosslessSanitizedImage(pngBytes, CompatPostImageFormat.PNG, CompatImageOrientation(90, false), Int.MAX_VALUE))
        // JPEG/HEIF always go through the platform encoder.
        assertNull(compatPostLosslessSanitizedImage(byteArrayOf(-1, -40, -1), CompatPostImageFormat.JPEG, upright, Int.MAX_VALUE))
        assertNull(compatPostLosslessSanitizedImage(ByteArray(4), CompatPostImageFormat.OTHER, upright, Int.MAX_VALUE))
    }

    @Test
    fun sanitizedFileNameMatchesEncodedContent() {
        assertEquals("photo.jpg", compatPostSanitizedFileName("photo.heic", "jpg"))
        assertEquals("anim.gif", compatPostSanitizedFileName("anim.gif", "gif"))
        assertEquals("clip.png", compatPostSanitizedFileName("clip", "png"))
        assertEquals("attachment.png", compatPostSanitizedFileName(".png", "png"))
    }

    @Test
    fun messageLineAtOffsetHandlesLeadingNewlineAndOutOfRangeOffsets() {
        assertEquals("", compatMessageLineAtOffset("\n>>123", 0))
        assertEquals(">>123", compatMessageLineAtOffset("\n>>123", 1))
        assertEquals(">>123", compatMessageLineAtOffset("\n>>123", 6))
        assertEquals("first", compatMessageLineAtOffset("first\nsecond", 5))
        assertEquals("second", compatMessageLineAtOffset("first\nsecond", 6))
        assertEquals("", compatMessageLineAtOffset("a\n\nb", 2))
        assertEquals("b", compatMessageLineAtOffset("a\n\nb", 99))
        assertEquals("a", compatMessageLineAtOffset("a\n\nb", -3))
        assertEquals("", compatMessageLineAtOffset("", 0))
    }

    private fun png(vararg chunks: ByteArray): ByteArray =
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + chunks.fold(ByteArray(0)) { a, b -> a + b }

    private fun chunk(type: String, data: ByteArray): ByteArray =
        bigEndian(data.size) + type.encodeToByteArray() + data + ByteArray(4) // CRC is not validated

    private fun webp(vararg chunks: ByteArray): ByteArray {
        val body = "WEBP".encodeToByteArray() + chunks.fold(ByteArray(0)) { a, b -> a + b }
        return "RIFF".encodeToByteArray() + littleEndianBytes(body.size) + body
    }

    private fun riffChunk(type: String, data: ByteArray): ByteArray =
        type.encodeToByteArray() + littleEndianBytes(data.size) + data + ByteArray(data.size and 1)

    private fun bigEndian(value: Int) = byteArrayOf(
        (value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte()
    )

    private fun littleEndianBytes(value: Int) = byteArrayOf(
        value.toByte(), (value ushr 8).toByte(), (value ushr 16).toByte(), (value ushr 24).toByte()
    )

    private fun littleEndian(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}
