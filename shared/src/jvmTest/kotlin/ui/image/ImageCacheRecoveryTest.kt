@file:OptIn(coil3.annotation.ExperimentalCoilApi::class)
package com.valoser.futacha.shared.ui.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.fetch.SourceFetchResult
import coil3.network.*
import coil3.request.Options
import coil3.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.net.ProtocolException
import okio.ForwardingSource
import okio.buffer
import okio.Buffer
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.*

class ImageCacheRecoveryTest {
    @Test fun bodyFailureThenServerFailuresShareOneThreeAttemptBudget(): Unit = runBlocking(Dispatchers.IO) {
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).diskCache(null).build()
        var calls = 0
        val network = object : NetworkClient {
            override suspend fun <T> executeRequest(request: NetworkRequest, block: suspend (NetworkResponse) -> T): T {
                calls++
                val brokenBody = object : ForwardingSource(Buffer().writeUtf8("partial")) {
                    override fun read(sink: Buffer, byteCount: Long): Long = throw ProtocolException("unexpected end of stream")
                }.buffer()
                return block(NetworkResponse(code = if (calls == 1) 200 else 503,
                    body = NetworkResponseBody(if (calls == 1) brokenBody else Buffer().writeUtf8("unavailable"))))
            }
        }
        val factory = NetworkFetcher.Factory(networkClient = { network }, cacheStrategy = { ImageCacheStrategy },
            concurrentRequestStrategy = { RetryingImageRequestStrategy { _, _ -> } })
        try {
            assertFailsWith<HttpException> {
                factory.create("https://example.test/body.jpg".toUri(), Options(context = PlatformContext.INSTANCE), loader)!!.fetch()
            }
            assertEquals(3, calls, "Streaming and status failures must not have separate retry budgets")
        } finally { loader.shutdown() }
    }

    @Test fun previouslyCached404RevalidatesThenUsesSuccessfulDiskEntry(): Unit = runBlocking(Dispatchers.IO) {
        val directory = Files.createTempDirectory("image-cache-recovery").toFile()
        val disk = DiskCache.Builder().directory(directory.path.toPath()).maxSizeBytes(1024L * 1024).build()
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).diskCache(disk).build()
        var calls = 0
        val network = object : NetworkClient {
            override suspend fun <T> executeRequest(request: NetworkRequest, block: suspend (NetworkResponse) -> T): T {
                calls++
                return block(NetworkResponse(code = if (calls == 1) 404 else 200,
                    body = NetworkResponseBody(Buffer().writeUtf8(if (calls == 1) "not found" else "recovered image"))))
            }
        }
        val old = NetworkFetcher.Factory(networkClient = { network })
        val repaired = NetworkFetcher.Factory(networkClient = { network }, cacheStrategy = { ImageCacheStrategy })
        val url = "https://may.2chan.net/b/thumb/recovery.jpg".toUri()
        suspend fun fetch(factory: NetworkFetcher.Factory) = factory.create(url, Options(context = PlatformContext.INSTANCE), loader)!!.fetch()
        try {
            assertFailsWith<HttpException> { fetch(old) }
            repeat(2) { assertFailsWith<HttpException> { fetch(old) } }
            assertEquals(1, calls)
            assertIs<SourceFetchResult>(fetch(repaired)).source.close()
            assertEquals(2, calls)
            assertIs<SourceFetchResult>(fetch(repaired)).source.close()
            assertEquals(2, calls, "Successful replacement must remain cached")
        } finally { loader.shutdown();disk.shutdown();directory.deleteRecursively() }
    }
}
