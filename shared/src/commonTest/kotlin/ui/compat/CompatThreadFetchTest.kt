package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.parser.ThreadHtmlParserCore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.isSuccess
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CompatThreadFetchTest {
    private val source = "https://may.2chan.net/b/res/123.htm"
    private val cache = "https://cache.example"
    private val page = ThreadPage("123", "b", null, null, emptyList())

    @Test
    fun coldOrEmptySnapshotAlwaysFetchesWithoutManualRefresh() {
        assertTrue(shouldFetchCompatThread(manual = false, refreshOnActivation = false, cachedPostCount = 0))
        assertEquals(
            false,
            shouldFetchCompatThread(manual = false, refreshOnActivation = false, cachedPostCount = 10)
        )
        assertTrue(shouldFetchCompatThread(manual = true, refreshOnActivation = false, cachedPostCount = 10))
    }

    @Test
    fun cacheHitDoesNotTouchLiveOrArchive() = runBlocking {
        val requests = mutableListOf<String>()
        val result = loadCompatThreadWithFallback(source, true, cache) { url ->
            requests += url
            page
        }

        assertEquals(CompatThreadFetchSource.CACHE, result.getOrThrow().source)
        assertEquals(listOf("https://cache.example/b/res/123.htm"), requests)
    }

    @Test
    fun cacheMissFallsBackToLiveAndDoesNotUseArchiveAfterLiveSuccess() = runBlocking {
        val requests = mutableListOf<String>()
        val result = loadCompatThreadWithFallback(source, true, cache) { url ->
            requests += url
            if (url.startsWith(cache)) error("cache miss")
            page
        }

        assertEquals(CompatThreadFetchSource.PRIMARY, result.getOrThrow().source)
        assertEquals(
            listOf("https://cache.example/b/res/123.htm", source),
            requests
        )
    }

    @Test
    fun incompleteCacheHitIsSupplementedByArchive() = runBlocking {
        val cached = ThreadPage(
            threadId = "123",
            boardTitle = "板",
            expiresAtLabel = null,
            deletedNotice = null,
            posts = listOf(
                com.valoser.futacha.shared.model.Post(
                    id = "123",
                    order = 0,
                    author = "としあき",
                    subject = null,
                    timestamp = "26/08/08(土)00:00:00",
                    posterId = null,
                    messageHtml = "cached",
                    imageUrl = null,
                    thumbnailUrl = null
                )
            ),
            isTruncated = true,
            truncationReason = "cache limit"
        )
        val archive = cached.copy(
            isTruncated = false,
            truncationReason = null,
            posts = cached.posts + cached.posts.single().copy(
                id = "124",
                order = 1,
                messageHtml = "archive reply"
            )
        )
        val result = loadCompatThreadWithFallback(
            sourceUrl = source,
            cacheEnabled = true,
            cacheBaseUrl = cache,
            expectedReplyCount = 1,
            archiveLoader = { archive },
            loader = { url ->
                if (url.startsWith(cache)) cached else error("live must not be reached")
            }
        ).getOrThrow()

        assertEquals(CompatThreadFetchSource.MERGED, result.source)
        assertEquals(listOf("123", "124"), result.page.posts.map { it.id })
        assertEquals(false, result.page.isTruncated)
    }

    @Test
    fun liveMissUsesArchiveOnlyAfterCacheMiss() = runBlocking {
        val requests = mutableListOf<String>()
        val result = loadCompatThreadWithFallback(source, true, cache) { url ->
            requests += url
            if (url != "https://may.inqueuet.com/b/res/123.htm") error("not archive")
            page
        }

        assertEquals(CompatThreadFetchSource.ARCHIVE, result.getOrThrow().source)
        assertEquals(false, result.getOrThrow().primaryThreadGone)
        assertEquals(
            listOf(
                "https://cache.example/b/res/123.htm",
                source,
                "https://may.inqueuet.com/b/res/123.htm",
            ),
            requests,
        )
    }

    @Test
    fun goneLiveThreadCanStillBeViewedFromArchiveButIsMarkedGone() = runBlocking {
        val result = loadCompatThreadWithFallback(source, false, cache) { url ->
            if (url == source) {
                throw NetworkException("live thread is gone", statusCode = 410)
            }
            page
        }

        val fetched = result.getOrThrow()
        assertEquals(CompatThreadFetchSource.ARCHIVE, fetched.source)
        assertTrue(fetched.primaryThreadGone)
    }

    @Test
    fun transientLiveFailureDoesNotMarkArchiveCopyAsGone() = runBlocking {
        val result = loadCompatThreadWithFallback(source, false, cache) { url ->
            if (url == source) error("temporary timeout")
            page
        }

        assertEquals(false, result.getOrThrow().primaryThreadGone)
    }

    @Test
    fun goneFailureDetectionUsesStatusCodeAndDoesNotTreatTimeoutAsGone() {
        assertTrue(isCompatThreadGoneFailure(NetworkException("gone", statusCode = 404)))
        assertTrue(isCompatThreadGoneFailure(IllegalStateException("HTTP error 410")))
        assertEquals(false, isCompatThreadGoneFailure(IllegalStateException("timeout")))
    }

    @Test
    fun cancellationIsNotHiddenByFallback() {
        val result = runCatching {
            runBlocking {
                loadCompatThreadWithFallback(source, true, cache) {
                    throw kotlinx.coroutines.CancellationException("screen left")
                }
            }
        }
        assertTrue(result.isFailure)
        assertIs<kotlinx.coroutines.CancellationException>(result.exceptionOrNull())
    }

    @Test
    fun failureRetainsLiveFailureForDeadThreadClassification() = runBlocking {
        val result = loadCompatThreadWithFallback(source, false, cache) { url ->
            error(if (url == source) "HTTP 410" else "archive unavailable")
        }
        val failure = assertIs<CompatThreadFetchException>(result.exceptionOrNull())
        assertEquals("HTTP 410", failure.primaryFailure?.message)
        assertEquals("archive unavailable", failure.fallbackFailure?.message)
    }

    @Test
    fun savedHtmlAndMediaFixtureLoadsThroughConfiguredCacheEndpoint() = runBlocking {
        val html = """
            <html><body>
              <div class="thre" data-res="123">
                <span class="cnw">25/01/01(水)00:00:00 ID:CACHE</span>
                <span class="cno">No.123</span>
                <a href="/b/src/123.jpg"><img src="/b/thumb/123s.jpg"></a>
                <blockquote>保存済み本文</blockquote>
              </div>
            </body></html>
        """.trimIndent()
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/b/res/123.htm" -> respond(html, HttpStatusCode.OK, headersOf("Content-Type", "text/html"))
                        "/b/thumb/123s.jpg" -> respond(imageBytes, HttpStatusCode.OK, headersOf("Content-Type", "image/jpeg"))
                        else -> respond("not found", HttpStatusCode.NotFound, headersOf())
                    }
                }
            }
        }

        try {
            val result = loadCompatThreadWithFallback(source, true, cache) { url ->
                val response = client.get(url)
                check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
                ThreadHtmlParserCore.parseThread(response.bodyAsText(), url)
            }
            val fetched = result.getOrThrow()
            assertEquals(CompatThreadFetchSource.CACHE, fetched.source)
            assertEquals("保存済み本文", fetched.page.posts.single().messageHtml)
            assertEquals("https://cache.example/b/src/123.jpg", fetched.page.posts.single().imageUrl)
            assertEquals(imageBytes.toList(), client.get("https://cache.example/b/thumb/123s.jpg").bodyAsBytes().toList())
        } finally {
            client.close()
        }
    }
}
