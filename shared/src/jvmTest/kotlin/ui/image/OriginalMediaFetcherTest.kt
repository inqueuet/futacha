package com.valoser.futacha.shared.ui.image

import coil3.ColorImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.disk.DiskCache
import coil3.fetch.SourceFetchResult
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import com.sun.net.httpserver.HttpServer
import com.valoser.futacha.shared.media.source.*
import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.prompt.PromptMediaSource
import kotlinx.coroutines.flow.first
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.*
import okio.Path.Companion.toPath
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.CRC32
import javax.imageio.ImageIO
import kotlin.test.*

class OriginalMediaFetcherTest {
    @Test fun twoCoilLoadersAndMetadataReadTheSameOriginalWithOneHttpGet(): Unit = runBlocking {
        val bytes = metadataPng()
        val calls = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val responseAllowed = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/src/image.png") { exchange ->
            calls.incrementAndGet()
            started.complete(Unit)
            check(responseAllowed.await(10, TimeUnit.SECONDS))
            exchange.responseHeaders.add("Content-Type", "image/png")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val directory = Files.createTempDirectory("original-coil").toFile()
        val client = HttpClient(OkHttp) {
            engine { config { dns { listOf(java.net.InetAddress.getByName("127.0.0.1")) } } }
        }
        val downloader = KtorOriginalMediaDownloader(client, maxBytes = 1024 * 1024)
        val store = OriginalMediaStore("test-profile", {
            DiskCache.Builder().directory(directory.path.toPath()).maxSizeBytes(1024 * 1024).build()
        }, downloader, Dispatchers.IO)
        val gate = MediaFeatureGate().apply { update(MediaFeatureSettings(promptDisplayEnabled = true)) }
        val prompts = PromptMediaSource(store, gate)
        val decodedFiles = CopyOnWriteArrayList<String>()
        fun imageLoader() = ImageLoader.Builder(PlatformContext.INSTANCE).diskCache(null).components {
            addOriginalMediaSupport(prompts)
            // Actual Coil fetch/decode lifecycle, with ImageIO decoding on JVM.
            // Platform-native Android/iOS decoders are a separate device check.
            add(Decoder.Factory { result, _, _ ->
                object : Decoder {
                    override suspend fun decode(): DecodeResult {
                        decodedFiles += result.source.file().toString()
                        val decoded = ImageIO.read(result.source.source().inputStream())
                        assertEquals(8, decoded.width)
                        assertEquals(8, decoded.height)
                        return DecodeResult(ColorImage(0xff2468ac.toInt(), decoded.width, decoded.height), false)
                    }
                }
            })
        }.build()
        val catalog = imageLoader()
        val viewer = imageLoader()
        val original = OriginalMediaRequest("http://media-test.2chan.net:${server.address.port}/src/image.png")
        // Existing screen data is a URL string; exercise Coil's String -> Uri mapping
        // and our original-only factory, without requiring each screen to opt in.
        val imageRequest = ImageRequest.Builder(PlatformContext.INSTANCE).data(original.url)
            .memoryCachePolicy(CachePolicy.DISABLED).build()
        try {
            withTimeout(15000) {
                val firstDisplay = async { catalog.execute(imageRequest) }
                started.await()
                val secondDisplay = async { viewer.execute(imageRequest) }
                val metadata = async(start = CoroutineStart.UNDISPATCHED) { store.acquire(original) }
                responseAllowed.countDown()
                metadata.await().use { lease ->
                    assertIs<SuccessResult>(firstDisplay.await())
                    assertIs<SuccessResult>(secondDisplay.await())
                    assertEquals(listOf(lease.file.toString(), lease.file.toString()), decodedFiles.toList())
                    val originalBytes = lease.readAt(0, bytes.size)
                    assertContentEquals(bytes, originalBytes, "Decoder must not replace the raw source with a recompressed image")
                    assertTrue(originalBytes.decodeToString().contains("parameters\u0000cat\nNegative prompt: blurry"))
                    assertIs<SuccessResult>(viewer.execute(imageRequest))
                }
                prompts.changes.first { prompts.metadata(original.url) != null }
                assertEquals("cat", prompts.metadata(original.url)!!.candidates.single().positive)
                assertTrue(prompts.metadata(original.url)!!.hasAiEvidence)
                assertEquals(1, calls.get(), "Two independent Coil loaders and metadata must share the first download")
                assertNull(catalog.diskCache)
                assertNull(viewer.diskCache)
            }
        } finally {
            responseAllowed.countDown()
            prompts.close()
            catalog.shutdown(); viewer.shutdown()
            withTimeout(5000) { store.closeAndAwait() }
            downloader.close(); client.close(); server.stop(0)
            directory.deleteRecursively()
        }
    }

