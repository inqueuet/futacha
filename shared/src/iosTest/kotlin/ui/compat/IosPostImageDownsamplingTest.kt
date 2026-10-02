@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.util.ImageData
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRef
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.*
import platform.posix.memcpy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosPostImageDownsamplingTest {
    @Test fun decoderLimitsPixelsAndPreservesAspectRatio() {
        val bytes = solidPng(120, 60)
        val image = assertNotNull(downsampleIosPostImage(bytes, 32))
        image.size.useContents {
            assertEquals(32.0, width)
            assertEquals(16.0, height)
        }
        assertNull(downsampleIosPostImage(byteArrayOf(1, 2, 3), 32))
        assertNull(downsampleIosPostImage(bytes, 0))
    }

    @Test fun largeAttachmentIsDownsampledBeforeJpegCompression() = runBlocking {
        // JPEG input: PNG/GIF/WebP that fit are now sent losslessly without decoding (U-1).
        val result = compressCompatPostImage(ImageData(solidJpeg(3000, 3000), "photo.jpeg"), 1024 * 1024).getOrThrow()
        assertTrue(result.bytes.size <= 1024 * 1024)
        assertEquals("photo.jpg", result.fileName)
        val image = assertNotNull(downsampleIosPostImage(result.bytes, 4000))
        image.size.useContents {
            assertTrue(width * height <= 8_000_000)
            assertTrue(width > 0 && height > 0)
        }
    }

    @Test fun fittingPngIsSentLosslesslyWithItsExtension() = runBlocking {
        val source = solidPng(40, 20, transparentLeftHalf = true)
        val result = compressCompatPostImage(ImageData(source, "clip.PNG"), 1024 * 1024).getOrThrow()
        assertEquals("clip.png", result.fileName)
        assertEquals(CompatPostImageFormat.PNG, detectCompatPostImageFormat(result.bytes))
        assertEquals(0, pixelAt(result.bytes, 5, 10).alpha, "transparency must survive")
    }

    @Test fun gifIsNotFlattenedToAStillJpeg() = runBlocking {
        // Header-only GIF: ImageIO cannot decode it, so a JPEG re-encode would fail.
        val gif = "GIF89a".encodeToByteArray() + ByteArray(32)
        val result = compressCompatPostImage(ImageData(gif, "anim.gif"), 1024).getOrThrow()
        assertEquals("anim.gif", result.fileName)
        assertTrue(gif.contentEquals(result.bytes))
    }

    @Test fun transparentImageAboveTheLimitIsFlattenedOnWhiteNotBlack() = runBlocking {
        val source = noisePng(512, 512)
        val limit = source.size / 4
        val result = compressCompatPostImage(ImageData(source, "noise.png"), limit).getOrThrow()
        assertTrue(result.bytes.size <= limit)
        assertEquals("noise.jpg", result.fileName)
        val pixel = pixelAt(result.bytes, 2, 2)
        assertTrue(pixel.red > 230 && pixel.green > 230 && pixel.blue > 230, "transparent area became $pixel")
    }

    @Test fun aspectRatioIsReadFromTheHeaderWithoutDecoding() {
        assertEquals(2f, compatPostImageAspectRatio(solidPng(120, 60)))
        assertEquals(0.5f, compatPostImageAspectRatio(solidPng(30, 60)))
        assertNull(compatPostImageAspectRatio(byteArrayOf(1, 2, 3)))
        assertNull(compatPostImageAspectRatio(ByteArray(0)))
    }

    @Test fun imageHashOfALargeImageMatchesTheSameImageAtSmallSize() = runBlocking {
        val large = assertNotNull(computeCompatImagePhashFromBytes(splitPng(2400, 1600)))
        val small = assertNotNull(computeCompatImagePhashFromBytes(splitPng(240, 160)))
        assertTrue(
            com.valoser.futacha.shared.compat.CompatImagePhash.hammingDistance(large, small) <= 2,
            "large=$large small=$small"
        )
        assertNull(computeCompatImagePhashFromBytes(byteArrayOf(1, 2, 3)))
    }

    /** Left half red, right half blue, top quarter green: a hash with structure. */
    private fun splitPng(width: Int, height: Int): ByteArray = autoreleasepool {
        UIGraphicsBeginImageContextWithOptions(CGSizeMake(width.toDouble(), height.toDouble()), false, 1.0)
        try {
            UIColor.redColor.setFill()
            UIRectFill(CGRectMake(0.0, 0.0, width / 2.0, height.toDouble()))
            UIColor.blueColor.setFill()
            UIRectFill(CGRectMake(width / 2.0, 0.0, width / 2.0, height.toDouble()))
            UIColor.greenColor.setFill()
            UIRectFill(CGRectMake(0.0, 0.0, width.toDouble(), height / 4.0))
            val image = assertNotNull(UIGraphicsGetImageFromCurrentImageContext())
            val data = assertNotNull(UIImagePNGRepresentation(image))
            ByteArray(data.length.toInt()).also { bytes ->
                bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
            }
        } finally {
            UIGraphicsEndImageContext()
        }
    }

    private fun solidPng(width: Int, height: Int, transparentLeftHalf: Boolean = false): ByteArray = autoreleasepool {
        UIGraphicsBeginImageContextWithOptions(CGSizeMake(width.toDouble(), height.toDouble()), false, 1.0)
        try {
            UIColor.redColor.setFill()
            val left = if (transparentLeftHalf) width / 2.0 else 0.0
            UIRectFill(CGRectMake(left, 0.0, width - left, height.toDouble()))
            val image = assertNotNull(UIGraphicsGetImageFromCurrentImageContext())
            assertNotNull(UIImagePNGRepresentation(image)).toTestBytes()
        } finally {
            UIGraphicsEndImageContext()
        }
    }

    private fun solidJpeg(width: Int, height: Int): ByteArray = autoreleasepool {
        UIGraphicsBeginImageContextWithOptions(CGSizeMake(width.toDouble(), height.toDouble()), true, 1.0)
        try {
            UIColor.redColor.setFill()
            UIRectFill(CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
            val image = assertNotNull(UIGraphicsGetImageFromCurrentImageContext())
            assertNotNull(UIImageJPEGRepresentation(image, 0.9)).toTestBytes()
        } finally {
            UIGraphicsEndImageContext()
        }
    }

    /** Left quarter fully transparent, the rest per-pixel noise so PNG cannot compress it. */
    private fun noisePng(width: Int, height: Int): ByteArray {
        val random = kotlin.random.Random(7)
        val rgba = ByteArray(width * height * 4)
        for (y in 0 until height) {
            for (x in width / 4 until width) {
                val offset = (y * width + x) * 4
                rgba[offset] = random.nextInt(256).toByte()
                rgba[offset + 1] = random.nextInt(256).toByte()
                rgba[offset + 2] = random.nextInt(256).toByte()
                rgba[offset + 3] = 0xff.toByte()
            }
        }
        return autoreleasepool {
            withRgbaContext(width, height, rgba) { context ->
                val cgImage = assertNotNull(CGBitmapContextCreateImage(context))
                try {
                    assertNotNull(UIImagePNGRepresentation(UIImage.imageWithCGImage(cgImage))).toTestBytes()
                } finally {
                    CGImageRelease(cgImage)
                }
            }
        }
    }

    private data class Rgba(val red: Int, val green: Int, val blue: Int, val alpha: Int)

    /** Decodes [bytes] and samples the pixel at ([x], [y]) from the top-left corner. */
    private fun pixelAt(bytes: ByteArray, x: Int, y: Int): Rgba = autoreleasepool {
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        val cgImage = assertNotNull(assertNotNull(UIImage.imageWithData(data)).CGImage)
        val width = CGImageGetWidth(cgImage).toDouble()
        val height = CGImageGetHeight(cgImage).toDouble()
        val raw = ByteArray(4)
        withRgbaContext(1, 1, raw) { context ->
            // Core Graphics draws from the bottom-left corner.
            CGContextDrawImage(context, CGRectMake(-x.toDouble(), y + 1.0 - height, width, height), cgImage)
        }
        Rgba(raw[0].toInt() and 0xff, raw[1].toInt() and 0xff, raw[2].toInt() and 0xff, raw[3].toInt() and 0xff)
    }

    private fun <T> withRgbaContext(width: Int, height: Int, pixels: ByteArray, block: (CGContextRef) -> T): T {
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        try {
            return pixels.usePinned { pinned ->
                val context = assertNotNull(
                    CGBitmapContextCreate(
                        data = pinned.addressOf(0),
                        width = width.toULong(),
                        height = height.toULong(),
                        bitsPerComponent = 8u,
                        bytesPerRow = (width * 4).toULong(),
                        space = colorSpace,
                        bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value
                    )
                )
                try {
                    block(context)
                } finally {
                    CGContextRelease(context)
                }
            }
        } finally {
            CGColorSpaceRelease(colorSpace)
        }
    }

    private fun NSData.toTestBytes(): ByteArray =
        ByteArray(length.toInt()).also { bytes ->
            if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
        }
}
