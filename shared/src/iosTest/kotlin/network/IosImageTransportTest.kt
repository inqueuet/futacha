@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, coil3.annotation.ExperimentalCoilApi::class)
package com.valoser.futacha.shared.network

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.*
import coil3.decode.DataSource
import com.valoser.futacha.shared.ui.image.*
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.network.sockets.SocketTimeoutException
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.NSLock
import platform.posix.*
import kotlin.test.*
import kotlin.time.measureTime

class IosImageTransportTest {
    @Test fun requestSocketTimeoutIsNotOverwrittenByTheEngine(): Unit = runBlocking(Dispatchers.Default) {
        val server = IosImageHttpServer { delay(6000); Reply() }
        val client = createHttpClient()
        try {
            var error: Throwable? = null
            val elapsed = measureTime {
                try { client.get(server.url("/stall.jpg")) {
                    attributes.put(HigherLayerRetryManaged, true)
                    timeout { requestTimeoutMillis = 3000; socketTimeoutMillis = 1000 }
                }.bodyAsText() } catch (e: Throwable) { error = e }
            }
            assertIs<SocketTimeoutException>(error)
            assertTrue(elapsed.inWholeMilliseconds in 500..2499, "$elapsed")
            assertEquals(1, server.count("/stall.jpg"))
            println("IMAGE_REGRESSION ios socketMs=${elapsed.inWholeMilliseconds}")
        } finally { client.close();server.close() }
    }

    @Test fun imageServerFailureRetriesOnlyTheOriginalUrl(): Unit = runBlocking(Dispatchers.Default) {
        val server = IosImageHttpServer { Reply(status = 503) }
        val parent = createHttpClient()
        val client = parent.config { configureImageRequests(); install(HttpRequestRetry) { delayMillis { 0 } } }
        val loader = loader(client, null)
        try {
            val result = loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(server.url("/src/123.jpg")).size(64).build())
            assertIs<ErrorResult>(result)
            assertEquals(3, server.count("/src/123.jpg"))
            assertEquals(3, server.total())
        } finally { loader.shutdown();client.close();parent.close();server.close() }
    }

    @Test fun interruptedResponseBodyRecoversWithinTheSameFetchBudget(): Unit = runBlocking(Dispatchers.Default) {
        var attempts = 0
        val server = IosImageHttpServer { Reply(truncated = ++attempts == 1) }
        val parent = createHttpClient()
        val client = parent.config { configureImageRequests() }
        val loader = loader(client, null)
        try {
            val result = loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE)
                .data(server.url("/body.jpg")).size(64).build())
            assertIs<SuccessResult>(result, (result as? ErrorResult)?.throwable.toString())
            assertEquals(2, server.count("/body.jpg"))
        } finally { loader.shutdown();client.close();parent.close();server.close() }
    }

    @Test fun legacyNegativeCacheRevalidatesOnDarwin(): Unit = runBlocking(Dispatchers.Default) {
        val status = MutableStateFlow(404)
        val server = IosImageHttpServer { Reply(status = status.value) }
        val parent = createHttpClient()
        val client = parent.config { configureImageRequests() }
        val directory = (NSTemporaryDirectory() + "futacha-image-test-${NSUUID().UUIDString}").toPath()
        val cache = DiskCache.Builder().directory(directory).maxSizeBytes(1024L * 1024).build()
        val legacy = ImageLoader.Builder(PlatformContext.INSTANCE)
            .components { add(KtorNetworkFetcherFactory(httpClient = client)) }.diskCache(cache)
            .mainCoroutineContext(Dispatchers.Default).build()
        val fixed = loader(client, cache)
        try {
            val request = ImageRequest.Builder(PlatformContext.INSTANCE).data(server.url("/image.jpg")).size(64).build()
            assertIs<ErrorResult>(legacy.execute(request))
            status.value = 200
            assertIs<ErrorResult>(legacy.execute(request))
            assertEquals(1, server.count("/image.jpg"))
            assertIs<SuccessResult>(fixed.execute(request))
            assertEquals(2, server.count("/image.jpg"))
            val diskResult = assertIs<SuccessResult>(fixed.execute(request.newBuilder().memoryCacheKey("different").build()))
            assertEquals(DataSource.DISK, diskResult.dataSource)
            assertEquals(2, server.count("/image.jpg"))
        } finally {
            legacy.shutdown();fixed.shutdown();cache.shutdown();client.close();parent.close();server.close()
            okio.FileSystem.SYSTEM.deleteRecursively(directory)
        }
    }

    private fun loader(client: io.ktor.client.HttpClient, cache: DiskCache?): ImageLoader {
        val original = buildFutachaImageLoader(PlatformContext.INSTANCE, Dispatchers.Default, Dispatchers.Default,
            MemoryCache.Builder().maxSizeBytes(1024L * 1024).build(), cache,
            AdaptiveImageRequestGate(resolveImageMemoryPressurePolicy(3, 1024L * 1024, 256, ImageMemoryPressureLevel.NORMAL)), client)
        // These headless Native tests have no UIApplication main run loop.
        return original.newBuilder().mainCoroutineContext(Dispatchers.Default).build().also { original.shutdown() }
    }
}

