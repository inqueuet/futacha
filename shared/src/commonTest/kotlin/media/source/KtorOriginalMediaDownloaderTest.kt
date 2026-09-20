package com.valoser.futacha.shared.media.source

import coil3.network.HttpException
import com.valoser.futacha.shared.network.ImageRequestRejected
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.IOException
import kotlin.test.*

class KtorOriginalMediaDownloaderTest {
    private val request = OriginalMediaRequest("https://example.test/src/image.png?token=keep-me")

    @Test fun webmOriginalUsesOneGetWithoutDependingOnServerRangesOrMime(): Unit = runBlocking {
        for (mime in listOf("video/webm", "application/octet-stream", "text/plain")) {
            var gets = 0
            val original = byteArrayOf(0x1a, 0x45, 0xdf.toByte(), 0xa3.toByte(), 0)
            val client = HttpClient(MockEngine {
                gets++; assertNull(it.headers[HttpHeaders.Range])
                respond(original, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType to listOf(mime),
                    HttpHeaders.ContentLength to listOf(original.size.toString()), HttpHeaders.AcceptRanges to listOf("none")))
            })
            val downloader = KtorOriginalMediaDownloader(client, 1024)
            try {
                val sink = Buffer()
                val info = downloader.download(OriginalMediaRequest("https://example.test/a.webm"), sink)
                assertContentEquals(original, sink.readByteArray()); assertEquals(mime, info.mimeType)
                assertEquals(1, gets)
            } finally { downloader.close(); client.close() }
        }
    }

    @Test fun preservesBytesHeadersAndSignedQueryWithoutOwningAppClient(): Unit = runBlocking {
        val client = HttpClient(MockEngine {
            assertEquals(request.url, it.url.toString())
            assertEquals("identity", it.headers[HttpHeaders.AcceptEncoding])
            assertEquals("https://example.test/thread/1", it.headers[HttpHeaders.Referrer])
            respond(byteArrayOf(0, 1, 2, -1), HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType to listOf("image/png; charset=binary"),
                    HttpHeaders.ContentLength to listOf("4"), HttpHeaders.ETag to listOf("v1")))
        })
        val downloader = KtorOriginalMediaDownloader(client, maxBytes = 16)
        try {
            val bytes = Buffer()
            val info = downloader.download(request.copy(headers = mapOf(HttpHeaders.Referrer to "https://example.test/thread/1")), bytes)
            assertContentEquals(byteArrayOf(0, 1, 2, -1), bytes.readByteArray())
            assertEquals(4, info.sizeBytes)
            assertEquals("image/png", info.mimeType)
            assertEquals("v1", info.etag)
            assertEquals(request.url, info.resolvedUrl)
        } finally { downloader.close(); client.close() }
    }

    @Test fun declaredAndUndeclaredOversizedBodiesAndTruncationAreRejected(): Unit = runBlocking {
        for ((body, declared) in listOf("12345" to "5", "12345" to null, "12" to "4", "12" to "invalid", "" to null)) {
            val client = HttpClient(MockEngine {
                respond(body, HttpStatusCode.OK, if (declared == null) headersOf() else headersOf(HttpHeaders.ContentLength, declared))
            })
            val downloader = KtorOriginalMediaDownloader(client, maxBytes = 4)
            try { assertFailsWith<IOException> { downloader.download(request, Buffer()) } }
            finally { downloader.close(); client.close() }
        }
    }

    @Test fun partialAndErrorResponsesAreNotTreatedAsOriginals(): Unit = runBlocking {
        for (status in listOf(HttpStatusCode.PartialContent, HttpStatusCode.NotFound, HttpStatusCode.ServiceUnavailable)) {
            val client = HttpClient(MockEngine { respond("error", status) })
            val downloader = KtorOriginalMediaDownloader(client, maxBytes = 128, waitBeforeRetry = { _, _ -> })
            try {
                val failure = assertFailsWith<HttpException> { downloader.download(request, Buffer()) }
                assertEquals(status.value, failure.response.code)
                assertEquals(status == HttpStatusCode.ServiceUnavailable, downloader.retryAfter(0, failure))
            } finally { downloader.close(); client.close() }
        }
    }

    @Test fun restrictedTransportRejectsScriptUrlsAndRangeRequests(): Unit = runBlocking {
        var sends = 0
        val client = HttpClient(MockEngine { sends++; respond("data") })
        val downloader = KtorOriginalMediaDownloader(client, maxBytes = 128)
        try {
            assertFailsWith<ImageRequestRejected> {
                downloader.download(request.copy(url = "https://example.test/post.php?fake=.png"), Buffer())
            }
            assertFailsWith<IllegalArgumentException> {
                downloader.download(request.copy(headers = mapOf("range" to "bytes=0-10")), Buffer())
            }
            assertEquals(0, sends)
        } finally { downloader.close(); client.close() }
    }

    @Test fun noStoreResponseIsMarkedNonReusable(): Unit = runBlocking {
        val client = HttpClient(MockEngine {
            respond("data", HttpStatusCode.OK, headersOf(HttpHeaders.CacheControl, "private, No-Store"))
        })
        val downloader = KtorOriginalMediaDownloader(client, maxBytes = 128)
        try { assertFalse(downloader.download(request, Buffer()).cacheable) }
        finally { downloader.close(); client.close() }
    }

    @Test fun originalKeysKeepRepresentationConditionsButIgnoreHeaderCaseAndOrder() {
        val first = request.copy(headers = linkedMapOf("Referer" to "thread", "Accept" to "image/png"))
        val second = request.copy(headers = linkedMapOf("accept" to "image/png", "referer" to "thread"), allowNetwork = false)
        assertEquals(first.cacheKey(), second.cacheKey())
        assertNotEquals(first.cacheKey(), first.copy(url = request.url.substringBefore('?')).cacheKey())
        assertNotEquals(first.cacheKey(), first.copy(headers = mapOf("Referer" to "different")).cacheKey())
        assertFailsWith<IllegalArgumentException> { request.copy(headers = mapOf("Accept" to "a", "accept" to "b")) }
    }
}
