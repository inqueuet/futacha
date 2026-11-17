package com.valoser.futacha.shared.ui.image

import coil3.PlatformContext
import coil3.memory.MemoryCache
import coil3.request.*
import com.valoser.futacha.shared.ui.compat.fetchCompatArchiveThreadPage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.*

class ArchiveImageFallbackTest {
    private val forest = "https://futabaforest.net/b/src/123.jpg"
    private val futapo = "https://kako.futakuro.com/futa/may_b/456/123.jpg"
    private val bytes = ByteArrayOutputStream().apply {
        ImageIO.write(BufferedImage(32, 24, BufferedImage.TYPE_INT_RGB).apply {
            for (x in 0 until width) for (y in 0 until height) setRGB(x, y, 0x33cc55)
        }, "png", this)
    }.toByteArray()

    private fun loader(client: HttpClient) = buildFutachaImageLoader(
        platformContext = PlatformContext.INSTANCE,
        fetcherDispatcher = Dispatchers.IO,
        decoderDispatcher = Dispatchers.Default,
        memoryCache = MemoryCache.Builder().maxSizeBytes(1024 * 1024L).build(),
        diskCache = null,
        pressureGate = AdaptiveImageRequestGate(ImageMemoryPressurePolicy(ImageMemoryPressureLevel.NORMAL, 2, 1024 * 1024L, 1)),
        imageHttpClient = client
    )

