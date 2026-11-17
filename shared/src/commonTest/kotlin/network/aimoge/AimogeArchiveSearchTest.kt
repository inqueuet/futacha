package com.valoser.futacha.shared.network.aimoge

import com.valoser.futacha.shared.compat.AimogeAvailability
import com.valoser.futacha.shared.network.ArchiveSearchItem
import com.valoser.futacha.shared.network.ArchiveSearchScope
import com.valoser.futacha.shared.network.aimogeArchiveSearcher
import com.valoser.futacha.shared.network.extractArchiveSearchScope
import com.valoser.futacha.shared.network.extractPastSearchScope
import com.valoser.futacha.shared.network.searchInqueuetArchiveThreads
import com.valoser.futacha.shared.ui.compat.buildCompatArchiveSourceThreadUrl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** あいもげの過去ログ検索：`/archive/{slug}?q=` へ向け、ふたばの過去ログ検索には渡さない。 */
class AimogeArchiveSearchTest {
    private val original = aimogeArchiveSearcher
    private val originalSites = AimogeAvailability.sitesEnabled

    @BeforeTest fun on() { AimogeAvailability.sitesEnabled = true }
    @AfterTest fun restore() { aimogeArchiveSearcher = original; AimogeAvailability.sitesEnabled = originalSites }

    // The shape of a real answer of 2026-11-17 (`/archive/aimg?q=ふたちゃ`), cut down.
    private val body = """
        {"threads":[
          {"threadId":"01A1D21B-947B-77CA-8A6F-A1F8B4D88975","opExcerpt":"\nふたちゃスレ\nまとめ","thumbnailUrl":"https://storage.nijiurachan.net/aimg/thumbnails/a.webp",
           "opPreviewUrl":null,"replyCount":125,"tags":[],"expiresAt":"2026-11-15T12:11:31.558Z","isPermanent":false,"archivedAt":"2026-11-15T12:11:55.004Z"},
          {"threadId":"not-a-uuid","opExcerpt":"x","replyCount":1},
          {"threadId":"01a1d21b-947b-77ca-8a6f-a1f8b4d88975","opExcerpt":"dup","replyCount":1},
          {"threadId":"01a1cff8-6bff-7000-8000-000000000001","opExcerpt":"あいもげからおてすと","thumbnailUrl":"http://insecure/x.webp","replyCount":-3}
        ],"pagination":{"page":1,"limit":20,"total":3,"totalPages":1},"storage":{}}
    """.trimIndent()

    @Test fun theArchiveAnswerBecomesPastLogRowsThatOpenTheAimogeThread() {
        val items = parseAimogeArchiveSearch(body, "aimg")
        assertEquals(2, items.size)
        val first = items[0]
        assertEquals("01a1d21b-947b-77ca-8a6f-a1f8b4d88975", first.threadId)
        assertEquals("ふたちゃスレ", first.title)
        assertEquals("https://nijiurachan.net/b/aimg/thread/01a1d21b-947b-77ca-8a6f-a1f8b4d88975", first.htmlUrl)
        assertEquals(125, first.replyCount)
        assertEquals("aimoge", first.server)
        assertNull(items[1].thumbUrl)
        assertEquals(0, items[1].replyCount)
    }

