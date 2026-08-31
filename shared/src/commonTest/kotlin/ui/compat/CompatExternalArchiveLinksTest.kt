package com.valoser.futacha.shared.ui.compat

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.http.parseQueryString
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompatExternalArchiveLinksTest {
    @Test
    fun existingTsumanneEntryIsOpenedWithoutPostingDuplicate() = runBlocking {
        val requests = mutableListOf<String>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requests += "${request.method.value} ${request.url.encodedPath}"
                    assertEquals("https://tsumanne.net/my/", request.headers[HttpHeaders.Referrer])
                    assertEquals(
                        "https://may.2chan.net/b/res/123.htm",
                        request.url.parameters["w"]
                    )
                    assertEquals("URL", request.url.parameters["sbmt"])
                    assertEquals("json", request.url.parameters["format"])
                    respond(
                        "{\"success\":true,\"logs\":[{\"path\":\"data/2026/08/09/123/\"}]}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }

        try {
            val result = registerCompatTsumanne(
                client,
                "https://may.2chan.net/b/res/123.htm",
                "タイトル"
            )
            assertEquals("https://tsumanne.net/data/2026/08/09/123/", result.getOrThrow())
            assertEquals(listOf("GET /my/indexes.php"), requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun unregisteredTsumanneEntryUsesReferenceFormAndReturnsBoard() = runBlocking {
        val requests = mutableListOf<String>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requests += "${request.method.value} ${request.url.encodedPath}"
                    when (request.url.encodedPath) {
                        "/si/indexes.php" -> respond(
                            "{\"success\":true,\"logs\":[]}",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        "/si/input.php" -> {
                            assertEquals(HttpMethod.Post, request.method)
                            assertEquals("https://tsumanne.net/si/", request.headers[HttpHeaders.Referrer])
                            val body = request.body as? OutgoingContent.ByteArrayContent
                                ?: error("expected form body, got ${request.body::class}")
                            val form: Parameters = parseQueryString(body.bytes().decodeToString())
                            assertEquals("https://img.2chan.net/b/res/456.htm", form["url"])
                            assertEquals("タイトル", form["category"])
                            assertEquals("追加", form["sbmt"])
                            respond("registered", HttpStatusCode.OK, headersOf())
                        }
                        else -> error("unexpected URL: ${request.url}")
                    }
                }
            }
        }

        try {
            val result = registerCompatTsumanne(
                client,
                "https://img.2chan.net/b/res/456.htm",
                "タイトル\n本文"
            )
            assertEquals("https://tsumanne.net/si/", result.getOrThrow())
            assertEquals(
                listOf("GET /si/indexes.php", "POST /si/input.php"),
                requests
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun tsumanneRejectsUnsupportedBoardsBeforeNetworkAccess() = runBlocking {
        var requested = false
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    requested = true
                    error("network must not be used")
                }
            }
        }

        try {
            val result = registerCompatTsumanne(
                client,
                "https://dec.2chan.net/1/res/789.htm",
                "タイトル"
            )
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("mayかimg"))
            assertTrue(!requested)
        } finally {
            client.close()
        }
    }
}
