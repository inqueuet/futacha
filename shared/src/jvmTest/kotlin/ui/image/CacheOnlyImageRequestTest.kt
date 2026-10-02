package com.valoser.futacha.shared.ui.image

import coil3.PlatformContext
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.network.ktor3.asNetworkClient
import com.valoser.futacha.shared.media.source.OriginalMediaNotCached
import com.valoser.futacha.shared.media.source.originalMediaRequestFromUrl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.measureTime

class CacheOnlyImageRequestTest {
    private fun loader(client: HttpClient) = buildFutachaImageLoader(
        platformContext = PlatformContext.INSTANCE,
        fetcherDispatcher = Dispatchers.IO,
        decoderDispatcher = Dispatchers.Default,
        memoryCache = MemoryCache.Builder().maxSizeBytes(1024 * 1024L).build(),
        diskCache = null,
        pressureGate = AdaptiveImageRequestGate(ImageMemoryPressurePolicy(ImageMemoryPressureLevel.NORMAL, 2, 1024 * 1024L, 1)),
        imageHttpClient = client
    )

    @Test fun cacheOnlyMissNeverReachesTheHttpEngineAndIsNotRetried() = runBlocking {
        val requests = mutableListOf<String>()
        // Darwin ignores only-if-cached; this engine downloads whatever it is asked.
        val client = HttpClient(MockEngine { request ->
            requests += request.url.toString()
            respond("image", HttpStatusCode.OK)
        })
        val loader = loader(client)
        try {
            lateinit var result: coil3.request.ImageResult
            val elapsed = measureTime {
                result = loader.execute(
                    ImageRequest.Builder(PlatformContext.INSTANCE).data("https://may.2chan.net/b/thumb/1s.jpg")
                        .networkCachePolicy(CachePolicy.DISABLED).build()
                )
            }
            val error = assertIs<ErrorResult>(result)
            assertIs<ImageNotCachedException>(error.throwable)
            assertEquals(emptyList(), requests)
            // The retry strategy waits at least a second before a retry.
            assertTrue(elapsed.inWholeMilliseconds < 1_000, "cache-only miss took $elapsed")
            assertFalse(isMissingImage(error.throwable))
        } finally { loader.shutdown(); client.close() }
    }

    @Test fun deadArchiveImageIsNotSearchedAgainUntilExplicitReloadOrExpiry() = runBlocking {
        val futapo = "https://kako.futakuro.com/futa/may_b/456/123.jpg"
        val requests = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            requests += request.url.toString()
            respond("missing", HttpStatusCode.NotFound)
        })
        val now = AtomicLong(1_000L)
        val interceptor = ArchiveImageFallbackInterceptor(nowMillis = { now.get() })
        val loader = coil3.ImageLoader.Builder(PlatformContext.INSTANCE).diskCache(null).memoryCache(null).components {
            add(ImageRefreshInterceptor())
            add(interceptor)
            add(coil3.network.NetworkFetcher.Factory(
                networkClient = { CacheOnlyAwareNetworkClient(client.asNetworkClient()) }
            ))
        }.build()
        val mirrors = archiveImageFallbackCandidates(futapo)
        assertTrue(mirrors.isNotEmpty())
        try {
            suspend fun load(refresh: Long = 0L) = loader.execute(
                ImageRequest.Builder(PlatformContext.INSTANCE).data(futapo).refreshImageOnce(refresh).build()
            )
            assertIs<ErrorResult>(load())
            assertTrue(requests.containsAll(mirrors))
            requests.clear()

            assertIs<ErrorResult>(load())
            assertEquals(listOf(futapo), requests, "a known-dead image must not probe its mirrors again")
            requests.clear()

            assertIs<ErrorResult>(load(refresh = 7L))
            assertTrue(requests.containsAll(mirrors), "an explicit reload searches again")
            requests.clear()

            assertIs<ErrorResult>(load(refresh = 7L))
            assertEquals(listOf(futapo), requests, "recomposition must not repeat a failed explicit mirror search")
            requests.clear()

            now.addAndGet(ARCHIVE_IMAGE_FALLBACK_MISSING_TTL_MILLIS + 1)
            assertIs<ErrorResult>(load())
            assertTrue(requests.containsAll(mirrors), "the search resumes after the negative entry expires")
        } finally { loader.shutdown(); client.close() }
    }

    @Test fun viewerReloadFragmentBecomesAReloadTokenOfTheSameOriginal() {
        val request = originalMediaRequestFromUrl("https://img.2chan.net/b/src/1.png#compat-reload=42")
        assertEquals("https://img.2chan.net/b/src/1.png", request.url)
        assertEquals(42L, request.reloadToken)
        assertEquals(
            originalMediaRequestFromUrl("https://img.2chan.net/b/src/1.png").cacheKey(),
            request.cacheKey(),
            "a reload must not store a second copy under another key"
        )
    }

    @Test fun onlyInterruptedHighQualityLoadsAreRetried() {
        assertFalse(isTransientHighQualityFailure(null))
        assertFalse(isTransientHighQualityFailure(OriginalMediaNotCached()))
        assertFalse(isTransientHighQualityFailure(ImageNotCachedException()))
        assertFalse(isTransientHighQualityFailure(IllegalStateException("wrapped", OriginalMediaNotCached())))
        assertTrue(isTransientHighQualityFailure(okio.IOException("connection reset")))
    }
}
