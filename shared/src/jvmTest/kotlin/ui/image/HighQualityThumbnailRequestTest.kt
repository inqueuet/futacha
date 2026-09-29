package com.valoser.futacha.shared.ui.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Precision
import com.valoser.futacha.shared.media.source.OriginalMediaInfo
import com.valoser.futacha.shared.media.source.OriginalMediaStore
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HighQualityThumbnailRequestTest {
    private val originalUrl = "https://img.2chan.net/b/src/1790640387139.png"

    private fun plan(allowNetwork: Boolean, animation: Boolean = false) = HighQualityThumbnailPlan(
        url = originalUrl,
        allowNetwork = allowNetwork,
        widthPx = 400,
        heightPx = 400,
        allowAnimation = animation
    )

    private fun png(size: Int): ByteArray {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until size) for (x in 0 until size) image.setRGB(x, y, (x * 255 / size) shl 16 or (y * 255 / size))
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    @Test
    fun requestNeverEnlargesAndSkipsGuessedFallbacks() {
        val cached = buildHighQualityThumbnailRequest(PlatformContext.INSTANCE, plan(allowNetwork = false))
        assertEquals(CachePolicy.DISABLED, cached.networkCachePolicy)
        assertEquals(Precision.INEXACT, cached.precision)
        assertEquals(0, cached.futabaExtensionFallbackPolicy().maxAttempts)
        val network = buildHighQualityThumbnailRequest(PlatformContext.INSTANCE, plan(allowNetwork = true))
        assertEquals(CachePolicy.ENABLED, network.networkCachePolicy)
    }

    @Test
    fun cachedModeShowsOnlyAnAlreadyDownloadedOriginalAndAnnouncesLaterDownloads(): Unit = runBlocking {
        val bytes = png(600)
        val downloads = AtomicInteger()
        val directory = Files.createTempDirectory("hq-thumbnail").toFile()
        val store = OriginalMediaStore("test", {
            DiskCache.Builder().directory(directory.path.toPath()).maxSizeBytes(4 * 1024 * 1024).build()
        }, { request, sink ->
            downloads.incrementAndGet()
            sink.write(bytes)
            OriginalMediaInfo("image/png", bytes.size.toLong(), resolvedUrl = request.url)
        }, Dispatchers.IO)
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).diskCache(null).memoryCache(null).components {
            addOriginalMediaSupport(store)
            addPlatformImageComponents()
        }.build()
        try {
            // Nothing cached yet: the thumbnail stays and nothing is downloaded.
            val miss = loader.execute(buildHighQualityThumbnailRequest(PlatformContext.INSTANCE, plan(allowNetwork = false)))
            assertIs<ErrorResult>(miss)
            assertEquals(0, downloads.get())

            // The viewer (or a save) downloads the original through the same store,
            // which announces it so a waiting thumbnail slot can retry.
            val announced = async(start = CoroutineStart.UNDISPATCHED) { store.persistedUrls.first() }
            assertIs<SuccessResult>(loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(originalUrl).build()))
            assertEquals(1, downloads.get())
            assertEquals(originalUrl, withTimeout(5000) { announced.await() })

            val hit = assertIs<SuccessResult>(
                loader.execute(buildHighQualityThumbnailRequest(PlatformContext.INSTANCE, plan(allowNetwork = false)))
            )
            assertEquals(1, downloads.get())
            assertTrue(hit.image.width in 1..400, "decoded ${hit.image.width}px for a 400px slot")
            assertEquals(hit.image.width, hit.image.height)
        } finally {
            loader.shutdown()
            withTimeout(5000) { store.closeAndAwait() }
            directory.deleteRecursively()
        }
    }

    @Test
    fun networkModeDownloadsOnceIntoTheSharedStore(): Unit = runBlocking {
        val bytes = png(300)
        val downloads = AtomicInteger()
        val directory = Files.createTempDirectory("hq-thumbnail-network").toFile()
        val store = OriginalMediaStore("test", {
            DiskCache.Builder().directory(directory.path.toPath()).maxSizeBytes(4 * 1024 * 1024).build()
        }, { request, sink ->
            downloads.incrementAndGet()
            sink.write(bytes)
            OriginalMediaInfo("image/png", bytes.size.toLong(), resolvedUrl = request.url)
        }, Dispatchers.IO)
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).diskCache(null).memoryCache(null).components {
            addOriginalMediaSupport(store)
            addPlatformImageComponents()
        }.build()
        try {
            val result = assertIs<SuccessResult>(
                loader.execute(buildHighQualityThumbnailRequest(PlatformContext.INSTANCE, plan(allowNetwork = true)))
            )
            // INEXACT keeps a 300px original at 300px for a 400px slot.
            assertEquals(300, result.image.width)
            assertEquals(1, downloads.get())
            // The viewer reuses the stored file instead of downloading again.
            assertIs<SuccessResult>(loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(originalUrl).build()))
            assertEquals(1, downloads.get())
        } finally {
            loader.shutdown()
            withTimeout(5000) { store.closeAndAwait() }
            directory.deleteRecursively()
        }
    }
}
