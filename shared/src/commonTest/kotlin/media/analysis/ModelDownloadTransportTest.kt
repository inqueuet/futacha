package com.valoser.futacha.shared.media.analysis

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import kotlin.test.*

class ModelDownloadTransportTest {
    private val spec = ModelDistribution("https://model.test/model", 5, "model".encodeUtf8().sha256().hex())
    private suspend fun using(handler: MockRequestHandler, block: suspend (KtorModelDownloader) -> Unit) {
        KtorModelDownloader(HttpClient(MockEngine(handler)) { configureModelDownloads() }).use { block(it) }
    }

    @Test fun redirectsDoNotCarryCredentialsCookiesOrMediaAndUnknownLengthIsBounded() = runBlocking {
        val requests = mutableListOf<String>()
        using(handler = { request ->
            requests += request.url.toString()
            assertEquals(HttpMethod.Get, request.method)
            for (header in listOf(HttpHeaders.Cookie, HttpHeaders.Authorization, HttpHeaders.ProxyAuthorization, HttpHeaders.Referrer)) {
                assertNull(request.headers[header])
            }
            assertEquals("identity", request.headers[HttpHeaders.AcceptEncoding])
            when (requests.size) {
                1 -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location to listOf("/redirect"), HttpHeaders.SetCookie to listOf("session=private")))
                2 -> respond("", HttpStatusCode.TemporaryRedirect, headersOf(HttpHeaders.Location, "https://cdn.test/file"))
                else -> respond("model")
            }
        }) { downloader ->
            val sink = Buffer(); downloader.download(spec, sink)
            assertEquals("model", sink.readUtf8())
            assertEquals(listOf("https://model.test/model", "https://model.test/redirect", "https://cdn.test/file"), requests)
        }
    }

    @Test fun refusesHttpDowngradeAndEmbeddedCredentialsBeforeFollowing() = runBlocking {
        for (target in listOf("http://model.test/file", "https://user:password@model.test/file")) {
            var requests = 0
            using(handler = { requests++; respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, target)) }) { downloader ->
                assertFails { downloader.download(spec, Buffer()) }
                assertEquals(1, requests)
            }
        }
    }

    @Test fun rejectsUnexpectedStatusEncodingTruncationAndSizeWithoutRetry() = runBlocking {
        data class Response(val text: String = "model", val status: HttpStatusCode = HttpStatusCode.OK, val headers: Headers = Headers.Empty)
        for (response in listOf(
            Response(status = HttpStatusCode.InternalServerError), Response(status = HttpStatusCode.PartialContent),
            Response(headers = headersOf(HttpHeaders.ContentLength, "-1")),
            Response(headers = headersOf(HttpHeaders.ContentLength, "6")),
            Response(headers = headersOf(HttpHeaders.ContentEncoding, "gzip")),
            Response(text = "part"), Response(text = "model extra")
        )) {
            var requests = 0
            using(handler = { requests++; respond(response.text, response.status, response.headers) }) { downloader ->
                assertFails { downloader.download(spec, Buffer()) }; assertEquals(1, requests)
            }
        }
    }

    @Test fun redirectsAreLimitedAndMissingLocationIsRejected() = runBlocking {
        var requests = 0
        using(handler = { requests++; respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/again")) }) { downloader ->
            assertFails { downloader.download(spec, Buffer()) }; assertEquals(6, requests)
        }
        using(handler = { respond("", HttpStatusCode.Found) }) { downloader ->
            assertFails { downloader.download(spec, Buffer()) }
        }
    }
}
