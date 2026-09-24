package com.valoser.futacha.shared.ui.board

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class DesktopVideoFrameConverterTest {
    /** RV32 in memory: B, G, R, X (X is not alpha and may be 0). */
    private fun frame(vararg bgrx: Int) = ByteBuffer.wrap(ByteArray(bgrx.size) { bgrx[it].toByte() })

    @Test
    fun rv32FramesBecomeOpaquePixelsWithTheRightColours() {
        val converter = DesktopVideoFrameConverter()
        val image = converter.convert(frame(0xFF, 0, 0, 0, 0, 0, 0xFF, 0), width = 2, height = 1)

        val pixels = IntArray(2)
        image.readPixels(pixels)
        assertEquals(0xFF0000FF.toInt(), pixels[0]) // blue
        assertEquals(0xFFFF0000.toInt(), pixels[1]) // red
    }

    @Test
    fun theFrameBufferIsReusedUntilTheSizeChanges() {
        val converter = DesktopVideoFrameConverter()
        converter.convert(frame(1, 2, 3, 0, 4, 5, 6, 0), width = 2, height = 1)
        val first = converter.buffer
        converter.convert(frame(7, 8, 9, 0, 1, 2, 3, 0), width = 2, height = 1)
        assertSame(first, converter.buffer)

        converter.convert(frame(1, 2, 3, 0), width = 1, height = 1)
        assertNotSame(first, converter.buffer)
    }
}
