@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.valoser.futacha.shared.media.video

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.valoser.futacha.shared.media.video.model.*
import com.valoser.futacha.shared.util.createFileSystem
import com.valoser.futacha.testing.video.VideoEditFixtures
import kotlinx.cinterop.*
import kotlinx.coroutines.runBlocking
import platform.AVFoundation.*
import platform.CoreGraphics.CGImageRelease
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSURL
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy
import kotlin.test.*

class EditedVideoCompositionTest {
    @Test fun playbackCompositionUsesUprightCoordinatesAndActualFrameTimes(): Unit = runBlocking {
        val fs = createFileSystem(); val root = "edited_playback_contract"
        try {
            for (name in listOf("variable", "asymmetric-rotated")) {
                val path = fs.resolveAbsolutePath("$root/$name.mp4")
                fs.writeBytes(path, VideoEditFixtures.bytes(name)).getOrThrow()
                val info = inspectDeviceVideo(path)
                val sampleIndices = if (name == "variable") listOf(29, 30, 31) else listOf(0, info.frames.size / 2)
                val region = MosaicRegion("cover", startUs = if (name == "variable") 1_000_000 else 0,
                    endUs = if (name == "variable") 1_100_000 else info.frames.durationUs, style = MosaicStyle.BLACK)
                val doc = MosaicDocument(listOf(region))
                val editing = VideoEditPlayback(info, doc, 0, {}, {})
                val item = editedVideoPlayerItem(NSURL.fileURLWithPath(path), editing)
                val generator = AVAssetImageGenerator(item.asset).apply {
                    videoComposition = item.videoComposition
                    requestedTimeToleranceBefore = CMTimeMake(1, 1_000_000)
                    requestedTimeToleranceAfter = CMTimeMake(1, 1_000_000)
                }
                try {
                    for (index in sampleIndices) {
                        val time = info.frames.timeAt(index)
                        val actual = generator.frame(time)
                        val expected = previewDeviceVideo(path, info, time, doc)
                        assertEquals(info.width, actual.width, name)
                        assertEquals(info.height, actual.height, name)
                        val a = IntArray(actual.width * actual.height).also { actual.readPixels(it) }
                        val b = IntArray(expected.width * expected.height).also { expected.readPixels(it) }
                        for ((x, y) in listOf(.25f to .25f, .75f to .25f, .25f to .75f, .75f to .75f, .5f to .5f)) {
                            val av = a[(y * actual.height).toInt() * actual.width + (x * actual.width).toInt()]
                            val bv = b[(y * expected.height).toInt() * expected.width + (x * expected.width).toInt()]
                            for (shift in listOf(0, 8, 16)) assertTrue(kotlin.math.abs((av ushr shift and 255) - (bv ushr shift and 255)) < 35,
                                "$name at $time / $x,$y differs: $av vs $bv")
                        }
                    }
                } finally { generator.cancelAllCGImageGeneration(); editing.close() }
                assertContentEquals(VideoEditFixtures.bytes(name), fs.readBytes(path).getOrThrow())
            }
        } finally { fs.deleteRecursively(root).getOrThrow() }
    }

    private fun AVAssetImageGenerator.frame(timeUs: Long): ImageBitmap {
        val image = requireNotNull(copyCGImageAtTime(CMTimeMake(timeUs, 1_000_000), null, null))
        try {
            val data = requireNotNull(UIImagePNGRepresentation(UIImage.imageWithCGImage(image)))
            val bytes = ByteArray(data.length.toInt()).also { b -> b.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) } }
            val skia = org.jetbrains.skia.Image.makeFromEncoded(bytes)
            try { return skia.toComposeImageBitmap() } finally { skia.close() }
        } finally { CGImageRelease(image) }
    }
}
