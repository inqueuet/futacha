package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.SendCountExceedException
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Engine-independent check of the redirect cap every platform client installs (also runs on iOS Native). */
class HttpRedirectLimitTest {
    private val methods = mutableListOf<HttpMethod>()

    private fun loopingClient(okAfter: Int = Int.MAX_VALUE) =
        HttpClient(MockEngine(MockEngineConfig().apply {
            addHandler { request ->
                methods += request.method
                if (methods.size > okAfter) respond("ok", HttpStatusCode.OK)
                else respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/loop"))
            }
        })) {
            // Same redirect and read retry setup as the platform factories.
            installPostRedirectFollowingAndReadRetry { delayMillis { 1L } }
        }

    /** POST answered 302; the redirected GET answers 500 for the first [failingGets] GETs. */
    private fun postRedirectingToFailingGetClient(failingGets: Int) =
        HttpClient(MockEngine(MockEngineConfig().apply {
            addHandler { request ->
                methods += request.method
                when {
                    request.method == HttpMethod.Post ->
                        respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/b/res/1.htm"))
                    methods.count { it == HttpMethod.Get } <= failingGets ->
                        respond("busy", HttpStatusCode.InternalServerError)
                    else -> respond("ok", HttpStatusCode.OK)
                }
            }
        })) {
            installPostRedirectFollowingAndReadRetry { delayMillis { 1L } }
        }

    @Test fun getRedirectLoopFailsAfterTheSendLimit() = runBlocking {
        val client = loopingClient()
        try {
            assertFailsWith<SendCountExceedException> { client.get("https://may.2chan.net/loop") }
            assertEquals(MAX_SENDS_PER_CALL, methods.size)
            assertEquals(20, MAX_SENDS_PER_CALL)
        } finally { client.close() }
    }

    @Test fun postFollowedAsGetIntoALoopAlsoStopsAtTheSendLimit() = runBlocking {
        val client = loopingClient()
        try {
            assertFailsWith<SendCountExceedException> {
                client.submitForm("https://may.2chan.net/b/futaba.php", Parameters.build { append("com", "x") })
            }
            assertEquals(MAX_SENDS_PER_CALL, methods.size)
            assertEquals(listOf(HttpMethod.Post), methods.filter { it == HttpMethod.Post })
        } finally { client.close() }
    }

    @Test fun postFollowedAsGetThroughAFurtherRedirectNeverResendsThePost() = runBlocking {
        val client = loopingClient(okAfter = 3)
        try {
            assertEquals("ok", client.submitForm("https://may.2chan.net/b/futaba.php", Parameters.build { append("com", "x") }).bodyAsText())
            assertEquals(listOf(HttpMethod.Post, HttpMethod.Get, HttpMethod.Get, HttpMethod.Get), methods)
        } finally { client.close() }
    }

    // F4-1: the retry used to see the redirected GET's 5xx, judge it safe and
    // resend its own sub-request, the POST with its body: P,G,P,G,P,G.
    @Test fun postWhoseRedirectedGetKeepsAnswering5xxIsSentExactlyOnce() = runBlocking {
        val client = postRedirectingToFailingGetClient(failingGets = Int.MAX_VALUE)
        try {
            val response = client.submitForm("https://may.2chan.net/b/futaba.php", Parameters.build { append("com", "x") })
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals(1, methods.count { it == HttpMethod.Post })
            assertEquals(listOf(HttpMethod.Post, HttpMethod.Get, HttpMethod.Get, HttpMethod.Get), methods)
        } finally { client.close() }
    }

    @Test fun postWhoseRedirectedGetRecoversAfterA5xxRetriesOnlyTheGet() = runBlocking {
        val client = postRedirectingToFailingGetClient(failingGets = 1)
        try {
            val response = client.submitForm("https://may.2chan.net/b/futaba.php", Parameters.build { append("com", "x") })
            assertEquals("ok", response.bodyAsText())
            assertEquals(listOf(HttpMethod.Post, HttpMethod.Get, HttpMethod.Get), methods)
        } finally { client.close() }
    }

    @Test fun getAnswering5xxIsStillRetried() = runBlocking {
        val client = postRedirectingToFailingGetClient(failingGets = 2)
        try {
            assertEquals("ok", client.get("https://may.2chan.net/b/res/1.htm").bodyAsText())
            assertEquals(listOf(HttpMethod.Get, HttpMethod.Get, HttpMethod.Get), methods)
        } finally { client.close() }
    }

    @Test fun ordinaryRedirectChainsBelowTheLimitStillSucceed() = runBlocking {
        val client = loopingClient(okAfter = MAX_SENDS_PER_CALL - 1)
        try {
            assertEquals("ok", client.get("https://may.2chan.net/loop").bodyAsText())
            assertEquals(MAX_SENDS_PER_CALL, methods.size)
        } finally { client.close() }
    }
}
