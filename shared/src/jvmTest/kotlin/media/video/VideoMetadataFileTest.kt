package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.prompt.*
import com.valoser.futacha.testing.video.VideoEditFixtures
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.*

class VideoMetadataFileTest {
    @Test fun nativeFileAdapterPreservesSourceWithPromptFeatureOff(): Unit = runBlocking {
        val directory = Files.createTempDirectory("video-metadata").toFile()
        try {
            val gate = MediaFeatureGate().apply { update(MediaFeatureSettings(videoEditorEnabled = true)) }
            assertNull(gate.permit(MediaFeature.PROMPT))
            val input = directory.resolve("input.mp4").apply { writeBytes(VideoEditFixtures.bytes("variable")) }
            val output = directory.resolve("output.mp4").apply { writeBytes(VideoEditFixtures.bytes("rotated")) }
            preserveEditedVideoMetadata(input.absolutePath, output.absolutePath, 2)
            val bytes = output.readBytes()
            val scan = VideoMetadataReader().read(bytes.size.toLong()) { offset, count -> bytes.copyOfRange(offset.toInt(), offset.toInt() + count) }
            val saved = PreservedVideoMetadata.fromScan(scan)
            assertTrue(saved.fields.any { it.key == "prompt" && it.value.contains("9007199254740993") })
            assertEquals(2, saved.edits.single().regionCount)
            assertEquals("Futacha", saved.edits.single().application)
            assertContentEquals(VideoEditFixtures.bytes("variable"), input.readBytes())
            assertNull(gate.permit(MediaFeature.PROMPT))
            assertFailsWith<IllegalArgumentException> { preserveEditedVideoMetadata(input.absolutePath, input.absolutePath, 2) }
            assertContentEquals(VideoEditFixtures.bytes("variable"), input.readBytes())
        } finally { directory.deleteRecursively() }
    }
}
