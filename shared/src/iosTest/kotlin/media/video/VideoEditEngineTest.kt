package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.media.video.model.*
import com.valoser.futacha.shared.media.prompt.*
import com.valoser.futacha.shared.util.createFileSystem
import com.valoser.futacha.testing.video.VideoEditFixtures
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class VideoEditEngineTest {
    @Test fun nativeExportsKeepVfrRotationAndBlackPixels(): Unit = runBlocking {
        val fs = createFileSystem(); val root = "video_engine_contract"
        fs.deleteRecursively(root).getOrThrow()
        var passed = false
        try {
            for (name in listOf("portrait", "variable", "rotated", "asymmetric-rotated")) {
                val input = fs.resolveAbsolutePath("$root/$name.mp4")
                val output = fs.resolveAbsolutePath("$root/$name-edited.mp4")
                fs.writeBytes(input, VideoEditFixtures.bytes(name)).getOrThrow()
                val info = inspectDeviceVideo(input)
                val document = MosaicDocument(listOf(MosaicRegion("cover", endUs = info.frames.durationUs, style = MosaicStyle.BLACK)))
                val preview = previewDeviceVideo(input, info, 0, document)
                val pixels = IntArray(preview.width * preview.height).also { preview.readPixels(it) }
                assertEquals(0xff000000.toInt(), pixels[preview.height / 2 * preview.width + preview.width / 2], name)
                exportDeviceVideo(null, input, info, document, output) {}
                preserveEditedVideoMetadata(input, output, document.regions.size)
                val written = fs.readBytes(output).getOrThrow()
                val metadata = VideoMetadataReader().read(written.size.toLong()) { offset, count -> written.copyOfRange(offset.toInt(), offset.toInt() + count) }
                val preserved = PreservedVideoMetadata.fromScan(metadata)
                assertEquals(1, preserved.edits.size)
                if (name in listOf("portrait", "variable")) {
                    assertEquals("青い鳥\nDialogue: <Picture 1> says \"hello\" & goodbye.", metadata.generation().candidates.single().positive)
                }
                assertContentEquals(VideoEditFixtures.bytes(name), fs.readBytes(input).getOrThrow())
                val after = inspectDeviceVideo(output)
                verifyVideoEditTiming(info, after)
                assertTrue(kotlin.math.abs(info.frames.timeAt(0) - after.frames.timeAt(0)) <= 1_000, "$name presentation start")
                val beforeAudio = decodedVideoTestAudio(input); val afterAudio = decodedVideoTestAudio(output)
                assertEquals(beforeAudio.first, afterAudio.first, "$name audio start")
                assertTrue(beforeAudio.second.contentEquals(afterAudio.second), "$name decoded audio bytes ${beforeAudio.second.size} -> ${afterAudio.second.size}; first diff ${beforeAudio.second.indices.firstOrNull { it >= afterAudio.second.size || beforeAudio.second[it] != afterAudio.second[it] }}")
                val frame = previewDeviceVideo(output, after, after.frames.timeAt(after.frames.size / 2), MosaicDocument())
                val saved = IntArray(frame.width * frame.height).also { frame.readPixels(it) }
                val reference = previewDeviceVideo(input, info, info.frames.timeAt(info.frames.size / 2), MosaicDocument())
                val originalPixels = IntArray(reference.width * reference.height).also { reference.readPixels(it) }
                for ((x, y) in listOf(.25f to .25f, .75f to .25f, .25f to .75f, .75f to .75f)) {
                    val a = originalPixels[(y * reference.height).toInt() * reference.width + (x * reference.width).toInt()]
                    val b = saved[(y * frame.height).toInt() * frame.width + (x * frame.width).toInt()]
                    for (shift in listOf(0, 8, 16)) { val delta = kotlin.math.abs((a ushr shift and 255) - (b ushr shift and 255)); assertTrue(delta < 35, "$name orientation at $x/$y channel $shift: $delta") }
                }
                val center = saved[frame.height / 2 * frame.width + frame.width / 2]
                assertTrue((center ushr 16 and 255) < 20 && (center ushr 8 and 255) < 20 && (center and 255) < 20, "$name center=$center")
                assertTrue(saved.any { (it and 0x00ffffff) > 0x202020 }, "outside of mask remains visible: $name")
            }
            passed = true
        } finally {
            if (passed) fs.deleteRecursively(root).getOrThrow()
            else println("Video export diagnostics: ${fs.resolveAbsolutePath(root)}")
        }
    }
    @Test fun exportHonorsVfrRegionStartAndExclusiveEnd(): Unit = runBlocking {
        val fs = createFileSystem(); val root = "video_temporal_contract"
        try {
            val input = fs.resolveAbsolutePath("$root/input.mp4")
            val output = fs.resolveAbsolutePath("$root/output.mp4")
            fs.writeBytes(input, VideoEditFixtures.bytes("variable")).getOrThrow()
            val info = inspectDeviceVideo(input)
            val region = MosaicRegion("timed", startUs = 1_000_000, endUs = 1_100_000, style = MosaicStyle.BLACK,
                keyframes = listOf(MosaicKeyframe(0, MosaicBounds(.5f, .5f, 1f, 1f))))
            exportDeviceVideo(null, input, info, MosaicDocument(listOf(region)), output) {}
            val after = inspectDeviceVideo(output); verifyVideoEditTiming(info, after)
            for (index in listOf(29, 30, 31)) {
                val bitmap = previewDeviceVideo(output, after, after.frames.timeAt(index), MosaicDocument())
                val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.readPixels(it) }
                if (index == 30) assertTrue(pixels.all { (it ushr 16 and 255) < 20 && (it ushr 8 and 255) < 20 && (it and 255) < 20 })
                else assertTrue(pixels.any { (it ushr 16 and 255) > 100 })
            }
        } finally { fs.deleteRecursively(root).getOrThrow() }
    }
}
