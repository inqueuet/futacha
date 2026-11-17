package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * L100: posting from an `http://` 2chan board or a board outside 2chan.net always failed on Android and iOS, and the
 * only reason given was require()'s "Failed requirement.".
 */
class PostingBoardAddressTest {
    private suspend fun preparationFailure(pageUrl: String): NetworkException {
        var requests = 0
        val client = HttpClient(MockEngine { requests++; respond("// script", HttpStatusCode.OK) })
        val browser = object : PostingBrowser {
            override suspend fun userAgent() = "ua"
            override suspend fun evaluate(pageUrl: String, html: String, script: String) = error("not reached")
        }
        try {
            return assertFailsWith<NetworkException> {
                prepareOfficialPostingEnvironment(client, browser, pageUrl,
                    HttpBoardApiPostingConfig(HttpBoardApiPostEncoding.SHIFT_JIS, "Shift_JIS"), "ua")
            }.also { assertTrue(requests == 0, "nothing is fetched for such a board") }
        } finally { client.close() }
    }

    @Test
    fun aBoardOutside2chanIsToldWhyItCannotPost() = runBlocking {
        val message = preparationFailure("https://www.example.org/b/futaba.htm").message.orEmpty()
        assertTrue(message.contains("2chan.net"), message)
        assertFalse(message.contains("Failed requirement"), message)
    }

    @Test
    fun anHttpBoardOf2chanIsToldToUseHttps() = runBlocking {
        val message = preparationFailure("http://may.2chan.net/b/futaba.htm").message.orEmpty()
        assertTrue(message.contains("https"), message)
        assertFalse(message.contains("Failed requirement"), message)
    }
}
