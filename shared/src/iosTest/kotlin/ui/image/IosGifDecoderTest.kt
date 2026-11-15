@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)
package com.valoser.futacha.shared.ui.image

import kotlin.io.encoding.Base64
import kotlin.test.*
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data

class IosGifDecoderTest {
    @Test fun viewerActuallyAnimatesThenStopsWhenDisabledAndDisposed() = com.valoser.futacha.shared.ui.image.runIosMediaMainTest {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            val image = decodeIosGif(gif).image
            val request = coil3.request.ImageRequest.Builder(coil3.PlatformContext.INSTANCE).data(gif).build()
            val fallback = coil3.compose.ImagePainter(image)
            val state = coil3.compose.AsyncImagePainter.State.Success(fallback,
                coil3.request.SuccessResult(image = image, request = request, dataSource = coil3.decode.DataSource.MEMORY_CACHE))
            val animate = androidx.compose.runtime.mutableStateOf(true)
            val scene = androidx.compose.ui.ImageComposeScene(2, 2) {
                androidx.compose.foundation.Image(platformAnimatedViewerPainter(state, fallback, animate.value), null)
            }
            var clock = 0L
            fun pixel(): Int {
                androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
                clock += 25_000_000L
                val frame = scene.render(clock)
                try {
                    val bitmap = frame.toComposeImageBitmap()
                    val pixels = IntArray(4)
                    bitmap.readPixels(pixels)
                    return pixels[0]
                } finally { frame.close() }
            }
            try {
                val colors = mutableSetOf<Int>()
                repeat(24) { colors += pixel(); kotlinx.coroutines.delay(25) }
                assertTrue(colors.contains(0xffff0000.toInt()) && colors.contains(0xff0000ff.toInt()), "GIF never displayed both frames: $colors")
                animate.value = false
                pixel()
                kotlinx.coroutines.delay(100)
                val stopped = pixel()
                repeat(8) { kotlinx.coroutines.delay(30); assertEquals(stopped, pixel()) }
            } finally { scene.close() }
            kotlinx.coroutines.delay(50)
        }
    }

    private val gif get() = Base64.decode("R0lGODlhAgACAIEAAP8AAAAAAAAAAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQABgAAACwAAAAAAgACAAAIBgABCAQQEAAh+QQBDgABACwAAAAAAgACAIEAAP8AAAAAAAAAAAAIBgABCAQQEAA7")
    @Test fun animatedGifKeepsItsEncodedFramesAndStaticPoster() {
        val result = decodeIosGif(gif)
        val image = assertIs<IosGifImage>(result.image)
        assertEquals(2, image.width)
        assertEquals(2, image.height)
        assertContentEquals(gif, image.bytes)
        assertTrue(image.size >= gif.size + 16)
    }
    @Test fun framesHaveDifferentPixelsAndPreserveTimingAndLooping() {
        Data.makeFromBytes(gif).useGifNative { data ->
            Codec.makeFromData(data).useGifNative { codec ->
                assertEquals(2, codec.frameCount)
                assertEquals(60, codec.getFrameInfo(0).duration)
                assertEquals(140, codec.getFrameInfo(1).duration)
                assertEquals(-1, codec.repetitionCount)
                Bitmap().useGifNative { bitmap ->
                    bitmap.allocPixels(codec.imageInfo)
                    codec.readPixels(bitmap, 0)
                    val red = bitmap.getColor(0, 0)
                    codec.readPixels(bitmap, 1)
                    assertNotEquals(red, bitmap.getColor(0, 0))
                    codec.readPixels(bitmap, 0)
                    assertEquals(red, bitmap.getColor(0, 0))
                }
            }
        }
    }
    @Test fun largeCanvasesAndTruncatedInputAreRejected() {
        val huge = gif.copyOf().apply { this[6] = 0xff.toByte(); this[7] = 0xff.toByte() }
        assertFails { decodeIosGif(huge) }
        assertFails { decodeIosGif(byteArrayOf(1, 2)) }
    }
}
