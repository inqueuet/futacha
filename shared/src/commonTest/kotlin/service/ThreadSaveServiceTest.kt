package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThreadSaveServiceTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun saveThread_writesMetadataAndRawHtmlWithRewrittenPaths() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val service = ThreadSaveService(
            httpClient = createClient(),
            fileSystem = fileSystem
        )
        val storageId = buildThreadStorageId("b", "123")

        val saved = service.saveThread(
            threadId = "123",
            boardId = "b",
            boardName = "may/b",
            boardUrl = "https://may.2chan.net/b/futaba.php",
            title = "title",
            expiresAtLabel = "soon",
            posts = listOf(samplePost()),
            baseDirectory = "manual",
            writeMetadata = true
        ).getOrThrow()

        val metadata = readMetadata(fileSystem, "manual/$storageId/metadata.json")
        val rawHtml = fileSystem.readString("manual/$storageId/123.htm").getOrThrow()

        assertEquals(SaveStatus.COMPLETED, saved.status)
        assertEquals("b/thumb/1s.jpg", saved.thumbnailPath)
        assertEquals(1, saved.imageCount)
        assertEquals(0, saved.videoCount)

        assertEquals("123.htm", metadata.rawHtmlPath)
        assertTrue(metadata.strippedExternalResources)
        assertEquals("b/src/1.jpg", metadata.posts.single().localImagePath)
        assertEquals("b/thumb/1s.jpg", metadata.posts.single().localThumbnailPath)
        assertTrue(metadata.posts.single().messageHtml.contains("""src="b/thumb/1s.jpg""""))
        assertTrue(metadata.posts.single().messageHtml.contains("""href="b/src/1.jpg""""))

        assertFalse(rawHtml.contains("<script"))
        assertFalse(rawHtml.contains("<iframe"))
        assertTrue(rawHtml.contains("""charset="UTF-8""""))
        assertTrue(rawHtml.contains("""src="b/thumb/1s.jpg""""))
        assertTrue(rawHtml.contains("""href="b/src/1.jpg""""))
    }

    @Test
    fun saveThread_skipsRawHtmlWhenDisabled() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val service = ThreadSaveService(
            httpClient = createClient(),
            fileSystem = fileSystem
        )
        val storageId = buildThreadStorageId("b", "456")

        service.saveThread(
            threadId = "456",
            boardId = "b",
            boardName = "may/b",
            boardUrl = "https://may.2chan.net/b/futaba.php",
            title = "title",
            expiresAtLabel = null,
            posts = listOf(samplePost()),
            baseDirectory = "manual",
            writeMetadata = true,
            rawHtmlOptions = RawHtmlSaveOptions(enable = false, stripExternalResources = false)
        ).getOrThrow()

        val metadata = readMetadata(fileSystem, "manual/$storageId/metadata.json")

        assertNull(metadata.rawHtmlPath)
        assertFalse(fileSystem.exists("manual/$storageId/456.htm"))
    }

    private fun createClient(): HttpClient {
        val engine = MockEngine(
            MockEngineConfig().apply {
                addHandler { request ->
                    when {
                        request.url.encodedPath.endsWith("/thumb/1s.jpg") -> binaryResponse(
                            body = "thumb-bytes".encodeToByteArray(),
                            contentType = "image/jpeg"
                        )
                        request.url.encodedPath.endsWith("/src/1.jpg") -> binaryResponse(
                            body = "image-bytes".encodeToByteArray(),
                            contentType = "image/jpeg"
                        )
                        request.url.encodedPath.endsWith("/res/123.htm") ||
                            request.url.encodedPath.endsWith("/res/456.htm") -> textResponse(
                            body = """
                                <html>
                                  <head>
                                    <meta charset="Shift_JIS">
                                    <script src="https://dec.2chan.net/bin/ad.js"></script>
                                  </head>
                                  <body>
                                    <iframe src="https://dec.2chan.net/bin/frame.html"></iframe>
                                    <img src="https://may.2chan.net/b/thumb/1s.jpg">
                                    <a href="https://may.2chan.net/b/src/1.jpg">image</a>
                                  </body>
                                </html>
                            """.trimIndent(),
                            contentType = "text/html; charset=UTF-8"
                        )
                        else -> error("Unexpected request: ${request.url}")
                    }
                }
            }
        )
        return HttpClient(engine)
    }

    private fun MockRequestHandleScope.binaryResponse(
        body: ByteArray,
        contentType: String
    ): HttpResponseData = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = Headers.build {
            append(HttpHeaders.ContentType, contentType)
            append(HttpHeaders.ContentLength, body.size.toString())
        }
    )

    private fun MockRequestHandleScope.textResponse(
        body: String,
        contentType: String
    ): HttpResponseData = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = Headers.build {
            append(HttpHeaders.ContentType, contentType)
            append(HttpHeaders.ContentLength, body.encodeToByteArray().size.toString())
        }
    )

    private fun samplePost(): Post {
        return Post(
            id = "1",
            order = 1,
            author = "author",
            subject = "subject",
            timestamp = "24/01/01(月)00:00:00",
            messageHtml = """
                <img src="https://may.2chan.net/b/thumb/1s.jpg">
                <a href="https://may.2chan.net/b/src/1.jpg">image</a>
            """.trimIndent(),
            imageUrl = "https://may.2chan.net/b/src/1.jpg",
            thumbnailUrl = "https://may.2chan.net/b/thumb/1s.jpg"
        )
    }

    private suspend fun readMetadata(
        fileSystem: InMemoryFileSystem,
        path: String
    ): SavedThreadMetadata {
        return json.decodeFromString(
            fileSystem.readString(path).getOrThrow()
        )
    }
}
