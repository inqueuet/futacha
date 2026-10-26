package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.ui.board.registerFutachaImageNgRule
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.lang.reflect.Proxy
import javax.imageio.ImageIO
import kotlin.test.*
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import okio.Path.Companion.toPath
import java.nio.file.Files
import com.valoser.futacha.shared.media.source.*

class CatalogImageNgRecoveryTest {
    @Test fun deletedThumbnailCanUseItsExactDiskCacheBytesWithoutRequestingIt(): Unit = runBlocking {
        val root = Files.createTempDirectory("catalog-hash-cache").toFile()
        val disk = DiskCache.Builder().directory(root.path.toPath()).maxSizeBytes(1024 * 1024).build()
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).diskCache(disk).build()
        val prefix = "https://example.invalid/cache-only-${System.nanoTime()}"
        val thumbnail = "$prefix/thumb.jpg"
        val editor = requireNotNull(disk.openEditor(thumbnail))
        disk.fileSystem.write(editor.metadata) { writeUtf8("") }
        disk.fileSystem.write(editor.data) { write(picture()) }
        editor.commit()
        val requests = mutableListOf<String>()
        try {
            HttpClient(MockEngine { requests += it.url.toString(); respondError(HttpStatusCode.NotFound) }).use { client ->
                val hash = fetchCompatImagePhash(client, "$prefix/original.jpg", fallbackImageUrl = thumbnail,
                    cachedImageLoader = loader) { "$prefix/original.png" }.getOrThrow()
                assertEquals(computeCompatImagePhashFromBytes(picture()), hash)
                assertEquals(listOf("$prefix/original.jpg", "$prefix/original.png"), requests)
                assertNull(readCompatCachedImageBytes(client, loader, thumbnail, 1), "Oversized cache must not be loaded")
            }
        } finally { loader.shutdown(); disk.shutdown(); root.deleteRecursively() }
    }

    @Test fun originalCacheSurvivesServerDeletionAndNeverDownloadsAgain(): Unit = runBlocking {
        val root = Files.createTempDirectory("catalog-original-hash").toFile()
        val payload = picture()
        var originalRequests = 0
        val source = OriginalMediaSession("catalog-hash", { request, sink ->
            originalRequests++; sink.write(payload); OriginalMediaInfo("image/png", payload.size.toLong(), resolvedUrl = request.url)
        })
        val url = "https://may.2chan.net/b/src/cache-hash-${System.nanoTime()}.png"
        try {
            source.configure(OriginalMediaCacheConfiguration(root.path.toPath(), 1024 * 1024))
            source.acquire(OriginalMediaRequest(url)).close()
            HttpClient(MockEngine { error("Cached original must not access HTTP") }).use { client ->
                client.bindOriginalMediaSource(source)
                assertEquals(computeCompatImagePhashFromBytes(payload), fetchCompatImagePhash(client, url).getOrThrow())
            }
            assertEquals(1, originalRequests)
        } finally { source.closeAndAwait(); root.deleteRecursively() }
    }

    @Test fun deletedPngUsesTheSurvivingThumbnailForRegistrationAndNextCatalog() = deletedOriginal("png")
    @Test fun deletedJpegDoesNotRetryItselfAndUsesTheSurvivingThumbnail() = deletedOriginal("jpg")

    private fun deletedOriginal(extension: String): Unit = runBlocking {
        val prefix = "https://example.invalid/deleted-$extension-${System.nanoTime()}"
        val payload = picture()
        val requests = mutableListOf<String>()
        HttpClient(MockEngine { request ->
            val url = request.url.toString(); requests += url
            if ("/thumb/" in url) respond(payload, headers = headersOf(HttpHeaders.ContentType, "image/png"))
            else respondError(HttpStatusCode.NotFound)
        }).use { client ->
            val registered = fetchCompatImagePhash(client, "$prefix/src/old.jpg", fallbackImageUrl = "$prefix/thumb/old.jpg") {
                resolveCompatCatalogPhashUrl("$prefix/thumb/old.jpg") { "$prefix/src/old.$extension" }
            }.getOrThrow()
            val next = collectCompatImagePhashes(client, listOf("next" to "$prefix/src/next.jpg"),
                fallbackImageUrl = { "$prefix/thumb/next.jpg" },
                resolveMissingUrl = { resolveCompatCatalogPhashUrl("$prefix/thumb/next.jpg") { "$prefix/src/next.$extension" } })
            assertEquals(registered, next["next"])
            assertEquals(if (extension == "jpg") 4 else 6, requests.size)
            assertEquals(2, requests.count { "/thumb/" in it })
            // The initial URL's successful hash is retained for existing DB/cache keys.
            assertEquals(registered, fetchCompatImagePhash(client, "$prefix/src/old.jpg").getOrThrow())
            assertEquals(if (extension == "jpg") 4 else 6, requests.size)
        }
    }

    @Test fun confirmedServerFailureDoesNotSilentlySwitchToThumbnail(): Unit = runBlocking {
        val prefix = "https://example.invalid/confirmed-500-${System.nanoTime()}"
        val requests = mutableListOf<String>()
        HttpClient(MockEngine { request ->
            requests += request.url.toString()
            respondError(if (request.url.encodedPath.endsWith(".png")) HttpStatusCode.InternalServerError else HttpStatusCode.NotFound)
        }).use { client ->
            val result = fetchCompatImagePhash(client, "$prefix/original.jpg", fallbackImageUrl = "$prefix/thumb.jpg") { "$prefix/original.png" }
            assertTrue(result.isFailure)
            assertEquals(listOf("$prefix/original.jpg", "$prefix/original.png"), requests)
        }
    }

    @Test fun allDeletedCandidatesAreAttemptedOnceAndRemainAFailure(): Unit = runBlocking {
        val prefix = "https://example.invalid/all-gone-${System.nanoTime()}"
        val requests = mutableListOf<String>()
        HttpClient(MockEngine { request -> requests += request.url.toString(); respondError(HttpStatusCode.Gone) }).use { client ->
            assertTrue(fetchCompatImagePhash(client, "$prefix/original.jpg", fallbackImageUrl = "$prefix/thumb.jpg") { "$prefix/original.png" }.isFailure)
            assertEquals(listOf("$prefix/original.jpg", "$prefix/original.png", "$prefix/thumb.jpg"), requests)
            assertEquals(0, compatPhashRequestLockCountForTest())
        }
    }

    private fun picture() = ByteArrayOutputStream().also { out ->
        val image = BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB)
        for (x in 0..31) for (y in 0..31) image.setRGB(x, y, if (x / 4 == y / 4) 0xFFFFFF else 0x112244)
        ImageIO.write(image, "png", out)
    }.toByteArray()
    private fun item(full: String?, thumb: String?) = CatalogItem("123", "https://example.invalid/res/123.htm", "fixture", thumb, full, replyCount = 1)

    @Test fun registeringAndComparingTheNextCatalogUseTheSameConfirmedOriginalHash() = runBlocking {
        val payload = picture()
        val prefix = "https://example.invalid/catalog-recovery-${System.nanoTime()}"
        val requests = mutableListOf<String>()
        var rule: CompatNgRule? = null
        val store = Proxy.newProxyInstance(CompatibilityStore::class.java.classLoader,
            arrayOf(CompatibilityStore::class.java)) { _, method, args ->
            if (method.name == "upsertNgRule") { rule = args[0] as CompatNgRule; true } else error(method.name)
        } as CompatibilityStore
        HttpClient(MockEngine { request ->
            val url = request.url.toString(); requests += url
            if (url.endsWith(".jpg")) respondError(HttpStatusCode.NotFound)
            else respond(payload, headers = headersOf(HttpHeaders.ContentType, "image/png"))
        }).use { client ->
            registerFutachaImageNgRule(store, CompatImageNgSource.CATALOG, "board", "$prefix/thumb/s.jpg", "fixture", false,
                fetchHash = { fetchCompatImagePhash(client, "$prefix/old.jpg") { "$prefix/old.png" }.getOrThrow() })
            // A different URL forces the actual recovery in the catalog worker instead of merely hitting the register cache.
            val hashes = collectCompatImagePhashes(client, listOf("next" to "$prefix/next.jpg"),
                resolveMissingUrl = { id -> assertEquals("next", id); "$prefix/next.png" })
            assertEquals(rule!!.normalizedValue, hashes["next"])
            assertTrue(CompatImagePhash.isSimilar(hashes.getValue("next"), rule!!.normalizedValue, 0))
            assertEquals(listOf("$prefix/old.jpg", "$prefix/old.png", "$prefix/next.jpg", "$prefix/next.png"), requests)
        }
    }

    @Test fun normalImagesAndServerFailuresNeverTriggerOriginalResolution() = runBlocking {
        var resolutions = 0
        val prefix = "https://example.invalid/catalog-errors-${System.nanoTime()}"
        HttpClient(MockEngine { request ->
            if (request.url.encodedPath.endsWith("bad.jpg")) respondError(HttpStatusCode.InternalServerError)
            else respond(picture(), headers = headersOf(HttpHeaders.ContentType, "image/png"))
        }).use { client ->
            assertTrue(fetchCompatImagePhash(client, "$prefix/good.png") { resolutions++; null }.isSuccess)
            assertTrue(fetchCompatImagePhash(client, "$prefix/bad.jpg") { resolutions++; "$prefix/good.png" }.isFailure)
            assertEquals(0, resolutions)
        }
    }

    @Test fun failedResolutionDoesNotRecurseOrPretendRegistrationSucceeded() = runBlocking {
        var resolutions = 0; var requests = 0
        HttpClient(MockEngine { requests++; respondError(HttpStatusCode.Gone) }).use { client ->
            val url = "https://example.invalid/catalog-gone-${System.nanoTime()}.jpg"
            assertTrue(fetchCompatImagePhash(client, url) { resolutions++; url }.isFailure)
            assertEquals(1, resolutions); assertEquals(1, requests)
        }
    }

    @Test fun videosUseTheThumbnailForBothRegistrationAndComparison() = runBlocking {
        val media = item("https://example.invalid/src/123.webm", "https://example.invalid/thumb/123s.jpg")
        assertEquals(media.thumbnailUrl, compatCatalogPhashRequestUrl(media))
        assertEquals(media.thumbnailUrl, resolveCompatCatalogPhashUrl(media.thumbnailUrl) { media.fullImageUrl })
        val png = media.copy(fullImageUrl = "https://example.invalid/src/123.png")
        assertEquals(png.fullImageUrl, compatCatalogPhashRequestUrl(png))
        assertEquals(png.fullImageUrl, resolveCompatCatalogPhashUrl(png.thumbnailUrl) { png.fullImageUrl })
    }

    @Test fun aGoneThreadCanUseItsSurvivingThumbnailButOtherFailuresRemainFailures(): Unit = runBlocking {
        assertEquals("thumb", resolveCompatCatalogPhashUrl("thumb") { throw NetworkException("gone", statusCode = 404) })
        assertFailsWith<NetworkException> { resolveCompatCatalogPhashUrl("thumb") { throw NetworkException("server", statusCode = 500) } }
        assertFailsWith<CancellationException> { resolveCompatCatalogPhashUrl("thumb") { throw CancellationException("cancelled") } }
    }

    @Test fun cancellationDuringRecoveryReleasesTheHashRequestLock() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        HttpClient(MockEngine { respondError(HttpStatusCode.NotFound) }).use { client ->
            val job = launch { fetchCompatImagePhash(client, "https://example.invalid/catalog-cancel-${System.nanoTime()}.jpg") {
                entered.complete(Unit); awaitCancellation()
            } }
            entered.await(); job.cancelAndJoin()
            assertEquals(0, compatPhashRequestLockCountForTest())
        }
    }
}
