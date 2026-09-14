package com.valoser.futacha.shared.ui.image

import kotlin.test.Test
import kotlin.test.assertFailsWith
import okio.Buffer

class CompatAnimatedImageCanvasSupportTest {
    @Test fun compressedImageCannotAllocateAnUnboundedSourceCanvas() {
        for (format in CompatAnimatedImageFormat.entries) {
            requireSafeCompatAnimatedImageCanvas(header(format, 4096, 4096), format)
            assertFailsWith<IllegalArgumentException> {
                requireSafeCompatAnimatedImageCanvas(header(format, 4096, 4097), format)
            }
            assertFailsWith<IllegalArgumentException> {
                requireSafeCompatAnimatedImageCanvas(header(format, 16385, 1), format)
            }
            assertFailsWith<IllegalStateException> {
                requireSafeCompatAnimatedImageCanvas(byteArrayOf(), format)
            }
        }
    }

    private fun header(format: CompatAnimatedImageFormat, width: Int, height: Int): ByteArray = Buffer().apply {
        when (format) {
            CompatAnimatedImageFormat.GIF -> write(ByteArray(6)).writeShortLe(width).writeShortLe(height)
            CompatAnimatedImageFormat.PNG -> write(ByteArray(16)).writeInt(width).writeInt(height)
            CompatAnimatedImageFormat.WEBP -> {
                write(ByteArray(24))
                for (value in listOf(width - 1, height - 1)) {
                    repeat(3) { shift -> writeByte((value ushr (8 * shift)) and 0xff) }
                }
            }
        }
    }.readByteArray()
}
