@file:OptIn(coil3.annotation.ExperimentalCoilApi::class)
package com.valoser.futacha.shared.ui.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.fetch.SourceFetchResult
import coil3.network.*
import coil3.request.Options
import coil3.toUri
import com.valoser.futacha.shared.media.source.*
import kotlinx.coroutines.*
import okio.Buffer
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.*

class OriginalMediaCacheSupportTest {
    @Test fun originalRoutingExcludesThumbnailsVideoPrivateHostsAndScripts() {
        assertTrue(isSharedOriginalImageUrl("https://may.2chan.net/b/src/123.png?version=2"))
        for (url in listOf("https://may.2chan.net/b/thumb/123s.jpg", "https://may.2chan.net/b/src/123.mp4",
            "https://may.2chan.net.evil.test/b/src/123.png", "https://private.test/src/123.png",
            "file:///src/123.png", "https://may.2chan.net/b/futaba.php?file=/src/123.png")) {
            assertFalse(isSharedOriginalImageUrl(url), url)
        }
    }

    @Test fun diskBudgetPreservesTotalForBothModesAndOddQuotas() {
        for (total in listOf(128L * 1024 * 1024, 256L * 1024 * 1024, 512L * 1024 * 1024, 107L)) {
            val budget = splitImageDiskBudget(total)
            assertEquals(total, budget.images + budget.originals)
            assertTrue(budget.images > 0 && budget.originals > 0)
        }
    }

    @Test fun legacyCoilOriginalIsImportedOfflineAndSharedCacheClearIsNotDuplicated(): Unit = runBlocking {
        val root = Files.createTempDirectory("original-migration").toFile()
        val legacy = DiskCache.Builder().directory(root.resolve("legacy").path.toPath()).maxSizeBytes(1024 * 1024).build()
        val oldLoader = ImageLoader.Builder(PlatformContext.INSTANCE).diskCache(legacy).build()
        val url = "https://may.2chan.net/b/src/123.png"
        var requests = 0
        val oldFetcher = NetworkFetcher.Factory(networkClient = { object : NetworkClient {
            override suspend fun <T> executeRequest(request: NetworkRequest, block: suspend (NetworkResponse) -> T): T {
                requests++
                return block(NetworkResponse(200, headers = NetworkHeaders.Builder().set("Content-Type", "image/png").build(),
                    body = NetworkResponseBody(Buffer().writeUtf8("original PNG bytes including metadata"))))
            }
        } })
        val session = OriginalMediaSession("test", { _, _ -> error("Original must be imported without HTTP") })
        val registration = session.registerCacheImporter(CoilOriginalCacheImporter(PlatformContext.INSTANCE, oldLoader))
        try {
            assertIs<SourceFetchResult>(oldFetcher.create(url.toUri(), Options(PlatformContext.INSTANCE), oldLoader)!!.fetch()).source.close()
            session.configure(OriginalMediaCacheConfiguration(root.resolve("originals").path.toPath(), 1024 * 1024))
            session.acquire(OriginalMediaRequest(url, allowNetwork = false)).use {
                assertTrue(it.fromCache)
                assertEquals("original PNG bytes including metadata", it.readAt(0, 100).decodeToString())
            }
            assertEquals(1, requests)
            var clears = 0
            val source = object : OriginalMediaSource by session {
                override suspend fun clear() { clears++; session.clear() }
            }
            val general = StableImageLoader(oldLoader, source)
            val catalog = StableImageLoader(ImageLoader.Builder(PlatformContext.INSTANCE).diskCache(null).build(), source)
            assertTrue(general.originalMediaCacheSizeBytes() > 0)
            clearFutachaImageCaches(general, catalog)
            assertEquals(1, clears)
            assertEquals(0, session.sizeBytes())
            assertEquals(0, legacy.size)
            catalog.shutdown()
        } finally {
            registration.close()
            withTimeout(5000) { session.closeAndAwait() }
            oldLoader.shutdown()
            root.deleteRecursively()
        }
    }

    @Test fun importerWaitsForColdDiskPromotionBeforeConsideringNetwork(): Unit = runBlocking {
        val stable = StableImageLoader(ImageLoader.Builder(PlatformContext.INSTANCE).diskCache(null).build())
        try {
            val importer = CoilOriginalCacheImporter(PlatformContext.INSTANCE, stable)
            val waiting = async(start = CoroutineStart.UNDISPATCHED) {
                importer.copyCached(OriginalMediaRequest("https://may.2chan.net/b/src/image.png"), Buffer())
            }
            assertFalse(waiting.isCompleted)
            stable.finishDiskCacheInitialization()
            assertNull(withTimeout(5000) { waiting.await() })
        } finally { stable.shutdown() }
    }
}
