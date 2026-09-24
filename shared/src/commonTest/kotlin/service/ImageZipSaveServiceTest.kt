package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.FileWriteSink
import com.valoser.futacha.shared.util.writeByteStreamReplacingImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageZipSaveServiceTest {
    @Test
    fun streamsDistinctImagesIntoAValidStoredZipShape() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    val payload = when (request.url.encodedPath.substringAfterLast('/')) {
                        "a.jpg" -> "abc".encodeToByteArray()
                        else -> "xyz".encodeToByteArray()
                    }
                    respond(
                        payload,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentLength, payload.size.toString())
                    )
                }
            }
        }
        val saved = ImageZipSaveService(client, fileSystem).save(
            mediaUrls = listOf(
                "https://may.2chan.net/b/src/a.jpg",
                "https://may.2chan.net/b/src/b.png",
                "https://may.2chan.net/b/src/c.mp4",
                "https://may.2chan.net/b/src/a.jpg"
            ),
            boardId = "may/b",
            threadId = "123",
            baseDirectory = "manual"
        ).getOrThrow()

        assertEquals(3, saved.savedItems)
        assertEquals(0, saved.failedItems)
        assertTrue(saved.fileName.endsWith("_media.zip"))
        val bytes = fileSystem.readBytes("manual/${saved.fileName}").getOrThrow()
        assertEquals(0x50, bytes[0].toInt() and 0xff)
        assertEquals(0x4b, bytes[1].toInt() and 0xff)
        assertTrue(bytes.decodeToString().contains("a.jpg"))
        assertTrue(bytes.decodeToString().contains("b.png"))
        assertTrue(bytes.decodeToString().contains("c.mp4"))
        assertEquals(0x50, bytes[bytes.size - 22].toInt() and 0xff)
        assertEquals(0x4b, bytes[bytes.size - 21].toInt() and 0xff)
        assertEquals(bytes.size.toLong(), saved.byteSize)
        client.close()
    }

    @Test
    fun reportsFailedMediaUrlsAndEmitsPerItemProgress() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val failedUrl = "https://may.2chan.net/b/src/missing.webm"
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    if (request.url.toString() == failedUrl) {
                        respond(ByteArray(0), HttpStatusCode.NotFound)
                    } else {
                        val payload = "mp4".encodeToByteArray()
                        respond(
                            payload,
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentLength, payload.size.toString())
                        )
                    }
                }
            }
        }
        val progress = mutableListOf<Pair<Int, Long>>()

        val saved = ImageZipSaveService(client, fileSystem).save(
            mediaUrls = listOf("https://may.2chan.net/b/src/ok.mp4", failedUrl),
            boardId = "b",
            threadId = "456",
            baseDirectory = "manual",
            fileNameSuffix = "retry1",
            onProgress = { current, total, _, itemBytes, _ ->
                assertEquals(2, total)
                progress += current to itemBytes
            }
        ).getOrThrow()

        assertEquals(1, saved.savedItems)
        assertEquals(1, saved.failedItems)
        assertEquals(listOf(failedUrl), saved.failedUrls)
        assertEquals("b_456_retry1_media.zip", saved.fileName)
        assertTrue(progress.any { it.first == 1 })
        assertTrue(progress.any { it.second == 3L })
        client.close()
    }

    @Test
    fun cancellationRemovesThePartialArchive() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val payload = ByteArray(1024) { it.toByte() }
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        payload,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentLength, payload.size.toString())
                    )
                }
            }
        }
        val service = ImageZipSaveService(client, fileSystem)

        kotlin.test.assertFailsWith<CancellationException> {
            service.save(
                mediaUrls = listOf("https://may.2chan.net/b/src/cancel.mp4"),
                boardId = "b",
                threadId = "789",
                baseDirectory = "manual",
                onProgress = { _, _, _, itemBytes, _ ->
                    if (itemBytes > 0L) throw CancellationException("test cancellation")
                }
            )
        }

        assertFalse(fileSystem.exists("manual/b_789_media.zip"))
        client.close()
    }

    @Test
    fun existingArchiveSurvivesWhenEveryDownloadFails() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        fileSystem.writeBytes("manual/b_1_media.zip", OLD_ARCHIVE).getOrThrow()
        val client = zipTestClient(status = HttpStatusCode.InternalServerError)

        val result = ImageZipSaveService(client, fileSystem).save(
            mediaUrls = listOf("https://may.2chan.net/b/src/gone.jpg"),
            boardId = "b",
            threadId = "1",
            baseDirectory = "manual"
        )

        assertTrue(result.isFailure)
        assertContentEquals(OLD_ARCHIVE, fileSystem.readBytes("manual/b_1_media.zip").getOrThrow())
        assertEquals(listOf("b_1_media.zip"), zipTestFileNames(fileSystem))
        client.close()
    }

    @Test
    fun existingArchiveSurvivesCancellationAndNoPartialFileRemains() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        fileSystem.writeBytes("manual/b_2_media.zip", OLD_ARCHIVE).getOrThrow()
        val client = zipTestClient()

        assertFailsWith<CancellationException> {
            ImageZipSaveService(client, fileSystem).save(
                mediaUrls = listOf("https://may.2chan.net/b/src/cancel.mp4"),
                boardId = "b",
                threadId = "2",
                baseDirectory = "manual",
                onProgress = { _, _, _, itemBytes, _ ->
                    if (itemBytes > 0L) throw CancellationException("test cancellation")
                }
            )
        }

        assertContentEquals(OLD_ARCHIVE, fileSystem.readBytes("manual/b_2_media.zip").getOrThrow())
        assertEquals(listOf("b_2_media.zip"), zipTestFileNames(fileSystem))
        client.close()
    }

    @Test
    fun successfulResaveReplacesTheArchive() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        fileSystem.writeBytes("manual/b_3_media.zip", OLD_ARCHIVE).getOrThrow()
        val client = zipTestClient()

        val saved = ImageZipSaveService(client, fileSystem).save(
            mediaUrls = listOf("https://may.2chan.net/b/src/new.jpg"),
            boardId = "b",
            threadId = "3",
            baseDirectory = "manual"
        ).getOrThrow()

        assertEquals("b_3_media.zip", saved.fileName)
        val bytes = fileSystem.readBytes("manual/b_3_media.zip").getOrThrow()
        assertEquals(0x50, bytes[0].toInt() and 0xff)
        assertTrue(bytes.decodeToString().contains("new.jpg"))
        assertEquals(listOf("b_3_media.zip"), zipTestFileNames(fileSystem))
        client.close()
    }

    @Test
    fun failedAtomicReplaceKeepsTheOldArchiveAndRemovesTheTemporaryFile() = runBlocking {
        val fileSystem = ReplaceTestFileSystem(atomic = true, failAtomicReplace = true)
        fileSystem.writeBytes("manual/b_4_media.zip", OLD_ARCHIVE).getOrThrow()
        val client = zipTestClient()

        val result = ImageZipSaveService(client, fileSystem).save(
            mediaUrls = listOf("https://may.2chan.net/b/src/new.jpg"),
            boardId = "b",
            threadId = "4",
            baseDirectory = "manual"
        )

        assertTrue(result.isFailure)
        assertContentEquals(OLD_ARCHIVE, fileSystem.readBytes("manual/b_4_media.zip").getOrThrow())
        assertEquals(listOf("b_4_media.zip"), zipTestFileNames(fileSystem))
        client.close()
    }

    @Test
    fun nonAtomicLocationKeepsTheOldArchiveWhenTheNewOneFails() = runBlocking {
        val fileSystem = ReplaceTestFileSystem(atomic = false)
        fileSystem.writeBytes("manual/b_5_media.zip", OLD_ARCHIVE).getOrThrow()
        val client = zipTestClient()

        assertFailsWith<CancellationException> {
            ImageZipSaveService(client, fileSystem).save(
                mediaUrls = listOf("https://may.2chan.net/b/src/cancel.mp4"),
                boardId = "b",
                threadId = "5",
                baseDirectory = "manual",
                onProgress = { _, _, _, itemBytes, _ ->
                    if (itemBytes > 0L) throw CancellationException("test cancellation")
                }
            )
        }

        assertContentEquals(OLD_ARCHIVE, fileSystem.readBytes("manual/b_5_media.zip").getOrThrow())
        assertEquals(listOf("b_5_media.zip"), zipTestFileNames(fileSystem))
        client.close()
    }

    @Test
    fun nonAtomicLocationTakesOverTheNormalNameAfterTheNewArchiveIsComplete() = runBlocking {
        val fileSystem = ReplaceTestFileSystem(atomic = false, renameSupported = true)
        fileSystem.writeBytes("manual/b_6_media.zip", OLD_ARCHIVE).getOrThrow()
        val client = zipTestClient()

        val saved = ImageZipSaveService(client, fileSystem).save(
            mediaUrls = listOf("https://may.2chan.net/b/src/new.jpg"),
            boardId = "b",
            threadId = "6",
            baseDirectory = "manual"
        ).getOrThrow()

        assertEquals("b_6_media.zip", saved.fileName)
        assertTrue(fileSystem.readBytes("manual/b_6_media.zip").getOrThrow().decodeToString().contains("new.jpg"))
        assertEquals(listOf("b_6_media.zip"), zipTestFileNames(fileSystem))
        client.close()
    }

    @Test
    fun nonAtomicLocationWithoutRenameKeepsTheNewArchiveUnderItsAlternateName() = runBlocking {
        val fileSystem = ReplaceTestFileSystem(atomic = false, renameSupported = false)
        fileSystem.writeBytes("manual/b_7_media.zip", OLD_ARCHIVE).getOrThrow()
        val client = zipTestClient()

        val saved = ImageZipSaveService(client, fileSystem).save(
            mediaUrls = listOf("https://may.2chan.net/b/src/new.jpg"),
            boardId = "b",
            threadId = "7",
            baseDirectory = "manual"
        ).getOrThrow()

        assertTrue(saved.fileName.matches(Regex("b_7_media-\\d{8}-\\d{6}\\.zip")), saved.fileName)
        assertEquals(listOf(saved.fileName), zipTestFileNames(fileSystem))
        client.close()
    }

    @Test
    fun oldArchiveThatCannotBeRemovedIsKeptNextToTheNewOne() = runBlocking {
        val fileSystem = ReplaceTestFileSystem(atomic = false, renameSupported = true, failDeleteOf = "manual/b_8_media.zip")
        fileSystem.writeBytes("manual/b_8_media.zip", OLD_ARCHIVE).getOrThrow()
        val client = zipTestClient()

        val saved = ImageZipSaveService(client, fileSystem).save(
            mediaUrls = listOf("https://may.2chan.net/b/src/new.jpg"),
            boardId = "b",
            threadId = "8",
            baseDirectory = "manual"
        ).getOrThrow()

        assertTrue(saved.fileName.startsWith("b_8_media-"), saved.fileName)
        assertContentEquals(OLD_ARCHIVE, fileSystem.readBytes("manual/b_8_media.zip").getOrThrow())
        assertEquals(setOf("b_8_media.zip", saved.fileName), zipTestFileNames(fileSystem).toSet())
        client.close()
    }

    private fun zipTestClient(status: HttpStatusCode = HttpStatusCode.OK) = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                val payload = request.url.encodedPath.substringAfterLast('/').encodeToByteArray() + ByteArray(512)
                if (status == HttpStatusCode.OK) {
                    respond(payload, status, headersOf(HttpHeaders.ContentLength, payload.size.toString()))
                } else {
                    respond(ByteArray(0), status)
                }
            }
        }
    }

    private suspend fun zipTestFileNames(fileSystem: FileSystem): List<String> =
        fileSystem.listFiles("manual").map { it.substringAfterLast('/') }.sorted()

    private companion object {
        val OLD_ARCHIVE = "previous archive".encodeToByteArray()
    }
}

