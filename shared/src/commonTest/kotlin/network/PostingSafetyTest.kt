package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import kotlin.io.encoding.Base64

class PostingSafetyTest {
    private fun form(upload: Boolean = true, hash: Boolean = true): String = """
        <form id="fm"><input name="resto" value="123"><input name="mode" value="regist"><input name="chrenc" value="UTF-8">
        ${if(hash) "<input name='hash' value='server-hash'>" else ""}
        <input name="ptua" value="123"><input name="MAX_FILE_SIZE" value="3072000">
        <input name="baseform"><input name="js"><input name="pthc"><input name="pthb"><input name="pthd"><input name="scsz">
        <input name="email"><textarea name="com"></textarea><input name="pwd">
        ${if(upload) "<input name='upfile'><input name='textonly'>" else ""}
        </form>
    """.trimIndent()

    @Test fun unavailableOrIncompleteFormNeverSendsOnEitherBoard() = runBlocking {
        for (board in listOf("img", "may")) for (condition in listOf("404", "410", "429", "timeout", "hash", "form", "chrenc", "ptua", "pwd", "resto")) {
            var posts = 0
            val client = HttpClient(MockEngine { request ->
                if (request.method == HttpMethod.Post) { posts++; error("POST must never occur") }
                if (condition == "timeout") throw io.ktor.client.plugins.HttpRequestTimeoutException(request)
                val html = when(condition) {
                    "hash" -> form(hash=false)
                    "form" -> "<html>maintenance</html>"
                    "chrenc", "ptua", "pwd", "resto" -> form().replace("name=\"$condition\"", "name=\"missing\"")
                    else -> form()
                }
                respond(html, condition.toIntOrNull()?.let { HttpStatusCode.fromValue(it) } ?: HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8"))
            })
            try {
                assertFailsWith<Exception>("$board/$condition") {
                    HttpBoardApi(client).replyToThread("https://$board.2chan.net/b/", "123", "", "", "", "draft", "key", null, null, true)
                }
                assertEquals(0, posts, "$board/$condition")
            } finally { client.close() }
        }
    }

    @Test fun explicitErrorsWinOverEveryKindOfThreadLink() {
        for (body in listOf(
            """{"status":"error","message":"書き込み規制中です","back":"res/123.htm"}""",
            """<html><title>エラー</title>書き込み規制中です<a href="res/123.htm">戻る</a></html>""",
            """<html>エラー<meta http-equiv="refresh" content="0;URL=res/123.htm"></html>"""
        )) for (mode in HttpBoardApiPostResponseMode.entries) {
            assertFalse(isSuccessfulHttpBoardApiPostResponse(body))
            assertFailsWith<NetworkException> { resolveHttpBoardApiPostResponseOrThrow(mode, body, "test", "https://img.2chan.net/b/res/123.htm") }
        }
        assertFalse(isSuccessfulHttpBoardApiPostResponse("<a href='res/123.htm'>戻る</a>"))
        assertFalse(isSuccessfulHttpBoardApiPostResponse("""{"nested":{"status":"ok"},"status":"error"}"""))
        assertFalse(isSuccessfulHttpBoardApiPostResponse("""{"status":"ok","broken":}"""))
        assertEquals("123", resolveHttpBoardApiPostResponseOrThrow(HttpBoardApiPostResponseMode.CREATE_THREAD,
            """<div class="thre"><blockquote>規制という単語がある投稿</blockquote></div>""", "test", "https://may.2chan.net/b/res/123.htm"))
        assertEquals("124", resolveHttpBoardApiPostResponseOrThrow(HttpBoardApiPostResponseMode.REPLY, """{"status":"ok","thisno":124}""", "test"))
    }

    @Test fun multipartUsesOneDispositionAndRealFormFieldsAndDrawingPayload() = runBlocking {
        val png = ByteArray(24).apply {
            byteArrayOf(-119,80,78,71,13,10,26,10).copyInto(this)
            "IHDR".encodeToByteArray().copyInto(this,12)
            this[18]=1; this[19]=88; this[23]=135.toByte()
        }
        for (drawing in listOf(false,true)) {
            var wire = ""
            val client = HttpClient(MockEngine { req ->
                if(req.method == HttpMethod.Get) respond(form(upload=!drawing), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType,"text/html; charset=UTF-8"))
                else {
                    wire=(req.body as OutgoingContent).toByteArray().decodeToString()
                    respond("""{"status":"ok","thisno":124}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType,"application/json"))
                }
            })
            try {
                // Text-only must ignore a retained attachment, including its board-specific size limit.
                HttpBoardApi(client).replyToThread("https://img.2chan.net/b/","123","ignored","","ignored","draft","key",if(drawing) png else ByteArray(3_072_001),"drawing.png",!drawing,handwriting=drawing)
                val dispositions = wire.lineSequence().filter { it.startsWith("Content-Disposition:") }.toList()
                assertTrue(dispositions.isNotEmpty())
                assertTrue(dispositions.all { it.split("form-data").size == 2 && it.split("name=").size <= 3 })
                assertFalse(wire.contains("name=\"name\"")); assertFalse(wire.contains("name=\"sub\""))
                if(drawing) {
                    assertTrue(wire.contains(Base64.Default.encode(png)))
                    assertFalse(wire.contains("name=\"upfile\""))
                    assertFalse(wire.contains("name=\"textonly\""))
                } else assertTrue(wire.contains("filename=\"\""))
            } finally { client.close() }
        }
    }

    @Test fun unsupportedReplyAttachmentIsRejectedWithoutSending() = runBlocking {
        var posts=0
        val client=HttpClient(MockEngine { req ->
            if(req.method==HttpMethod.Post) posts++
            respond(form(upload=false),HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"text/html; charset=UTF-8"))
        })
        try {
            assertFailsWith<NetworkException> { HttpBoardApi(client).replyToThread("https://img.2chan.net/b/","123","","","","draft","key",byteArrayOf(1),"image.png",false) }
            assertEquals(0,posts)
        } finally { client.close() }
    }
}
