package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.ThreadPageContent
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.parser.ThreadHtmlParserCore
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.*

class ThreadArchiveFallbackTest {
    private val html = """<html><body><span class="thre" data-res="123">
        <span class="cno">No.123</span><a href="/b/src/123.jpg"><img src="/b/thumb/123s.jpg"></a>
        <blockquote>archived body</blockquote></span></body></html>"""
    private val board = "https://may.2chan.net/b/"
    private val source = "${board}res/123.htm"
    private fun repository(load: suspend () -> ThreadPageContent) = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getThreadContentByUrl(threadUrl: String) = load()
    }
    private suspend fun fetch(client: HttpClient, repo: BoardRepository) = performThreadArchiveFallback(
        client, repo, "123", null, board, null, Json
    )

    @Test fun selfHostedArchiveSuccessSkipsThirdPartyRequests() = runBlocking<Unit> {
        val client = HttpClient(MockEngine { error("Third party must not be contacted") })
        try {
            val outcome = assertIs<ArchiveFallbackOutcome.Success>(fetch(client, repository {
                ThreadPageContent(ThreadHtmlParserCore.parseThread(html, board))
            }))
            assertEquals("https://may.inqueuet.com/b/res/123.htm", outcome.threadUrl)
        } finally { client.close() }
    }

    @Test fun missingArchivesFallThroughToForestAndKeepItsImageUrls() = runBlocking<Unit> {
        val hosts = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            hosts += request.url.host
            if (request.url.host == "futabaforest.net") {
                respond(html, headers = headersOf("Content-Type", "text/html; charset=UTF-8"))
            } else respond("missing", HttpStatusCode.NotFound)
        })
        try {
            val outcome = assertIs<ArchiveFallbackOutcome.Success>(fetch(client, repository {
                throw NetworkException("missing", statusCode = 404)
            }))
            assertEquals(listOf("dev2.ftbucket.info", "futabaforest.net"), hosts)
            assertEquals("archived body", outcome.page.posts.first().messageHtml)
            assertEquals("http://futabaforest.net/b/src/123.jpg", outcome.page.posts.first().imageUrl)
            assertEquals(source, outcome.threadUrl)
        } finally { client.close() }
    }

    @Test fun timedOutArchiveStillTriesThirdPartiesAndAllFailuresPreserveFallback() = runBlocking<Unit> {
        val hosts = mutableListOf<String>()
        val client = HttpClient(MockEngine { request -> hosts += request.url.host; respond("missing", HttpStatusCode.NotFound) })
        try {
            assertEquals(ArchiveFallbackOutcome.NoMatch, fetch(client, repository {
                delay(10_000)
                error("unreachable")
            }))
            assertEquals(listOf("dev2.ftbucket.info", "futabaforest.net", "kako.futakuro.com"), hosts)
            assertEquals(ArchiveFallbackOutcome.NotFound, fetch(client, repository {
                throw NetworkException("gone", statusCode = 410)
            }))
        } finally { client.close() }
    }

    @Test fun cancellationDoesNotStartAnotherProvider() = runBlocking<Unit> {
        val client = HttpClient(MockEngine { error("Cancelled request must not fall through") })
        try {
            assertFailsWith<CancellationException> {
                fetch(client, repository { throw CancellationException("left thread") })
            }
        } finally { client.close() }
    }
}
