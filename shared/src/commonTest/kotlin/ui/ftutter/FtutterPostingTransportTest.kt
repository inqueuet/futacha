package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.network.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class FtutterPostingTransportTest {
    @Test fun replyAndNewThreadReachOfficialPreparationAndSubmitOverHttps() = runBlocking {
        for (reply in listOf(false, true)) {
            var submissions = 0
            var preparations = 0
            val client = HttpClient(MockEngine { request ->
                assertEquals("https", request.url.protocol.name)
                if (request.method == HttpMethod.Post) {
                    submissions++
                    respond("""{"status":"ok","thisno":124}""", HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"))
                } else if (request.url.encodedPath.startsWith("/bin/")) {
                    respond("// official script", HttpStatusCode.OK)
                } else {
                    respond("""<form id="fm"><input name="mode" value="regist">
                        <input name="resto" value="${if (reply) "123" else ""}">
                        <input name="chrenc" value="文字"><input name="hash" value="fixture-hash"><input name="ptua" value="123"><input name="MAX_FILE_SIZE" value="3072000">
                        <input name="baseform"><input name="js"><input name="pthc"><input name="pthb">
                        <input name="pthd"><input name="scsz"><input name="email"><input name="pwd">
                        <textarea name="com"></textarea><input name="upfile"><input name="textonly"></form>""",
                        HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8"))
                }
            })
            client.attributes.put(PostingBrowserKey, object : PostingBrowser {
                override suspend fun userAgent() = "fixture browser"
                override suspend fun evaluate(pageUrl: String, html: String, script: String): String {
                    preparations++
                    assertEquals("https://img.2chan.net/b/${if (reply) "res/123.htm" else "futaba.htm"}", pageUrl)
                    return """{"js":"on","ptua":"123","scsz":"390x844x24","pthc":"123","pthb":"","pthd":""}"""
                }
            })
            try {
                val board = ftutterNetworkUrl("http://img.2chan.net/b/")
                val api = HttpBoardApi(client)
                if (reply) api.replyToThread(board, "123", "", "", "", "draft", "key", null, null, true)
                else api.createThread(board, "", "", "", "draft", "key", null, null, true)
                assertEquals(1, preparations)
                assertEquals(1, submissions)
            } finally { client.close() }
        }
    }
}
