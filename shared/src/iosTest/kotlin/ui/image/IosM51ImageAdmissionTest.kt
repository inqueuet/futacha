package com.valoser.futacha.shared.ui.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.decode.Decoder
import coil3.decode.SkiaImageDecoder
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.size.Size
import com.valoser.futacha.shared.media.prompt.GenerationImageFixtures
import kotlinx.coroutines.*
import okio.Buffer
import kotlin.test.*

/** Real Coil memory cache, fetch and Skia decode; the stalled sources have not reached decode. */
class IosM51ImageAdmissionTest {
    @Test fun cachedVisibleImageDoesNotWaitForTwoSlowSources(): Unit = runBlocking(Dispatchers.Default) {
        verifyCachedVisibleImage(ImageMemoryPressureLevel.NORMAL)
    }

    @Test fun criticalPressureAlsoKeepsMemoryHitsAndSourceAcquisitionOutsideTheDecodeBudget(): Unit = runBlocking(Dispatchers.Default) {
        verifyCachedVisibleImage(ImageMemoryPressureLevel.CRITICAL)
    }

    private suspend fun verifyCachedVisibleImage(level: ImageMemoryPressureLevel): Unit = coroutineScope {
        val requestScope = this
        val started = List(2) { CompletableDeferred<Unit>() }
        val finishFetch = CompletableDeferred<Unit>()
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE)
            .mainCoroutineContext(Dispatchers.Default)
            .memoryCache(MemoryCache.Builder().maxSizeBytes(1024 * 1024).build())
            .diskCache(null)
            .components {
                add(platformImageMemoryPressureInterceptor(AdaptiveImageRequestGate(
                    resolveImageMemoryPressurePolicy(4, 1024L * 1024, null, level))))
                add(IosLowMemoryImageInterceptor(isLowRam = { true }, gate = LowMemoryImageRequestGate()))
                add(IosAdmittedDecoderFactory(SkiaImageDecoder.Factory()))
                add(object : Fetcher.Factory<Uri> {
                    override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
                        val path = data.toString()
                        if (!path.startsWith("m51://")) return null
                        return Fetcher {
                            path.substringAfterLast('/').toIntOrNull()?.let { index ->
                                started[index].complete(Unit)
                                finishFetch.await()
                            }
                            SourceFetchResult(ImageSource(Buffer().write(GenerationImageFixtures.jpeg), options.fileSystem),
                                "image/jpeg", DataSource.NETWORK)
                        }
                    }
                })
            }.build()
        fun request(path: String) = ImageRequest.Builder(PlatformContext.INSTANCE)
            .data("m51://$path").memoryCacheKey(path).size(24).build()
        val pending = mutableListOf<Deferred<*>>()
        try {
            assertIs<SuccessResult>(loader.execute(request("cached")))
            withTimeout(5000) {
                repeat(2) { index -> pending += requestScope.async { loader.execute(request("slow/$index")) } }
                started.forEach { it.await() }
                val hit = withTimeout(1500) { assertIs<SuccessResult>(loader.execute(request("cached"))) }
                assertEquals(DataSource.MEMORY_CACHE, hit.dataSource)
                assertTrue(pending.all { !it.isCompleted }, "A cache hit must finish while both fetches remain stalled")
            }
        } finally {
            pending.forEach { it.cancel() }
            finishFetch.complete(Unit)
            pending.forEach { it.join() }
            loader.shutdown()
        }
    }

    @Test fun actualViewerPrefetchYieldsToQueuedVisibleDecode(): Unit = runBlocking(Dispatchers.Default) {
        val created = List(3) { CompletableDeferred<Unit>() }
        val entered = List(3) { CompletableDeferred<Unit>() }
        val release = List(3) { CompletableDeferred<Unit>() }
        val sequence = kotlinx.coroutines.flow.MutableStateFlow<List<Int>>(emptyList())
        val decoder = Decoder.Factory { result, options, loader ->
            val id = (result.source.metadata as RequestId).id
            created[id].complete(Unit)
            val delegate = SkiaImageDecoder.Factory().create(result, options, loader)!!
            Decoder {
                sequence.value = sequence.value + id
                entered[id].complete(Unit)
                release[id].await()
                delegate.decode()
            }
        }
        val decodeDispatcher = Dispatchers.Default.limitedParallelism(1)
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).mainCoroutineContext(Dispatchers.Default)
            .decoderCoroutineContext(decodeDispatcher)
            .diskCache(null).memoryCache(null).components {
                add(IosLowMemoryImageInterceptor({ true }, LowMemoryImageRequestGate()))
                add(IosAdmittedDecoderFactory(SkiaImageDecoder.Factory()))
                add(object : Fetcher.Factory<Uri> {
                    override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
                        if (!data.toString().startsWith("m51://")) return null
                        val id = data.toString().substringAfterLast('/').toInt()
                        return Fetcher {
                            SourceFetchResult(ImageSource(Buffer().write(GenerationImageFixtures.jpeg), options.fileSystem,
                                metadata = RequestId(id)), "image/jpeg", DataSource.NETWORK)
                        }
                    }
                })
            }.build()
        fun request(id: Int) = ImageRequest.Builder(PlatformContext.INSTANCE).data("m51://decode/$id")
            .size(Size.ORIGINAL).decoderFactory(decoder).build()
        val prefetcher = ImagePrefetcher(loader)
        var first: Deferred<*>? = null
        var visible: Deferred<*>? = null
        try {
            withTimeout(10000) {
                first = async { assertIs<SuccessResult>(loader.execute(request(0))) }
                entered[0].await()
                // The production ImagePrefetcher adds the same mark used by the viewer.
                prefetcher.update(listOf(request(1)), currentUrl = null)
                created[1].await()
                withContext(decodeDispatcher) { yield() }
                visible = async { assertIs<SuccessResult>(loader.execute(request(2))) }
                created[2].await()
                withContext(decodeDispatcher) { yield() }
                assertFalse(entered[1].isCompleted)
                release[0].complete(Unit)
                entered[2].await()
                assertEquals(listOf(0, 2), sequence.value)
                assertFalse(entered[1].isCompleted, "A queued prefetch must not reserve the heavy budget")
                release[2].complete(Unit)
                entered[1].await()
                assertEquals(listOf(0, 2, 1), sequence.value)
                release[1].complete(Unit)
                first!!.await(); visible!!.await()
            }
        } finally {
            prefetcher.close()
            first?.cancel(); visible?.cancel()
            release.forEach { it.complete(Unit) }
            first?.join(); visible?.join()
            loader.shutdown()
        }
    }

    @Test fun nativeLoaderMaintainsTwoDecodeSlotsAndCriticalOneWithoutLeakingOnCancellation(): Unit = runBlocking(Dispatchers.Default) {
        val requestScope = this
        for ((level, limit) in listOf(ImageMemoryPressureLevel.NORMAL to 2, ImageMemoryPressureLevel.CRITICAL to 1)) {
            val never = CompletableDeferred<Unit>()
            val created = List(3) { CompletableDeferred<Unit>() }
            val entered = List(3) { CompletableDeferred<Unit>() }
            val decodeDispatcher = Dispatchers.Default.limitedParallelism(1)
            val decoder = Decoder.Factory { result, options, loader ->
                val id = (result.source.metadata as RequestId).id
                created[id].complete(Unit)
                val delegate = SkiaImageDecoder.Factory().create(result, options, loader)!!
                Decoder { entered[id].complete(Unit); never.await(); delegate.decode() }
            }
            val loader = ImageLoader.Builder(PlatformContext.INSTANCE).mainCoroutineContext(Dispatchers.Default)
                .decoderCoroutineContext(decodeDispatcher).diskCache(null).memoryCache(null).components {
                    add(platformImageMemoryPressureInterceptor(AdaptiveImageRequestGate(
                        resolveImageMemoryPressurePolicy(4, 1024L * 1024, null, level))))
                    add(IosLowMemoryImageInterceptor({ true }, LowMemoryImageRequestGate()))
                    // Exercise both the registered default and the per-request factory paths.
                    add(IosAdmittedDecoderFactory(decoder))
                    add(object : Fetcher.Factory<Uri> {
                        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
                            if (!data.toString().startsWith("m51://")) return null
                            val id = data.toString().substringAfterLast('/').toInt()
                            return Fetcher {
                                SourceFetchResult(ImageSource(Buffer().write(GenerationImageFixtures.jpeg), options.fileSystem,
                                    metadata = RequestId(id)), "image/jpeg", DataSource.NETWORK)
                            }
                        }
                    })
                }.build()
            val jobs = mutableListOf<Deferred<*>>()
            try {
                withTimeout(10000) {
                    repeat(3) { id ->
                        val request = ImageRequest.Builder(PlatformContext.INSTANCE).data("m51://bounded/$id").size(24)
                            .apply { if (id == 1) decoderFactory(decoder) }.build()
                        jobs += requestScope.async { loader.execute(request) }
                        created[id].await()
                        withContext(decodeDispatcher) { yield() }
                    }
                    assertEquals(limit, entered.count { it.isCompleted }, "$level decode ceiling")
                    jobs.first().cancelAndJoin()
                    entered[limit].await()
                    assertEquals(limit + 1, entered.count { it.isCompleted }, "Cancellation must return the complete budget")
                }
            } finally {
                jobs.forEach { it.cancel() }; never.complete(Unit); jobs.forEach { it.join() }
                loader.shutdown()
            }
        }
    }

    private class RequestId(val id: Int) : ImageSource.Metadata()

}
