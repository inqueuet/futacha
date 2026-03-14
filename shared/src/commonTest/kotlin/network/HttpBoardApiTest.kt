package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.http.parseQueryString
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HttpBoardApiTest {
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
            assertEquals(1, postingConfigFetchCount)
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
            assertEquals("4", form["cl"])
            assertEquals("0", form["cm"])
            assertEquals("0", form["ci"])
            assertEquals("on", form["vh"])
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
