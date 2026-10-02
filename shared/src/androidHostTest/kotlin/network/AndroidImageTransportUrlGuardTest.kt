package com.valoser.futacha.shared.network

import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** S4-1: the Android image client refuses ambiguous URLs like the general clients. */
class AndroidImageTransportUrlGuardTest {
    @Test
    fun imageClientRefusesUserInfoAndEscapedHostsBeforeSending() = runBlocking {
        val transport = createAndroidImageTransport(AcceptAllCookiesStorage())
        try {
            listOf(
                "https://evil.com@may.2chan.net/b/src/1.jpg",
                "https://may.2chan.net@evil.com/b/src/1.jpg",
                "https://evil.com%40may.2chan.net/b/src/1.jpg"
            ).forEach { url ->
                assertFailsWith<AmbiguousRequestUrlException>(url) { transport.client.get(url) }
            }
        } finally {
            transport.close()
        }
    }
}
