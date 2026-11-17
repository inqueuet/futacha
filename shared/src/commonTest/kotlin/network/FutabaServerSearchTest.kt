package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class FutabaServerSearchTest {
    private val board = "https://img.2chan.net/b/"
    private val html = """<html>検索モード<script>var ret={"res":{"123":{"resto":0,"com":"スレ本文<br>あ&amp;い","now":"日時"},"456":{"resto":123,"com":"一致 } ; 引用","now":""}},"bbscode":"b"};</script><div id="searchResult"></div></html>"""

    @Test fun searchResponsesOpenTheContainingThreadAndKeepTheMatchingPostNumber() {
        val rows = parseFutabaServerSearch(html, board)
        assertEquals(listOf("123", "456"), rows.map { it.postId })
        assertTrue(rows.all { it.thread.id == "123" && it.thread.threadUrl == "${board}res/123.htm" })
        assertTrue(rows.first().text.contains("あ&い"))
        assertEquals("一致 } ; 引用", rows[1].text)
    }

    @Test fun emptyResultsAreDifferentFromAnUnreadableBlockPage() {
        assertTrue(parseFutabaServerSearch("""<script>var ret={"bbscode":"b"};</script><div id="searchResult"></div>""", board).isEmpty())
        assertFails { parseFutabaServerSearch("<html>アクセス制限</html>", board) }
        assertFails { parseFutabaServerSearch("""<script>var ret={"res":"invalid"};</script>""", board) }
    }

    @Test fun searchIsAReadOnlyRequestWithTheSitesShiftJisEncoding() = runTest {
        var request: HttpRequestData? = null
        val client = HttpClient(MockEngine { request = it; respond(html, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8")) })
        try {
            assertEquals(2, kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { searchFutabaPosts(client, board, "検索") }.size)
            assertEquals(HttpMethod.Get, request!!.method)
            assertEquals("/b/futaba.php", request!!.url.encodedPath)
            assertTrue(request!!.url.encodedQuery.contains("keyword=%8c%9f%8d%f5", ignoreCase = true))
            assertEquals("search", request!!.url.parameters["mode"])
        } finally { client.close() }
    }

    @Test fun onlyFutabaBoardsAndNonemptyBoundedQueriesAreAccepted() {
        assertNull(futabaServerSearchBase("https://nijiurachan.net/b/aimg/"))
        assertNull(futabaServerSearchBase("https://img.2chan.net.example.com/b/"))
        assertFails { futabaServerSearchUrl(board, " ") }
        assertFails { futabaServerSearchUrl(board, "x".repeat(201)) }
    }
}