    @Test fun theRequestCarriesTheBoardKeyAndTheEncodedWords() = runTest {
        var url = ""; var key: String? = null
        val client = HttpClient(MockEngine { request ->
            url = request.url.toString(); key = request.headers["X-Board-Key"]
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val items = searchAimogeArchiveThreads(AimogeApi(client), "aimg", " ふたちゃ ")
        assertEquals(2, items.size)
        assertTrue(url.startsWith("https://api.nijiurachan.net/archive/aimg?q=%E3%81%B5%E3%81%9F%E3%81%A1%E3%82%83&page=1&limit=20"), url)
        assertEquals("aimg", key)
    }

    @Test fun anAimogeBoardsPastSearchNeverReachesTheFutabaServices() = runTest {
        val scope = extractPastSearchScope("https://nijiurachan.net/b/aimg/")!!
        assertEquals(ArchiveSearchScope("aimoge", "aimg", "aimg"), scope)
        // The thread fallbacks still see no scope for あいもげ.
        assertNull(extractArchiveSearchScope("https://nijiurachan.net/b/aimg/"))
        var asked: Pair<String, String>? = null
        aimogeArchiveSearcher = { slug, query, _ -> asked = slug to query; listOf(ArchiveSearchItem("t", "aimoge", slug, htmlUrl = "u")) }
        var futabaRequests = 0
        val futaba = HttpClient(MockEngine { futabaRequests++; respond("[]", HttpStatusCode.OK) })
        val items = searchInqueuetArchiveThreads(futaba, Json, "ふたちゃ", scope, includeAllSources = true)
        assertEquals(1, items.size)
        assertEquals("aimg" to "ふたちゃ", asked)
        assertEquals(0, futabaRequests)
    }

    @Test fun aFutabaBoardKeepsItsScopeAndAimogeOffGivesNone() {
        assertEquals(ArchiveSearchScope("may", "b"), extractPastSearchScope("https://may.2chan.net/b/"))
        AimogeAvailability.sitesEnabled = false
        assertNull(extractPastSearchScope("https://nijiurachan.net/b/aimg/"))
    }

    @Test fun toshiakiOpensTheAimogeAddressOfAnArchiveRow() {
        val url = "https://nijiurachan.net/b/aimg/thread/01a1d21b-947b-77ca-8a6f-a1f8b4d88975"
        assertEquals(url, buildCompatArchiveSourceThreadUrl(ArchiveSearchItem("01a1d21b-947b-77ca-8a6f-a1f8b4d88975", "aimoge", "aimg", htmlUrl = url)))
        assertNull(buildCompatArchiveSourceThreadUrl(ArchiveSearchItem("x", "aimoge", "aimg", htmlUrl = "https://evil.example/thread/x")))
    }

    private val postsBody = """
        {"query":"ふたちゃ","posts":[
          {"id":"p1","threadId":"01a1e1d7-acb0-7722-9d00-527ff04b318f","seq":59,"status":"public","body":"\niOS版 ふたちゃモード\n本文","attachment":null},
          {"id":"p2","threadId":"01a1e1d7-acb0-7722-9d00-527ff04b318f","seq":0,"status":"public","body":"ふたちゃスレ\nまとめ","attachment":{"thumbnailUrl":"https://storage.nijiurachan.net/aimg/t.webp"}},
          {"id":"p3","threadId":"01a1e000-0000-7000-8000-000000000002","seq":4,"status":"public","body":"別スレのふたちゃ"},
          {"id":"p4","threadId":"01a1e000-0000-7000-8000-000000000003","seq":1,"status":"deleted","body":"消えた"}
        ]}
    """.trimIndent()

    @Test fun postHitsBecomeOneRowPerLiveThreadTitledByTheOpOrTheHit() {
        val rows = parseAimogePostSearch(postsBody, "aimg")
        assertEquals(listOf("01a1e1d7-acb0-7722-9d00-527ff04b318f", "01a1e000-0000-7000-8000-000000000002"), rows.map { it.threadId })
        assertEquals("ふたちゃスレ", rows[0].title)
        assertEquals("https://storage.nijiurachan.net/aimg/t.webp", rows[0].thumbUrl)
        assertEquals("No.4：別スレのふたちゃ", rows[1].title)
        assertEquals("live", rows[1].status)
    }

    @Test fun theBoardSearchListsLiveThreadsBeforeArchivedOnesAndSurvivesOneSideFailing() = runTest {
        var failArchive = false
        val client = HttpClient(MockEngine { request ->
            when {
                request.url.encodedPath == "/search" -> respond(postsBody, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                failArchive -> respond("{}", HttpStatusCode.ServiceUnavailable)
                else -> respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        })
        val rows = searchAimogeThreads(AimogeApi(client), "aimg", "ふたちゃ")
        assertEquals(listOf("live", "live", "archived", "archived"), rows.map { it.status })
        failArchive = true
        assertEquals(2, searchAimogeThreads(AimogeApi(client), "aimg", "ふたちゃ").size)
    }
}
