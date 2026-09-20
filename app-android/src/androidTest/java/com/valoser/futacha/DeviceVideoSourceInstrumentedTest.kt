@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha

import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.video.*
import com.valoser.futacha.shared.util.createFileSystem
import com.valoser.futacha.testing.video.VideoEditFixtures
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DeviceVideoSourceInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun extractorPreservesVfrPresentationTimesRotationAndAudio() = runBlocking {
        for (name in listOf("landscape", "portrait", "variable", "rotated", "asymmetric-rotated")) {
            withVideo(name) { source ->
                val info = source.useFile { inspectDeviceVideo(it) }
                val portrait = name in listOf("portrait", "rotated", "asymmetric-rotated")
                assertEquals(name, if (portrait) 240 else 320, info.width)
                assertEquals(name, if (portrait) 320 else 240, info.height)
                assertEquals(if (name in listOf("rotated", "asymmetric-rotated")) 90 else 0, info.rotationDegrees)
                assertEquals(name, name in listOf("landscape", "variable", "rotated"), info.hasAudio)
                assertFalse(info.hdr)
                assertEquals(when (name) { "variable", "rotated" -> 40; "asymmetric-rotated" -> 6; else -> 60 }, info.frames.size)
                if (name == "asymmetric-rotated") { assertEquals(0L, info.frames.timeAt(0)); assertEquals(600_000L, info.frames.durationUs) }
                if (name in listOf("variable", "rotated")) {
                    assertEquals(1_000_000L, info.frames.timeAt(30)); assertEquals(1_100_000L, info.frames.timeAt(31))
                    // Container duration can be rounded to the movie timescale.
                    // This is within the same 1ms tolerance used by output verification.
                    assertTrue("recorded video end: ${info.frames.durationUs}", kotlin.math.abs(info.frames.durationUs - 1_933_333) <= 1000)
                }
            }
        }
    }
    @Test fun inputIsCopiedOnceAndFeatureRevocationPreventsReusingOwnedFile() = runBlocking {
        withVideo("rotated") { source ->
            assertArrayEquals(VideoEditFixtures.bytes("rotated"), File(source.path).readBytes())
            val a = source.useFile { inspectDeviceVideo(it) }
            verifyVideoEditTiming(a, source.useFile { inspectDeviceVideo(it) })
            source.close()
            assertFalse(File(source.path).exists())
            assertThrows(IllegalStateException::class.java) { runBlocking { source.useFile {} } }
        }
    }
    @Test fun extractorRejectsRemoteAndTruncatedInput() = runBlocking {
        val broken = File(context.cacheDir, "device-video-broken.mp4")
        try {
            broken.writeBytes(VideoEditFixtures.bytes("landscape").copyOf(24))
            assertThrows(Exception::class.java) { runBlocking { inspectDeviceVideo(broken.absolutePath) } }
            assertThrows(IllegalArgumentException::class.java) { runBlocking { inspectDeviceVideo("https://example.invalid/video.mp4") } }
        } finally { broken.delete() }
        Unit
    }
    private suspend fun withVideo(name: String, test: suspend (VideoEditSource) -> Unit) {
        val fs = createFileSystem(context); val gate = MediaFeatureGate().apply { update(MediaFeatureSettings(videoEditorEnabled = true)) }
        val dir = File(context.cacheDir, "video-source-contract/$name").apply { mkdirs() }
        val original = File(dir, "device-original.mp4").apply { writeBytes(VideoEditFixtures.bytes(name)) }
        var source: VideoEditSource? = null
        try {
            val imported = fs.readByteStream(original.absolutePath) { reader ->
                VideoEditSource.import(fs, File(dir, "work").absolutePath, name, reader, gate, gate.permit(MediaFeature.VIDEO_EDITOR)!!)
            }.getOrThrow()
            source = imported
            gate.update(MediaFeatureSettings(videoEditorEnabled = true, promptDisplayEnabled = true))
            test(imported)
            assertArrayEquals(VideoEditFixtures.bytes(name), original.readBytes())
        } finally { source?.close(); dir.deleteRecursively() }
    }
}
