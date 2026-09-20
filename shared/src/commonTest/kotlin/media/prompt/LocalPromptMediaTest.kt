package com.valoser.futacha.shared.media.prompt

import com.valoser.futacha.testing.video.VideoEditFixtures
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.*

class LocalPromptMediaTest {
    @Test fun savedImagesAndVideoUseTheirLocalOriginalAndPreserveItsBytes(): Unit = runBlocking {
        val fs = FileSystem.SYSTEM
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("local-prompt-${Random.nextLong()}")
        fs.createDirectories(root)
        try {
            val file = root.resolve("saved image.jpg")
            fs.write(file) { write(GenerationImageFixtures.jpeg) }
            val normalized = file.toString().replace('\\', '/').replace(" ", "%20")
            val metadata = readLocalGenerationMetadata("file://${if (normalized.startsWith('/')) "" else "/"}$normalized", null)
            assertEquals(GenerationImageFixtures.positive, metadata.candidates.single().positive)
            assertTrue(metadata.hasAiEvidence)
            assertContentEquals(GenerationImageFixtures.jpeg, fs.read(file) { readByteArray() })
            // The same saved path can be replaced. The next visit must read its current content.
            fs.write(file) { write(byteArrayOf(0, 0, 0, 0)) }
            assertFalse(readLocalGenerationMetadata(file.toString(), null).hasAiEvidence)
            val video = root.resolve("saved.mp4")
            val bytes = VideoEditFixtures.bytes("portrait")
            fs.write(video) { write(bytes) }
            val tags = readLocalGenerationMetadata(video.toString(), null)
            assertEquals("青い鳥\nDialogue: <Picture 1> says \"hello\" & goodbye.", tags.candidates.single().positive)
            assertContentEquals(bytes, fs.read(video) { readByteArray() })
        } finally { fs.deleteRecursively(root) }
    }

    @Test fun localReaderRejectsHttpRatherThanFetchingAnOriginal(): Unit = runBlocking {
        assertFailsWith<IllegalArgumentException> { readLocalGenerationMetadata("https://may.2chan.net/b/src/image.png", null) }
    }
}
