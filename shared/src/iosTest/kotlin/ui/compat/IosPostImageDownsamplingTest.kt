@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.util.ImageData
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
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
        val result = compressCompatPostImage(ImageData(solidPng(3000, 3000), "photo.png"), 1024 * 1024).getOrThrow()
        assertTrue(result.bytes.size <= 1024 * 1024)
        assertEquals("photo.jpg", result.fileName)
        val image = assertNotNull(downsampleIosPostImage(result.bytes, 4000))
        image.size.useContents {
            assertTrue(width * height <= 8_000_000)
            assertTrue(width > 0 && height > 0)
        }
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

    private fun solidPng(width: Int, height: Int): ByteArray = autoreleasepool {
        UIGraphicsBeginImageContextWithOptions(CGSizeMake(width.toDouble(), height.toDouble()), false, 1.0)
        try {
            UIColor.redColor.setFill()
            UIRectFill(CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
            val image = assertNotNull(UIGraphicsGetImageFromCurrentImageContext())
            val data = assertNotNull(UIImagePNGRepresentation(image))
            ByteArray(data.length.toInt()).also { bytes ->
                bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
            }
        } finally {
            UIGraphicsEndImageContext()
        }
    }
}
