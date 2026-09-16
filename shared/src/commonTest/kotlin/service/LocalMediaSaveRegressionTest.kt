package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.util.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class LocalMediaSaveRegressionTest {
    private fun offlineClient() = HttpClient(MockEngine { error("Local exports must not use the network") })

    @Test
    fun copiesAbsoluteFileUrlAndContentSourcesWithoutNetwork() = runBlocking {
        val fs = InMemoryFileSystem()
        val payload = ByteArray(180_013) { (it % 251).toByte() }
        val sources = listOf("/cache/a.jpg", "file:///cache/photo%20b.webm", "content://provider/document/c.mp4")
        offlineClient().use { client ->
            sources.forEach { source ->
                fs.writeBytes(localMediaSavePath(source), payload).getOrThrow()
                val target = SaveLocation.TreeUri("content://selected/tree/destination")
                val saved = SingleMediaSaveService(client, fs).saveMedia(source, "b", "1", target).getOrThrow()
                assertContentEquals(payload, fs.readBytes(target, saved.relativePath).getOrThrow())
                assertContentEquals(payload, fs.readBytes(localMediaSavePath(source)).getOrThrow())
                assertEquals(payload.size.toLong(), saved.byteSize)
            }
        }
    }

    @Test
    fun copiesThroughStreamingReaderInsteadOfReadBytes() = runBlocking {
        val backing = InMemoryFileSystem()
        var reads = 0
        val fs = object : FileSystem by backing {
            override suspend fun readBytes(path: String): Result<ByteArray> = error("Whole-file read is forbidden")
            override suspend fun <T> readByteStream(path: String, block: suspend (FileReadSource) -> T): Result<T> =
                runSuspendCatchingPreservingCancellation {
                    var remaining = 200_000
                    block(object : FileReadSource {
                        override suspend fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                            reads++
                            if (remaining == 0) return -1
                            val count = minOf(remaining, length, 4096)
                            bytes.fill(7, offset, offset + count)
                            remaining -= count
                            return count
                        }
                    })
                }
        }
        offlineClient().use { client ->
            val saved = SingleMediaSaveService(client, fs).saveMedia("/cache/a.jpg", "b", "1", baseDirectory = "manual").getOrThrow()
            assertEquals(200_000, backing.readBytes("manual/${saved.relativePath}").getOrThrow().size)
            assertTrue(reads > 2)
        }
    }

    @Test
    fun copyingOntoSourceFailsWithoutDeletingIt() = runBlocking {
        val fs = InMemoryFileSystem()
        val source = "/virtual/manual/a.jpg"
        val bytes = "original".encodeToByteArray()
        fs.writeBytes(source, bytes).getOrThrow()
        offlineClient().use { client ->
            val result = SingleMediaSaveService(client, fs).saveMedia(source, "b", "1",
                baseDirectory = "/virtual/manual", storageDirectoryOverride = "", useTypeSubdirectory = false,
                outputFileNameOverride = "a.jpg")
            assertTrue(result.isFailure)
            assertContentEquals(bytes, fs.readBytes(source).getOrThrow())
        }
    }

    @Test
    fun incompleteLocalReadIsNotReportedAsSuccessfulSave() = runBlocking {
        val backing = InMemoryFileSystem()
        backing.writeBytes("/cache/truncated.jpg", byteArrayOf(1, 2)).getOrThrow()
        val fs = object : FileSystem by backing {
            override suspend fun getFileSize(path: String) = 100L
        }
        offlineClient().use { client ->
            val result = SingleMediaSaveService(client, fs).saveMedia("/cache/truncated.jpg", "b", "1", baseDirectory = "manual")
            assertTrue(result.isFailure)
            assertTrue(backing.listFiles("manual/${buildThreadStorageId("b", "1")}/preview_media/images").isEmpty())
        }
    }

    @Test
    fun localZipIncludesBothImagesAndVideosAndReportsMissingSources() = runBlocking {
        val fs = InMemoryFileSystem()
        fs.writeBytes("/cache/a.jpg", "JPEG".encodeToByteArray()).getOrThrow()
        fs.writeBytes("/cache/b.webm", "WEBM".encodeToByteArray()).getOrThrow()
        offlineClient().use { client ->
            val saved = ImageZipSaveService(client, fs).save(
                listOf("/cache/a.jpg", "file:///cache/b.webm", "/cache/missing.png"), "b", "1", baseDirectory = "manual"
            ).getOrThrow()
            assertEquals(2, saved.savedItems)
            assertEquals(listOf("/cache/missing.png"), saved.failedUrls)
            val zip = fs.readBytes("manual/${saved.fileName}").getOrThrow()
            assertTrue(zip.decodeToString().contains("JPEG"))
            assertTrue(zip.decodeToString().contains("WEBM"))
            assertEquals(2, zip[zip.size - 12].toInt()) // EOCD total entry count.
        }
    }

    @Test
    fun cancellationDuringLocalZipRemovesPartialOutputAndKeepsSource() = runBlocking {
        val fs = InMemoryFileSystem()
        fs.writeBytes("/cache/a.jpg", ByteArray(4096) { 3 }).getOrThrow()
        offlineClient().use { client ->
            assertFailsWith<CancellationException> {
                ImageZipSaveService(client, fs).save(listOf("/cache/a.jpg"), "b", "1", baseDirectory = "manual",
                    onProgress = { _, _, _, bytes, _ -> if (bytes > 0) throw CancellationException("cancel") })
            }
            assertFalse(fs.exists("manual/b_1_media.zip"))
            assertTrue(fs.exists("/cache/a.jpg"))
        }
    }

    @Test
    fun providerCloseFailureIsReturnedAndOriginalWriteFailureIsPreserved() = runBlocking {
        val closeFailure = IllegalStateException("provider could not commit")
        assertEquals(closeFailure.message, assertFailsWith<IllegalStateException> {
            withFileWriteCompletion(close = { throw closeFailure }) { "written" }
        }.message)
        val writeFailure = IllegalArgumentException("disk full")
        assertSame(writeFailure, assertFailsWith<IllegalArgumentException> {
            withFileWriteCompletion(close = { throw closeFailure }) { throw writeFailure }
        })
        assertTrue(writeFailure.suppressedExceptions.any { it.message == closeFailure.message })
    }

    @Test
    fun cancellationStillClosesTheProviderAndRemainsCancellation() = runBlocking {
        var closed = false
        assertFailsWith<CancellationException> {
            withFileWriteCompletion(close = { closed = true; throw IllegalStateException("close") }) {
                throw CancellationException("cancel")
            }
        }
        assertTrue(closed)
    }
}
