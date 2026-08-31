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
import kotlin.test.assertFailsWith
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
        assertNull(items.single().thumbUrl)
    }

    @Test
    fun buildDirectArchiveSearchItems_usesInjectedArchiveBase() {
        val items = buildDirectArchiveSearchItems(
            "123",
            ArchiveSearchScope("may", "b"),
            archiveBaseUrl = "https://cache.example"
        )
        assertEquals("https://cache.example/b/res/123.htm", items.single().htmlUrl)
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
    fun archiveSearch_rejectsOversizedQueriesAndThreadIds() = runBlocking {
        assertEquals(
            emptyList(),
            buildDirectArchiveSearchItems("1".repeat(21), ArchiveSearchScope("may", "b"))
        )
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { error("Oversized query must be rejected before a request") }
            }
        }
        try {
            assertFailsWith<IllegalArgumentException> {
                searchInqueuetArchiveThreads(
                    httpClient = client,
                    archiveSearchJson = Json { ignoreUnknownKeys = true },
                    query = "x".repeat(513),
                    scope = ArchiveSearchScope("may", "b")
                )
            }
        } finally {
            client.close()
        }
        Unit
    }

    @Test
    fun searchInqueuetArchiveThreads_callsSearchEndpointAndMapsSnakeCaseResponse() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requestedUrls += request.url.toString()
                    when (request.url.encodedPath) {
                        "/search" -> respond(
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
                                      "archive_url": "https://may.inqueuet.com/b/res/1416523187.htm",
                                      "thumb_url": "/b/thumb/1416523187s.jpg"
                                    }
                                  ]
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        "/b/res/1416523187.htm" -> respond(
                            content = "<html><body>ok</body></html>",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "text/html; charset=Shift_JIS")
                        )
                        else -> error("Unexpected request: ${request.url}")
                    }
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
            listOf(
                "https://may.inqueuet.com/search?q=%E3%83%95%E3%82%A3%E3%82%AE%E3%83%A5%E3%82%A2&server=may&board=b&limit=20",
                "https://may.inqueuet.com/b/res/1416523187.htm"
            ),
            requestedUrls
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
        assertEquals("https://may.inqueuet.com/b/thumb/1416523187s.jpg", items.single().thumbUrl)
    }

    @Test
    fun searchInqueuetArchiveThreads_rejectsUntrustedResultUrlsBeforeProbing() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requestedUrls += request.url.toString()
                    respond(
                        content = """
                            {
                              "results": [
                                {
                                  "server": "may",
                                  "board": "b",
                                  "thread_no": "123",
                                  "archive_url": "http://127.0.0.1/res/123.htm"
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
            query = "test",
            scope = ArchiveSearchScope(server = "may", board = "b")
        )

        assertEquals(emptyList(), items)
        assertEquals(
            listOf("https://may.inqueuet.com/search?q=test&server=may&board=b&limit=20"),
            requestedUrls
        )
    }

    @Test
    fun searchInqueuetArchiveThreads_usesDirectCandidateForThreadNumberAndFetchesThumbnail() = runBlocking {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals("https://may.inqueuet.com/b/res/1415555296.htm", request.url.toString())
                    respond(
                        content = """
                            <html><body>
                              <div class="thre" data-res="1415555296">
                                <a href="/b/src/1782110274559.png">
                                  <img src="/b/thumb/1782110274559s.jpg">
                                </a>
                              </div>
                            </body></html>
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/html; charset=Shift_JIS")
                    )
                }
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
        assertEquals("https://may.inqueuet.com/b/thumb/1782110274559s.jpg", items.single().thumbUrl)
    }

    @Test
    fun searchInqueuetArchiveThreads_fetchesThreadHeadThumbnailWhenSearchResultHasNoThumb() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requestedUrls += request.url.toString()
                    when (request.url.encodedPath) {
                        "/search" -> respond(
                            content = """
                                {
                                  "q": "株",
                                  "server": "may",
                                  "board": "b",
                                  "limit": 5,
                                  "count": 1,
                                  "results": [
                                    {
                                      "id": "may/b/1416564780",
                                      "server": "may",
                                      "board": "b",
                                      "thread_no": "1416564780",
                                      "reply_count": 1000,
                                      "status": "complete",
                                      "total_bytes": 1034296,
                                      "saved_at": 1782121963135,
                                      "title": "株スレ",
                                      "archive_url": "https://may.inqueuet.com/b/res/1416564780.htm"
                                    }
                                  ]
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        "/b/res/1416564780.htm" -> respond(
                            content = """
                                <html><body>
                                  <div class="thre" data-res="1416564780">
                                    <a href="/b/src/1782110274559.png" target="_blank">
                                      <img src="/b/thumb/1782110274559s.jpg" border=0>
                                    </a>
                                  </div>
                                </body></html>
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "text/html; charset=Shift_JIS")
                        )
                        else -> error("Unexpected request: ${request.url}")
                    }
                }
            }
        }

        val items = searchInqueuetArchiveThreads(
            httpClient = client,
            archiveSearchJson = Json { ignoreUnknownKeys = true },
            query = "株",
            scope = ArchiveSearchScope(server = "may", board = "b"),
            limit = 5
        )

        assertEquals(
            listOf(
                "https://may.inqueuet.com/search?q=%E6%A0%AA&server=may&board=b&limit=5",
                "https://may.inqueuet.com/b/res/1416564780.htm"
            ),
            requestedUrls
        )
        assertEquals("https://may.inqueuet.com/b/thumb/1782110274559s.jpg", items.single().thumbUrl)
    }

    @Test
    fun searchInqueuetArchiveThreads_filtersSearchResultsReturning404EvenWithThumb() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requestedUrls += request.url.toString()
                    when (request.url.encodedPath) {
                        "/search" -> respond(
                            content = """
                                {
                                  "q": "株",
                                  "server": "may",
                                  "board": "b",
                                  "limit": 5,
                                  "count": 1,
                                  "results": [
                                    {
                                      "id": "may/b/1417196768",
                                      "server": "may",
                                      "board": "b",
                                      "thread_no": "1417196768",
                                      "reply_count": 1000,
                                      "status": "deleting",
                                      "title": "株スレ",
                                      "archive_url": "https://may.inqueuet.com/b/res/1417196768.htm",
                                      "thumb_url": "/b/thumb/1417196768s.jpg"
                                    }
                                  ]
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        "/b/res/1417196768.htm" -> respond(
                            content = "not found",
                            status = HttpStatusCode.NotFound,
                            headers = headersOf(HttpHeaders.ContentType, "text/plain; charset=UTF-8")
                        )
                        else -> error("Unexpected request: ${request.url}")
                    }
                }
            }
        }

        val items = searchInqueuetArchiveThreads(
            httpClient = client,
            archiveSearchJson = Json { ignoreUnknownKeys = true },
            query = "株",
            scope = ArchiveSearchScope(server = "may", board = "b"),
            limit = 5
        )

        assertEquals(emptyList(), items)
        assertEquals(
            listOf(
                "https://may.inqueuet.com/search?q=%E6%A0%AA&server=may&board=b&limit=5",
                "https://may.inqueuet.com/b/res/1417196768.htm"
            ),
            requestedUrls
        )
    }

    @Test
    fun searchInqueuetArchiveThreads_filtersDirectCandidateReturning404() = runBlocking {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals("https://may.inqueuet.com/b/res/1415555296.htm", request.url.toString())
                    respond(
                        content = "not found",
                        status = HttpStatusCode.NotFound,
                        headers = headersOf(HttpHeaders.ContentType, "text/plain; charset=UTF-8")
                    )
                }
            }
        }

        val items = searchInqueuetArchiveThreads(
            httpClient = client,
            archiveSearchJson = Json { ignoreUnknownKeys = true },
            query = "1415555296",
            scope = ArchiveSearchScope(server = "may", board = "b")
        )

        assertEquals(emptyList(), items)
    }

    @Test
    fun searchInqueuetArchiveThreads_keepsResultWhenProbeReturnsServerError() = runBlocking {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/search" -> respond(
                            content = """
                                {
                                  "q": "株",
                                  "server": "may",
                                  "board": "b",
                                  "limit": 5,
                                  "count": 1,
                                  "results": [
                                    {
                                      "id": "may/b/1417196768",
                                      "server": "may",
                                      "board": "b",
                                      "thread_no": "1417196768",
                                      "reply_count": 1000,
                                      "status": "deleting",
                                      "title": "株スレ",
                                      "archive_url": "https://may.inqueuet.com/b/res/1417196768.htm"
                                    }
                                  ]
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        "/b/res/1417196768.htm" -> respond(
                            content = "server error",
                            status = HttpStatusCode.InternalServerError,
                            headers = headersOf(HttpHeaders.ContentType, "text/plain; charset=UTF-8")
                        )
                        else -> error("Unexpected request: ${request.url}")
                    }
                }
            }
        }

        val items = searchInqueuetArchiveThreads(
            httpClient = client,
            archiveSearchJson = Json { ignoreUnknownKeys = true },
            query = "株",
            scope = ArchiveSearchScope(server = "may", board = "b"),
            limit = 5
        )

        assertEquals(1, items.size)
        assertEquals("1417196768", items.single().threadId)
    }
}
