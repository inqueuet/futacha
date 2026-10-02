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
            val job = launch { collectCompatThreadImagePhashes(http, store, posts) {} }
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
}