/** Simulates locations without atomic rename (SAF) and failures at the final step. */
private class ReplaceTestFileSystem(
    private val atomic: Boolean,
    private val renameSupported: Boolean = false,
    private val failAtomicReplace: Boolean = false,
    private val failDeleteOf: String? = null,
    private val delegate: InMemoryFileSystem = InMemoryFileSystem()
) : FileSystem by delegate {
    override suspend fun writeByteStreamReplacing(
        base: SaveLocation,
        relativePath: String,
        block: suspend (FileWriteSink) -> Unit
    ): Result<String> = writeByteStreamReplacingImpl(base, relativePath, block)

    override fun supportsAtomicReplace(base: SaveLocation): Boolean = atomic

    override suspend fun replaceAtomically(base: SaveLocation, fromRelative: String, toRelative: String): Result<Unit> =
        if (failAtomicReplace) Result.failure(IllegalStateException("rename failed"))
        else delegate.replaceAtomically(base, fromRelative, toRelative)

    override suspend fun renameIfAbsent(base: SaveLocation, fromRelative: String, toRelative: String): Result<String?> {
        if (!renameSupported || delegate.exists(base, toRelative)) return Result.success(null)
        return delegate.replaceAtomically(base, fromRelative, toRelative).map { toRelative }
    }

    override suspend fun delete(base: SaveLocation, relativePath: String): Result<Unit> {
        val path = (base as SaveLocation.Path).path + "/" + relativePath
        return if (path == failDeleteOf) Result.failure(IllegalStateException("locked")) else delegate.delete(base, relativePath)
    }
}
