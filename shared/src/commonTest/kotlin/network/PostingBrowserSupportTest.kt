package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.util.TextEncoding
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class PostingBrowserSupportTest {
    @Test
    fun officialShiftJisScriptsReachBrowserWithoutCorruptingJapanese() = runBlocking {
        var calls = 0
        val client = HttpClient(MockEngine { request ->
            assertEquals("actual browser", request.headers[HttpHeaders.UserAgent])
            assertEquals("https://img.2chan.net/b/futaba.htm", request.headers[HttpHeaders.Referrer])
            calls++
            respond(TextEncoding.encodeToShiftJis("// 日本語の準備処理\n"), HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/javascript"))
        })
        val browser = object : PostingBrowser {
            override suspend fun userAgent() = "actual browser"
            override suspend fun evaluate(pageUrl: String, html: String, script: String): String {
                assertTrue(script.contains("日本語の準備処理"))
                assertTrue(html.contains("form-action 'none'"))
                return """{"js":"on","ptua":"123","scsz":"390x844x24","pthc":"123","pthb":"","pthd":""}"""
            }
        }
        try {
            val prepared = prepareOfficialPostingEnvironment(client, browser,
                "https://img.2chan.net/b/futaba.htm", HttpBoardApiPostingConfig(HttpBoardApiPostEncoding.SHIFT_JIS, "Shift_JIS"), "actual browser")
            assertEquals("on", prepared.environment["js"])
            assertEquals(2, calls)
        } finally { client.close() }
    }
}
