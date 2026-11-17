package com.valoser.futacha.shared.network.aimoge

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AimogeControlResponseLimitTest {
    @Test fun responseLargerThanOneMiBIsRejectedBeforeJsonParsing() = runTest {
        val client = HttpClient(MockEngine { respond(ByteReadChannel(ByteArray(1024 * 1024 + 1)), HttpStatusCode.OK) })
        try {
            assertFailsWith<Exception> { readAimogeControlBody(client.get("https://example.invalid/")) }
        } finally { client.close() }
    }

    @Test fun multibyteUtf8WithinTheLimitSurvives() = runTest {
        val expected = "本文".repeat(100)
        val client = HttpClient(MockEngine { respond(expected, HttpStatusCode.OK) })
        try { assertEquals(expected, readAimogeControlBody(client.get("https://example.invalid/"))) }
        finally { client.close() }
    }
}
