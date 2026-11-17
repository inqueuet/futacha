package com.valoser.futacha.shared.network

import com.sun.net.httpserver.HttpServer
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import io.ktor.client.plugins.SendCountExceedException
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class HttpClientPostRedirectTest {
    @Test fun postAnswered302IsFollowedOnceWithBodylessGetCarryingCookies() = runBlocking {
        var followedMethod: String? = null
        var followedContentType: String? = "not reached"
        var followedBodySize = -1
        var followedCookie: String? = null
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/futaba.php") {
            it.requestBody.readBytes()
            it.responseHeaders.add("Location", "res/123.htm")
            it.responseHeaders.add("Set-Cookie", "posted=yes; Path=/")
            it.sendResponseHeaders(302, -1)
            it.close()
        }
        server.createContext("/res/123.htm") {
            followedMethod = it.requestMethod
            followedContentType = it.requestHeaders.getFirst("Content-Type")
            followedBodySize = it.requestBody.readBytes().size
            followedCookie = it.requestHeaders.getFirst("Cookie")
            it.sendResponseHeaders(200, 2)
            it.responseBody.use { body -> body.write("ok".encodeToByteArray()) }
        }
        server.start()
        val client = createHttpClient(null, PersistentCookieStorage(InMemoryFileSystem(), "cookies.json"))
        try {
            val response = client.submitForm(
                url = "http://127.0.0.1:${server.address.port}/futaba.php?guid=on",
                formParameters = Parameters.build { append("com", "本文") }
            )
            assertEquals(200, response.status.value)
            assertEquals("ok", response.bodyAsText())
            assertTrue(response.call.request.url.toString().endsWith("/res/123.htm"))
            assertEquals("GET", followedMethod)
            assertNull(followedContentType)
            assertEquals(0, followedBodySize)
            assertEquals("posted=yes", followedCookie)
        } finally { client.close(); server.stop(0) }
    }

    // F4-1: the platform client's read retry used to resend the POST (with its
    // body) when the GET it was redirected to answered 5xx: a duplicate reply.
    @Test fun postWhoseRedirectedGetAnswers5xxIsSentExactlyOnce() = runBlocking {
        val posts = AtomicInteger()
        val gets = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/futaba.php") {
            if (it.requestMethod == "POST") posts.incrementAndGet()
            it.requestBody.readBytes()
            it.responseHeaders.add("Location", "res/123.htm")
            it.sendResponseHeaders(302, -1)
            it.close()
        }
        server.createContext("/res/123.htm") {
            gets.incrementAndGet()
            it.requestBody.readBytes()
            it.sendResponseHeaders(503, -1)
            it.close()
        }
        server.start()
        val client = createHttpClient(null, null)
        try {
            val response = client.submitForm(
                url = "http://127.0.0.1:${server.address.port}/futaba.php?guid=on",
                formParameters = Parameters.build { append("com", "本文") }
            )
            assertEquals(503, response.status.value)
            assertEquals(1, posts.get())
            // The redirected GET itself is still an automatically retried read.
            assertEquals(3, gets.get())
        } finally { client.close(); server.stop(0) }
    }

    // S4-1: the URL names may.2chan.net to a string check but Ktor sends it to the host before `\`.
    @Test fun platformClientRefusesABackslashAuthorityBypass() = runBlocking {
        val hits = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") {
            hits.incrementAndGet()
            it.sendResponseHeaders(200, -1)
            it.close()
        }
        server.start()
        val client = createHttpClient(null, null)
        try {
            assertFailsWith<AmbiguousRequestUrlException> {
                client.get("http://127.0.0.1:${server.address.port}\\@may.2chan.net/b/res/1.htm")
            }
            assertEquals(0, hits.get())
            assertEquals(200, client.get("http://127.0.0.1:${server.address.port}/b/res/1.htm").status.value)
            assertEquals(1, hits.get())
        } finally { client.close(); server.stop(0) }
    }

    @Test fun postAnswered307IsNotResent() = runBlocking {
        val posts = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/futaba.php") {
            posts.incrementAndGet()
            it.requestBody.readBytes()
            it.responseHeaders.add("Location", "/futaba.php")
            it.sendResponseHeaders(307, -1)
            it.close()
        }
        server.start()
        val client = createHttpClient(null, null)
        try {
            val response = client.submitForm(
                url = "http://127.0.0.1:${server.address.port}/futaba.php",
                formParameters = Parameters.build { append("com", "x") }
            )
            assertEquals(307, response.status.value)
            assertEquals(1, posts.get())
        } finally { client.close(); server.stop(0) }
    }

    @Test fun redirectLoopStopsAtTheSendLimit() = runBlocking {
        val hits = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/loop") {
            hits.incrementAndGet()
            it.responseHeaders.add("Location", "/loop")
            it.sendResponseHeaders(302, -1)
            it.close()
        }
        server.start()
        val client = createHttpClient(null, null)
        try {
            assertFailsWith<SendCountExceedException> {
                client.get("http://127.0.0.1:${server.address.port}/loop") {
                    attributes.put(HigherLayerRetryManaged, true)
                }
            }
            assertTrue(hits.get() in 1..20, "hits=${hits.get()}")
        } finally { client.close(); server.stop(0) }
    }

    @Test fun postRedirectedIntoALoopStopsAtTheSendLimitWithoutResendingThePost() = runBlocking {
        val posts = AtomicInteger()
        val hits = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/futaba.php") {
            posts.incrementAndGet()
            hits.incrementAndGet()
            it.requestBody.readBytes()
            it.responseHeaders.add("Location", "/loop")
            it.sendResponseHeaders(302, -1)
            it.close()
        }
        server.createContext("/loop") {
            hits.incrementAndGet()
            // Each hop after the POST must be a body-less GET, never the form again.
            if (it.requestMethod != "GET" || it.requestBody.readBytes().isNotEmpty()) posts.incrementAndGet()
            it.responseHeaders.add("Location", "/loop")
            it.sendResponseHeaders(302, -1)
            it.close()
        }
        server.start()
        val client = createHttpClient(null, null)
        try {
            assertFailsWith<SendCountExceedException> {
                client.submitForm(
                    url = "http://127.0.0.1:${server.address.port}/futaba.php",
                    formParameters = Parameters.build { append("com", "x") }
                )
            }
            assertEquals(1, posts.get())
            assertEquals(MAX_SENDS_PER_CALL, hits.get())
        } finally { client.close(); server.stop(0) }
    }
}
