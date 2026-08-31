package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.repository.InMemoryFileSystem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
