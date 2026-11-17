package com.valoser.futacha.shared.network.aimoge

import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.HttpMethod
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AM-B4: the あいもげ client never sends a write twice. A local server reads each request and drops the connection
 * without an answer (the way a dropped line looks); every write shape must reach it exactly once.
 */
class AimogeHttpClientNoReplayTest {
    @Test
    fun aDroppedConnectionIsNotRetriedForAnyWriteShape() = runBlocking {
        val shapes = listOf(
            HttpMethod.Post to "/threads/x/posts",        // a reply
            HttpMethod.Post to "/uploads/presign",        // presign
            HttpMethod.Delete to "/posts/x",              // self-delete
            HttpMethod.Put to "/posts/x/reactions/up",    // そうだね
            HttpMethod.Put to "/posts/x/del",             // del
        )
        for ((method, path) in shapes) {
            val received = AtomicInteger()
            val server = ServerSocket(0)
            val acceptor = thread(isDaemon = true) {
                while (!server.isClosed) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    socket.use {
                        val input = it.getInputStream().bufferedReader()
                        val first = input.readLine()
                        if (!first.isNullOrEmpty()) received.incrementAndGet()
                        // Read the headers, then drop the line with no answer.
                        while (true) { val line = input.readLine() ?: break; if (line.isEmpty()) break }
                    }
                }
            }
            val client = createAimogeHttpClient("test")
            try {
                val failed = runCatching {
                    client.request("http://127.0.0.1:${server.localPort}$path") {
                        this.method = method
                        setBody("{\"requestId\":\"r\"}")
                    }
                }.isFailure
                assertTrue(failed, "$method $path should fail")
                assertEquals(1, received.get(), "$method $path reached the server ${received.get()} times")
            } finally {
                client.close()
                server.close()
                acceptor.join(2_000)
            }
        }
    }
}
