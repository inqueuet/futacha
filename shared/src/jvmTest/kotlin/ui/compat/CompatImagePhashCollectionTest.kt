package com.valoser.futacha.shared.ui.compat

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import com.valoser.futacha.shared.compat.*
import kotlinx.coroutines.*
import java.lang.reflect.Proxy
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class CompatImagePhashCollectionTest {
    private val png: ByteArray = ByteArrayOutputStream().also { out ->
        val image = BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until 32) for (y in 0 until 32) image.setRGB(x, y, if ((x / 4 + y / 4) % 2 == 0) 0xFFFFFF else 0x000000)
        ImageIO.write(image, "png", out)
    }.toByteArray()

    private fun client(slowPaths: Set<String> = emptySet()) = HttpClient(MockEngine { request ->
        if (request.url.encodedPath.substringAfterLast('/') in slowPaths) delay(5_000)
        respond(png, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Image.PNG.toString()))
    })

    /** Unique URLs per test: fetched hashes are cached process-wide by URL. */
    private fun candidates(count: Int): List<Pair<String, String>> {
        val run = Random.nextLong().toULong().toString(16)
        return (1..count).map { "$it" to "https://img.test/$run/$it.png" }
    }

    @Test fun cancellingThreadHashingPersistsCompletedResultsBelowTheUiBatchSize() = runBlocking {
        val secondEntered = CompletableDeferred<Unit>()
        val urls = candidates(2)
        val saved = linkedMapOf<String, String>()
        val store = Proxy.newProxyInstance(CompatibilityStore::class.java.classLoader,
            arrayOf(CompatibilityStore::class.java)) { _, method, arguments ->
            when (method.name) {
                "loadImagePhashes" -> emptyMap<String, String>()
                "saveImagePhashes" -> { saved.putAll(arguments[0] as Map<String, String>); Unit }
                else -> error("Unexpected store method: ${method.name}")
            }
        } as CompatibilityStore
        val http = HttpClient(MockEngine { request ->
            if (request.url.toString() == urls[1].second) { secondEntered.complete(Unit); awaitCancellation() }
            respond(png, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/png"))
        })
        try {
            val posts = urls.mapIndexed { index, (id, url) -> CompatPostSnapshot(index, id, timestamp = "", messageHtml = "", imageUrl = url) }
            // Sequential on purpose: with several slots the hang of the second image could be
            // entered before the first one is decoded (the concurrent case is covered below).
            val job = launch { collectCompatThreadImagePhashes(http, store, posts, concurrency = 1) {} }
            withTimeout(10_000) { secondEntered.await() }
            job.cancelAndJoin()
            assertTrue(saved.containsKey(compatImagePhashCachePreferenceKey(urls[0].second)))
            assertEquals(1, saved.size)
        } finally { http.close() }
    }

    @Test
    fun partialResultsArePublishedInBatchesAndAtTheEnd() = runBlocking {
        val partials = mutableListOf<Int>()
        val http = client()
        try {
            val result = collectCompatImagePhashes(http, candidates(5), publishEvery = 2, onPartial = { partials += it.size })

            assertEquals(5, result.size)
            assertEquals(listOf(2, 4, 5), partials)
        } finally { http.close() }
    }

    @Test
    fun batchBudgetReturnsWhatWasFoundInsteadOfWaitingForEveryImage() = runBlocking {
        val http = client(slowPaths = setOf("3.png", "4.png", "5.png"))
        try {
            val result = withTimeout(3_000) {
                collectCompatImagePhashes(http, candidates(5), batchTimeoutMillis = 800, requestTimeoutMillis = 10_000)
            }

            assertEquals(setOf("1", "2"), result.keys)
        } finally { http.close() }
    }

    @Test
    fun slowImagesAreSkippedByTheRequestLimit() = runBlocking {
        val http = client(slowPaths = setOf("2.png"))
        try {
            // Initialize the HTTP/decoder/hash path before measuring the mock
            // response delay. Use a different URL so the measured images still
            // go through fetching and decoding instead of the process-wide cache.
            assertEquals(1, collectCompatImagePhashes(http, candidates(1),
                requestTimeoutMillis = 10_000).size)
            val result = collectCompatImagePhashes(http, candidates(3), batchTimeoutMillis = 10_000, requestTimeoutMillis = 300)

            assertEquals(setOf("1", "3"), result.keys)
        } finally { http.close() }
    }

    private fun recordingStore(saved: MutableMap<String, String>, stored: Map<String, String> = emptyMap()) =
        Proxy.newProxyInstance(CompatibilityStore::class.java.classLoader,
            arrayOf(CompatibilityStore::class.java)) { _, method, arguments ->
            when (method.name) {
                "loadImagePhashes" -> (arguments[0] as Collection<String>).mapNotNull { key -> stored[key]?.let { key to it } }.toMap()
                "saveImagePhashes" -> { saved.putAll(arguments[0] as Map<String, String>); Unit }
                else -> error("Unexpected store method: ${method.name}")
            }
        } as CompatibilityStore

    @Test
    fun concurrentCollectionFetchesSeveralImagesAtOnceAndFindsTheSameHashesAsOneByOne() = runBlocking {
        // The engine's handlers run on several threads: the counters must be atomic.
        val inFlight = java.util.concurrent.atomic.AtomicInteger()
        val maxInFlight = java.util.concurrent.atomic.AtomicInteger()
        val http = HttpClient(MockEngine {
            val now = inFlight.incrementAndGet()
            maxInFlight.accumulateAndGet(now) { a, b -> maxOf(a, b) }
            try {
                delay(200)
                respond(png, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/png"))
            } finally { inFlight.decrementAndGet() }
        })
        try {
            val concurrent = candidates(6)
            val sequential = concurrent.map { (id, url) -> id to url.replace("/$id.png", "/s$id.png") }
            val a = collectCompatImagePhashes(http, concurrent, batchTimeoutMillis = 20_000)
            val concurrentMax = maxInFlight.get()
            val b = collectCompatImagePhashes(http, sequential, batchTimeoutMillis = 20_000, concurrency = 1)
            assertTrue(concurrentMax in 2..COMPAT_PHASH_FETCH_CONCURRENCY, "max in flight was $concurrentMax")
            assertEquals(6, a.size)
            assertEquals(b, a)
        } finally { http.close() }
    }

    @Test
    fun cancellingAConcurrentBatchStillHandsOverTheResultsThatAlreadyArrived() = runBlocking {
        val urls = candidates(3)
        val secondEntered = CompletableDeferred<Unit>()
        val http = HttpClient(MockEngine { request ->
            if (request.url.toString() != urls[0].second) { secondEntered.complete(Unit); awaitCancellation() }
            respond(png, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/png"))
        })
        try {
            // Warm the process-wide hash cache so the first image completes without a suspension.
            assertTrue(fetchCompatImagePhash(http, urls[0].second).isSuccess)
            val got = linkedMapOf<String, String>()
            val job = launch { collectCompatImagePhashes(http, urls, onComputed = { id, hash -> got[id] = hash }) }
            withTimeout(10_000) { secondEntered.await() }
            job.cancelAndJoin()
            assertEquals(setOf("1"), got.keys)
        } finally { http.close() }
    }

    @Test
    fun catalogStyleCollectionRunsConcurrentlyAndHandsEachResultToASuspendingSaver() = runBlocking {
        // Mirrors the Toshiaki catalog loop: concurrent fetch, results arrive one at a time
        // in the calling coroutine and are saved in batches by a suspending callback.
        val inFlight = java.util.concurrent.atomic.AtomicInteger()
        val maxInFlight = java.util.concurrent.atomic.AtomicInteger()
        val http = HttpClient(MockEngine {
            val now = inFlight.incrementAndGet()
            maxInFlight.accumulateAndGet(now) { a, b -> maxOf(a, b) }
            try {
                delay(200)
                respond(png, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/png"))
            } finally { inFlight.decrementAndGet() }
        })
        try {
            val missing = candidates(6).map { (id, url) -> id to url.replace("/$id.png", "/c$id.png") }
            val urlById = missing.toMap()
            val computed = linkedMapOf<String, String>()
            val unsaved = linkedMapOf<String, String>()
            val saved = linkedMapOf<String, String>()
            val progress = mutableListOf<Int>()
            collectCompatImagePhashesConcurrently(
                httpClient = http,
                candidates = missing,
                batchTimeoutMillis = 20_000,
                publishEvery = 1,
                onComputed = { id, phash ->
                    computed[id] = phash
                    unsaved[compatImagePhashCachePreferenceKey(urlById.getValue(id))] = phash
                    if (unsaved.size >= 2) { delay(1); saved.putAll(unsaved); unsaved.clear() }
                },
                onPublish = { progress += computed.size }
            )
            saved.putAll(unsaved)
            assertTrue(maxInFlight.get() in 2..COMPAT_PHASH_FETCH_CONCURRENCY, "max in flight was ${maxInFlight.get()}")
            assertEquals(6, computed.size)
            assertEquals(urlById.values.map(::compatImagePhashCachePreferenceKey).toSet(), saved.keys)
            assertEquals(6, progress.last())
        } finally { http.close() }
    }

    @Test
    fun hiddenPostCollectionPersistsNewHashesAndReusesPersistedOnes() = runBlocking {
        val http = client()
        try {
            val urls = candidates(3)
            val posts = urls.mapIndexed { index, (id, url) -> CompatPostSnapshot(index, id, timestamp = "", messageHtml = "", imageUrl = url) }
            val phash = fetchCompatImagePhash(http, urls[0].second).getOrThrow()
            val rule = CompatNgRule(id = "r", kind = CompatNgKind.THREAD_IMAGE_PHASH, scopeKey = "",
                normalizedValue = phash, createdAtEpochMillis = 0L)
            val saved = linkedMapOf<String, String>()
            val hidden = compatImagePhashHiddenPostNos(http, posts, listOf(rule), 0, recordingStore(saved))
            assertEquals(setOf("1", "2", "3"), hidden)
            assertEquals(urls.map { compatImagePhashCachePreferenceKey(it.second) }.toSet(), saved.keys)

            // A second pass is served from the persisted hashes: no request reaches the network.
            val offline = HttpClient(MockEngine { error("must not fetch") })
            val stored = saved.toMap()
            val saved2 = linkedMapOf<String, String>()
            val again = compatImagePhashHiddenPostNos(offline, posts, listOf(rule), 0, recordingStore(saved2, stored))
            assertEquals(setOf("1", "2", "3"), again)
            assertTrue(saved2.isEmpty())
            offline.close()
        } finally { http.close() }
    }
}
