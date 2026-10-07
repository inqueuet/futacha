package com.valoser.futacha.shared.ui.compat

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class CompatAscii2dResultTest {
    @Test
    fun resultUrlContainingTheLetterSIsNotCutShort() = runBlocking {
        for ((body, expected) in listOf(
            """<a href="https://ascii2d.net/search/color/abcsdefs">r</a>""" to "https://ascii2d.net/search/color/abcsdefs",
            """<a href="/search/bovw/xysz9">r</a>""" to "https://ascii2d.net/search/bovw/xysz9"
        )) {
            val client = HttpClient(MockEngine {
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html"))
            })
            try {
                val result = searchCompatAscii2d(client, DEFAULT_COMPAT_ASCII2D_ENDPOINT, "https://may.2chan.net/b/src/example.jpg").getOrThrow()
                assertEquals(expected, result)
            } finally { client.close() }
        }
    }
}