    @Test fun coilCacheOnlyRequestDoesNotDownloadAndReadOnlyRequestReusesOriginal(): Unit = runBlocking {
        val directory = Files.createTempDirectory("original-coil-offline").toFile()
        var calls = 0
        val store = OriginalMediaStore("test", {
            DiskCache.Builder().directory(directory.path.toPath()).maxSizeBytes(1024 * 1024).build()
        }, { request, sink ->
            calls++
            sink.writeUtf8("bytes")
            OriginalMediaInfo("image/png", 5, resolvedUrl = request.url)
        }, Dispatchers.IO)
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).diskCache(null).build()
        val factory = OriginalMediaFetcher.Factory(store)
        val ref = OriginalMediaRef(OriginalMediaRequest("https://example.test/src/image.png"))
        try {
            val offline = Options(PlatformContext.INSTANCE, networkCachePolicy = CachePolicy.DISABLED)
            assertFailsWith<okio.IOException> { factory.create(ref, offline, loader).fetch() }
            assertEquals(0, calls)
            val online = Options(PlatformContext.INSTANCE)
            assertIs<SourceFetchResult>(factory.create(ref, online, loader).fetch()).source.close()
            val readOnly = offline.copy(diskCachePolicy = CachePolicy.READ_ONLY)
            val cached = assertIs<SourceFetchResult>(factory.create(ref, readOnly, loader).fetch())
            cached.source.use { assertEquals("bytes", it.source().readUtf8()) }
            assertEquals(1, calls)
        } finally {
            loader.shutdown()
            withTimeout(5000) { store.closeAndAwait() }
            directory.deleteRecursively()
        }
    }

    @Test fun explicitCoilRefreshTokenIsSharedAcrossLoaderInstances(): Unit = runBlocking {
        val directory = Files.createTempDirectory("original-coil-refresh").toFile()
        val calls = AtomicInteger()
        val store = OriginalMediaStore("test", {
            DiskCache.Builder().directory(directory.path.toPath()).maxSizeBytes(1024 * 1024).build()
        }, { request, sink ->
            sink.writeUtf8("v${calls.incrementAndGet()}")
            OriginalMediaInfo("image/png", 2, resolvedUrl = request.url)
        }, Dispatchers.IO)
        val decoded = CopyOnWriteArrayList<String>()
        fun loader() = ImageLoader.Builder(PlatformContext.INSTANCE).diskCache(null).components {
            add(ImageRefreshInterceptor())
            addOriginalMediaSupport(store)
            add(Decoder.Factory { result, _, _ -> object : Decoder {
                override suspend fun decode(): DecodeResult {
                    decoded += result.source.source().readUtf8()
                    return DecodeResult(ColorImage(0, 1, 1), false)
                }
            } })
        }.build()
        val first = loader()
        val second = loader()
        val request = ImageRequest.Builder(PlatformContext.INSTANCE)
            .data(OriginalMediaRef(OriginalMediaRequest("https://example.test/src/image.png")))
            .memoryCachePolicy(CachePolicy.DISABLED).build()
        try {
            assertIs<SuccessResult>(first.execute(request))
            val refresh = request.newBuilder().refreshImageOnce(77).build()
            assertIs<SuccessResult>(first.execute(refresh))
            assertIs<SuccessResult>(second.execute(refresh))
            assertIs<SuccessResult>(second.execute(refresh))
            assertEquals(listOf("v1", "v2", "v2", "v2"), decoded.toList())
            assertEquals(2, calls.get())
        } finally {
            first.shutdown(); second.shutdown()
            withTimeout(5000) { store.closeAndAwait() }
            directory.deleteRecursively()
        }
    }

    private fun metadataPng(): ByteArray {
        val bitmap = BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB)
        val png = ByteArrayOutputStream().also { ImageIO.write(bitmap, "png", it) }.toByteArray()
        val payload = "parameters\u0000cat\nNegative prompt: blurry\nSteps: 20, Sampler: Euler, Seed: 42".toByteArray()
        val chunk = ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use {
                val type = "tEXt".toByteArray()
                it.writeInt(payload.size)
                it.write(type)
                it.write(payload)
                it.writeInt(CRC32().apply { update(type); update(payload) }.value.toInt())
            }
        }.toByteArray()
        return png.copyOfRange(0, png.size - 12) + chunk + png.copyOfRange(png.size - 12, png.size)
    }
}
