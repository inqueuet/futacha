package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.ui.compat.CompatImageSearchResult
import com.valoser.futacha.shared.ui.compat.CompatImageSearchTarget
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.test.*

class ThreadImageSearchTest {
    private val imageUrl = "https://may.2chan.net/b/src/a b.png"

    @Test fun searchesOriginalUrlAndExcludesLocalFilesVideosAndAscii2dGif() = runBlocking<Unit> {
        val result = searchThreadImage(null, imageUrl, CompatImageSearchTarget.LENS_URL).getOrThrow()
        assertEquals(
            "https://lens.google.com/uploadbyurl?url=https%3A%2F%2Fmay.2chan.net%2Fb%2Fsrc%2Fa%20b.png",
            assertIs<CompatImageSearchResult.RemoteUrl>(result).url
        )
        assertEquals(13, threadImageSearchTargets(imageUrl).size)
        listOf("file:///private/photo.png", "content://photos/1.png", "https://may.2chan.net/b/src/1.webm").forEach {
            assertTrue(threadImageSearchTargets(it).isEmpty())
            assertTrue(searchThreadImage(null, it, CompatImageSearchTarget.LENS_URL).isFailure)
        }
        assertFalse(CompatImageSearchTarget.ASCII2D_URL in threadImageSearchTargets("https://may.2chan.net/b/src/1.gif"))
    }

    @Test fun fileAndAscii2dSearchUseExistingProvidersAndReturnInlineOrRemoteResults() = runBlocking<Unit> {
        val hosts = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            hosts += request.url.host
            when (request.url.host) {
                "may.2chan.net" -> respond(byteArrayOf(1, 2, 3), headers = headersOf("Content-Type", "image/png"))
                "iqdb.org" -> respond("<html><body>Image match</body></html>")
                "ascii2d.net" -> respond("", HttpStatusCode.Found, headersOf("Location", "/search/abc123"))
                else -> error("Unexpected request")
            }
        }) { followRedirects = false }
        try {
            assertIs<CompatImageSearchResult.InlineHtml>(
                searchThreadImage(client, imageUrl, CompatImageSearchTarget.IQDB_FILE).getOrThrow()
            )
            assertEquals("https://ascii2d.net/search/abc123", assertIs<CompatImageSearchResult.RemoteUrl>(
                searchThreadImage(client, imageUrl, CompatImageSearchTarget.ASCII2D_URL).getOrThrow()
            ).url)
            assertEquals(listOf("may.2chan.net", "iqdb.org", "ascii2d.net"), hosts)
        } finally { client.close() }
    }

    @Test fun failedImageAcquisitionDoesNotUploadAndCancellationPropagates() = runBlocking<Unit> {
        var requests = 0
        val failed = HttpClient(MockEngine { requests++; respond("missing", HttpStatusCode.NotFound) })
        val cancelled = HttpClient(MockEngine { throw CancellationException("closed") })
        try {
            assertTrue(searchThreadImage(failed, imageUrl, CompatImageSearchTarget.LENS_FILE).isFailure)
            assertEquals(1, requests)
            assertFailsWith<CancellationException> {
                searchThreadImage(cancelled, imageUrl, CompatImageSearchTarget.LENS_FILE)
            }
        } finally { failed.close(); cancelled.close() }
    }

    @Test fun providerTimeoutReturnsAnErrorInsteadOfLeavingSearchPending() = runBlocking<Unit> {
        val client = HttpClient(MockEngine { withTimeout(1) { delay(100); error("unreachable") } })
        try {
            val error = searchThreadImage(client, imageUrl, CompatImageSearchTarget.LENS_FILE).exceptionOrNull()
            assertEquals("画像検索がタイムアウトしました", error?.message)
        } finally { client.close() }
    }
}
