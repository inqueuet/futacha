package com.valoser.futacha

import com.valoser.futacha.shared.media.MediaFeatureGate
import com.valoser.futacha.shared.media.MediaFeatureSettings
import com.valoser.futacha.shared.media.prompt.PromptMediaSource
import com.valoser.futacha.shared.media.prompt.MetadataCoverage

import android.graphics.Bitmap
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.network.*
import com.valoser.futacha.shared.media.source.OriginalMediaCacheConfiguration
import com.valoser.futacha.shared.media.source.OriginalMediaRequest
import com.valoser.futacha.shared.ui.image.OriginalMediaRef
import com.valoser.futacha.shared.ui.image.addOriginalMediaSupport
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import okio.Path.Companion.toPath
import kotlinx.coroutines.flow.first
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetryEvent
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class ImageTransportInstrumentedTest {
    @Test fun jpegAndWebpGenerationMetadataShareOneDownloadWithNativeDecoders(): Unit = runBlocking(Dispatchers.IO) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        for ((file, mime, coverage) in listOf(
            Triple("a1111.jpg", "image/jpeg", MetadataCoverage.JPEG_METADATA),
            Triple("a1111.webp", "image/webp", MetadataCoverage.WEBP_METADATA),
            Triple("a1111-xmp.jpg", "image/jpeg", MetadataCoverage.JPEG_METADATA)
        )) {
            val bytes = instrumentation.context.assets.open("generation/$file").use { it.readBytes() }
            val directory = java.io.File(context.cacheDir, "original-format-${java.util.UUID.randomUUID()}")
            val transport = createAndroidImageTransport(AcceptAllCookiesStorage())
            val session = transport.originalMediaSession
            session.configure(OriginalMediaCacheConfiguration(directory.path.toPath(), 1024 * 1024))
            val prompts = PromptMediaSource(session, MediaFeatureGate().apply { update(MediaFeatureSettings(promptDisplayEnabled = true)) })
            fun loader() = ImageLoader.Builder(context).diskCache(null).components { addOriginalMediaSupport(prompts) }.build()
            val first = loader()
            val second = loader()
            try {
                ImageTransportTestServer(bytes, mime).use { server ->
                    val original = OriginalMediaRequest(server.url("/image.jpg"))
                    val request = ImageRequest.Builder(context).data(OriginalMediaRef(original)).build()
                    withTimeout(10000) {
                        val a = async { first.execute(request) }
                        val b = async { second.execute(request) }
                        assertTrue(a.await() is SuccessResult)
                        assertTrue(b.await() is SuccessResult)
                        prompts.changes.first { prompts.metadata(original.url) != null }
                        val result = prompts.metadata(original.url)!!
                        assertEquals(coverage, result.coverage)
                        assertEquals(if (file == "a1111-xmp.jpg") 2 else 1, result.candidates.size)
                        assertTrue(result.candidates.all { it.positive == "  cat 日本語 🐈" && it.negative == "blurry" })
                        assertEquals(1, server.count("/image.jpg"))
                    }
                }
            } finally {
                prompts.close(); first.shutdown(); second.shutdown()
                withTimeout(5000) { session.closeAndAwait() }
                transport.close(); directory.deleteRecursively()
            }
        }
    }

    @Test fun originalSessionSharesOneHttpGetAcrossTwoAndroidDecoders(): Unit = runBlocking(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = java.io.File(context.cacheDir, "original-device-${java.util.UUID.randomUUID()}")
        val transport = createAndroidImageTransport(AcceptAllCookiesStorage())
        val session = transport.originalMediaSession
        session.configure(OriginalMediaCacheConfiguration(directory.path.toPath(), 1024 * 1024))
        val prompts = PromptMediaSource(session, MediaFeatureGate().apply { update(MediaFeatureSettings(promptDisplayEnabled = true)) })
        fun loader() = ImageLoader.Builder(context).diskCache(null).components { addOriginalMediaSupport(prompts) }.build()
        val first = loader()
        val second = loader()
        try {
            ImageTransportTestServer().use { server ->
                val original = OriginalMediaRequest(server.url("/image.jpg"))
                val request = ImageRequest.Builder(context).data(OriginalMediaRef(original)).size(64).build()
                withTimeout(10000) {
                    val a = async { first.execute(request) }
                    val b = async { second.execute(request) }
                    session.acquire(original).use { lease ->
                        assertTrue(a.await() is SuccessResult)
                        assertTrue(b.await() is SuccessResult)
                        assertArrayEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10), lease.readAt(0, 8))
                    }
                    prompts.changes.first { prompts.metadata(original.url) != null }
                    assertEquals(MetadataCoverage.PNG_METADATA, prompts.metadata(original.url)!!.coverage)
                    assertEquals(1, server.count("/image.jpg"))
                }
            }
        } finally {
            prompts.close()
            first.shutdown(); second.shutdown()
            withTimeout(5000) { session.closeAndAwait() }
            transport.close(); directory.deleteRecursively()
        }
    }

    @Test fun mediaFeatureSettingsPersistIndependentlyInAndroidDataStore(): Unit = runBlocking(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = com.valoser.futacha.shared.state.createAppStateStore(context)
        val previous = store.mediaFeatureSettings.first()
        try {
            store.updateMediaFeatureSettings { com.valoser.futacha.shared.media.MediaFeatureSettings.Disabled }
            val fresh = com.valoser.futacha.shared.state.createAppStateStore(context)
            assertFalse(fresh.mediaFeatureSettings.first().promptDisplayEnabled)
            fresh.updateMediaFeatureSettings { it.copy(promptDisplayEnabled = true, imageEditorEnabled = true) }
            val reopened = com.valoser.futacha.shared.state.createAppStateStore(context)
            assertTrue(reopened.mediaFeatureSettings.first().showAiLabels)
            assertTrue(reopened.mediaFeatureSettings.first().imageEditorEnabled)
            assertFalse(reopened.mediaFeatureSettings.first().videoEditorEnabled)
        } finally { store.updateMediaFeatureSettings { previous } }
    }

    // The production API intentionally does not expose a general-purpose client.
    private fun client(transport: FutachaImageTransport): HttpClient =
        transport.javaClass.getDeclaredField("client").apply { isAccessible = true }.get(transport) as HttpClient

    @Test fun discardedKeepAliveSocketsRecoverWithoutKtorBackoff(): Unit = runBlocking(Dispatchers.IO) {
        val transport = createAndroidImageTransport(AcceptAllCookiesStorage())
        val http = client(transport)
        var retries = 0
        http.monitor.subscribe(HttpRequestRetryEvent) { retries++ }
        try {
            ImageTransportTestServer().use { server ->
                (0..2).map { async { http.get(server.url("/warm/$it.jpg")).bodyAsText() } }.awaitAll()
                server.cutConnections(); delay(150)
                val elapsed = measureTimeMillis { assertEquals("ok", http.get(server.url("/fresh.jpg")).bodyAsText()) }
                assertEquals(0, retries)
                assertTrue("Unexpected backoff: $elapsed", elapsed < 1000)
                Log.i("ImageTransportTest", "stale recoveryMs=$elapsed retries=$retries")
            }
        } finally { transport.close() }
    }

    @Test fun mutationRequestsAreRejectedOnInitialAndRedirectSends(): Unit = runBlocking(Dispatchers.IO) {
        val transport = createAndroidImageTransport(AcceptAllCookiesStorage())
        val http = client(transport)
        try {
            ImageTransportTestServer().use { server ->
                assertTrue(runCatching { http.post(server.url("/fresh.jpg")) { setBody("dummy") } }.isFailure)
                assertTrue(runCatching { http.get(server.url("/sd.php")) }.isFailure)
                assertTrue(runCatching { http.get(server.url("/redirect.jpg")) }.isFailure)
                assertEquals(0, server.count("/fresh.jpg"))
                assertEquals(0, server.count("/sd.php"))
                assertEquals(1, server.count("/redirect.jpg"))
            }
        } finally { transport.close() }
    }

    @Test fun redirectCookiesAreSelectedForEachHostAndIntermediateCookiesPersist(): Unit = runBlocking(Dispatchers.IO) {
        val transport = createAndroidImageTransport(AcceptAllCookiesStorage())
        val http = client(transport)
        try {
            ImageTransportTestServer().use { server ->
                http.get(server.url("/set.jpg?value=hostOnly")).bodyAsText()
                assertTrue(http.get(server.url("/echo.jpg")).bodyAsText().contains("probe=hostOnly"))
                assertFalse(http.get(server.url("/cross-host.jpg")).bodyAsText().contains("probe=hostOnly"))
                assertTrue(http.get(server.url("/set-redirect.jpg")).bodyAsText().contains("probe=redirected"))
                assertTrue(http.get(server.url("/echo.jpg")).bodyAsText().contains("probe=redirected"))
            }
        } finally { transport.close() }
    }
}

