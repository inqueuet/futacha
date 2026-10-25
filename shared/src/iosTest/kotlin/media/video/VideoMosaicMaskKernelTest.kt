@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.valoser.futacha.shared.media.video

import androidx.compose.ui.graphics.toComposeImageBitmap
import com.valoser.futacha.shared.media.video.model.*
import com.valoser.futacha.shared.util.createFileSystem
import com.valoser.futacha.shared.util.iosAsyncVideoCompositionAvailable
import com.valoser.futacha.testing.video.VideoEditFixtures
import kotlinx.coroutines.runBlocking
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSURL
import kotlinx.cinterop.*
import platform.CoreGraphics.*
import platform.CoreImage.*
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy
import kotlin.test.*

/** Compare real rendered pixels with ordinary rectangles, independently of
 * bitmap row order. Each atlas slot and an asymmetric local mask are exercised. */
class VideoMosaicMaskKernelTest {
    private val input = CIImage.imageWithColor(CIColor.colorWithRed(1.0, 0.0, 0.0, 1.0))
        .imageByCroppingToRect(CGRectMake(0.0, 0.0, 256.0, 256.0))
    private val bounds = MosaicBounds(.5f, .5f, .5f, .5f)

    @Test fun aFullMaskMatchesTheSameRectangleInEveryAtlasSlot() {
        val kernel = VideoMosaicKernel()
        val context = videoRenderingContext()
        val expected = pixels(context, kernel.apply(input, document(0, null), 0))
        assertTrue(expected.any { it and 0x00ffffff == 0 })
        assertTrue(expected.any { it and 0x00ffffff != 0 })
        for (slot in 0 until 16) {
            samePixels(expected, pixels(context, kernel.apply(input, document(slot, MosaicMask.FULL), 0)), "full mask slot=$slot")
        }
    }

    @Test fun anAsymmetricLocalMaskKeepsItsTopLeftOrientationInEveryAtlasSlot() {
        val kernel = VideoMosaicKernel()
        val context = videoRenderingContext()
        val quarter = MosaicBounds(.375f, .375f, .25f, .25f)
        val expected = pixels(context, kernel.apply(input, document(0, null, quarter), 0))
        val mask = MosaicMask.from { x, y -> x < 64 && y < 64 }
        for (slot in 0 until 16) {
            samePixels(expected, pixels(context, kernel.apply(input, document(slot, mask), 0)), "top-left mask slot=$slot")
        }
    }

    @Test fun changingFullEmptyAndAbsentMasksDoesNotReuseStaleCoverage() {
        val kernel = VideoMosaicKernel()
        val context = videoRenderingContext()
        val covered = pixels(context, kernel.apply(input, document(0, null), 0))
        val original = pixels(context, input)
        val region = region("changing").copy(masks = listOf(
            MosaicMaskKeyframe(0, MosaicMask.FULL), MosaicMaskKeyframe(100, MosaicMask.EMPTY), MosaicMaskKeyframe(200, null)))
        val doc = MosaicDocument(listOf(region))
        samePixels(covered, pixels(context, kernel.apply(input, doc, 0)), "full")
        samePixels(original, pixels(context, kernel.apply(input, doc, 100)), "empty")
        samePixels(covered, pixels(context, kernel.apply(input, doc, 200)), "rectangle fallback")
    }

