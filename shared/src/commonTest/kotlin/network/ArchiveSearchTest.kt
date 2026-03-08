package com.valoser.futacha.shared.network

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArchiveSearchTest {
    private val json = Json { ignoreUnknownKeys = true }

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
    fun buildArchiveSearchUrl_includesEncodedQueryAndScope() {
        val url = buildArchiveSearchUrl(
            query = "猫 123",
            scope = ArchiveSearchScope(server = "may", board = "b")
        )

        assertEquals(
            "https://spider.serendipity01234.workers.dev/search?q=%E7%8C%AB+123&server=may&board=b",
            url
        )
    }

    @Test
    fun parseArchiveSearchResults_supportsObjectEnvelopeAndFallbackFields() {
        val items = parseArchiveSearchResults(
            body = """
                {
                  "threads": [
                    {
                      "url": "https://may.2chan.net/b/res/100.htm",
                      "subject": "一件目",
                      "thumb": "https://img.example/100s.jpg",
                      "state": "archived",
                      "created": "1700000000"
                    },
                    {
                      "html_url": "https://may.2chan.net/b/res/200.htm",
                      "id": "200",
                      "title": "二件目"
                    }
                  ]
                }
            """.trimIndent(),
            scope = ArchiveSearchScope(server = "may", board = "b"),
            json = json
        )

        assertEquals(2, items.size)
        assertEquals("100", items[0].threadId)
        assertEquals("一件目", items[0].title)
        assertEquals("https://img.example/100s.jpg", items[0].thumbUrl)
        assertEquals("archived", items[0].status)
        assertEquals(1_700_000_000L, items[0].createdAt)
        assertEquals("may", items[0].server)
        assertEquals("b", items[0].board)
        assertEquals("200", items[1].threadId)
        assertEquals("二件目", items[1].title)
    }

    @Test
    fun parseArchiveSearchItem_usesScopeWhenServerAndBoardAreMissing() {
        val item = parseArchiveSearchItem(
            element = json.parseToJsonElement(
                """
                    {
                      "href": "https://may.2chan.net/b/res/300.htm",
                      "title": "fallback"
                    }
                """.trimIndent()
            ),
            scope = ArchiveSearchScope(server = "may", board = "b")
        )

        assertEquals(
            ArchiveSearchItem(
                threadId = "300",
                server = "may",
                board = "b",
                title = "fallback",
                htmlUrl = "https://may.2chan.net/b/res/300.htm"
            ),
            item
        )
    }

    @Test
    fun selectLatestArchiveMatch_prefersUploadedThenFinalizedThenCreated() {
        val match = selectLatestArchiveMatch(
            items = listOf(
                ArchiveSearchItem(
                    threadId = "100",
                    server = "may",
                    board = "b",
                    htmlUrl = "https://may.2chan.net/b/res/100.htm",
                    createdAt = 10
                ),
                ArchiveSearchItem(
                    threadId = "100",
                    server = "may",
                    board = "b",
                    htmlUrl = "https://may.2chan.net/b/res/100.htm",
                    finalizedAt = 20
                ),
                ArchiveSearchItem(
                    threadId = "100",
                    server = "may",
                    board = "b",
                    htmlUrl = "https://may.2chan.net/b/res/100.htm",
                    uploadedAt = 30
                ),
                ArchiveSearchItem(
                    threadId = "200",
                    server = "may",
                    board = "b",
                    htmlUrl = "https://may.2chan.net/b/res/200.htm",
                    uploadedAt = 99
                )
            ),
            threadId = "100"
        )

        assertEquals(30L, match?.uploadedAt)
    }
}
