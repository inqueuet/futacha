package com.valoser.futacha

import com.valoser.futacha.shared.compat.ARCHIVE_REPORT_MAX_RESPONSE_BYTES
import com.valoser.futacha.shared.compat.ArchiveReportResponse
import com.valoser.futacha.shared.compat.NormalizedArchiveThread
import com.valoser.futacha.shared.compat.buildArchiveReportPayload
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveReportSenderTest {
    @Test
    fun senderUsesOnlyPermittedApplicationHeadersAndExactPayloadBytes() = runBlocking {
        val payload = requireNotNull(
            buildArchiveReportPayload(
                "12345678-http",
                listOf(NormalizedArchiveThread("may/b/1", "https://may.2chan.net/b/res/1.htm"))
            )
        )
        var requestBody: ByteArray? = null
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requestBody = (request.body as OutgoingContent.ByteArrayContent).bytes()
                    assertEquals(ContentType.Application.Json, request.body.contentType)
                    assertEquals(ContentType.Application.Json.toString(), request.headers[HttpHeaders.Accept])
                    assertEquals("Futacha/5", request.headers[HttpHeaders.UserAgent])
                    assertNull(request.headers[HttpHeaders.Cookie])
                    assertNull(request.headers[HttpHeaders.Authorization])
                    assertNull(request.headers[HttpHeaders.Referrer])
                    assertNull(request.headers[HttpHeaders.Origin])
                    respond(
                        content = "{\"accepted\":true,\"received\":1,\"retry_after_seconds\":0}",
                        status = HttpStatusCode.Accepted,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            }
        }
        try {
            val result = ArchiveReportSender(
                client,
                endpoint = "https://unit.test/api/v1/viewed-threads",
                allowTestEndpoint = true
            ).send(payload, 1_000L)
            assertEquals(202, result.status)
            assertEquals(ArchiveReportResponse(accepted = true, received = 1, retryAfterSeconds = 0), result.response)
            assertArrayEquals(payload.bytes, requestBody)
        } finally {
            client.close()
        }
    }

    @Test
    fun senderHonorsRetryAfterAndRejectsOversizedResponseBody() = runBlocking {
        val payload = requireNotNull(
            buildArchiveReportPayload(
                "12345678-limit",
                listOf(NormalizedArchiveThread("may/b/2", "https://may.2chan.net/b/res/2.htm"))
            )
        )
        var call = 0
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    call += 1
                    if (call == 1) {
                        respond(
                            content = "{\"accepted\":false,\"reason\":\"rate_limited\",\"retry_after_seconds\":1}",
                            status = HttpStatusCode.TooManyRequests,
                            headers = headersOf(HttpHeaders.RetryAfter, "600")
                        )
                    } else {
                        respond(
                            content = "x".repeat(ARCHIVE_REPORT_MAX_RESPONSE_BYTES + 1),
                            status = HttpStatusCode.Accepted
                        )
                    }
                }
            }
        }
        val sender = ArchiveReportSender(
            client,
            endpoint = "https://unit.test/api/v1/viewed-threads",
            allowTestEndpoint = true
        )
        try {
            val limited = sender.send(payload, 1_000L)
            assertEquals(600_000L, limited.retryAfterMillis)
            assertEquals("rate_limited", limited.response?.reason)
            val oversized = sender.send(payload, 1_000L)
            assertEquals(202, oversized.status)
            assertNull(oversized.response)
            assertTrue(call == 2)
        } finally {
            client.close()
        }
    }
}
