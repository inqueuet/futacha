@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.ui.image

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.decode.SkiaImageDecoder
import coil3.fetch.SourceFetchResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.size.pxOrElse
import com.valoser.futacha.shared.ui.compat.createIosImageThumbnail
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import okio.Buffer
import okio.use
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateWithName
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGColorSpaceSRGB

// iOS registers only Skia's still-image decoder.
internal actual fun ImageRequest.Builder.staticImageDecoding(): ImageRequest.Builder = this

/** Skia decodes the full-resolution original before scaling; ImageIO subsamples. */
internal actual fun ImageRequest.Builder.boundedOriginalDecoding(): ImageRequest.Builder =
    decoderFactory(IosBoundedImageDecoder.Factory())

/**
 * Decodes at most the requested size with ImageIO's thumbnail API, which reads a
 * JPEG at a reduced scale instead of allocating the full-resolution bitmap. A
 * request without a bounded size, or bytes ImageIO cannot read, use Skia.
 */
internal class IosBoundedImageDecoder(
    private val source: ImageSource,
    private val options: Options
) : Decoder {
    override suspend fun decode(): DecodeResult {
        val bytes = source.source().use { it.readByteArray() }
        val maximumSide = maxOf(options.size.width.pxOrElse { 0 }, options.size.height.pxOrElse { 0 })
        decodeBounded(bytes, maximumSide)?.let { return it }
        return SkiaImageDecoder(ImageSource(Buffer().write(bytes), options.fileSystem), options).decode()
    }

    private fun decodeBounded(bytes: ByteArray, maximumSide: Int): DecodeResult? {
        if (maximumSide <= 0) return null
        val thumbnail = createIosImageThumbnail(bytes, maximumSide, applyOrientation = true) ?: return null
        try {
            val width = CGImageGetWidth(thumbnail).toInt()
            val height = CGImageGetHeight(thumbnail).toInt()
            if (width <= 0 || height <= 0) return null
            val rowBytes = width * 4
            val pixels = ByteArray(rowBytes * height)
            val colorSpace = CGColorSpaceCreateWithName(kCGColorSpaceSRGB) ?: return null
            try {
                val drawn = pixels.usePinned { pinned ->
                    val context = CGBitmapContextCreate(
                        pinned.addressOf(0), width.toULong(), height.toULong(), 8u, rowBytes.toULong(),
                        colorSpace, CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value
                    ) ?: return@usePinned false
                    try {
                        CGContextDrawImage(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), thumbnail)
                    } finally {
                        CGContextRelease(context)
                    }
                    true
                }
                if (!drawn) return null
            } finally {
                CGColorSpaceRelease(colorSpace)
            }
            val bitmap = Bitmap()
            val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
            if (!bitmap.installPixels(info, pixels, rowBytes)) {
                bitmap.close()
                return null
            }
            bitmap.setImmutable()
            return DecodeResult(image = bitmap.asImage(), isSampled = true)
        } finally {
            CFRelease(thumbnail)
        }
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder =
            IosBoundedImageDecoder(result.source, options)
    }
}