    @Test fun futapoPlaceholderIsRecoveredAsDecodedImageFromForest() = runBlocking {
        val requests = mutableListOf<String>()
        val source = "https://kako.futakuro.com/futa/may_b/456/"
        val client = HttpClient(MockEngine { request ->
            requests += request.url.toString()
            when (request.url.toString()) {
                source -> respond("""<div class="thre"><span class="cno">No.456</span>
                    <a href="$futapo">123.jpg</a><a href="http://kako.futakuro.com/futa/404.png">
                    <img src="http://kako.futakuro.com/futa/404s.png"></a><blockquote>OP</blockquote></div>""")
                futapo -> respond("missing", HttpStatusCode.NotFound)
                forest -> respond(bytes, headers = headersOf("Content-Type", "image/png"))
                else -> error("Unexpected request: ${request.url}")
            }
        })
        val loader = loader(client)
        try {
            val post = fetchCompatArchiveThreadPage(client, source).posts.single()
            for (url in listOf(post.thumbnailUrl, post.imageUrl)) {
                val result = loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(url).build())
                assertIs<SuccessResult>(result, (result as? ErrorResult)?.throwable.toString())
                assertEquals(32, result.image.width)
                assertEquals(24, result.image.height)
            }
            assertTrue(forest in requests)
            assertFalse(requests.any { "/404" in it })
        } finally { loader.shutdown(); client.close() }
    }

    @Test fun forestMissingThumbnailUsesSameThumbnailOnArchive() = runBlocking {
        val requests = mutableListOf<String>()
        val thumb = forest.replace("/src/123.jpg", "/thumb/123s.jpg")
        val target = "https://may.inqueuet.com/b/thumb/123s.jpg"
        val client = HttpClient(MockEngine { request ->
            requests += request.url.toString()
            if (request.url.toString() == target) respond(bytes)
            else respond("gone", HttpStatusCode.Gone)
        })
        val loader = loader(client)
        try {
            val result = loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(thumb).build())
            assertIs<SuccessResult>(result)
            assertEquals(listOf(thumb, target), requests)
        } finally { loader.shutdown(); client.close() }
    }

    @Test fun retainedLiveImageUrlAlsoRecoversAfterThreadHasDisappeared() = runBlocking {
        val requests = mutableListOf<String>()
        val live = "https://may.2chan.net/b/src/123.jpg"
        val client = HttpClient(MockEngine { request ->
            requests += request.url.toString()
            if (request.url.toString() == forest) respond(bytes)
            else respond("missing", HttpStatusCode.NotFound)
        })
        val loader = loader(client)
        try {
            val result = loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(live).build())
            assertIs<SuccessResult>(result)
            assertEquals(live, requests.first())
            assertEquals(forest, requests.last())
            assertEquals(listOf(live, forest), requests)
        } finally { loader.shutdown(); client.close() }
    }

    @Test fun liveExtensionRecoveryStillWorksWithoutRepeatingArchiveSearch() = runBlocking {
        val requests = mutableListOf<String>()
        val live = "https://may.2chan.net/b/src/123.jpg"
        val png = live.replace(".jpg", ".png")
        val client = HttpClient(MockEngine { request ->
            requests += request.url.toString()
            if (request.url.toString() == png) respond(bytes)
            else respond("missing", HttpStatusCode.NotFound)
        })
        val loader = loader(client)
        try {
            val result = loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(live)
                .futabaExtensionFallbackPolicy(FutabaExtensionFallbackPolicy(allowVideoFallback = false)).build())
            assertIs<SuccessResult>(result)
            assertEquals(png, requests.last())
            assertEquals(archiveImageFallbackCandidates(live), requests.filterNot { "may.2chan.net" in it })
        } finally { loader.shutdown(); client.close() }
    }

    @Test fun nonMissingFailuresDoNotProbeMirrors() = runBlocking {
        for (status in listOf(HttpStatusCode.Forbidden, HttpStatusCode.InternalServerError)) {
            val requests = mutableListOf<String>()
            val client = HttpClient(MockEngine { request ->
                requests += request.url.toString()
                respond("failure", status)
            })
            val loader = loader(client)
            try {
                assertIs<ErrorResult>(loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(forest).build()))
                // The transport may retry a 500 at the original URL.
                assertEquals(listOf(forest), requests.distinct())
            } finally { loader.shutdown(); client.close() }
        }
    }

    @Test fun cancellationStopsMirrorRequest() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine { request ->
            if (request.url.host == "kako.futakuro.com") respond("missing", HttpStatusCode.NotFound)
            else {
                started.complete(Unit)
                try { awaitCancellation() } finally { cancelled.complete(Unit) }
            }
        })
        val loader = loader(client)
        try {
            val job = launch { loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(futapo).build()) }
            withTimeout(3_000) { started.await() }
            job.cancelAndJoin()
            withTimeout(3_000) { cancelled.await() }
        } finally { loader.shutdown(); client.close() }
    }

    @Test fun missingOnEveryMirrorReturnsErrorWithoutExtensionFanOut() = runBlocking {
        val requests = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            requests += request.url.toString()
            respond("missing", HttpStatusCode.NotFound)
        })
        val loader = loader(client)
        try {
            assertIs<ErrorResult>(loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(futapo).build()))
            assertEquals(listOf(futapo) + archiveImageFallbackCandidates(futapo), requests)
        } finally { loader.shutdown(); client.close() }
    }

    @Test fun candidateMappingsKeepBoardFileAndThumbnailAndExcludeUnrelatedUrls() {
        assertEquals(listOf(forest, "https://may.inqueuet.com/b/src/123.jpg",
            "https://dev2.ftbucket.info/scdev2/cont/may.2chan.net_b_res_456/img/123.jpg"), archiveImageFallbackCandidates(futapo))
        assertEquals(listOf("https://img.inqueuet.com/b/thumb/123s.jpg"),
            archiveImageFallbackCandidates("https://dev2.ftbucket.info/scdev2/cont/img.2chan.net_b_res_456/thumb/123s.jpg"))
        for (url in listOf("https://example.com/b/src/123.jpg", "$forest?token=secret",
            "https://futabaforest.net.evil.test/b/src/123.jpg", forest.replace("jpg", "webm"),
            "https://kako.futakuro.com/futa/404.png", "file:///b/src/123.jpg")) {
            assertEquals(emptyList(), archiveImageFallbackCandidates(url), url)
        }
    }
}
