package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.ThreadPageContent
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.parser.ThreadHtmlParserCore
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The archived copy of a thread that fell off its board, as the ふたばー風 thread screen asks for it. */
class FutaberArchivedThreadTest {
    private val board = "https://may.2chan.net/b/"
    private val html = """<html><body><span class="thre" data-res="123">
        <span class="cno">No.123</span><blockquote>archived body</blockquote></span></body></html>"""

    private fun repository(load: suspend () -> ThreadPageContent) = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getThreadContentByUrl(threadUrl: String) = load()
    }

    @Test fun anArchiveThatHasTheThreadGivesItsPage() = runBlocking<Unit> {
        val client = HttpClient(MockEngine { error("The board's own archive answered; no third party is asked") })
        try {
            val page = futaberArchivedThread(client, repository { ThreadPageContent(ThreadHtmlParserCore.parseThread(html, board)) }, "123", board, Json)
            assertNotNull(page)
            assertEquals("123", page.threadId)
            assertEquals("archived body", page.posts.first().messageHtml)
        } finally { client.close() }
    }

    @Test fun noArchiveHavingItGivesNullSoTheLoadErrorIsKept() = runBlocking<Unit> {
        val client = HttpClient(MockEngine { respond("missing", HttpStatusCode.NotFound) })
        try {
            assertNull(futaberArchivedThread(client, repository { throw NetworkException("gone", statusCode = 404) }, "123", board, Json))
        } finally { client.close() }
    }

    @Test fun withoutAConnectionThereIsNothingToAsk() = runBlocking<Unit> {
        assertNull(futaberArchivedThread(null, repository { error("must not be asked") }, "123", board, Json))
    }

    @Test fun leavingTheScreenCancelsTheLookup() = runBlocking<Unit> {
        val client = HttpClient(MockEngine { error("A cancelled lookup must not fall through to another archive") })
        try {
            assertFailsWith<CancellationException> {
                futaberArchivedThread(client, repository { throw CancellationException("left the thread") }, "123", board, Json)
            }
        } finally { client.close() }
    }
}
