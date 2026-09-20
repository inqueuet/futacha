package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.edit.EditRaster
import com.valoser.futacha.shared.media.video.model.*
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.util.*
import kotlinx.coroutines.*
import kotlin.test.*

class VideoEditExportTest {
    @Test fun temporalEllipseBlackCoverAndOverlappingMosaicNeverRevealOriginal(): Unit = runBlocking {
        val input = EditRaster(100, 100, IntArray(10_000) { 0xffeeeeee.toInt() })
        val cover = MosaicRegion("black", shape = MosaicShape.ELLIPSE, startUs = 100, endUs = 200, style = MosaicStyle.BLACK,
            keyframes = listOf(MosaicKeyframe(0, MosaicBounds(.5f, .5f, .6f, .6f))))
        val mosaic = cover.copy(id = "mosaic", style = MosaicStyle.PIXELATE, darkness = .1f)
        for (regions in listOf(listOf(cover, mosaic), listOf(mosaic, cover))) {
            val doc = MosaicDocument(regions)
            assertContentEquals(input.argb, renderVideoPreview(input, doc, 99).argb)
            assertContentEquals(input.argb, renderVideoPreview(input, doc, 200).argb)
            val pixels = renderVideoPreview(input, doc, 100).argb
            assertEquals(0xff000000.toInt(), pixels[50 * 100 + 50])
            assertEquals(0xffeeeeee.toInt(), pixels[21 * 100 + 21]) // rectangle corner is outside the ellipse.
        }
    }
    @Test fun motionUsesActualTimestampAndPreservesUncoveredPixels(): Unit = runBlocking {
        val input = EditRaster(100, 100, IntArray(10_000) { 0xffffffff.toInt() })
        val region = MosaicRegion("moving", endUs = 400, style = MosaicStyle.BLACK,
            keyframes = listOf(MosaicKeyframe(0, MosaicBounds(.2f, .5f, .2f, .2f)), MosaicKeyframe(300, MosaicBounds(.8f, .5f, .2f, .2f))))
        val image = renderVideoPreview(input, MosaicDocument(listOf(region)), 150)
        assertEquals(0xff000000.toInt(), image.argb[50 * 100 + 50])
        assertEquals(0xffffffff.toInt(), image.argb[50 * 100 + 20])
        assertContentEquals(IntArray(10_000) { 0xffffffff.toInt() }, input.argb)
    }
    @Test fun invalidOrUnreviewedVideoCannotStartExport() {
        val info = VideoEditInfo(100, 100, buildVideoFrameIndex(listOf(0, 100), 200), false, false, 0)
        assertFailsWith<IllegalArgumentException> { validateVideoEditDocument(MosaicDocument(), info) }
        val valid = MosaicDocument(listOf(MosaicRegion("one", endUs = 200)))
        validateVideoEditDocument(valid, info)
        assertFailsWith<IllegalArgumentException> { validateVideoEditDocument(valid, info.copy(hdr = true)) }
        assertFailsWith<IllegalArgumentException> { validateVideoEditDocument(valid.copy(regions = listOf(MosaicRegion("one", endUs = 300))), info) }
    }
    private suspend fun source(fs: FileSystem, gate: MediaFeatureGate): VideoEditSource {
        val bytes = byteArrayOf(0, 0, 0, 24) + "ftypisom".encodeToByteArray()
        var position = 0
        return VideoEditSource.import(fs, "/virtual/videos", "phone.mov", object : FileReadSource {
            override suspend fun read(out: ByteArray, offset: Int, length: Int): Int {
                if (position == bytes.size) return -1
                val count = minOf(length, bytes.size - position); bytes.copyInto(out, offset, position, position + count); position += count; return count
            }
        }, gate, gate.permit(MediaFeature.VIDEO_EDITOR)!!)
    }
    @Test fun savingOnlyCopiesOwnedOutputAndKeepsOriginalUnchanged(): Unit = runBlocking {
        val fs = InMemoryFileSystem(); val gate = MediaFeatureGate().apply { update(MediaFeatureSettings(videoEditorEnabled = true)) }
        val source = source(fs, gate); val target = SaveLocation.Path("saved")
        try {
            val original = fs.readBytes(source.path).getOrThrow()
            val output = source.path.substringBeforeLast('/') + "/edited-123-abc.mp4"
            val payload = ByteArray(600_000) { (it % 251).toByte() }; fs.writeBytes(output, payload).getOrThrow()
            val path = saveEditedVideo(source, fs, output, target)
            assertContentEquals(payload, fs.readBytes(target, path).getOrThrow())
            assertContentEquals(original, fs.readBytes(source.path).getOrThrow())
            assertFailsWith<IllegalArgumentException> { saveEditedVideo(source, fs, "/foreign/edited-123-abc.mp4", target) }
            gate.update(MediaFeatureSettings.Disabled)
            assertFailsWith<IllegalStateException> { saveEditedVideo(source, fs, output, target) }
            source.close()
            assertContentEquals(payload, fs.readBytes(target, path).getOrThrow())
        } finally { source.close() }
    }
    @Test fun disablingDuringDestinationCopyRemovesPartialOutput(): Unit = runBlocking {
        val fs = InMemoryFileSystem(); val gate = MediaFeatureGate().apply { update(MediaFeatureSettings(videoEditorEnabled = true)) }
        val source = source(fs, gate); val target = SaveLocation.Path("saved")
        val name = "edited-124-abc.mp4"; val output = source.path.substringBeforeLast('/') + "/$name"
        fs.writeBytes(output, ByteArray(600_000) { 12 }).getOrThrow()
        val cancelling = object : FileSystem by fs {
            override suspend fun writeByteStream(location: SaveLocation, relativePath: String, block: suspend (FileWriteSink) -> Unit): Result<Unit> =
                fs.writeByteStream(location, relativePath) { sink -> block(object : FileWriteSink {
                    override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
                        sink.write(bytes, offset, length); gate.update(MediaFeatureSettings.Disabled)
                    }
                }) }
        }
        try {
            assertFails { saveEditedVideo(source, cancelling, output, target) }
            assertFalse(fs.exists(target, "edited_videos/$name"))
            assertTrue(fs.exists(source.path))
        } finally { source.close() }
    }
}
