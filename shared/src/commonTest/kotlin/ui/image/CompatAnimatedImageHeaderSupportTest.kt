package com.valoser.futacha.shared.ui.image

import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatAnimatedImageHeaderSupportTest {
    @Test fun apngDetectionSkipsMetadataAndLeavesPixelPayloadUnread() {
        val apng = png().chunk("IHDR", ByteArray(13)).chunk("tEXt", ByteArray(100)).chunk("acTL", ByteArray(8))
        val before = apng.size
        assertTrue(hasCompatAnimatedPngHeader(apng.peek()))
        assertEquals(before, apng.size)
        val static = png().chunk("IHDR", ByteArray(13)).writeInt(Int.MAX_VALUE).writeUtf8("IDAT")
        assertFalse(hasCompatAnimatedPngHeader(static))
        assertEquals(0L, static.size) // no attempt to read the claimed 2 GiB pixel payload
    }

    @Test fun apngRejectsHugeChunksBeforeReadingThemAndBoundsEmptyChunkLoops() {
        val huge = png().writeInt(Int.MAX_VALUE).writeUtf8("tEXt")
        assertFailsWith<IllegalArgumentException> { hasCompatAnimatedPngHeader(huge) }
        val empty = png().apply { repeat(4097) { chunk("tEXt", byteArrayOf()) } }
        assertFailsWith<IllegalStateException> { hasCompatAnimatedPngHeader(empty) }
        assertFalse(hasCompatAnimatedPngHeader(png().writeInt(8).writeUtf8("acTL")))
    }

    @Test fun webpChecksAnimationFlagWithoutReadingFramesOrMetadata() {
        fun webp(flags: Int) = Buffer().writeUtf8("RIFF").writeIntLe(Int.MAX_VALUE).writeUtf8("WEBPVP8X")
            .writeIntLe(10).writeByte(flags).write(ByteArray(9))
        val animated = webp(2)
        assertTrue(hasCompatAnimatedWebpHeader(animated.peek()))
        assertEquals(30L, animated.size)
        assertFalse(hasCompatAnimatedWebpHeader(webp(0)))
        assertFalse(hasCompatAnimatedWebpHeader(Buffer().writeUtf8("not a webp")))
        assertFalse(hasCompatAnimatedWebpHeader(Buffer().writeUtf8("RIFF").writeInt(0).writeUtf8("WEBPVP8X")))
    }

    private fun png() = Buffer().writeLong(0x89504e470d0a1a0auL.toLong())
    private fun Buffer.chunk(type: String, bytes: ByteArray): Buffer =
        writeInt(bytes.size).writeUtf8(type).write(bytes).writeInt(0)
}
