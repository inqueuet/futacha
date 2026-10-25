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

    // L99: the stages (agent, form GET, official scripts, WebView) each allow 30 s on their own; one
    // shared 30 s cut a slow line that was still progressing. Virtual time, no real waiting.
    @Test fun aSlowFirstFailureStillLeavesTimeForTheRetry() = kotlinx.coroutines.test.runTest {
        var attempt = 0
        val result = withPostingPreparationReadRetry {
            attempt++
            if (attempt == 1) { delay(25_000); throw IOException("slow line dropped") }
            delay(10_000); "prepared"
        }
        assertEquals("prepared", result)
        assertEquals(2, attempt)
    }

    @Test fun theWholePreparationStopsAt75SecondsAndKeepsTheFirstFailure() = kotlinx.coroutines.test.runTest {
        var attempt = 0
        val error = assertFailsWith<NetworkException> {
            withPostingPreparationReadRetry<String> {
                attempt++
                if (attempt == 1) throw IOException("form read failed: x")
                awaitCancellation()
            }
        }
        assertEquals(75_000L, testScheduler.currentTime, "The whole preparation shares one 75 s budget")
        assertEquals(2, attempt)
        assertTrue(error.message.orEmpty().contains("投稿準備が時間内に完了しなかった"), error.message)
        assertTrue(error.message.orEmpty().contains("form read failed: x"), "The first failure is told: ${error.message}")
        assertTrue(error.cause is IOException, "The first failure is the cause")
    }

    private suspend fun reply(api: HttpBoardApi) = api.replyToThread("https://may.2chan.net/b/", "123", "", "", "", "test", "test-only", null, null, true, false)
    private val form = """<form id="fm"><input name="resto" value="123"><input name="email"><textarea name="com"></textarea><input name="pwd"><input name="mode" value="regist"><input name="hash" value="server-hash"><input name="ptua" value="123"><input name="chrenc" value="UTF-8"></form>"""
}
