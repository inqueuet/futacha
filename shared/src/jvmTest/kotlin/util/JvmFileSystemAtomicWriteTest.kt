package com.valoser.futacha.shared.util

import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class JvmFileSystemAtomicWriteTest {
    @Test
    fun writeReplacesTheFileWithoutWritingThroughItOrLeavingTemporaryFiles() = runBlocking {
        val root = Files.createTempDirectory("futacha-atomic").toFile()
        try {
            val fileSystem = JvmFileSystem(root)
            fileSystem.writeString("index.json", "old").getOrThrow()
            // A reader holding the old file (or a hard link to it) keeps the old content:
            // the new content is renamed into place instead of truncating the existing file.
            val linked = root.resolve("linked.json")
            Files.createLink(linked.toPath(), root.resolve("index.json").toPath())

            fileSystem.writeString("index.json", "new").getOrThrow()

            assertEquals("new", root.resolve("index.json").readText())
            assertEquals("old", linked.readText())
            assertEquals(listOf("index.json", "linked.json"), root.list()!!.sorted())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cleanupDeletesOnlyStaleTemporaryFiles() {
        val root = Files.createTempDirectory("futacha-atomic-clean").toFile()
        try {
            val nested = File(root, "auto/thread").apply { mkdirs() }
            val stale = File(nested, "tmp_123.tmp").apply { writeText("x"); setLastModified(System.currentTimeMillis() - 2 * 60 * 60 * 1000L) }
            val fresh = File(nested, "tmp_456.tmp").apply { writeText("x") }
            val ordinary = File(nested, "old.tmp").apply { writeText("x"); setLastModified(System.currentTimeMillis() - 2 * 60 * 60 * 1000L) }

            assertEquals(1, JvmFileSystem(root).cleanupTempFiles())

            assertFalse(stale.exists())
            assertTrue(fresh.exists())
            assertTrue(ordinary.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun operationsLeaveTheCallersThread() = runBlocking {
        val root = Files.createTempDirectory("futacha-atomic-thread").toFile()
        val executor = Executors.newSingleThreadExecutor { Thread(it, "fake-ui-thread") }
        try {
            val fileSystem = JvmFileSystem(root)
            withContext(executor.asCoroutineDispatcher()) {
                fileSystem.writeString("a.txt", "a").getOrThrow()
                val readerThread = fileSystem.readByteStream("a.txt") { Thread.currentThread().name }.getOrThrow()
                assertNotEquals("fake-ui-thread", readerThread)
                var writerThread = ""
                fileSystem.writeByteStream("b.txt") { writerThread = Thread.currentThread().name }.getOrThrow()
                assertNotEquals("fake-ui-thread", writerThread)
            }
        } finally {
            executor.shutdownNow()
            root.deleteRecursively()
        }
    }
}
