package com.valoser.futacha.shared.network

import com.sun.net.httpserver.HttpServer
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.*

class HttpClientRedirectCookieTest {
    @Test fun redirectSavesIntermediateCookiesAndDoesNotForwardThemToAnotherHost() = runBlocking {
        var destinationCookie: String? = "not reached"
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/start") {
            it.responseHeaders.add("Location", "http://localhost:${server.address.port}/destination")
            it.responseHeaders.add("Set-Cookie", "redirect_cookie=kept; Path=/")
            it.sendResponseHeaders(302, -1)
            it.close()
        }
        server.createContext("/destination") {
            destinationCookie = it.requestHeaders.getFirst("Cookie")
            it.sendResponseHeaders(200, 2)
            it.responseBody.use { body -> body.write("ok".encodeToByteArray()) }
        }
        server.start()
        val storage = PersistentCookieStorage(InMemoryFileSystem(), "cookies.json")
        val client = createHttpClient(null, storage)
        try {
            assertEquals("ok", client.get("http://127.0.0.1:${server.address.port}/start") {
                header(HttpHeaders.Cookie, "explicit=private")
            }.bodyAsText())
            assertNull(destinationCookie)
            assertTrue(storage.listCookies().any { it.name == "redirect_cookie" && it.value == "kept" })
        } finally { client.close(); server.stop(0) }
    }
}