private data class Reply(val status: Int = 200, val truncated: Boolean = false)

private class IosImageHttpServer(private val reply: suspend (String) -> Reply) {
    private val listener = socket(AF_INET, SOCK_STREAM, 0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = NSLock()
    private val clients = mutableSetOf<Int>()
    private val counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val port: Int
    init {
        check(listener >= 0)
        port = memScoped {
            val address = alloc<sockaddr_in>()
            memset(address.ptr, 0, sizeOf<sockaddr_in>().convert())
            address.sin_family = AF_INET.convert()
            // Project iOS targets are little-endian arm64.
            address.sin_addr.s_addr = 0x0100007fu
            address.sin_port = 0u
            check(bind(listener, address.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) == 0)
            check(listen(listener, 16) == 0)
            val length = alloc<socklen_tVar>();length.value = sizeOf<sockaddr_in>().convert()
            check(getsockname(listener, address.ptr.reinterpret(), length.ptr) == 0)
            ((address.sin_port.toInt() and 255) shl 8) or (address.sin_port.toInt() ushr 8)
        }
        scope.launch {
            while (isActive) {
                val fd = accept(listener, null, null)
                if (fd < 0) break
                lock.lock();clients.add(fd);lock.unlock()
                scope.launch {
                    try {
                        memScoped { val yes = alloc<IntVar>();yes.value = 1;setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, yes.ptr, sizeOf<IntVar>().convert()) }
                        while (isActive) {
                            val header = StringBuilder()
                            val one = ByteArray(1)
                            while (!header.endsWith("\r\n\r\n") && header.length < 16384) {
                                val n = one.usePinned { recv(fd, it.addressOf(0), 1u, 0) }
                                if (n <= 0) return@launch
                                header.append(one[0].toInt().toChar())
                            }
                            val path = header.toString().substringBefore("\r\n").split(' ')[1]
                            counts.update { it + (path to ((it[path] ?: 0) + 1)) }
                            val result = reply(path)
                            val body = if (result.status == 200) png else "unavailable".encodeToByteArray()
                            val output = "HTTP/1.1 ${result.status} Response\r\nContent-Type: image/png\r\nContent-Length: ${body.size + if (result.truncated) 100 else 0}\r\nConnection: keep-alive\r\n\r\n".encodeToByteArray() + body
                            output.usePinned { buffer ->
                                var offset = 0
                                while (offset < output.size) {
                                    val n = send(fd, buffer.addressOf(offset), (output.size - offset).convert(), 0)
                                    if (n <= 0) return@launch
                                    offset += n.toInt()
                                }
                            }
                            if (result.truncated) return@launch
                        }
                    } finally { lock.lock();clients.remove(fd);platform.posix.close(fd);lock.unlock() }
                }
            }
        }
    }
    fun url(path: String) = "http://127.0.0.1:$port$path"
    fun count(path: String) = counts.value[path] ?: 0
    fun total() = counts.value.values.sum()
    fun close() {
        scope.cancel();shutdown(listener, SHUT_RDWR);platform.posix.close(listener)
        lock.lock();clients.forEach { shutdown(it, SHUT_RDWR) };lock.unlock()
    }
    companion object {
        private val png = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 0, 64, 0, 0, 0, 64, 8, 6, 0, 0, 0, -86, 105, 113, -34, 0, 0, 0, -88, 73, 68, 65, 84, 120, -100, -27, -50, 33, 1, 0, 0, 12, 4, -95, -21, 95, -6, 23, 3, 49, -127, -89, -38, 126, -29, 1, -115, 7, 52, 30, -48, 120, 64, -29, 1, -115, 7, 52, 30, -48, 120, 64, -29, 1, -115, 7, 52, 30, -48, 120, 64, -29, 1, -115, 7, 52, 30, -48, 120, 64, -29, 1, -115, 7, 52, 30, -48, 120, 64, -29, 1, -115, 7, 52, 30, -48, 120, 64, -29, 1, -115, 7, 52, 30, -48, 120, 64, -29, 1, -115, 7, 52, 30, -48, 120, 64, -29, 1, -115, 7, 52, 30, -48, 120, 64, -29, 1, -115, 7, 52, 30, -48, 120, 64, -29, 1, -115, 7, 52, 30, -48, 120, 64, -29, 1, -115, 7, 52, 30, -48, 120, 64, -29, 1, -115, 7, 52, 30, -48, 120, 64, -29, 1, -115, 7, 52, 30, -48, 120, 64, -29, 1, -115, 7, 52, 30, -48, 120, 64, -29, 1, -115, 7, 52, 30, -80, 14, 97, -88, -31, -46, 109, 114, -14, -41, 0, 0, 0, 0, 73, 69, 78, 68, -82, 66, 96, -126)
    }
}
