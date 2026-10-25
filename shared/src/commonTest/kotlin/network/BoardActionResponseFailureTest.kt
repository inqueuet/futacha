package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.ui.compat.toCompatUserMessage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlin.test.*

/**
 * A 2xx answer to a deletion, a del request or a vote whose body cannot be read
 * says nothing about whether the server applied it (L83). No real server is used.
 */
class BoardActionResponseFailureTest {
    private val board = "https://may.2chan.net/b/"

    @Test fun ownDeletionWithUnreadableBodyIsNotReportedAsDone(): Unit = runBlocking {
        for (oversized in listOf(false, true)) {
            var requests = 0
            val api = api(oversized) { requests++ }
            try {
                val error = assertFailsWith<NetworkException>("oversized=$oversized") {
                    api.deleteByUser(board, "555", "321", "pass1234", imageOnly = false)
                }
                assertTrue(error.message.orEmpty().contains("本人削除の結果を確認できませんでした"), error.message)
                assertTrue(error.message.orEmpty().contains("再読み込みして確認"), error.message)
                assertNotNull(error.cause)
                assertEquals(1, requests, "A deletion must never be resent")
                // としあき(仮) shows a fixed text for its failures; this one must not read "削除できませんでした".
                val shown = error.toCompatUserMessage("削除できませんでした")
                assertNotEquals("削除できませんでした", shown)
                assertTrue(shown.contains("結果を確認できませんでした"), shown)
            } finally { api.close() }
        }
    }

    @Test fun delRequestWithUnreadableBodyIsNotReportedAsSent(): Unit = runBlocking {
        for (oversized in listOf(false, true)) {
            var requests = 0
            val api = api(oversized) { requests++ }
            try {
                val error = assertFailsWith<NetworkException>("oversized=$oversized") {
                    api.requestDeletion(board, "777", "123", "110")
                }
                assertTrue(error.message.orEmpty().contains("del依頼の結果を確認できませんでした"), error.message)
                assertEquals(1, requests)
                val shown = error.toCompatUserMessage("削除依頼を送信できませんでした")
                assertTrue(shown.contains("結果を確認できませんでした"), shown)
                assertFalse(shown.contains("del"), "The toshiaki text must not name del (it may say 通報): $shown")
            } finally { api.close() }
        }
    }

    @Test fun saidaneWithUnreadableBodyIsNotReportedAsFailed(): Unit = runBlocking {
        for (oversized in listOf(false, true)) {
            var requests = 0
            val api = api(oversized) { requests++ }
            try {
                val error = assertFailsWith<NetworkException>("oversized=$oversized") {
                    api.voteSaidane(board, "777", "123")
                }
                val message = error.message.orEmpty()
                assertTrue(message.contains("そうだねの結果を確認できませんでした"), message)
                assertTrue(message.contains("加算されている可能性があります"), message)
                assertFalse(message.contains("失敗しました"), message)
                assertEquals(1, requests, "A vote must never be resent")
            } finally { api.close() }
        }
    }

    @Test fun readableAnswersKeepTheirMeaning(): Unit = runBlocking {
        // An empty but complete body is still a successful deletion and a failed vote.
        val api = HttpBoardApi(HttpClient(MockEngine { respond("") }))
        try {
            api.deleteByUser(board, "555", "321", "pass1234", imageOnly = false)
            api.requestDeletion(board, "777", "123", "110")
            val error = assertFailsWith<NetworkException> { api.voteSaidane(board, "777", "123") }
            assertTrue(error.message.orEmpty().contains("そうだね投票に失敗しました"), error.message)
            assertFalse(error.message.orEmpty().contains("確認できませんでした"), error.message)
        } finally { api.close() }
    }

    @Test fun cancellationWhileReadingTheBodyIsPropagated(): Unit = runBlocking {
        val cancelled = CancellationException("caller cancelled")
        val api = HttpBoardApi(HttpClient(MockEngine {
            respond(ByteChannel().apply { cancel(cancelled) }, HttpStatusCode.OK)
        }))
        try {
            assertFailsWith<CancellationException> {
                api.deleteByUser(board, "555", "321", "pass1234", imageOnly = false)
            }
            assertFailsWith<CancellationException> { api.requestDeletion(board, "777", "123", "110") }
            assertFailsWith<CancellationException> { api.voteSaidane(board, "777", "123") }
        } finally { api.close() }
    }

    private fun api(oversized: Boolean, onRequest: () -> Unit): HttpBoardApi =
        HttpBoardApi(HttpClient(MockEngine { _ ->
            onRequest()
            unreadable(oversized)
        }))

    // Both a dropped connection and the 64 KiB reader's own refusal happen after
    // the HTTP 200 headers have arrived.
    private fun MockRequestHandleScope.unreadable(oversized: Boolean): HttpResponseData =
        if (oversized) respond("", HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, "65537"))
        else respond(ByteChannel().apply { cancel(IOException("response connection lost")) }, HttpStatusCode.OK)
}
