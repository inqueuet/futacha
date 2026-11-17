package com.valoser.futacha.shared.ui.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okio.Buffer

class GifFrameCounterTest {
    @Test
    fun staticAndAnimatedGifFramesAreDistinguishedWithoutPixelDecode() {
        val staticGif = gifWithFrames(1)
        val animatedGif = gifWithFrames(2)

        assertEquals(1, countGifFrames(Buffer().write(staticGif)))
        assertFalse(hasMultipleGifFrames(Buffer().write(staticGif)))
        assertEquals(2, countGifFrames(Buffer().write(animatedGif)))
        assertTrue(hasMultipleGifFrames(Buffer().write(animatedGif)))
    }

    @Test
    fun boundedScanStopsAfterSecondFrameAndToleratesTruncation() {
        val threeFrames = gifWithFrames(3)
        assertEquals(2, countGifFrames(Buffer().write(threeFrames), limit = 2))
        assertEquals(0, countGifFrames(Buffer().write(threeFrames.copyOf(11))))
    }

    private fun gifWithFrames(count: Int): ByteArray {
        val headerAndLogicalScreen = byteArrayOf(
            'G'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(),
            '8'.code.toByte(), '9'.code.toByte(), 'a'.code.toByte(),
            1, 0, 1, 0, 0, 0, 0
        )
        val frame = byteArrayOf(
            0x2C, 0, 0, 0, 0, 1, 0, 1, 0, 0,
            2, 2, 0x4C, 0x01, 0
        )
        return buildList<Byte> {
            addAll(headerAndLogicalScreen.toList())
            repeat(count) { addAll(frame.toList()) }
            add(0x3B)
        }.toByteArray()
    }
}
