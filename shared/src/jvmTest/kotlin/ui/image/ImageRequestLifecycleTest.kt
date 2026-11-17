package com.valoser.futacha.shared.ui.image

import coil3.ColorImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.*
import kotlinx.coroutines.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class ImageRequestLifecycleTest {
    @Test fun successfulManualRefreshBypassesOldDataOnceAndStillWritesCache(): Unit = runBlocking {
        val policies = mutableListOf<Pair<CachePolicy, CachePolicy>>()
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).diskCache(null).components {
            add(ImageRefreshInterceptor())
            add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                    policies += chain.request.memoryCachePolicy to chain.request.diskCachePolicy
                    return SuccessResult(ColorImage(0xff00ff00.toInt()), chain.request, DataSource.MEMORY)
                }
            })
        }.build()
        try {
            val request = ImageRequest.Builder(PlatformContext.INSTANCE).data("https://example.test/a.jpg").refreshImageOnce(42).build()
            repeat(3) { assertIs<SuccessResult>(loader.execute(request)) }
            assertEquals(listOf(CachePolicy.WRITE_ONLY to CachePolicy.WRITE_ONLY, CachePolicy.ENABLED to CachePolicy.ENABLED, CachePolicy.ENABLED to CachePolicy.ENABLED), policies)
            loader.execute(request.newBuilder().refreshImageOnce(43).build())
            assertEquals(CachePolicy.WRITE_ONLY to CachePolicy.WRITE_ONLY, policies.last())
        } finally { loader.shutdown() }
    }

    @Test fun prefetchIsBoundedAndVisibleOwnershipSurvivesClose(): Unit = runBlocking {
        val started = CopyOnWriteArrayList<String>()
        val canceled = CopyOnWriteArrayList<String>()
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).diskCache(null).components {
            add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                    val url = chain.request.data.toString()
                    started += url
                    val count = active.incrementAndGet()
                    maximum.accumulateAndGet(count, ::maxOf)
                    try { awaitCancellation() } finally { active.decrementAndGet(); canceled += url }
                }
            })
        }.build()
        val session = ImagePrefetcher(loader)
        val first = ImageRequest.Builder(PlatformContext.INSTANCE).data("https://example.test/1.jpg").size(1024).build()
        val second = first.newBuilder().data("https://example.test/2.jpg").build()
        try {
            session.update(listOf(first, second), null)
            withTimeout(3000) { while (started.isEmpty()) delay(10) }
            // Both adjacent candidates have equal priority; dispatcher ordering
            // must not determine whether the ownership regression test passes.
            assertEquals(1, started.size)
            val activeUrl = started.single()
            assertTrue(activeUrl in setOf(first.data.toString(), second.data.toString()))
            val visibleFinished = CompletableDeferred<Unit>()
            val visible = launch { VisibleImageRequests.track(activeUrl) { visibleFinished.await() } }
            yield()
            session.close()
            delay(100)
            assertTrue(canceled.isEmpty(), "Visible consumer must keep its in-flight download")
            visibleFinished.complete(Unit);visible.join()
            withTimeout(3000) { while (active.get() != 0) delay(10) }
            assertEquals(1, maximum.get())
            assertEquals(listOf(activeUrl), started.toList(), "Queued obsolete image must never start")
        } finally { session.close();loader.shutdown() }
    }
}
