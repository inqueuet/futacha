package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.io.IOException
import kotlin.test.*

class PostingPreparationRetryTest {
    @Test fun aTransientPreparationReadRecoversAndPostsExactlyOnce() = runBlocking {
        var gets = 0; var posts = 0
        val client = HttpClient(MockEngine { request ->
            if (request.method == HttpMethod.Post) {
                posts++
                respond("""{"status":"ok","thisno":124}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                gets++
                if (gets == 1) throw IOException("unexpected end of stream")
                respond(form, headers = headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8"))
            }
        })
        try {
            assertEquals("124", reply(HttpBoardApi(client)))
            assertEquals(2, gets); assertEquals(1, posts)
        } finally { client.close() }
    }

    @Test fun repeatedReadFailureNeverPostsAndStopsAfterTwoAttempts() = runBlocking {
        var gets = 0; var posts = 0
        val client = HttpClient(MockEngine { request ->
            if (request.method == HttpMethod.Post) posts++ else gets++
            throw IOException("unexpected end of stream")
        })
        try {
            assertFailsWith<NetworkException> { reply(HttpBoardApi(client)) }
            assertEquals(2, gets); assertEquals(0, posts)
        } finally { client.close() }
    }

    @Test fun missingFormsAndHttpErrorsAreNotRetried() = runBlocking {
        for (status in listOf(HttpStatusCode.OK, HttpStatusCode.NotFound, HttpStatusCode.Gone, HttpStatusCode.Forbidden)) {
            var requests = 0
            val client = HttpClient(MockEngine { requests++; respond("<html>invalid</html>", status) })
            try {
                assertFailsWith<NetworkException> { reply(HttpBoardApi(client)) }
                assertEquals(1, requests, status.toString())
            } finally { client.close() }
        }
    }

    @Test fun aPostConnectionFailureIsNeverReposted() = runBlocking {
        var gets = 0; var posts = 0
        val client = HttpClient(MockEngine { request ->
            if (request.method == HttpMethod.Post) { posts++; throw IOException("connection lost after send") }
            gets++; respond(form, headers = headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8"))
        })
        try {
            assertFails { reply(HttpBoardApi(client)) }
            assertEquals(1, gets); assertEquals(1, posts)
        } finally { client.close() }
    }

    @Test fun scriptReadRetryRebuildsTheEntirePreparationAndHonorsCancellation() = runBlocking {
        val stages = mutableListOf<String>(); var attempt = 0
        val result = withPostingPreparationReadRetry(retryDelayMillis = 0) {
            attempt++; stages += "form$attempt"; stages += "script$attempt"
            if (attempt == 1) throw IOException("closed script response")
            "prepared$attempt"
        }
        assertEquals("prepared2", result)
        assertEquals(listOf("form1", "script1", "form2", "script2"), stages)
        var cancelledAttempts = 0
        assertFailsWith<CancellationException> { withPostingPreparationReadRetry<String> {
            cancelledAttempts++; throw CancellationException("left")
        } }
        assertEquals(1, cancelledAttempts)
    }

    @Test fun theWholePreparationHasOneTimeBudget() = runBlocking {
        var attempts = 0
        assertFailsWith<NetworkException> { withPostingPreparationReadRetry<String>(timeoutMillis = 20) {
            attempts++; delay(100); "late"
        } }
        assertEquals(1, attempts)
    }

    private suspend fun reply(api: HttpBoardApi) = api.replyToThread("https://may.2chan.net/b/", "123", "", "", "", "test", "test-only", null, null, true, false)
    private val form = """<form id="fm"><input name="resto" value="123"><input name="email"><textarea name="com"></textarea><input name="pwd"><input name="mode" value="regist"><input name="hash" value="server-hash"><input name="ptua" value="123"><input name="chrenc" value="UTF-8"></form>"""
}
