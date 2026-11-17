package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** ふたばへの通信は、読む・画像・投稿のどれも端末のブラウザと同じUA（2026-11-13）。 */
class FutabaBrowserIdentityTest {
    private val seen = mutableListOf<Pair<String, Map<String, String?>>>()
    private var asked = 0
    private val browser = object : PostingBrowser {
        override suspend fun userAgent(): String { asked++; return "Mozilla/5.0 (Linux; Android 15; wv) Mobile Safari/537.36" }
        override suspend fun evaluate(pageUrl: String, html: String, script: String) = ""
    }
    private fun client() = HttpClient(MockEngine { request ->
        seen += request.url.host to mapOf(
            "ua" to request.headers[HttpHeaders.UserAgent],
            "lang" to request.headers[HttpHeaders.AcceptLanguage]
        )
        respond("")
    }) { installFutabaBrowserIdentity() }

    @AfterTest fun reset() = FutabaBrowserIdentity.resetForTest()

    @Test fun everyFutabaRequestGoesOutAsTheBrowser() = runTest {
        FutabaBrowserIdentity.resetForTest(browser)
        val c = client()
        // A read that still names the old desktop Chrome, a picture with no UA, the site root.
        c.get("https://may.2chan.net/b/futaba.php?mode=cat") { header(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/152") }
        c.get("https://may.2chan.net/b/src/1.jpg")
        c.get("https://www.2chan.net/")
        assertTrue(seen.all { it.second["ua"] == "Mozilla/5.0 (Linux; Android 15; wv) Mobile Safari/537.36" }, "$seen")
        assertTrue(seen.all { it.second["lang"] == FUTABA_BROWSER_ACCEPT_LANGUAGE })
        assertEquals(1, asked, "the browser is asked once")
    }

    @Test fun otherSitesAndNoBrowserAreLeftAlone() = runTest {
        FutabaBrowserIdentity.resetForTest(browser)
        val c = client()
        c.get("https://nijiurachan.net/api/boards") { header(HttpHeaders.UserAgent, "futacha/14.2") }
        c.get("https://evil2chan.net/") { header(HttpHeaders.UserAgent, "x") }
        assertEquals("futacha/14.2", seen[0].second["ua"])
        assertEquals("x", seen[1].second["ua"])
        FutabaBrowserIdentity.resetForTest(null)
        seen.clear()
        client().get("https://may.2chan.net/b/") { header(HttpHeaders.UserAgent, "desktop") }
        assertEquals("desktop", seen[0].second["ua"])
    }
}
