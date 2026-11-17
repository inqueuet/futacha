@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha

import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.media.video.*
import com.valoser.futacha.shared.media.video.model.*
import com.valoser.futacha.shared.media.prompt.*
import com.valoser.futacha.testing.video.VideoEditFixtures
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DeviceVideoEngineInstrumentedTest {
    @Test fun exportKeepsFrameTimesRotationAndBlackCover() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "video-engine-contract").apply { mkdirs() }
        try {
            for (name in listOf("portrait", "variable", "rotated", "asymmetric-rotated")) {
                val input = File(directory, "$name.mp4").apply { writeBytes(VideoEditFixtures.bytes(name)) }
                val output = File(directory, "$name-output.mp4")
                val info = inspectDeviceVideo(input.absolutePath)
                val document = MosaicDocument(listOf(MosaicRegion("cover", endUs = info.frames.durationUs, style = MosaicStyle.BLACK)))
                val preview = previewDeviceVideo(input.absolutePath, info, 0, document)
                val pixels = IntArray(preview.width * preview.height).also { preview.readPixels(it) }
                assertEquals(0xff000000.toInt(), pixels[preview.height / 2 * preview.width + preview.width / 2])
                exportDeviceVideo(context, input.absolutePath, info, document, output.absolutePath) {}
                preserveEditedVideoMetadata(input.absolutePath, output.absolutePath, document.regions.size)
                val written = output.readBytes()
                val metadata = VideoMetadataReader().read(written.size.toLong()) { offset, count -> written.copyOfRange(offset.toInt(), offset.toInt() + count) }
                val preserved = PreservedVideoMetadata.fromScan(metadata)
                assertEquals(1, preserved.edits.size)
                if (name in listOf("portrait", "variable")) {
                    assertEquals("青い鳥\nDialogue: <Picture 1> says \"hello\" & goodbye.", metadata.generation().candidates.single().positive)
                }
                assertArrayEquals(VideoEditFixtures.bytes(name), input.readBytes())
                val after = inspectDeviceVideo(output.absolutePath)
                verifyVideoEditTiming(info, after)
                val frame = previewDeviceVideo(output.absolutePath, after, after.frames.timeAt(after.frames.size / 2), MosaicDocument())
                val saved = IntArray(frame.width * frame.height).also { frame.readPixels(it) }
                val reference = previewDeviceVideo(input.absolutePath, info, info.frames.timeAt(info.frames.size / 2), MosaicDocument())
                val originalPixels = IntArray(reference.width * reference.height).also { reference.readPixels(it) }
                // Keep diagnostic frames on the test device when an orientation comparison fails.
                fun saveDiagnostic(label: String, values: IntArray, width: Int, height: Int) {
                    val image = android.graphics.Bitmap.createBitmap(values, width, height, android.graphics.Bitmap.Config.ARGB_8888)
                    try { File(context.cacheDir, "video-$name-$label.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
                    finally { image.recycle() }
                }
                for ((x, y) in listOf(.25f to .25f, .75f to .25f, .25f to .75f, .75f to .75f)) {
                    val a = originalPixels[(y * reference.height).toInt() * reference.width + (x * reference.width).toInt()]
                    val b = saved[(y * frame.height).toInt() * frame.width + (x * frame.width).toInt()]
                    for (shift in listOf(0, 8, 16)) {
                        val delta = kotlin.math.abs((a ushr shift and 255) - (b ushr shift and 255))
                        if (delta >= 35) {
                            saveDiagnostic("source", originalPixels, reference.width, reference.height)
                            saveDiagnostic("output", saved, frame.width, frame.height)
                            output.copyTo(File(context.cacheDir, "video-$name-output.mp4"), overwrite = true)
                        }
                        assertTrue("$name orientation at $x/$y channel $shift: $delta", delta < 35)
                    }
                }
                val color = saved[frame.height / 2 * frame.width + frame.width / 2]
                assertTrue("$name black=$color", (color ushr 16 and 255) < 20 && (color ushr 8 and 255) < 20 && (color and 255) < 20)
                assertTrue(saved.any { (it and 0x00ffffff) > 0x202020 })
            }
        } finally { directory.deleteRecursively() }
        Unit
    }
}
