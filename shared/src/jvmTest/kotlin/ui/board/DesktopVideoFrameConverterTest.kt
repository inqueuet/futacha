package com.valoser.futacha.shared.ui.board

import androidx.compose.ui.graphics.asSkiaBitmap
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class DesktopVideoFrameConverterTest {
    @Test
    fun framesConsumedWithoutRecompositionAreReleasedWhileDrawnFrameStaysValid() {
        val converter = DesktopVideoFrameConverter()
        fun next() = DesktopVideoFrame(converter.convert(frame(1, 2, 3, 0), 1, 1), 1, 1)
        val presentation = DesktopVideoFramePresentation()
        val shown = next()
        presentation.accept(shown)
        presentation.displayed(shown)
        val skipped = next()
        presentation.accept(skipped)
        val latest = next()
        presentation.accept(latest)
        kotlin.test.assertTrue(skipped.image.asSkiaBitmap().isClosed)
        kotlin.test.assertFalse(shown.image.asSkiaBitmap().isClosed)
        kotlin.test.assertFalse(latest.image.asSkiaBitmap().isClosed)
        presentation.displayed(latest)
        kotlin.test.assertTrue(shown.image.asSkiaBitmap().isClosed)
        presentation.close()
        kotlin.test.assertTrue(latest.image.asSkiaBitmap().isClosed)
    }

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

    @Test
    fun aFrameReplacedBeforeTheUiTookItIsClosed() {
        val converter = DesktopVideoFrameConverter()
        val mailbox = DesktopVideoFrameMailbox()
        val first = DesktopVideoFrame(converter.convert(frame(1, 2, 3, 0), 1, 1), 1, 1)
        val second = DesktopVideoFrame(converter.convert(frame(4, 5, 6, 0), 1, 1), 1, 1)
        mailbox.publish(first)
        mailbox.publish(second)

        kotlin.test.assertTrue(first.image.asSkiaBitmap().isClosed)
        assertSame(second, mailbox.take())
        kotlin.test.assertNull(mailbox.take())
        kotlin.test.assertFalse(second.image.asSkiaBitmap().isClosed)
        second.close()
        kotlin.test.assertTrue(second.image.asSkiaBitmap().isClosed)
    }
}