private class ImageTransportTestServer(private val imageBytes: ByteArray? = null, private val imageMime: String = "image/png") : AutoCloseable {
    private val socket = ServerSocket(0)
    private val running = AtomicBoolean(true)
    private val sockets = CopyOnWriteArrayList<Socket>()
    private val warm = java.util.concurrent.CountDownLatch(3)
    private val counts = ConcurrentHashMap<String, Int>()
    private val png = ByteArrayOutputStream().apply {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, this)
        bitmap.recycle()
    }.toByteArray()
    private val acceptor = thread(isDaemon = true) {
        while (running.get()) {
            val client = try { socket.accept() } catch (_: Exception) { break }
            sockets += client
            thread(isDaemon = true) {
                try {
                    val reader = client.getInputStream().bufferedReader(Charsets.US_ASCII)
                    val out = client.getOutputStream()
                    while (running.get()) {
                        val request = reader.readLine() ?: break
                        val target = request.split(' ').getOrNull(1) ?: break
                        val path = target.substringBefore('?')
                        val headers = mutableMapOf<String, String>()
                        while (true) {
                            val h = reader.readLine() ?: break
                            if (h.isEmpty()) break
                            headers[h.substringBefore(':').lowercase()] = h.substringAfter(':').trim()
                        }
                        counts.merge(path, 1, Int::plus)
                        if (path.startsWith("/warm/")) { warm.countDown(); check(warm.await(10, java.util.concurrent.TimeUnit.SECONDS)) }
                        val route = path.removeSuffix(".jpg")
                        var status = "200 OK"
                        var extra = ""
                        var body = "ok".toByteArray()
                        when (route) {
                            "/set" -> extra = "Set-Cookie: probe=${target.substringAfter("value=")}; Path=/; HttpOnly\r\n"
                            "/echo" -> body = headers["cookie"].orEmpty().toByteArray()
                            "/redirect" -> { status = "302 Found"; extra = "Location: /sd.php\r\n" }
                            "/set-redirect" -> { status = "302 Found"; extra = "Location: /echo.jpg\r\nSet-Cookie: probe=redirected; Path=/; HttpOnly\r\n" }
                            "/cross-host" -> { status = "302 Found"; extra = "Location: http://localhost:${socket.localPort}/echo.jpg\r\n" }
                            "/image" -> { body = imageBytes ?: png; extra = "Content-Type: $imageMime\r\n" }
                            "/503" -> { status = "503 Unavailable"; extra = "Retry-After: 2\r\n" }
                            "/404" -> status = "404 Not Found"
                            "/slow" -> {
                                out.write("HTTP/1.1 200 OK\r\nContent-Length: 1048576\r\nContent-Type: image/png\r\n\r\n".toByteArray())
                                repeat(256) { out.write(ByteArray(4096)); out.flush(); Thread.sleep(50) }
                                continue
                            }
                        }
                        out.write("HTTP/1.1 $status\r\n${extra}Content-Length: ${body.size}\r\nConnection: keep-alive\r\n\r\n".toByteArray())
                        out.write(body); out.flush()
                    }
                } catch (_: Exception) { } finally { sockets.remove(client); runCatching { client.close() } }
            }
        }
    }
    fun cutConnections() { sockets.forEach { runCatching { it.close() } } }
    fun count(path: String) = counts[path] ?: 0
    fun url(path: String) = "http://127.0.0.1:${socket.localPort}$path"
    override fun close() { running.set(false); socket.close(); sockets.forEach { runCatching { it.close() } }; acceptor.join(1000) }
}
