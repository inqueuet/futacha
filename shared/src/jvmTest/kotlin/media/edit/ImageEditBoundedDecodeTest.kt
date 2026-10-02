package com.valoser.futacha.shared.media.edit

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.asImage
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.util.ImageData
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import kotlin.test.*

/** B-8: the desktop editor decoded huge images at full resolution before downscaling. */
class ImageEditBoundedDecodeTest {
    private val gate = MediaFeatureGate().apply { update(MediaFeatureSettings(imageEditorEnabled = true)) }

    @Test fun hugeImageOpensAtTheEditorEdgeThroughSubsampledDecoding() = runBlocking {
        // 400M pixels: 1.6GB as the full-resolution bitmap Skia used to allocate first.
        val png = binaryPng(20_000, 20_000)
        val bounded = assertNotNull(decodeSubsampledImageEditBitmap(png, IMAGE_EDIT_MAX_EDGE, JVM_IMAGE_EDIT_FULL_DECODE_PIXELS))
        assertEquals(IMAGE_EDIT_MAX_EDGE, bounded.width); bounded.close()
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).build()
        try {
            ImageEditSession.open(ImageEditInput(ImageData(png, "huge.png")), gate, loader, PlatformContext.INSTANCE).use { session ->
                val raster = session.original
                assertEquals(IMAGE_EDIT_MAX_EDGE, raster.width); assertEquals(IMAGE_EDIT_MAX_EDGE, raster.height)
                assertEquals(0xff000000.toInt(), raster.argb[1024 * raster.width + 100])
                assertEquals(0xffffffff.toInt(), raster.argb[1024 * raster.width + raster.width - 100])
                assertTrue(session.export(ImageEditDocument()).bytes.isNotEmpty())
            }
        } finally { loader.shutdown() }
    }

    @Test fun ordinaryImagesKeepTheSkiaDecode() {
        assertNull(decodeSubsampledImageEditBitmap(encodeImageEditJpeg(pattern()), IMAGE_EDIT_MAX_EDGE, JVM_IMAGE_EDIT_FULL_DECODE_PIXELS))
        assertNull(decodeSubsampledImageEditBitmap(byteArrayOf(1, 2, 3), IMAGE_EDIT_MAX_EDGE, 0))
    }

    @Test fun subsampledDecodeOrientsAndScalesExactlyLikeSkia() = runBlocking {
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).build()
        val jpeg = encodeImageEditJpeg(pattern())
        try {
            for (orientation in 1..8) {
                val header = byteArrayOf(69, 120, 105, 102, 0, 0, 73, 73, 42, 0, 8, 0, 0, 0, 1, 0,
                    18, 1, 3, 0, 1, 0, 0, 0, orientation.toByte(), 0, 0, 0, 0, 0, 0, 0)
                val bytes = jpeg.copyOfRange(0, 2) + byteArrayOf(-1, -31, 0, (header.size + 2).toByte()) + header + jpeg.copyOfRange(2, jpeg.size)
                val skia = ImageEditSession.open(ImageEditInput(ImageData(bytes, "phone.jpg")), gate, loader, PlatformContext.INSTANCE)
                    .use { it.original }
                val full = imageEditPixels(assertNotNull(decodeSubsampledImageEditBitmap(bytes, IMAGE_EDIT_MAX_EDGE, 0)).asImage())
                assertEquals(skia.width, full.width, "EXIF $orientation"); assertEquals(skia.height, full.height, "EXIF $orientation")
                assertEquals(corners(skia), corners(full), "EXIF $orientation")
                val scaled = imageEditPixels(assertNotNull(decodeSubsampledImageEditBitmap(bytes, 32, 0)).asImage())
                assertEquals(32, maxOf(scaled.width, scaled.height), "EXIF $orientation")
                assertEquals(skia.width > skia.height, scaled.width > scaled.height, "EXIF $orientation")
                assertEquals(corners(skia), corners(scaled), "EXIF $orientation")
            }
        } finally { loader.shutdown() }
    }

    /** B4-4: Skia has no sampled decode, so WebP/HEIF/CMYK JPEG beyond its pixel cap are refused, not decoded at full size. */
    @Test fun sourcesImageIoCannotSubsampleAreRefusedBeyondTheSkiaPixelCap() = runBlocking {
        val webp = skiaWebp(64, 48)
        // No ImageIO WebP reader: the subsampled path cannot take it.
        assertNull(decodeSubsampledImageEditBitmap(webp, 32, 0))
        requireSkiaImageEditDecodable(webp, 64L * 48)
        assertEquals(MEDIA_EDITOR_OUT_OF_MEMORY_MESSAGE,
            assertFailsWith<IllegalStateException> { requireSkiaImageEditDecodable(webp, 64L * 48 - 1) }.message)
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).build()
        try {
            // Within the cap a WebP still opens through the editor's decoder.
            ImageEditSession.open(ImageEditInput(ImageData(webp, "small.webp")), gate, loader, PlatformContext.INSTANCE).use {
                assertEquals(64, it.original.width); assertEquals(48, it.original.height)
            }
            val request = ImageRequest.Builder(PlatformContext.INSTANCE).data(webp).size(32)
                .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED)
                .decoderFactory(JvmBoundedImageEditDecoder.Factory(fullDecodePixels = 0, skiaDecodePixels = 64L * 48 - 1)).build()
            val failure = assertIs<ErrorResult>(loader.execute(request)).throwable
            assertEquals(MEDIA_EDITOR_OUT_OF_MEMORY_MESSAGE, failure.message)
        } finally { loader.shutdown() }
    }

    private fun skiaWebp(width: Int, height: Int): ByteArray {
        val surface = Surface.makeRasterN32Premul(width, height)
        try {
            surface.canvas.clear(0xff336699.toInt())
            return surface.makeImageSnapshot().use { assertNotNull(it.encodeToData(EncodedImageFormat.WEBP, 90)).bytes }
        } finally { surface.close() }
    }

    private fun pattern() = EditRaster(80, 48, IntArray(80 * 48) { i ->
        val right = i % 80 >= 40; val bottom = i / 80 >= 24
        when { !right && !bottom -> 0xffff0000.toInt(); right && !bottom -> 0xff00ff00.toInt()
            right && bottom -> 0xff0000ff.toInt(); else -> 0xffffff00.toInt() }
    })

    private fun corners(r: EditRaster): List<Int> {
        val inset = maxOf(2, minOf(r.width, r.height) / 8)
        return listOf(inset to inset, r.width - 1 - inset to inset, r.width - 1 - inset to r.height - 1 - inset, inset to r.height - 1 - inset)
            .map { (x, y) ->
                val c = r.argb[y * r.width + x]; val red = c ushr 16 and 255; val green = c ushr 8 and 255
                when { red > 180 && green > 180 -> 3; red > 180 -> 0; green > 180 -> 1; else -> 2 }
            }
    }

    /** A 1-bit grayscale PNG, black on the left half and white on the right, streamed without a raster. */
    private fun binaryPng(width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
        fun chunk(type: String, data: ByteArray) {
            val typeBytes = type.encodeToByteArray()
            out.write(ByteBuffer.allocate(4).putInt(data.size).array()); out.write(typeBytes); out.write(data)
            val crc = CRC32().apply { update(typeBytes); update(data) }
            out.write(ByteBuffer.allocate(4).putInt(crc.value.toInt()).array())
        }
        chunk("IHDR", ByteBuffer.allocate(13).putInt(width).putInt(height).put(1).put(0).put(0).put(0).put(0).array())
        val rowBytes = (width + 7) / 8
        val row = ByteArray(1 + rowBytes).also { for (i in 1 + rowBytes / 2 until it.size) it[i] = 0xff.toByte() }
        val compressed = ByteArrayOutputStream()
        DeflaterOutputStream(compressed, Deflater(Deflater.BEST_SPEED)).use { z -> repeat(height) { z.write(row) } }
        chunk("IDAT", compressed.toByteArray())
        chunk("IEND", ByteArray(0))
        return out.toByteArray()
    }
}
