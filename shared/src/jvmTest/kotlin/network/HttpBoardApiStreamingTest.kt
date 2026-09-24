package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
}
