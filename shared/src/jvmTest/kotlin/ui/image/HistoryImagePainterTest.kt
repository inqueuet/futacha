package com.valoser.futacha.shared.ui.image

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import coil3.ColorImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.intercept.Interceptor
import coil3.network.HttpException
import coil3.network.NetworkResponse
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.ui.board.HistoryThumbnailFailureCache
import kotlinx.coroutines.*
import kotlin.test.*

class HistoryImagePainterTest {
    private val url = "https://may.2chan.net/b/thumb/1s.jpg"

    @Test
    fun savedThumbnailDisplaysWithoutRemoteRequestEvenWhenUrlIsKnownMissing() = runBlocking {
        val fs = InMemoryFileSystem()
        val repo = SavedThreadRepository(fs, "history")
        repo.addThreadToIndex(SavedThread(
            threadId = "123", boardId = "b", boardName = "b", title = "title", storageId = "current",
            thumbnailPath = "thumb/1.jpg", savedAt = 1, postCount = 1, imageCount = 1,
            videoCount = 0, totalSize = 1, status = SaveStatus.COMPLETED
        )).getOrThrow()
        fs.writeBytes("history/current/thumb/1.jpg", byteArrayOf(1)).getOrThrow()
        val missing = HistoryThumbnailFailureCache().also { it.recordMissing(url) }
        val requests = mutableListOf<String>()
        val loader = loader { request ->
            requests += request.data.toString()
            assertTrue(request.data.toString().startsWith("/virtual/"))
            success(request)
        }
        withPainter(loader, loader, listOf(repo), missing) {
            awaitSuccess()
            assertEquals(listOf("/virtual/history/current/thumb/1.jpg"), requests)
        }
    }

    @Test
    fun missingRemoteStillUsesCatalogEcoCacheWithoutNetwork() = runBlocking {
        val requests = mutableListOf<String>()
        val missing = HistoryThumbnailFailureCache().also { it.recordMissing(url) }
        val main = loader { request ->
            assertEquals(CachePolicy.DISABLED, request.networkCachePolicy)
            requests += "main:${request.data}"
            failure(request)
        }
        val catalog = loader { request ->
            assertEquals(CachePolicy.DISABLED, request.networkCachePolicy)
            requests += "catalog:${request.data}"
            if ("/cat/" in request.data.toString()) success(request) else failure(request)
        }
        withPainter(main, catalog, missing = missing) {
            awaitSuccess()
            assertEquals(listOf("main:$url", "catalog:$url", "catalog:${url.replace("/thumb/", "/cat/")}"), requests)
        }
    }

    @Test
    fun waitsForColdStartDiskCacheBeforeTreatingCachedImageAsMissing() = runBlocking {
        var earlyRequests = 0
        var cacheRequests = 0
        val initial = loader { request -> earlyRequests++; failure(request) }
        val stable = StableImageLoader(initial)
        val ready = loader { request ->
            cacheRequests++
            assertEquals(CachePolicy.DISABLED, request.networkCachePolicy)
            success(request)
        }
        withPainter(stable, stable) {
            repeat(5) {
                delay(10)
                Snapshot.sendApplyNotifications()
                clock.sendFrame(System.nanoTime())
            }
            assertEquals(0, earlyRequests)
            assertEquals(0, cacheRequests)
            stable.promote(ready)
            stable.finishDiskCacheInitialization()
            awaitSuccess()
            assertEquals(0, earlyRequests)
            assertEquals(1, cacheRequests)
        }
    }

    @Test
    fun expiredMissingUrlRetriesWhileCompositionRemainsMounted() = runBlocking {
        var networkRequests = 0
        val missing = HistoryThumbnailFailureCache(ttlMillis = 100L)
        val main = loader { request ->
            if (request.networkCachePolicy == CachePolicy.DISABLED) failure(request)
            else {
                networkRequests++
                if (networkRequests == 1) ErrorResult(null, request, HttpException(NetworkResponse(code = 404)))
                else success(request)
            }
        }
        withPainter(main, main, missing = missing) {
            awaitSuccess()
            assertEquals(2, networkRequests)
            assertFalse(missing.isKnownMissing(url))
        }
    }

    @Test
    fun transientFailureIsNotMarkedMissingAndNewUrlLoadsImmediately() = runBlocking {
        val requests = mutableListOf<String>()
        val missing = HistoryThumbnailFailureCache()
        val main = loader { request ->
            if (request.networkCachePolicy == CachePolicy.DISABLED) failure(request)
            else {
                requests += request.data.toString()
                if (request.data == url) failure(request) else success(request)
            }
        }
        withPainter(main, main, missing = missing) {
            await { requests.contains(url) && image?.state is AsyncImagePainter.State.Error }
            assertFalse(missing.isKnownMissing(url))
            thumbnail = "https://archive.example/thumb/new.jpg"
            awaitSuccess()
            assertEquals(listOf(url, thumbnail), requests)
        }
    }

    private fun loader(result: (ImageRequest) -> ImageResult): ImageLoader =
        ImageLoader.Builder(PlatformContext.INSTANCE).diskCache(null).components {
            add(Interceptor { chain -> result(chain.request) })
        }.build()

    private fun success(request: ImageRequest) = SuccessResult(ColorImage(0xFF00FF00.toInt(), 16, 16), request)
    private fun failure(request: ImageRequest) = ErrorResult(null, request, IllegalStateException("unavailable"))

    private inner class Harness(val clock: BroadcastFrameClock) {
        var thumbnail by mutableStateOf(url)
        var image: ViewerImagePainter? = null
        suspend fun awaitSuccess() = await { image?.state is AsyncImagePainter.State.Success }
        suspend fun await(predicate: () -> Boolean) = withTimeout(5_000) {
            while (!predicate()) {
                delay(10)
                Snapshot.sendApplyNotifications()
                clock.sendFrame(System.nanoTime())
            }
        }
    }

    private suspend fun withPainter(
        main: ImageLoader,
        catalog: ImageLoader,
        repositories: List<SavedThreadRepository> = emptyList(),
        missing: HistoryThumbnailFailureCache = HistoryThumbnailFailureCache(),
        block: suspend Harness.() -> Unit
    ) = coroutineScope {
        val clock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + clock)
        val job = launch(clock) { recomposer.runRecomposeAndApplyChanges() }
        val composition = Composition(object : AbstractApplier<Unit>(Unit) {
            override fun insertTopDown(index: Int, instance: Unit) {}
            override fun insertBottomUp(index: Int, instance: Unit) {}
            override fun remove(index: Int, count: Int) {}
            override fun move(from: Int, to: Int, count: Int) {}
            override fun onClear() {}
        }, recomposer)
        val harness = Harness(clock)
        try {
            composition.setContent {
                CompositionLocalProvider(
                    LocalFutachaImageLoader provides main,
                    LocalFutachaCatalogImageLoader provides catalog,
                    LocalHistoryImageRepositories provides repositories
                ) {
                    val image = rememberHistoryImagePainter("123", "b", "https://may.2chan.net/b/", harness.thumbnail, 48,
                        failureCache = missing, catalogImageLoader = catalog)
                    SideEffect { harness.image = image }
                }
            }
            harness.block()
        } finally {
            composition.dispose()
            recomposer.close()
            job.cancelAndJoin()
            main.shutdown()
            if (catalog !== main) catalog.shutdown()
        }
    }
}
