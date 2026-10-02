package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.prepareGet
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Ktor's plain get() buffers the whole body before returning, so head-only
 * reads and size limits have to run on a streamed response. These bodies never
 * end: a buffering implementation would never return.
 */
class HttpBoardApiStreamingTest {
    private val writers = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @AfterTest fun stopWriters() = writers.cancel()

    private fun endlessClient(prefix: String, keepWriting: suspend (ByteChannel) -> Unit) = HttpClient(MockEngine {
        val channel = ByteChannel()
        writers.launch {
            channel.writeFully(prefix.encodeToByteArray())
            channel.flush()
            keepWriting(channel)
        }
        respond(channel, HttpStatusCode.OK)
    })

    private fun request(mode: HttpBoardApiTextReadMode) = HttpBoardApiTextGetRequest(
        url = "https://may.2chan.net/b/res/1.htm",
        referer = null,
        errorLabel = "thread",
        maxResponseSize = 1_000_000L,
        readMode = mode,
        maxLines = 3
    )

    private suspend fun get(client: HttpClient, request: HttpBoardApiTextGetRequest, maxBytes: Int = 1_000_000) =
        executeHttpBoardApiTextGet(
            client = client,
            request = request,
            userAgent = "test",
            accept = "*/*",
            acceptLanguage = "ja",
            readSmallResponseSummary = { null },
            readResponseBodyAsString = { response ->
                readHttpBoardApiResponseBodyAsString(response, maxBytes, 8_192, 3, 10L, 10_000L)
            },
            readResponseHeadAsString = { response, maxLines ->
                readHttpBoardApiResponseHeadAsString(response, maxLines, maxBytes, 8_192, 3, 10L, 10_000L)
            }
        )

    @Test
    fun headReadReturnsAfterTheFirstLinesOfAnEndlessBody(): Unit = runBlocking {
        val client = endlessClient("line1\nline2\nline3\nline4\n") { awaitCancellation() }
        try {
            val head = withTimeout(5_000) { get(client, request(HttpBoardApiTextReadMode.HEAD)) }
            assertTrue(head.startsWith("line1\nline2"), head)
        } finally { client.close() }
    }

    @Test
    fun bodyLimitStopsAnOversizedStreamWhileReceiving(): Unit = runBlocking {
        val chunk = ByteArray(64 * 1024) { 'a'.code.toByte() }
        val client = endlessClient("") { channel -> while (true) { channel.writeFully(chunk); channel.flush() } }
        try {
            withTimeout(10_000) {
                assertFailsWith<NetworkException> { get(client, request(HttpBoardApiTextReadMode.BODY), maxBytes = 256 * 1024) }
            }
        } finally { client.close() }
    }

    @Test
    fun readBudgetExpiryIsANetworkFailureNotACancellation(): Unit = runBlocking {
        // A body that keeps trickling never trips the idle timeout, only the total budget.
        val client = endlessClient("") { channel ->
            while (true) {
                channel.writeFully(byteArrayOf('a'.code.toByte()))
                channel.flush()
                delay(20)
            }
        }
        try {
            val failure = withTimeout(5_000) {
                client.prepareGet("https://example.com/slow").execute { response ->
                    runCatching { readBoundedHttpResponseBytes(response, 1_000_000, totalTimeoutMillis = 200L) }
                        .exceptionOrNull()
                }
            }
            assertTrue(failure is NetworkException, "was $failure")
            assertTrue(failure.cause is IOException, "cause was ${failure.cause}")
        } finally { client.close() }
    }

    @Test
    fun prefixReadReturnsCompleteLinesFromAnEndlessBody(): Unit = runBlocking {
        val line = "<input type=\"hidden\" name=\"chrenc\" value=\"文字\">\n".encodeToByteArray()
        val client = endlessClient("") { channel -> while (true) { channel.writeFully(line); channel.flush() } }
        try {
            val prefix = withTimeout(5_000) {
                client.prepareGet("https://may.2chan.net/b/res/1.htm").execute { response ->
                    readHttpBoardApiResponsePrefixAsString(response, 1_000, 256, 3, 10L, 5_000L)
                }
            }
            assertTrue(prefix.length <= 1_000)
            assertTrue(prefix.endsWith("\n"), prefix)
            assertTrue(prefix.lines().filter { it.isNotEmpty() }.all { it == line.decodeToString().trimEnd() }, prefix)
        } finally { client.close() }
    }
}