    @Test fun previewPlaybackAndExportCoverTheSameAsymmetricContour(): Unit = runBlocking {
        val fs = createFileSystem()
        val root = "video_contour_pixel_contract"
        try {
            for (name in listOf("variable", "asymmetric-rotated")) {
                val input = fs.resolveAbsolutePath("$root/$name.mp4")
                val output = fs.resolveAbsolutePath("$root/$name-edited.mp4")
                fs.writeBytes(input, VideoEditFixtures.bytes(name)).getOrThrow()
                val info = inspectDeviceVideo(input)
                val mask = MosaicMask.from { x, y -> x < 64 && y < 64 }
                val whole = MosaicBounds(.5f, .5f, 1f, 1f)
                val contour = region("contour", whole).copy(endUs = info.frames.durationUs,
                    masks = listOf(MosaicMaskKeyframe(0, mask)))
                val doc = MosaicDocument(listOf(contour))
                val reference = MosaicDocument(listOf(region("rectangle", MosaicBounds(.25f, .25f, .5f, .5f))
                    .copy(endUs = info.frames.durationUs)))
                val times = listOf(info.frames.timeAt(0), info.frames.timeAt(info.frames.size / 2)).distinct()
                val expected = times.associateWith { time ->
                    val frame = previewDeviceVideo(input, info, time, reference)
                    IntArray(frame.width * frame.height).also { frame.readPixels(it) }
                }
                for (time in times) {
                    val frame = previewDeviceVideo(input, info, time, doc)
                    val actual = IntArray(frame.width * frame.height).also { frame.readPixels(it) }
                    sameVideoPoints(expected.getValue(time), actual, info.width, info.height, "$name preview $time")
                }
                for (async in listOf(false, iosAsyncVideoCompositionAvailable()).distinct()) {
                    val editing = VideoEditPlayback(info, doc, 0, {}, {})
                    val item = editedVideoPlayerItem(NSURL.fileURLWithPath(input), editing, async)
                    val generator = AVAssetImageGenerator(item.asset).apply {
                        videoComposition = item.videoComposition
                        requestedTimeToleranceBefore = CMTimeMake(1, 1_000_000)
                        requestedTimeToleranceAfter = CMTimeMake(1, 1_000_000)
                    }
                    try {
                        for (time in times) {
                            val cg = requireNotNull(generator.copyCGImageAtTime(CMTimeMake(time, 1_000_000), null, null))
                            try {
                                assertEquals(info.width, CGImageGetWidth(cg).toInt())
                                assertEquals(info.height, CGImageGetHeight(cg).toInt())
                                sameVideoPoints(expected.getValue(time), pixels(cg), info.width, info.height, "$name playback async=$async $time")
                            } finally { CGImageRelease(cg) }
                        }
                    } finally { generator.cancelAllCGImageGeneration(); editing.close() }
                }
                exportDeviceVideo(null, input, info, doc, output) {}
                val after = inspectDeviceVideo(output)
                verifyVideoEditTiming(info, after)
                for (time in times) {
                    val frame = previewDeviceVideo(output, after, time, MosaicDocument())
                    val actual = IntArray(frame.width * frame.height).also { frame.readPixels(it) }
                    sameVideoPoints(expected.getValue(time), actual, info.width, info.height, "$name export $time")
                }
                assertContentEquals(VideoEditFixtures.bytes(name), fs.readBytes(input).getOrThrow())
            }
        } finally { fs.deleteRecursively(root).getOrThrow() }
    }

    private fun sameVideoPoints(expected: IntArray, actual: IntArray, width: Int, height: Int, label: String) {
        assertEquals(width * height, actual.size, label)
        for ((x, y) in listOf(.25f to .25f, .75f to .25f, .25f to .75f, .75f to .75f)) {
            val at = (y * height).toInt() * width + (x * width).toInt()
            for (shift in listOf(0, 8, 16)) {
                val value = actual[at] ushr shift and 255
                val reference = expected[at] ushr shift and 255
                assertTrue(kotlin.math.abs(value - reference) < 35, "$label $x/$y channel=$shift: $value vs $reference")
                if (x == .25f && y == .25f) assertTrue(value < 20, "$label must conceal the covered point")
            }
        }
    }

    private fun region(id: String, area: MosaicBounds = bounds) = MosaicRegion(id, endUs = 1_000,
        style = MosaicStyle.BLACK, maskMargin = 0, keyframes = listOf(MosaicKeyframe(0, area)))

    private fun document(slot: Int, mask: MosaicMask?, area: MosaicBounds = bounds): MosaicDocument =
        MosaicDocument(List(slot + 1) { index ->
            if (index == slot) region("region-$index", area).copy(masks = if (mask == null) emptyList() else listOf(MosaicMaskKeyframe(0, mask)))
            else region("inactive-$index").copy(startUs = 2_000, endUs = 3_000)
        })

    private fun pixels(context: CIContext, image: CIImage): IntArray {
        val cg = requireNotNull(context.createCGImage(image, image.extent))
        try { return pixels(cg) } finally { CGImageRelease(cg) }
    }

    private fun pixels(cg: CGImageRef): IntArray {
        val data = requireNotNull(UIImagePNGRepresentation(UIImage.imageWithCGImage(cg)))
        val bytes = ByteArray(data.length.toInt()).also { b -> b.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) } }
        val skia = org.jetbrains.skia.Image.makeFromEncoded(bytes)
        try {
            val bitmap = skia.toComposeImageBitmap()
            return IntArray(bitmap.width * bitmap.height).also { bitmap.readPixels(it) }
        } finally { skia.close() }
    }

    private fun samePixels(expected: IntArray, actual: IntArray, label: String) {
        assertEquals(expected.size, actual.size, label)
        val mismatch = expected.indices.firstOrNull { expected[it] != actual[it] }
        assertNull(mismatch, "$label: first different pixel=$mismatch, expected=${mismatch?.let { expected[it] }}, actual=${mismatch?.let { actual[it] }}")
    }
}
