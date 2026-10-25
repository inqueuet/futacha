package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlin.test.*

/** Headers alone cannot establish whether a post was accepted. No real server is used. */
class PostingResponseFailureTest {
    private val form = """<form id="fm"><input name="resto" value="123"><input name="mode" value="regist"><input name="chrenc" value="UTF-8"><input name="hash" value="server-hash"><input name="ptua" value="1"><input name="email"><textarea name="com"></textarea><input name="pwd"></form>"""

    @Test fun replyWithUnreadableBodyWarnsToCheckTheThreadBeforeResending(): Unit = runBlocking {
        unreadableBody(create = false)
    }

    @Test fun threadCreationWithUnreadableBodyWarnsToCheckTheThreadBeforeResending(): Unit = runBlocking {
        unreadableBody(create = true)
    }

    private suspend fun unreadableBody(create: Boolean) {
        for (oversized in listOf(false, true)) {
            var posts = 0
            val client = HttpClient(MockEngine { request ->
                if (request.method == HttpMethod.Get) {
                    respond(form, headers = headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8"))
                } else {
                    posts++
                    // Both a transport error and the bounded reader's own rejection
                    // happen after the HTTP 200 headers reach execute { response -> }.
                    val channel = ByteChannel().apply { cancel(IOException("response connection lost")) }
                    if (oversized) respond("", HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentLength, "20971521"))
                    else respond(channel, HttpStatusCode.OK)
                }
            })
            try {
                val error = assertFailsWith<NetworkException> { post(HttpBoardApi(client), create) }
                assertTrue(error.message.orEmpty().contains("送信結果を確認できませんでした"), "$create/$oversized: ${error.message}")
                assertTrue(error.message.orEmpty().contains("スレッドを確認してから再送"), error.message)
                assertNotNull(error.cause)
                assertEquals(1, posts, "A post must never be retried")
            } finally { client.close() }
        }
    }

    @Test fun explicitServerRefusalKeepsItsOwnMessage(): Unit = runBlocking {
        for (create in listOf(false, true)) {
            var posts = 0
            val client = HttpClient(MockEngine { request ->
                if (request.method == HttpMethod.Get) respond(form)
                else { posts++; respond("""{"status":"error","message":"書き込み規制中です"}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json; charset=UTF-8")) }
            })
            try {
                val error = assertFailsWith<NetworkException> { post(HttpBoardApi(client), create) }
                assertTrue(error.message.orEmpty().contains("書き込み規制中"), error.message)
                assertFalse(error.message.orEmpty().contains("送信結果を確認できませんでした"), error.message)
                assertEquals(1, posts)
            } finally { client.close() }
        }
    }

    @Test fun cancellationWhileReadingTheBodyIsPropagated(): Unit = runBlocking {
        val cancelled = CancellationException("caller cancelled")
        val client = HttpClient(MockEngine { request ->
            if (request.method == HttpMethod.Get) respond(form)
            else respond(ByteChannel().apply { cancel(cancelled) }, HttpStatusCode.OK)
        })
        try {
            assertFailsWith<CancellationException> { post(HttpBoardApi(client), false) }
        } finally { client.close() }
    }

    private suspend fun post(api: HttpBoardApi, create: Boolean): String? =
        if (create) api.createThread("https://may.2chan.net/b/", "", "", "", "draft", "key", null, null, true)
        else api.replyToThread("https://may.2chan.net/b/", "123", "", "", "", "draft", "key", null, null, true)
}
