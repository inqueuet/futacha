package com.valoser.futacha.shared.desktop

import com.sun.net.httpserver.HttpServer
import com.valoser.futacha.shared.network.*
import com.valoser.futacha.shared.util.createFileSystem
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class DesktopNetworkTest {
    @Test fun actualHttpEngineRetriesReadsNeverPostsAndPersistsCookies() = runBlocking {
        val root = Files.createTempDirectory("futacha-network-test").toFile()
        val env = DesktopEnvironment(root, root.resolve("cache"))
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val reads = AtomicInteger(); val posts = AtomicInteger()
        server.createContext("/read") { exchange ->
            val attempt = reads.incrementAndGet()
            val bytes = "日本語の掲示板".toByteArray(charset("Shift_JIS"))
            exchange.responseHeaders.add("Content-Type", "text/plain; charset=Shift_JIS")
            exchange.responseHeaders.add("Set-Cookie", "session=desktop-check; Path=/; Max-Age=3600")
            exchange.sendResponseHeaders(if (attempt < 3) 503 else 200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }; exchange.close()
        }
        server.createContext("/post") { exchange ->
            posts.incrementAndGet(); exchange.requestBody.readBytes()
            exchange.sendResponseHeaders(503, -1); exchange.close()
        }
        server.start()
        val fs = createFileSystem(env)
        val cookies = PersistentCookieStorage(fs)
        val client = createHttpClient(cookieStorage = cookies)
        val base = "http://127.0.0.1:${server.address.port}"
        try {
            assertEquals("日本語の掲示板", client.get("$base/read").bodyAsText())
            assertEquals(3, reads.get())
            assertEquals(503, client.post("$base/post") { setBody("local-test-only") }.status.value)
            assertEquals(1, posts.get())
            val reopened = PersistentCookieStorage(fs)
            try { assertEquals("desktop-check", reopened.get(Url("$base/read")).single { it.name == "session" }.value) }
            finally { reopened.close() }
        } finally { client.close(); cookies.close(); server.stop(0); env.closeAndAwait(); root.deleteRecursively() }
    }
}
