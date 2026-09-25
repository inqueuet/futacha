package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Uses the same retry policy as the Android/iOS/JVM clients. */
class HttpBoardApiAutomaticRetryTest {
    private var attempts = 0

    private fun retryingClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        HttpClient(MockEngine(MockEngineConfig().apply { addHandler(handler) })) {
            install(HttpRequestRetry) {
                maxRetries = 2
                delayMillis { 1L }
                retryIf(maxRetries) { request, response ->
                    shouldUseClientAutomaticRetry(
                        method = request.method,
                        higherLayerRetryManaged = request.attributes.getOrNull(HigherLayerRetryManaged) == true
                    ) && response.status.value in 500..599
                }
                retryOnExceptionIf { request, cause ->
                    shouldUseClientAutomaticRetry(
                        method = request.method,
                        higherLayerRetryManaged = request.attributes.getOrNull(HigherLayerRetryManaged) == true
                    ) && cause !is CancellationException
                }
            }
        }

    @Test
    fun saidaneVoteIsSentOnceEvenWhenTheResponseIsAServerError() = runBlocking {
        val client = retryingClient {
            attempts += 1
            respond("error", HttpStatusCode.InternalServerError)
        }
        // Control: an ordinary GET is retried by this client.
        assertEquals("error", client.get("https://may.2chan.net/b/futaba.htm").bodyAsText())
        assertEquals(3, attempts)

        attempts = 0
        val api = HttpBoardApi(client)
        try {
            assertFailsWith<NetworkException> {
                api.voteSaidane("https://may.2chan.net/b/futaba.php", "123", "456")
            }
            assertEquals(1, attempts)
        } finally {
            api.close()
        }
    }

    @Test
    fun goneProbeIsNotRetriedByTheClient() = runBlocking {
        val api = HttpBoardApi(
            retryingClient {
                attempts += 1
                throw IOException("connection reset")
            }
        )
        try {
            assertFalse(api.probeThreadGone("https://may.2chan.net/b/res/123.htm"))
            assertEquals(1, attempts)
        } finally {
            api.close()
        }
    }

    @Test
    fun prePostConfigFetchIsSentOnceAndBoundedByItsOwnTimeout() = runBlocking {
        val client = retryingClient {
            attempts += 1
            delay(10_000L)
            respond("<input name=\"chrenc\" value=\"文字\">")
        }
        try {
            val error = assertFailsWith<NetworkException> {
                fetchHttpBoardApiPostingConfig(
                    client = client,
                    board = "https://may.2chan.net/b/futaba.php",
                    threadId = "123",
                    userAgent = "test",
                    accept = "*/*",
                    acceptLanguage = "ja",
                    cacheControl = "no-cache",
                    logTag = "HttpBoardApiAutomaticRetryTest",
                    fallbackChrencValue = "文字",
                    readSmallResponseSummary = { null },
                    readResponseBodyAsString = { it.bodyAsText() },
                    timeoutMillis = 50L
                )
            }
            assertTrue(error.message.orEmpty().contains("Timed out"))
            assertEquals(1, attempts)
        } finally {
            client.close()
        }
    }
}
