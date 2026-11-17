package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.util.createFileSystem
import com.valoser.futacha.testing.video.VideoEditFixtures
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DeviceVideoSourceTest {
    @Test fun nativeReaderPreservesRealVfrTimesRotationAndAudio() = runBlocking {
        for (name in listOf("landscape", "portrait", "variable", "rotated", "asymmetric-rotated")) {
            withVideo(name) { source ->
                val info = source.useFile { inspectDeviceVideo(it) }
                val portrait = name in listOf("portrait", "rotated", "asymmetric-rotated")
                assertEquals(if (portrait) 240 else 320, info.width, name)
                assertEquals(if (portrait) 320 else 240, info.height, name)
                assertEquals(if (name in listOf("rotated", "asymmetric-rotated")) 90 else 0, info.rotationDegrees, name)
                assertEquals(name in listOf("landscape", "variable", "rotated"), info.hasAudio, name)
                assertFalse(info.hdr, name)
                val expectedCount = when (name) { "variable", "rotated" -> 40; "asymmetric-rotated" -> 6; else -> 60 }
                assertEquals(expectedCount, info.frames.size, name)
                if (name == "asymmetric-rotated") { assertEquals(0L, info.frames.timeAt(0)); assertEquals(600_000L, info.frames.durationUs) }
                if (name in listOf("variable", "rotated")) {
                    assertEquals(1_000_000L, info.frames.timeAt(30))
                    assertEquals(1_100_000L, info.frames.timeAt(31))
                    assertTrue(kotlin.math.abs(info.frames.durationUs - 1_933_333) <= 1, "recorded video end, $name: ${info.frames.durationUs}")
                    assertEquals(info.frames.durationUs, info.frames.endAfter(1_900_000))
                }
            }
        }
    }
    @Test fun movieInputSharesItsExactBytesAndPromptToggleDoesNotInvalidateVideo() = runBlocking {
        withVideo("rotated") { source ->
            val fs = createFileSystem()
            assertContentEquals(VideoEditFixtures.bytes("rotated"), fs.readBytes(source.path).getOrThrow())
            val a = source.useFile { inspectDeviceVideo(it) }
            val b = source.useFile { inspectDeviceVideo(it) }
            verifyVideoEditTiming(a, b)
        }
    }
    @Test fun nativeReaderRejectsTruncatedLocalDataAndRemoteUrls(): Unit = runBlocking {
        val fs = createFileSystem(); val path = "video_source_contract/broken.mp4"
        try {
            fs.writeBytes(path, VideoEditFixtures.bytes("landscape").copyOf(24)).getOrThrow()
            assertFails { inspectDeviceVideo(fs.resolveAbsolutePath(path)) }
            assertFailsWith<IllegalArgumentException> { inspectDeviceVideo("https://example.invalid/video.mp4") }
        } finally { fs.deleteRecursively("video_source_contract").getOrThrow() }
    }
    private suspend fun withVideo(name: String, test: suspend (VideoEditSource) -> Unit) {
        val fs = createFileSystem(); val gate = MediaFeatureGate().apply { update(MediaFeatureSettings(videoEditorEnabled = true)) }
        val dir = "video_source_contract/$name"; val selected = "$dir/device-original.mp4"
        var source: VideoEditSource? = null
        try {
            fs.writeBytes(selected, VideoEditFixtures.bytes(name)).getOrThrow()
            val imported = fs.readByteStream(selected) { reader ->
                VideoEditSource.import(fs, fs.resolveAbsolutePath("$dir/work"), name, reader, gate, gate.permit(MediaFeature.VIDEO_EDITOR)!!)
            }.getOrThrow()
            source = imported
            gate.update(MediaFeatureSettings(videoEditorEnabled = true, promptDisplayEnabled = true))
            test(imported)
            imported.close()
            assertFalse(fs.exists(imported.path))
            assertContentEquals(VideoEditFixtures.bytes(name), fs.readBytes(selected).getOrThrow())
        } finally {
            source?.close()
            fs.deleteRecursively(dir).getOrThrow()
        }
    }
}
