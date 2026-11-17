package com.valoser.futacha.shared.ui.compat

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatApngMarkerCacheTest {
    @Test
    fun staticPngResultSurvivesGalleryReopenWithoutAnotherLoad() = runBlocking {
        coroutineScope {
            val cache = CompatApngMarkerCache(this)
            var loadCount = 0
            val url = "https://dec.2chan.net/up2/src/fu7189334.png"

            val first = cache.getOrLoad(url) {
                loadCount += 1
                Result.success(false)
            }
            val reopened = cache.getOrLoad(url) {
                loadCount += 1
                error("cached static PNG must not be loaded again")
            }

            assertFalse(first.getOrThrow())
            assertFalse(reopened.getOrThrow())
            assertEquals(1, loadCount)
            assertEquals(1, cache.cachedEntryCount())
        }
    }

    @Test
    fun simultaneousBadgeRequestsShareOneRangeLoad() = runBlocking {
        coroutineScope {
            val cache = CompatApngMarkerCache(this)
            val loadStarted = CompletableDeferred<Unit>()
            val releaseLoad = CompletableDeferred<Unit>()
            var loadCount = 0
            val url = "https://example.test/animated.png"

            val first = async {
                cache.getOrLoad(url) {
                    loadCount += 1
                    loadStarted.complete(Unit)
                    releaseLoad.await()
                    Result.success(true)
                }
            }
            loadStarted.await()
            val second = async {
                cache.getOrLoad(url) {
                    loadCount += 1
                    Result.success(false)
                }
            }
            yield()
            releaseLoad.complete(Unit)

            assertTrue(first.await().getOrThrow())
            assertTrue(second.await().getOrThrow())
            assertEquals(1, loadCount)
        }
    }

    @Test
    fun failedLookupIsNotPermanentlyCached() = runBlocking {
        coroutineScope {
            val cache = CompatApngMarkerCache(this)
            val url = "https://example.test/retry.png"

            assertTrue(cache.getOrLoad(url) { Result.failure(IllegalStateException("offline")) }.isFailure)
            assertTrue(cache.getOrLoad(url) { Result.success(true) }.getOrThrow())
            assertEquals(true, cache.get(url))
        }
    }
}
