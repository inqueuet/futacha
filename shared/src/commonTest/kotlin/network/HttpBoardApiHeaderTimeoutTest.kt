package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.*
import kotlin.test.*

class HttpBoardApiHeaderTimeoutTest {
    @Test fun stalledHeadersLeaveTimeForAnotherAttempt() = runBlocking {
        var attempts = 0
        val client = HttpClient(MockEngine { if (++attempts == 1) awaitCancellation() else respond("ok") })
        try {
            val body = withHttpBoardApiRetry("headers-test", 5_000, 2, 0, overallBudgetMillis = 5_000) {
                client.prepareGet("https://example.com").executeWithHeaderTimeout(50) { it.bodyAsText() }
            }
            assertEquals("ok", body)
            assertEquals(2, attempts)
        } finally { client.close() }
    }

    @Test fun receivedHeadersDisableTheHeaderDeadlineForBodyProcessing() = runBlocking {
        val client = HttpClient(MockEngine { respond("ok") })
        try {
            val body = client.prepareGet("https://example.com").executeWithHeaderTimeout(100) {
                delay(200)
                it.bodyAsText()
            }
            assertEquals("ok", body)
        } finally { client.close() }
    }

    @Test fun aSlowServerStillAnswersTheFirstRequestAfterTheHedgeDelay() = runBlocking {
        // Every request needs 300 ms for headers: a 150 ms header deadline failed
        // both attempts, the hedge keeps the first request and succeeds.
        var requests = 0
        val client = HttpClient(MockEngine { requests += 1; delay(300); respond("ok") })
        try {
            val body = withHttpBoardApiRetry("hedge-test", 2_000, 2, 0, overallBudgetMillis = 2_000) {
                executeWithHeaderHedge(150, { client.prepareGet("https://example.com") }) { it.bodyAsText() }
            }
            assertEquals("ok", body)
            assertEquals(2, requests)
        } finally { client.close() }
    }

    @Test fun aStalledFirstRequestIsReplacedByTheHedge() = runBlocking {
        var requests = 0
        val client = HttpClient(MockEngine { if (++requests == 1) awaitCancellation() else respond("ok") })
        try {
            val body = withTimeout(5_000) {
                executeWithHeaderHedge(50, { client.prepareGet("https://example.com") }) { it.bodyAsText() }
            }
            assertEquals("ok", body)
            assertEquals(2, requests)
        } finally { client.close() }
    }

    @Test fun aFailureBeforeTheHedgeDelayIsReportedWithoutASecondRequest() = runBlocking {
        var requests = 0
        val client = HttpClient(MockEngine { requests += 1; throw kotlinx.io.IOException("reset") })
        try {
            val error = assertFailsWith<kotlinx.io.IOException> {
                executeWithHeaderHedge(5_000, { client.prepareGet("https://example.com") }) { it.bodyAsText() }
            }
            assertEquals("reset", error.message)
            assertEquals(1, requests)
        } finally { client.close() }
    }

    @Test fun whenBothRequestsFailTheLaterFailureIsThrown() = runBlocking {
        var requests = 0
        val client = HttpClient(MockEngine {
            val index = ++requests
            if (index == 1) { delay(300); throw kotlinx.io.IOException("first") }
            delay(400)
            throw kotlinx.io.IOException("second")
        })
        try {
            val error = assertFailsWith<kotlinx.io.IOException> {
                executeWithHeaderHedge(50, { client.prepareGet("https://example.com") }) { it.bodyAsText() }
            }
            assertEquals("second", error.message)
            assertEquals(2, requests)
        } finally { client.close() }
    }
}
