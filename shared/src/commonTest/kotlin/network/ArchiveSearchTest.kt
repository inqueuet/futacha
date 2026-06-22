package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArchiveSearchTest {
    @Test
    fun extractArchiveSearchScope_usesBoardSlugAndServerFromThreadUrl() {
        val scope = extractArchiveSearchScope("https://may.2chan.net/b/res/123456.htm")

        assertEquals(ArchiveSearchScope(server = "may", board = "b"), scope)
    }

    @Test
    fun extractArchiveSearchScope_returnsNullForInvalidUrl() {
        assertNull(extractArchiveSearchScope("not a url"))
    }

    @Test
    fun buildInqueuetArchiveUrl_rewritesFutabaHostAndKeepsPath() {
        assertEquals(
            "https://may.inqueuet.com/b/res/1415555296.htm",
            buildInqueuetArchiveUrl("https://may.2chan.net/b/res/1415555296.htm")
        )
        assertEquals(
            "https://may.inqueuet.com/b/src/1234567890.jpg",
            buildInqueuetArchiveUrl("https://may.inqueuet.com/b/src/1234567890.jpg")
        )
    }

    @Test
    fun buildInqueuetArchiveThreadUrlFromUrl_requiresThreadPath() {
        assertEquals(
            "https://may.inqueuet.com/b/res/1415555296.htm",
            buildInqueuetArchiveThreadUrlFromUrl("https://may.2chan.net/b/res/1415555296.htm")
        )
        assertNull(buildInqueuetArchiveThreadUrlFromUrl("https://may.2chan.net/b/futaba.php"))
        assertNull(buildInqueuetArchiveThreadUrlFromUrl("https://may.2chan.net/b/"))
    }

    @Test
    fun isInqueuetArchiveUrl_detectsArchiveHostsOnly() {
        assertEquals(true, isInqueuetArchiveUrl("https://may.inqueuet.com/b/res/1.htm"))
        assertEquals(false, isInqueuetArchiveUrl("https://may.2chan.net/b/res/1.htm"))
    }

    @Test
    fun buildInqueuetArchiveThreadUrl_resolvesThreadFromBoardUrl() {
        assertEquals(
            "https://img.inqueuet.com/b/res/123.htm",
            buildInqueuetArchiveThreadUrl("https://img.2chan.net/b/futaba.php", "123")
        )
    }

    @Test
    fun buildDirectArchiveSearchItems_createsScopedThreadCandidateFromNumber() {
        val items = buildDirectArchiveSearchItems(" 1415555296 ", ArchiveSearchScope("may", "b"))

        assertEquals(1, items.size)
        assertEquals("1415555296", items.single().threadId)
        assertEquals("may", items.single().server)
        assertEquals("b", items.single().board)
        assertEquals("https://may.inqueuet.com/b/res/1415555296.htm", items.single().htmlUrl)
    }

    @Test
    fun buildDirectArchiveSearchItems_ignoresUrlInput() {
        val items = buildDirectArchiveSearchItems(
            "https://may.2chan.net/b/res/1415555296.htm",
            scope = ArchiveSearchScope("may", "b")
        )

        assertEquals(emptyList(), items)
    }

    @Test
    fun searchInqueuetArchiveThreads_callsSearchEndpointAndMapsSnakeCaseResponse() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requestedUrls += request.url.toString()
                    respond(
                        content = """
                            {
                              "q": "フィギュア",
                              "server": "may",
                              "board": "b",
                              "limit": 20,
                              "count": 1,
                              "results": [
                                {
                                  "id": "may/b/1416523187",
                                  "server": "may",
                                  "board": "b",
                                  "thread_no": "1416523187",
                                  "reply_count": 232,
                                  "status": "complete",
                                  "total_bytes": 17591812,
                                  "saved_at": 1782121545878,
                                  "title": "フィギュアスレ",
                                  "archive_url": "https://may.inqueuet.com/b/res/1416523187.htm"
                                }
                              ]
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }

        val items = searchInqueuetArchiveThreads(
            httpClient = client,
            archiveSearchJson = Json { ignoreUnknownKeys = true },
            query = "フィギュア",
            scope = ArchiveSearchScope(server = "may", board = "b")
        )

        assertEquals(
            "https://may.inqueuet.com/search?q=%E3%83%95%E3%82%A3%E3%82%AE%E3%83%A5%E3%82%A2&server=may&board=b&limit=20",
            requestedUrls.single()
        )
        assertEquals(1, items.size)
        assertEquals("1416523187", items.single().threadId)
        assertEquals("may", items.single().server)
        assertEquals("b", items.single().board)
        assertEquals(232, items.single().replyCount)
        assertEquals(17_591_812L, items.single().totalBytes)
        assertEquals(1_782_121_545_878L, items.single().savedAt)
        assertEquals("フィギュアスレ", items.single().title)
        assertEquals("https://may.inqueuet.com/b/res/1416523187.htm", items.single().htmlUrl)
    }

    @Test
    fun searchInqueuetArchiveThreads_usesDirectCandidateForThreadNumberWithoutNetwork() = runBlocking {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { error("Network should not be called") }
            }
        }

        val items = searchInqueuetArchiveThreads(
            httpClient = client,
            archiveSearchJson = Json { ignoreUnknownKeys = true },
            query = "1415555296",
            scope = ArchiveSearchScope(server = "may", board = "b")
        )

        assertEquals(1, items.size)
        assertEquals("https://may.inqueuet.com/b/res/1415555296.htm", items.single().htmlUrl)
    }
}
