package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.CatalogFetchSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.http.parseQueryString
import kotlinx.coroutines.runBlocking
import io.ktor.utils.io.ByteReadChannel
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpBoardApiTest {
    @Test
    fun fetchThread_recoversFromClosedConnectionWithoutManualReload() = runBlocking {
        var attempts = 0
        val api = createApi {
            attempts += 1
            if (attempts == 1) throw IOException("unexpected end of stream")
            htmlResponse("<html><body>loaded automatically</body></html>")
        }

        try {
            val html = api.fetchThreadByUrl("https://may.2chan.net/b/res/123.htm")

            assertTrue(html.contains("loaded automatically"))
            assertEquals(2, attempts)
        } finally {
            api.close()
        }
    }

    @Test
    fun fetchThread_acceptsImageHeavyResponseBeyondLegacyFiveMiBLimit() = runBlocking {
        val prefix = "<html><body>"
        val suffix = "</body></html>"
        val payload = prefix + "x".repeat(6 * 1024 * 1024) + suffix
        val api = createApi { htmlResponse(payload) }

        try {
            val html = api.fetchThread("https://may.2chan.net/b/", "123")

            assertEquals(payload.length, html.length)
            assertTrue(html.startsWith(prefix))
            assertTrue(html.endsWith(suffix))
        } finally {
            api.close()
        }
    }

    @Test
    fun probeThreadExists_usesHeadAndClassifiesOnlySuccessfulStatusAsExisting() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val api = createApi { request ->
            requests += request
            htmlResponse(
                body = "",
                status = if (request.url.encodedPath.endsWith("/100.htm")) {
                    HttpStatusCode.NoContent
                } else {
                    HttpStatusCode.NotFound
                }
            )
        }

        try {
            assertTrue(api.probeThreadExists("https://may.2chan.net/b/res/100.htm"))
            assertFalse(api.probeThreadExists("https://may.2chan.net/b/res/101.htm"))
            assertEquals(listOf(HttpMethod.Head, HttpMethod.Head), requests.map { it.method })
            assertTrue(requests.all { it.headers[HttpHeaders.Referrer] == "https://may.2chan.net/b/res/" })
        } finally {
            api.close()
        }
    }

    @Test
    fun probeThreadGone_classifiesOnly404And410AsGone() = runBlocking {
        val api = createApi { request ->
            val status = when (request.url.encodedPath.substringAfterLast('/')) {
                "404.htm" -> HttpStatusCode.NotFound
                "410.htm" -> HttpStatusCode.Gone
                "500.htm" -> HttpStatusCode.InternalServerError
                else -> HttpStatusCode.NoContent
            }
            htmlResponse("", status)
        }

        try {
            assertFalse(api.probeThreadGone("https://may.2chan.net/b/res/204.htm"))
            assertTrue(api.probeThreadGone("https://may.2chan.net/b/res/404.htm"))
            assertTrue(api.probeThreadGone("https://may.2chan.net/b/res/410.htm"))
            assertFalse(api.probeThreadGone("https://may.2chan.net/b/res/500.htm"))
        } finally {
            api.close()
        }
    }

    @Test
    fun boundedResponseReader_rejectsOversizedBodyWithoutContentLength() = runBlocking {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = ByteReadChannel(ByteArray(1_025) { 1 }),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/octet-stream")
                    )
                }
            }
        }
        try {
            val response = client.get("https://example.com/unbounded")
            assertFailsWith<NetworkException> {
                readBoundedHttpResponseBytes(response, maxBytes = 1_024)
            }
        } finally {
            client.close()
        }
        Unit
    }

    @Test
    fun createThread_extractsThreadIdFromHtmlResponse() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val api = createApi { request ->
            requests += request
            when {
                request.url.encodedPath.endsWith("/futaba.htm") -> htmlResponse(
                    """<input type="hidden" name="chrenc" value="UTF-8">"""
                )

                request.url.toString().contains("futaba.php?guid=on") -> htmlResponse(
                    """<html><body>書き込みました。<a href="res/123456.htm">jump</a></body></html>"""
                )

                else -> error("Unexpected request: ${request.url}")
            }
        }

        try {
            val threadId = api.createThread(
                board = "https://www.2chan.net/b/",
                name = "name",
                email = "",
                subject = "subject",
                comment = "comment",
                password = "1234",
                imageFile = null,
                imageFileName = null,
                textOnly = true
            )

            assertEquals("123456", threadId)
            assertEquals(2, requests.size)
            assertEquals(
                "https://www.2chan.net/b/futaba.htm",
                requests.last().headers[HttpHeaders.Referrer]
            )
        } finally {
            api.close()
        }
    }

    @Test
    fun replyToThread_reusesCachedPostingConfig_andReturnsThisNo() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        var postingConfigFetchCount = 0
        val api = createApi { request ->
            requests += request
            when {
                request.url.encodedPath.endsWith("/futaba.htm") -> {
                    postingConfigFetchCount += 1
                    htmlResponse("""<input type="hidden" name="chrenc" value="UTF-8">""")
                }

                request.url.toString().contains("futaba.php?guid=on") && request.headers[HttpHeaders.Referrer] == "https://www.2chan.net/b/futaba.htm" ->
                    htmlResponse("""<html><body><a href="res/777.htm">created</a></body></html>""")

                request.url.encodedPath.endsWith("/res/777.htm") -> {
                    postingConfigFetchCount += 1
                    htmlResponse("""<input type="hidden" name="chrenc" value="UTF-8">""")
                }

                request.url.toString().contains("futaba.php?guid=on") && request.headers[HttpHeaders.Referrer] == "https://www.2chan.net/b/res/777.htm" ->
                    jsonResponse("""{"status":"ok","thisno":888}""")

                else -> error("Unexpected request: ${request.url} headers=${request.headers}")
            }
        }

        try {
            val createdThreadId = api.createThread(
                board = "https://www.2chan.net/b/",
                name = "",
                email = "",
                subject = "",
                comment = "create",
                password = "1234",
                imageFile = null,
                imageFileName = null,
                textOnly = true
            )
            val thisNo = api.replyToThread(
                board = "https://www.2chan.net/b/",
                threadId = "777",
                name = "",
                email = "",
                subject = "",
                comment = "reply",
                password = "1234",
                imageFile = null,
                imageFileName = null,
                textOnly = true
            )

            assertEquals("777", createdThreadId)
            assertEquals("888", thisNo)
            assertEquals(2, postingConfigFetchCount)
        } finally {
            api.close()
        }
    }

    @Test
    fun replyToThread_refetchesDynamicPostingHash() = runBlocking {
        var postingConfigFetchCount = 0
        val api = createApi { request ->
            when {
                request.url.encodedPath.endsWith("/res/777.htm") -> {
                    postingConfigFetchCount += 1
                    htmlResponse(
                        """
                            <input type="hidden" name="chrenc" value="UTF-8">
                            <input type="hidden" name="hash" value="server-hash-$postingConfigFetchCount">
                            <input type="hidden" name="ptua" value="server-ptua-$postingConfigFetchCount">
                        """.trimIndent()
                    )
                }

                request.url.toString().contains("futaba.php?guid=on") &&
                    request.headers[HttpHeaders.Referrer] == "https://www.2chan.net/b/res/777.htm" ->
                    jsonResponse("""{"status":"ok","thisno":888}""")

                else -> error("Unexpected request: ${request.url} headers=${request.headers}")
            }
        }

        try {
            api.replyToThread(
                board = "https://www.2chan.net/b/",
                threadId = "777",
                name = "",
                email = "",
                subject = "",
                comment = "reply1",
                password = "1234",
                imageFile = null,
                imageFileName = null,
                textOnly = true
            )
            api.replyToThread(
                board = "https://www.2chan.net/b/",
                threadId = "777",
                name = "",
                email = "",
                subject = "",
                comment = "reply2",
                password = "1234",
                imageFile = null,
                imageFileName = null,
                textOnly = true
            )

            assertEquals(2, postingConfigFetchCount)
        } finally {
            api.close()
        }
    }

    @Test
    fun createThread_includesHttpStatusAndSummaryWhenServerReturnsFailureStatus() = runBlocking {
        val api = createApi { request ->
            when {
                request.url.encodedPath.endsWith("/futaba.htm") ->
                    htmlResponse("""<input type="hidden" name="chrenc" value="UTF-8">""")

                request.url.toString().contains("futaba.php?guid=on") ->
                    htmlResponse(
                        body = """
                            サーバーが混雑しています
                            しばらくしてからやり直してください
                        """.trimIndent(),
                        status = HttpStatusCode.InternalServerError
                    )

                else -> error("Unexpected request: ${request.url}")
            }
        }

        try {
            val error = assertFailsWith<NetworkException> {
                api.createThread(
                    board = "https://www.2chan.net/b/",
                    name = "",
                    email = "",
                    subject = "",
                    comment = "comment",
                    password = "1234",
                    imageFile = null,
                    imageFileName = null,
                    textOnly = true
                )
            }

            assertTrue(error.message!!.contains("HTTP 500"))
            assertTrue(error.message!!.contains("サーバーが混雑しています"))
        } finally {
            api.close()
        }
    }

    @Test
    fun replyToThread_throwsParsedServerErrorFromSuccessfulHttpResponse() = runBlocking {
        val api = createApi { request ->
            when {
                request.url.encodedPath.endsWith("/futaba.htm") ->
                    htmlResponse("""<input type="hidden" name="chrenc" value="UTF-8">""")

                request.url.toString().contains("futaba.php?guid=on") ->
                    htmlResponse("""<html><body><b>規制中です</b></body></html>""")

                else -> error("Unexpected request: ${request.url}")
            }
        }

        try {
            val error = assertFailsWith<NetworkException> {
                api.replyToThread(
                    board = "https://www.2chan.net/b/",
                    threadId = "777",
                    name = "",
                    email = "",
                    subject = "",
                    comment = "reply",
                    password = "1234",
                    imageFile = null,
                    imageFileName = null,
                    textOnly = true
                )
            }

            assertTrue(error.message!!.contains("返信に失敗しました"))
            assertTrue(error.message!!.contains("規制中"))
        } finally {
            api.close()
        }
    }

    @Test
    fun replyToThread_appendsCookieRecoveryGuidanceWhenCookieResetLooksRequired() = runBlocking {
        val api = createApi { request ->
            when {
                request.url.encodedPath.endsWith("/futaba.htm") ->
                    htmlResponse("""<input type="hidden" name="chrenc" value="UTF-8">""")

                request.url.toString().contains("futaba.php?guid=on") ->
                    htmlResponse("""posttime の期限切れです""")

                else -> error("Unexpected request: ${request.url}")
            }
        }

        try {
            val error = assertFailsWith<NetworkException> {
                api.replyToThread(
                    board = "https://www.2chan.net/b/",
                    threadId = "777",
                    name = "",
                    email = "",
                    subject = "",
                    comment = "reply",
                    password = "1234",
                    imageFile = null,
                    imageFileName = null,
                    textOnly = true
                )
            }

            assertTrue(error.message!!.contains("posttime の期限切れです"))
            assertTrue(error.message!!.contains("今回の投稿試行で投稿用 Cookie が保存された可能性"))
            assertTrue(error.message!!.contains("Cookie を保持したままもう一度投稿"))
        } finally {
            api.close()
        }
    }

    @Test
    fun requestDeletion_sendsExpectedFormParametersAndReferer() = runBlocking {
        lateinit var capturedRequest: HttpRequestData
        val api = createApi { request ->
            capturedRequest = request
            htmlResponse("ok")
        }

        try {
            api.requestDeletion(
                board = "https://may.2chan.net/b/",
                threadId = "777",
                postId = "123",
                reasonCode = "110"
            )

            val form = decodeFormBody(capturedRequest)
            assertEquals("https://may.2chan.net/del.php", capturedRequest.url.toString())
            assertEquals("https://may.2chan.net/b/res/777.htm", capturedRequest.headers[HttpHeaders.Referrer])
            assertEquals("post", form["mode"])
            assertEquals("b", form["b"])
            assertEquals("123", form["d"])
            assertEquals("110", form["reason"])
            assertEquals("ajax", form["responsemode"])
        } finally {
            api.close()
        }
    }

    @Test
    fun fetchCatalogSetup_sendsExpectedFormParametersAndReferer() = runBlocking {
        lateinit var capturedRequest: HttpRequestData
        val api = createApi { request ->
            capturedRequest = request
            htmlResponse("ok")
        }

        try {
            api.fetchCatalogSetup("https://may.2chan.net/b/")

            val form = decodeFormBody(capturedRequest)
            assertEquals("https://may.2chan.net/b/futaba.php?mode=catset", capturedRequest.url.toString())
            assertEquals("https://may.2chan.net/b/futaba.php?mode=catset", capturedRequest.headers[HttpHeaders.Referrer])
            assertEquals("catset", form["mode"])
            assertEquals("5", form["cx"])
            assertEquals("60", form["cy"])
            assertEquals("16", form["cl"])
            assertEquals("0", form["cm"])
            assertEquals("0", form["ci"])
            assertEquals("on", form["vh"])
        } finally {
            api.close()
        }
    }

    @Test
    fun fetchCatalogSetup_usesConfiguredFetchRows() = runBlocking {
        lateinit var capturedRequest: HttpRequestData
        val api = createApi { request ->
            capturedRequest = request
            htmlResponse("ok")
        }

        try {
            api.fetchCatalogSetup(
                board = "https://may.2chan.net/b/",
                settings = CatalogFetchSettings(rows = 100)
            )

            val form = decodeFormBody(capturedRequest)
            assertEquals("5", form["cx"])
            assertEquals("100", form["cy"])
            assertEquals("16", form["cl"])
        } finally {
            api.close()
        }
    }

    @Test
    fun fetchCatalogSetup_acceptsLegacyCompatibilityLayoutFor3000Threads() = runBlocking {
        lateinit var capturedRequest: HttpRequestData
        val api = createApi { request ->
            capturedRequest = request
            htmlResponse("ok")
        }

        try {
            api.fetchCatalogSetup(
                board = "https://may.2chan.net/b/",
                settings = CatalogFetchSettings(columns = 120, rows = 25, titleLines = 256)
            )

            val form = decodeFormBody(capturedRequest)
            assertEquals("120", form["cx"])
            assertEquals("25", form["cy"])
            assertEquals("256", form["cl"])
            assertEquals("on", form["vh"])
        } finally {
            api.close()
        }
    }

    @Test
    fun fetchCatalog_retriesRetryableServerFailureOnce() = runBlocking {
        var requestCount = 0
        val api = createApi {
            requestCount += 1
            if (requestCount == 1) {
                htmlResponse("server error", status = HttpStatusCode.InternalServerError)
            } else {
                htmlResponse("<html>catalog</html>")
            }
        }

        try {
            val html = api.fetchCatalog("https://may.2chan.net/b/", CatalogMode.Catalog)

            assertEquals("<html>catalog</html>", html)
            assertEquals(2, requestCount)
        } finally {
            api.close()
        }
    }

    @Test
    fun fetchThreadHead_doesNotRetryHelperServerFailure() = runBlocking {
        var requestCount = 0
        val api = createApi {
            requestCount += 1
            htmlResponse("server error", status = HttpStatusCode.InternalServerError)
        }

        try {
            assertFailsWith<NetworkException> {
                api.fetchThreadHead("https://may.2chan.net/b/", "123", maxLines = 16)
            }

            assertEquals(1, requestCount)
        } finally {
            api.close()
        }
    }

    @Test
    fun deleteByUser_sendsCompatibleDeletionFormFields() = runBlocking {
        lateinit var capturedRequest: HttpRequestData
        val api = createApi { request ->
            capturedRequest = request
            htmlResponse("ok")
        }

        try {
            api.deleteByUser(
                board = "https://may.2chan.net/img/",
                threadId = "555",
                postId = "321",
                password = "pass1234",
                imageOnly = true
            )

            val form = decodeFormBody(capturedRequest)
            assertEquals("https://may.2chan.net/img/futaba.php?guid=on", capturedRequest.url.toString())
            assertEquals("https://may.2chan.net/img/res/555.htm", capturedRequest.headers[HttpHeaders.Referrer])
            assertEquals("on", form["guid"])
            assertEquals("321", form["delete"])
            assertEquals("delete", form["321"])
            assertEquals("ajax", form["responsemode"])
            assertEquals("pass1234", form["pwd"])
            assertEquals("on", form["onlyimgdel"])
            assertEquals("usrdel", form["mode"])
        } finally {
            api.close()
        }
    }

    @Test
    fun deleteByUser_includesSummaryWhenServerReturnsFailureStatus() = runBlocking {
        val api = createApi { request ->
            htmlResponse(
                body = "削除できませんでした\n時間を置いてください",
                status = HttpStatusCode.BadRequest
            )
        }

        try {
            val error = assertFailsWith<NetworkException> {
                api.deleteByUser(
                    board = "https://may.2chan.net/img/",
                    threadId = "555",
                    postId = "321",
                    password = "pass1234",
                    imageOnly = false
                )
            }

            assertTrue(error.message!!.contains("HTTP 400"))
            assertTrue(error.message!!.contains("削除できませんでした"))
        } finally {
            api.close()
        }
    }

    @Test
    fun voteSaidane_acceptsNumericResponseBody() = runBlocking {
        lateinit var capturedRequest: HttpRequestData
        val api = createApi { request ->
            capturedRequest = request
            htmlResponse("2")
        }

        try {
            api.voteSaidane(
                board = "https://may.2chan.net/b/",
                threadId = "777",
                postId = "123"
            )

            assertEquals("https://may.2chan.net/sd.php?b.123", capturedRequest.url.toString())
            assertEquals("https://may.2chan.net/b/res/777.htm", capturedRequest.headers[HttpHeaders.Referrer])
        } finally {
            api.close()
        }
    }

    @Test
    fun voteSaidane_rejectsNonNumericResponseBody() = runBlocking {
        val api = createApi { _ ->
            htmlResponse("error")
        }

        try {
            val error = assertFailsWith<NetworkException> {
                api.voteSaidane(
                    board = "https://may.2chan.net/b/",
                    threadId = "777",
                    postId = "123"
                )
            }

            assertTrue(error.message!!.contains("そうだね投票に失敗しました"))
            assertTrue(error.message!!.contains("error"))
        } finally {
            api.close()
        }
    }

    private fun createApi(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpBoardApi {
        val engine = MockEngine(MockEngineConfig().apply {
            addHandler(handler)
        })
        return HttpBoardApi(HttpClient(engine))
    }

    private fun decodeFormBody(request: HttpRequestData): Parameters {
        val content = request.body as? OutgoingContent.ByteArrayContent
            ?: error("Expected ByteArrayContent but was ${request.body::class}")
        return parseQueryString(content.bytes().decodeToString())
    }

    private fun MockRequestHandleScope.htmlResponse(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK
    ): HttpResponseData = respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8")
    )

    private fun MockRequestHandleScope.jsonResponse(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK
    ): HttpResponseData = respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/json; charset=UTF-8")
    )
}
