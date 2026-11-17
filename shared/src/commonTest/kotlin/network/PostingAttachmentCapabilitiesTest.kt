package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class PostingAttachmentCapabilitiesTest {
    private fun form(extensions: String) = """<form id="fm"><input name="resto" value="123"><input name="mode" value="regist"><input name="chrenc" value="UTF-8"><input name="hash" value="server-hash"><input name="ptua" value="1"><input name="email"><textarea name="com"></textarea><input name="pwd"><input name="upfile"><input name="MAX_FILE_SIZE" value="1000"><span>添付可能：$extensions</span></form>"""

    @Test fun webpAllowedByTheUiCanBeSubmittedFromReplyAndThreadCreation(): Unit = runBlocking {
        for (create in listOf(false, true)) {
            var posts = 0
            var wire = ""
            val client = HttpClient(MockEngine { request ->
                if (request.method == HttpMethod.Get) respond(form("GIF JPG PNG WEBM MP4"))
                else {
                    posts++
                    wire = request.body.toByteArray().decodeToString()
                    respond("""{"status":"ok","thisno":124,"thread":124}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            })
            try {
                val api = HttpBoardApi(client)
                assertTrue("webp" in api.fetchPostingCapabilities("https://may.2chan.net/b/", if (create) null else "123").supportedExtensions)
                if (create) api.createThread("https://may.2chan.net/b/", "", "", "", "draft", "key", byteArrayOf(1), "image.WEBP", false)
                else api.replyToThread("https://may.2chan.net/b/", "123", "", "", "", "draft", "key", byteArrayOf(1), "image.WEBP", false)
                assertEquals(1, posts)
                assertTrue(wire.contains("image/webp"), wire)
                assertTrue(wire.contains("image.WEBP"), wire)
            } finally { client.close() }
        }
    }

    @Test fun unadvertisedVideoAndSizeLimitsRemainEnforced(): Unit = runBlocking {
        for ((board, extension, size) in listOf(
            Triple("https://img.2chan.net/b/", "webp", 1),
            Triple("https://may.2chan.net/b/", "mp4", 1),
            Triple("https://may.2chan.net/b/", "webp", 1001))) {
            var posts = 0
            val client = HttpClient(MockEngine { request ->
                if (request.method == HttpMethod.Post) posts++
                respond(form("GIF JPG PNG"))
            })
            try {
                val api = HttpBoardApi(client)
                val capabilities = api.fetchPostingCapabilities(board, "123")
                if (size == 1) assertFalse(extension in capabilities.supportedExtensions)
                else assertEquals(1000, capabilities.maxFileSizeBytes)
                assertFailsWith<NetworkException> {
                    api.replyToThread(board, "123", "", "", "", "draft", "key", ByteArray(size), "image.$extension", false)
                }
                assertEquals(0, posts)
            } finally { client.close() }
        }
    }
}
