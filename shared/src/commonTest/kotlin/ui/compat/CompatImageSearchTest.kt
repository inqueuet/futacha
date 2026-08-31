package com.valoser.futacha.shared.ui.compat

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CompatImageSearchTest {
    @Test
    fun referenceTargetListAndDefaultsMatchOldUi1810b() {
        assertEquals(13, CompatImageSearchTarget.entries.size)
        assertEquals(
            listOf("lens.file", "ascii2d.url"),
            parseCompatImageSearchTargets(null).map { it.id }
        )
        val selected = setOf(
            CompatImageSearchTarget.GOOGLE_URL,
            CompatImageSearchTarget.IQDB_FILE,
            CompatImageSearchTarget.BING_URL
        )
        assertEquals(
            "google.url|iqdb.file|bing.url",
            serializeCompatImageSearchTargets(selected)
        )
        assertEquals(selected.toList(), parseCompatImageSearchTargets("google.url|iqdb.file|bing.url"))
        assertEquals(
            listOf(
                CompatImageSearchTarget.GOOGLE_FILE,
                CompatImageSearchTarget.LENS_FILE,
                CompatImageSearchTarget.IQDB_FILE,
                CompatImageSearchTarget.SAUCENAO_FILE,
                CompatImageSearchTarget.YANDEX_FILE
            ),
            compatConfiguredFileSearchTargets(CompatImageSearchTarget.entries)
        )
        assertTrue(
            requireNotNull(
                buildCompatImageSearchTargetUrl(
                    CompatImageSearchTarget.BING_URL,
                    "https://may.2chan.net/b/src/a b.jpg"
                )
            ).contains("a%20b.jpg")
        )
        assertEquals(
            CompatImageSearchTarget.entries.map { it.label },
            compatImageSearchActionTargets(
                serializeCompatImageSearchTargets(CompatImageSearchTarget.entries)
            ).map { it.label }
        )
        assertTrue(
            "Bing Visual Search (URL)" in compatImageSearchActionTargets("bing.url").map { it.label }
        )
    }

    @Test
    fun ascii2dRequiresFirstUseRegistrationButAcceptsLegacyConfiguredEndpoint() {
        assertFalse(isCompatAscii2dRegistered(emptyMap()))
        assertFalse(
            isCompatAscii2dRegistered(
                mapOf(COMPAT_ASCII2D_ENDPOINT_KEY to "https://evil.example/search")
            )
        )
        assertTrue(
            isCompatAscii2dRegistered(
                mapOf(COMPAT_ASCII2D_ENDPOINT_KEY to DEFAULT_COMPAT_ASCII2D_ENDPOINT)
            )
        )
        assertTrue(
            isCompatAscii2dRegistered(
                mapOf(COMPAT_ASCII2D_ENABLED_KEY to "ON")
            )
        )
    }

    @Test
    fun endpointValidationAllowsAscii2dOnly() {
        assertTrue(isValidCompatAscii2dEndpoint(DEFAULT_COMPAT_ASCII2D_ENDPOINT))
        assertTrue(isValidCompatAscii2dEndpoint("https://www.ascii2d.net/imagesearch/search"))
        assertFalse(isValidCompatAscii2dEndpoint("https://evil.example/ascii2d"))
        assertFalse(isValidCompatAscii2dEndpoint("javascript:alert(1)"))
    }

    @Test
    fun customSearchEnginesSerializeInStableOrderAndBuildPublicUrls() {
        val selected = setOf(
            CompatImageSearchEngine.YANDEX,
            CompatImageSearchEngine.TINEYE,
            CompatImageSearchEngine.IQDB
        )
        assertEquals(
            "TinEye Search|IQDB Search|Yandex画像検索",
            serializeCompatImageSearchEngines(selected)
        )
        assertEquals(
            listOf(
                CompatImageSearchEngine.TINEYE,
                CompatImageSearchEngine.IQDB,
                CompatImageSearchEngine.YANDEX
            ),
            parseCompatImageSearchEngines("Yandex画像検索|TinEye Search|IQDB Search")
        )
        assertEquals(
            "https://tineye.com/search/?url=https%3A%2F%2Fmay.2chan.net%2Fb%2Fsrc%2Fa.jpg",
            buildCompatImageSearchEngineUrl(
                CompatImageSearchEngine.TINEYE,
                "https://may.2chan.net/b/src/a.jpg"
            )
        )
    }

    @Test
    fun everyConfiguredProviderUsesHttpsAndEncodedPublicImageUrl() {
        val imageUrl = "https://may.2chan.net/b/src/a b.jpg?x=1&y=2"
        val expectedHosts = mapOf(
            CompatImageSearchEngine.TINEYE to "tineye.com",
            CompatImageSearchEngine.IQDB to "iqdb.org",
            CompatImageSearchEngine.SAUCENAO to "saucenao.com",
            CompatImageSearchEngine.YANDEX to "yandex.com"
        )
        expectedHosts.forEach { (engine, host) ->
            val result = requireNotNull(buildCompatImageSearchEngineUrl(engine, imageUrl))
            assertTrue(result.startsWith("https://$host/"))
            assertTrue(result.contains("a%20b.jpg"))
            assertTrue(result.contains("%26y%3D2"))
        }
    }

    @Test
    fun customProviderRejectsLocalOrNonHttpImageUrls() {
        CompatImageSearchEngine.entries.forEach { engine ->
            assertEquals(null, buildCompatImageSearchEngineUrl(engine, "file:///tmp/a.jpg"))
            assertEquals(null, buildCompatImageSearchEngineUrl(engine, "javascript:alert(1)"))
            assertEquals(null, buildCompatImageSearchEngineUrl(engine, "https://user:pass@evil.example/a.jpg"))
        }
    }

    @Test
    fun referenceGoogleImageSearchAndMediaRestrictionsMatchLegacyViewer() {
        assertEquals(
            "https://www.google.com/searchbyimage?hl=ja&safe=off&client=chrome&image_url=https%3A%2F%2Fmay.2chan.net%2Fb%2Fsrc%2Fa.jpg",
            buildCompatGoogleImageSearchUrl("https://may.2chan.net/b/src/a.jpg")
        )
        assertEquals(
            "https://lens.google.com/uploadbyurl?url=https%3A%2F%2Fmay.2chan.net%2Fb%2Fsrc%2Fa.jpg",
            buildCompatGoogleLensUrl("https://may.2chan.net/b/src/a.jpg")
        )
        val encodedImageUrl = "https://may.2chan.net/b/src/a.jpg?x=1&y=2"
        assertTrue(requireNotNull(buildCompatGoogleLensUrl(encodedImageUrl)).contains("%26y%3D2"))
        assertEquals(null, buildCompatGoogleImageSearchUrl("file:///tmp/a.jpg"))
        assertEquals(null, buildCompatGoogleLensUrl("file:///tmp/a.jpg"))
        assertTrue(isCompatImageSearchableMediaUrl("https://may.2chan.net/b/src/a.jpg"))
        assertTrue(isCompatImageSearchableMediaUrl("https://may.2chan.net/b/src/a.gif"))
        assertFalse(isCompatImageSearchableMediaUrl("https://may.2chan.net/b/src/a.webm"))
        assertFalse(isCompatImageSearchableMediaUrl("https://may.2chan.net/b/src/a.mp4"))
        assertFalse(
            isCompatImageSearchableMediaUrl(
                "https://may.2chan.net/b/src/a.gif",
                allowGif = false
            )
        )
    }

    @Test
    fun googleLensFileSearchUploadsEncodedImageAndAcceptsTrustedRedirect() = runBlocking {
        var getCount = 0
        var postPath = ""
        val client = HttpClient(MockEngine) {
            followRedirects = false
            engine {
                addHandler { request ->
                    if (request.url.encodedPath == "/b/src/example.jpg") {
                        getCount += 1
                        respond(
                            byteArrayOf(1, 2, 3),
                            HttpStatusCode.OK,
                            headersOf(
                                HttpHeaders.ContentType to listOf("image/jpeg"),
                                HttpHeaders.ContentLength to listOf("3")
                            )
                        )
                    } else {
                        postPath = request.url.toString()
                        respond(
                            "",
                            HttpStatusCode.Found,
                            headersOf(
                                HttpHeaders.Location to listOf("https://lens.google.com/search?p=abc")
                            )
                        )
                    }
                }
            }
        }

        assertEquals(
            "https://lens.google.com/search?p=abc",
            searchCompatGoogleLensFile(
                client,
                "https://may.2chan.net/b/src/example.jpg",
                nowEpochMillis = 1234L
            ).getOrThrow()
        )
        assertEquals(1, getCount)
        assertTrue(postPath.contains("/v3/upload"))
        assertTrue(postPath.contains("ep=cntpubb"))
    }

    @Test
    fun iqdbAndSauceNaoFileTargetsUploadMultipartAndKeepProviderHtml() = runBlocking {
        suspend fun search(target: CompatImageSearchTarget): CompatImageSearchResult.InlineHtml {
            var uploadUrl = ""
            val client = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        if (request.url.host == "may.2chan.net") {
                            respond(
                                byteArrayOf(1, 2, 3),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, "image/jpeg")
                            )
                        } else {
                            uploadUrl = request.url.toString()
                            respond(
                                "<html><a href=\"/result/1\">result</a></html>",
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, "text/html")
                            )
                        }
                    }
                }
            }
            val result = searchCompatImageFileTarget(
                client,
                target,
                "https://may.2chan.net/b/src/example.jpg"
            ).getOrThrow() as? CompatImageSearchResult.InlineHtml
            assertNotNull(result)
            assertTrue(result.html.contains("/result/1"))
            assertTrue(uploadUrl.startsWith(if (target == CompatImageSearchTarget.IQDB_FILE) {
                "https://iqdb.org/"
            } else {
                "https://saucenao.com/search.php"
            }))
            return result
        }

        assertEquals("https://iqdb.org/", search(CompatImageSearchTarget.IQDB_FILE).baseUrl)
        assertEquals("https://saucenao.com/", search(CompatImageSearchTarget.SAUCENAO_FILE).baseUrl)
    }

    @Test
    fun yandexFileTargetBuildsResultUrlFromBoundedJson() = runBlocking {
        var yandexRequestBodySeen = false
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    if (request.url.host == "may.2chan.net") {
                        respond(
                            byteArrayOf(4, 5, 6),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "image/png")
                        )
                    } else {
                        yandexRequestBodySeen = true
                        assertEquals("yandex.com", request.url.host)
                        assertEquals("https://yandex.com", request.headers[HttpHeaders.Origin])
                        respond(
                            """{"cbir_id":"cbir 123","url":"https://may.2chan.net/b/src/a b.png"}""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                }
            }
        }

        val result = searchCompatImageFileTarget(
            client,
            CompatImageSearchTarget.YANDEX_FILE,
            "https://may.2chan.net/b/src/example.png"
        ).getOrThrow() as? CompatImageSearchResult.RemoteUrl

        assertNotNull(result)
        assertTrue(yandexRequestBodySeen)
        assertEquals("Yandex画像検索", result.title)
        assertTrue(result.url.startsWith("https://yandex.com/images/search?"))
        assertTrue(result.url.contains("cbir_id=cbir+123") || result.url.contains("cbir_id=cbir%20123"))
        assertTrue(result.url.contains("a+b.png") || result.url.contains("a%20b.png"))
    }

    @Test
    fun searchPostsExistingMediaUriAndExtractsRelativeResult() = runBlocking {
        var requestBody: Parameters? = null
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requestBody = decodeForm(request)
                    respond(
                        "<a href=\"/search/color/abc123\">result</a>",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "text/html")
                    )
                }
            }
        }

        val result = searchCompatAscii2d(
            client,
            DEFAULT_COMPAT_ASCII2D_ENDPOINT,
            "https://may.2chan.net/b/src/example.jpg"
        ).getOrThrow()

        assertEquals("https://ascii2d.net/search/color/abc123", result)
        assertEquals("https://may.2chan.net/b/src/example.jpg", requestBody?.get("uri"))
    }

    @Test
    fun searchAcceptsProviderRedirectButRejectsUntrustedResult() = runBlocking {
        val trusted = HttpClient(MockEngine) {
            followRedirects = false
            engine {
                addHandler {
                    respond(
                        "",
                        HttpStatusCode.Found,
                        headersOf(HttpHeaders.Location, "https://ascii2d.net/search/bovw/xyz")
                    )
                }
            }
        }
        assertEquals(
            "https://ascii2d.net/search/bovw/xyz",
            searchCompatAscii2d(
                trusted,
                DEFAULT_COMPAT_ASCII2D_ENDPOINT,
                "https://img.2chan.net/b/src/example.png"
            ).getOrThrow()
        )

        val untrusted = HttpClient(MockEngine) {
            followRedirects = false
            engine {
                addHandler {
                    respond(
                        "",
                        HttpStatusCode.Found,
                        headersOf(HttpHeaders.Location, "https://evil.example/result")
                    )
                }
            }
        }
        assertTrue(
            searchCompatAscii2d(
                untrusted,
                DEFAULT_COMPAT_ASCII2D_ENDPOINT,
                "https://img.2chan.net/b/src/example.png"
            ).isFailure
        )
    }

    private fun decodeForm(request: HttpRequestData): Parameters {
        val content = request.body as? OutgoingContent.ByteArrayContent
            ?: error("Expected ByteArrayContent but was ${request.body::class}")
        return parseQueryString(content.bytes().decodeToString())
    }
}
