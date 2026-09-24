package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.model.SaveLocation
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmFileSystemReplaceTest {
    @Test
    fun failedReplacingWriteKeepsTheExistingFileAndLeavesNoTemporaryFile() = runBlocking {
        val root = Files.createTempDirectory("futacha-replace").toFile()
        try {
            val fileSystem = JvmFileSystem(root)
            val base = SaveLocation.Path(root.resolve("saves").absolutePath)
            fileSystem.writeBytes(base, "thread_media.zip", "old".encodeToByteArray()).getOrThrow()

            val result = fileSystem.writeByteStreamReplacing(base, "thread_media.zip") { sink ->
                sink.write("partial".encodeToByteArray())
                throw IllegalStateException("connection lost")
            }

            assertTrue(result.isFailure)
            assertEquals("old", root.resolve("saves/thread_media.zip").readText())
            assertEquals(listOf("thread_media.zip"), root.resolve("saves").list()!!.sorted())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun successfulReplacingWriteSwapsInTheNewContent() = runBlocking {
        val root = Files.createTempDirectory("futacha-replace").toFile()
        try {
            val fileSystem = JvmFileSystem(root)
            val base = SaveLocation.Path(root.resolve("saves").absolutePath)
            fileSystem.writeBytes(base, "thread_media.zip", "old".encodeToByteArray()).getOrThrow()

            val saved = fileSystem.writeByteStreamReplacing(base, "thread_media.zip") { sink ->
                sink.write("new".encodeToByteArray())
            }.getOrThrow()

            assertEquals("thread_media.zip", saved)
            assertEquals("new", root.resolve("saves/thread_media.zip").readText())
            assertEquals(listOf("thread_media.zip"), root.resolve("saves").list()!!.sorted())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun linkedMediaCanBeRewrittenWithoutChangingTheSource() = runBlocking {
        val root = Files.createTempDirectory("futacha-link").toFile()
        try {
            val fileSystem = JvmFileSystem(root)
            fileSystem.writeBytes("auto/old/b/src/1.jpg", "original".encodeToByteArray()).getOrThrow()

            fileSystem.linkOrCopy("auto/old/b/src/1.jpg", "auto/new/b/src/1.jpg").getOrThrow()
            assertEquals("original", root.resolve("auto/new/b/src/1.jpg").readText())

            // Thread saves delete a media file before writing it again.
            fileSystem.delete("auto/new/b/src/1.jpg").getOrThrow()
            fileSystem.writeByteStream("auto/new/b/src/1.jpg") { it.write("replaced".encodeToByteArray()) }.getOrThrow()

            assertEquals("original", root.resolve("auto/old/b/src/1.jpg").readText())
            assertEquals("replaced", root.resolve("auto/new/b/src/1.jpg").readText())
        } finally {
            root.deleteRecursively()
        }
    }
}
