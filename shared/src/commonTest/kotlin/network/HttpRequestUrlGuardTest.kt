package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** S4-1: request URLs that Ktor would send to a host other than the one they appear to name. */
class HttpRequestUrlGuardTest {
    private val requestedHosts = mutableListOf<String>()

    private fun guardedClient() = HttpClient(MockEngine(MockEngineConfig().apply {
        addHandler { request ->
            requestedHosts += request.url.host
            respond("ok", HttpStatusCode.OK)
        }
    })) {
        installPostRedirectFollowingAndReadRetry { delayMillis { 1L } }
        installAmbiguousRequestUrlGuard()
    }

    @Test fun ktorSendsTheBackslashBypassToTheHostBeforeTheBackslash() {
        // Documents why the string checks must refuse `\`: the request host is evil.com.
        assertEquals("evil.com", Url("https://evil.com\\@may.2chan.net/b/res/1.htm").host)
    }

    @Test fun ambiguousRequestUrlsFailWithoutReachingAnyHost() = runBlocking {
        val client = guardedClient()
        try {
            listOf(
                "https://evil.com\\@may.2chan.net/b/res/1.htm",
                "https://evil.com\\may.2chan.net/b/res/1.htm",
                "https://evil.com@may.2chan.net/b/res/1.htm",
                "https://may.2chan.net@evil.com/b/res/1.htm",
                "https://user:pass@may.2chan.net/b/res/1.htm",
                "https://may.2chan.net/\\evil.com/b/res/1.htm",
                "https://evil.com%5C%40may.2chan.net/b/res/1.htm",
                "https://evil.com%40may.2chan.net/b/res/1.htm",
                "https://evil.com\u0001.2chan.net/b/res/1.htm"
            ).forEach { url ->
                assertFailsWith<AmbiguousRequestUrlException>(url) { client.get(url) }
            }
            // Ktor's own parser already refuses whitespace in a host.
            assertFailsWith<Exception> { client.get("https://evil.com\t.2chan.net/b/res/1.htm") }
            assertTrue(requestedHosts.isEmpty(), "requested=$requestedHosts")
        } finally { client.close() }
    }

    @Test fun ordinaryRequestUrlsStillLoad() = runBlocking {
        val client = guardedClient()
        try {
            assertEquals("ok", client.get("https://may.2chan.net/b/res/1.htm").bodyAsText())
            assertEquals("ok", client.get("https://may.2chan.net/b/futaba.php?mode=cat&q=a%5Cb%40c").bodyAsText())
            assertEquals("ok", client.get("https://example.com:8443/test/futaba.php").bodyAsText())
            assertEquals(listOf("may.2chan.net", "may.2chan.net", "example.com"), requestedHosts)
        } finally { client.close() }
    }
}
