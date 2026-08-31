package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.toThreadPage
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.service.ThreadSaveService
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SavedThreadRoundTripTest {
    @Test
    fun saveIndexLoadAndDelete_roundTripsSavedThreadMetadata() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val repository = SavedThreadRepository(
            fileSystem = fileSystem,
            baseDirectory = "manual"
        )
        val service = ThreadSaveService(
            httpClient = createClient(),
            fileSystem = fileSystem
        )

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

        repository.addThreadToIndex(saved).getOrThrow()

        val indexed = repository.getAllThreads()
        val metadata = repository.loadThreadMetadata(threadId = "123", boardId = "b").getOrThrow()
        val page = metadata.toThreadPage(
            fileSystem = fileSystem,
            baseDirectory = "manual"
        )

        assertEquals(1, indexed.size)
        assertEquals("123", indexed.single().threadId)
        assertEquals("123", metadata.threadId)
        assertEquals("123.htm", metadata.rawHtmlPath)
        assertEquals("/virtual/manual/${saved.storageId}/b/src/1.jpg", page.posts.single().imageUrl)
        assertEquals("/virtual/manual/${saved.storageId}/b/thumb/1s.jpg", page.posts.single().thumbnailUrl)

        assertTrue(repository.threadExists(threadId = "123", boardId = "b"))
        repository.deleteThread(threadId = "123", boardId = "b").getOrThrow()
        assertFalse(repository.threadExists(threadId = "123", boardId = "b"))
        assertTrue(repository.getAllThreads().isEmpty())
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
                        request.url.encodedPath.endsWith("/res/123.htm") -> textResponse(
                            body = """
                                <html>
                                  <head><meta charset="Shift_JIS"></head>
                                  <body>
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
}
