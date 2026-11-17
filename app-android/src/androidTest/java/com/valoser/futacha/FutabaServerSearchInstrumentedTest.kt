@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.shared.ui.board.FutabaServerSearchDialog
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import org.junit.*
import org.junit.Assert.*

class FutabaServerSearchInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var opened: String? = null
    private val client = HttpClient(MockEngine {
        assertEquals(HttpMethod.Get, it.method)
        respond("""<script>var ret={"res":{"456":{"resto":123,"com":"検索で見つかったレス"}}};</script>""",
            HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8"))
    })
    @After fun cleanup() { rule.runOnUiThread { rule.activity.setContent {} }; client.close() }
    @Test fun searchShowsMatchingResponsesAndOpensTheirThread() {
        rule.setContent { MaterialTheme {
            FutabaServerSearchDialog(client, "https://img.2chan.net/b/", {}, { opened = it.id })
        } }
        rule.onNodeWithTag("futaba-server-search-query").performTextInput("検索")
        rule.onNodeWithTag("futaba-server-search-submit").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("検索で見つかったレス").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("検索で見つかったレス").performClick()
        assertEquals("123", opened)
    }
}
