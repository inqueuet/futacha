package com.valoser.futacha.shared.ui.compat

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class CompatCacheServerTest {
    @Test
    fun statusDateAndRetryCadenceMatchReferenceActivityStartupChecks() {
        val now = Instant.parse("2026-08-25T19:37:00Z").toEpochMilliseconds()
        assertEquals("2026/08/25 19:00", formatCompatCacheStatusDate(now, TimeZone.UTC))
        assertEquals(
            "2026/08/25 19:00 - 稼働中",
            formatCompatCacheStatusSummary("2026/08/25 19:00", "稼働中")
        )

        assertEquals(
            true,
            shouldProbeCompatCacheServer(now, "2026/08/25 18:00", true, now, TimeZone.UTC)
        )
        assertEquals(
            false,
            shouldProbeCompatCacheServer(now, "2026/08/25 19:00", true, 0L, TimeZone.UTC)
        )
        assertEquals(
            false,
            shouldProbeCompatCacheServer(
                now,
                "2026/08/25 19:00",
                false,
                now - COMPAT_CACHE_UNAVAILABLE_RETRY_MILLIS,
                TimeZone.UTC
            )
        )
        assertEquals(
            true,
            shouldProbeCompatCacheServer(
                now,
                "2026/08/25 19:00",
                false,
                now - COMPAT_CACHE_UNAVAILABLE_RETRY_MILLIS - 1L,
                TimeZone.UTC
            )
        )
    }

    @Test
    fun catalogAndThreadToggleImmediatelyWithReferenceMessages() {
        assertEquals(CompatCacheToggle("ON", "通信の軽量化オン"), nextCompatCacheToggle(false))
        assertEquals(CompatCacheToggle("OFF", "通信の軽量化オフ"), nextCompatCacheToggle(true))
    }

    @Test
    fun cacheUseMatchesReferenceAvailabilityBoardAndResponseThreshold() {
        val mayThread = "https://may.2chan.net/b/res/123.htm"
        assertEquals(false, canUseCompatCacheServer(false, true, mayThread, 0))
        assertEquals(false, canUseCompatCacheServer(true, false, mayThread, 0))
        assertEquals(true, canUseCompatCacheServer(true, true, mayThread, 0))
        assertEquals(false, canUseCompatCacheServer(true, true, mayThread, 499))
        assertEquals(true, canUseCompatCacheServer(true, true, mayThread, 500))
        assertEquals(true, canUseCompatCacheServer(true, true, "https://img.2chan.net/b/res/1.htm", 500))
        assertEquals(true, canUseCompatCacheServer(true, true, "https://jun.2chan.net/jun/res/1.htm", 500))
        assertEquals(true, canUseCompatCacheServer(true, true, "https://dec.2chan.net/b/res/1.htm", 500))
        assertEquals(false, canUseCompatCacheServer(true, true, "https://may.2chan.net/c/res/1.htm", 500))
        assertEquals(false, canUseCompatCacheServer(true, true, "https://example.com/b/res/1.htm", 500))
    }

    @Test
    fun cacheBaseUrlRejectsCredentialsPathAndNonHttp() {
        assertEquals("https://cache.example", normalizeCompatCacheBaseUrl("https://cache.example/"))
        assertNull(normalizeCompatCacheBaseUrl("https://cache.example/api"))
        assertNull(normalizeCompatCacheBaseUrl("https://user:pass@cache.example"))
        assertNull(normalizeCompatCacheBaseUrl("javascript:alert(1)"))
    }

    @Test
    fun cacheThreadUrlPreservesBoardAndThreadPath() {
        assertEquals(
            "https://cache.example/b/res/123.htm",
            buildCompatCacheThreadUrl(
                "https://may.2chan.net/b/res/123.htm",
                "https://cache.example"
            )
        )
        assertNull(buildCompatCacheThreadUrl("https://may.2chan.net/b/futaba.php", "https://cache.example"))
    }

    @Test
    fun probeReadsOnlyHealthAndUsesTheReferenceStatusLabel() = runBlocking {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/health/search" -> respond("{\"ok\":true}", HttpStatusCode.OK, headersOf())
                        "/stats" -> respond("{\"thread_count\":1}", HttpStatusCode.OK, headersOf())
                        else -> error("unexpected \${request.url}")
                    }
                }
            }
        }
        val status = probeCompatCacheServer(client, "https://cache.example", 10L)
        assertEquals(true, status.available)
        assertEquals(10L, status.checkedAtEpochMillis)
        assertEquals("稼働中", status.message)
    }

    @Test
    fun probeTurnsItsOwnDeadlineIntoReferenceTimeoutStatus() = runBlocking {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    delay(100)
                    respond("{\"ok\":true}", HttpStatusCode.OK, headersOf())
                }
            }
        }

        val status = probeCompatCacheServer(
            client,
            "https://cache.example",
            nowEpochMillis = 20L,
            timeoutMillis = 10L
        )
        assertEquals(false, status.available)
        assertEquals("通信タイムアウト", status.message)
    }

    @Test
    fun probePreservesCancellation() = runBlocking {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { throw CancellationException("screen closed") }
            }
        }

        assertFailsWith<CancellationException> {
            probeCompatCacheServer(client, "https://cache.example", 10L)
        }
        Unit
    }
}
