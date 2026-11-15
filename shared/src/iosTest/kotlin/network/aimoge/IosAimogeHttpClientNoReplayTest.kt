@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.network.aimoge

import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.HttpMethod
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.posix.AF_INET
import platform.posix.INADDR_LOOPBACK
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_RCVTIMEO
import platform.posix.accept
import platform.posix.bind
import platform.posix.close
import platform.posix.getsockname
import platform.posix.listen
import platform.posix.recv
import platform.posix.setsockopt
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.socklen_tVar
import platform.posix.timeval
import kotlin.concurrent.AtomicInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AM-B4 on iOS: NSURLSession may resend an idempotent request (PUT, DELETE) when the line drops. The あいもげ
 * client must reach the server exactly once for every write shape. A loopback server reads each request and closes
 * the connection with no answer.
 */
class IosAimogeHttpClientNoReplayTest {
    private fun htons(v: Int): UShort = (((v and 0xff) shl 8) or ((v shr 8) and 0xff)).toUShort()

    @Test
    fun aDroppedConnectionIsNotRetriedForAnyWriteShape() = runBlocking {
        val shapes = listOf(
            HttpMethod.Post to "/threads/x/posts",
            HttpMethod.Post to "/uploads/presign",
            HttpMethod.Delete to "/posts/x",
            HttpMethod.Put to "/posts/x/reactions/up",
            HttpMethod.Put to "/posts/x/del",
        )
        for ((method, path) in shapes) {
            val fd = socket(AF_INET, SOCK_STREAM, 0)
            assertTrue(fd >= 0)
            val port = memScoped {
                val addr = alloc<sockaddr_in>()
                addr.sin_family = AF_INET.convert()
                addr.sin_port = htons(0)
                addr.sin_addr.s_addr = htons(0).toUInt().let { (INADDR_LOOPBACK.toUInt().let { a ->
                    ((a and 0xffu) shl 24) or ((a and 0xff00u) shl 8) or ((a shr 8) and 0xff00u) or (a shr 24) }) }
                assertEquals(0, bind(fd, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()))
                assertEquals(0, listen(fd, 8))
                val len = alloc<socklen_tVar>().apply { value = sizeOf<sockaddr_in>().convert() }
                getsockname(fd, addr.ptr.reinterpret(), len.ptr)
                val p = addr.sin_port.toInt()
                ((p and 0xff) shl 8) or ((p shr 8) and 0xff)
            }
            memScoped {
                val tv = alloc<timeval>().apply { tv_sec = 5; tv_usec = 0 }
                setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, tv.ptr, sizeOf<timeval>().convert())
            }
            val received = AtomicInt(0)
            val server = async(Dispatchers.IO) {
                // Accept until the listening socket is closed; each connection is read once and dropped.
                while (true) {
                    val c = accept(fd, null, null)
                    if (c < 0) break
                    memScoped {
                        val buf = allocArray<ByteVar>(4096)
                        if (recv(c, buf, 4096.convert(), 0) > 0) received.incrementAndGet()
                    }
                    close(c)
                }
            }
            val client = createAimogeHttpClient("test")
            try {
                val failed = runCatching {
                    withTimeout(20_000) {
                        client.request("http://127.0.0.1:$port$path") {
                            this.method = method
                            setBody("{\"requestId\":\"r\"}")
                        }
                    }
                }.isFailure
                assertTrue(failed, "$method $path should fail")
                assertEquals(1, received.value, "$method $path reached the server ${received.value} times")
            } finally {
                client.close()
                platform.posix.shutdown(fd, platform.posix.SHUT_RDWR)
                close(fd)
                withTimeout(10_000) { server.await() }
            }
        }
    }
}
