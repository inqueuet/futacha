package com.valoser.futacha.shared.media.source

import coil3.disk.DiskCache
import com.valoser.futacha.shared.media.MediaFeatureGate
import com.valoser.futacha.shared.media.MediaFeatureSettings
import com.valoser.futacha.shared.media.prompt.GenerationImageFixtures
import com.valoser.futacha.shared.media.prompt.ImageGenerationMetadataReader
import com.valoser.futacha.shared.media.prompt.PromptMediaSource
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.service.*
import com.valoser.futacha.shared.util.withMediaSaveSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okio.FileSystem
import okio.IOException
import kotlin.random.Random
import kotlin.test.*

/** Real disk originals + public save APIs; runs on JVM, Android host and iOS Native. */
class OriginalMediaSaveIntegrationTest {
    private val url = "https://may.2chan.net/b/src/original.jpg"
    private val request get() = OriginalMediaRequest(url)

    private inner class Fixture(
        val payload: ByteArray = GenerationImageFixtures.jpeg,
        quota: Long = 1024 * 1024,
        beforeResponse: suspend () -> Unit = {},
        status: HttpStatusCode = HttpStatusCode.OK,
        cacheUnavailable: Boolean = false,
        val url: String = this@OriginalMediaSaveIntegrationTest.url,
        val mime: String = "image/jpeg"
    ) {
        val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("futacha-save-test-${Random.nextLong()}")
        val calls = MutableStateFlow(0)
        val output = InMemoryFileSystem()
        val client = HttpClient(MockEngine {
            calls.update { it + 1 }
            beforeResponse()
            respond(payload, status, headersOf(
                HttpHeaders.ContentType to listOf(mime),
                HttpHeaders.ContentLength to listOf(payload.size.toString())
            ))
        })
        private val downloader = KtorOriginalMediaDownloader(client, 1024 * 1024, waitBeforeRetry = { _, _ -> })
        val session = OriginalMediaSession("save-test", downloader, createCache = {
            if (cacheUnavailable) throw IOException("disk unavailable before HTTP")
            DiskCache.Builder().directory(it.directory).maxSizeBytes(it.maxBytes).build()
        })
        init {
            // This is also the headless application graph: no Compose or image loader.
            session.configureIfAbsent(OriginalMediaCacheConfiguration(directory, quota))
            client.bindOriginalMediaSource(session)
        }
        suspend fun single() = SingleMediaSaveService(client, output)
            .saveMedia(url, "b", "123", baseDirectory = "single").getOrThrow()
        suspend fun thread(base: String = "thread") = ThreadSaveService(client, output).saveThread(
            threadId = "123", boardId = "b", boardName = "may/b",
            boardUrl = "https://may.2chan.net/b/", title = "test", expiresAtLabel = null,
            posts = listOf(Post("123", author = null, subject = null, timestamp = "", messageHtml = "",
                imageUrl = url, thumbnailUrl = null)),
            baseDirectory = base, rawHtmlOptions = RawHtmlSaveOptions(enable = false), writeMetadata = true
        ).getOrThrow()
        suspend fun close() {
            withTimeout(5_000) { session.closeAndAwait() }
            downloader.close(); client.close()
            FileSystem.SYSTEM.deleteRecursively(directory, mustExist = false)
        }
    }

    private suspend fun using(fixture: Fixture = Fixture(), block: suspend (Fixture) -> Unit) {
        try { withTimeout(15_000) { block(fixture) } } finally { fixture.close() }
    }

    @Test fun displayMetadataSingleThreadAutoAndZipReuseOneOriginal() = runBlocking {
        using { f ->
            f.session.acquire(request).use { display ->
                val metadata = ImageGenerationMetadataReader().read(display.info.sizeBytes, display::readAt)
                assertEquals(GenerationImageFixtures.positive, metadata.candidates.first().positive)
            }
            val single = f.single()
            assertContentEquals(f.payload, f.output.readBytes("single/${single.relativePath}").getOrThrow())
            for (base in listOf("thread", "auto")) {
                val saved = f.thread(base)
                assertEquals(SaveStatus.COMPLETED, saved.status)
                assertContentEquals(f.payload, f.output.readBytes("$base/${saved.storageId}/b/src/original.jpg").getOrThrow())
            }
            val zip = ImageZipSaveService(f.client, f.output).save(listOf(url), "b", "123", baseDirectory = "zip").getOrThrow()
            assertEquals(1, zip.savedItems)
            val archive = f.output.readBytes("zip/${zip.fileName}").getOrThrow()
            // ZIP local header: uncompressed entry payload follows the UTF-8 filename.
            val nameLength = (archive[26].toInt() and 255) or ((archive[27].toInt() and 255) shl 8)
            val extraLength = (archive[28].toInt() and 255) or ((archive[29].toInt() and 255) shl 8)
            val start = 30 + nameLength + extraLength
            assertContentEquals(f.payload, archive.copyOfRange(start, start + f.payload.size))
            assertEquals(1, f.calls.value)
        }
    }

    @Test fun videoPlaybackSingleThreadAutoAndZipSaveReuseTheSameOriginalWithoutParsing() = runBlocking {
        val videoUrl = "https://may.2chan.net/b/src/video.mp4"
        val bytes = com.valoser.futacha.testing.video.VideoEditFixtures.bytes("portrait")
        using(Fixture(payload = bytes, url = videoUrl, mime = "video/mp4")) { f ->
            f.session.acquireForPlayback(OriginalMediaRequest(videoUrl)).use { player ->
                val single = f.single()
                assertContentEquals(bytes, f.output.readBytes("single/${single.relativePath}").getOrThrow())
                for (base in listOf("thread", "auto")) {
                    val saved = f.thread(base)
                    assertEquals(SaveStatus.COMPLETED, saved.status)
                    assertContentEquals(bytes, f.output.readBytes("$base/${saved.storageId}/b/videos/video.mp4").getOrThrow())
                }
                val zip = ImageZipSaveService(f.client, f.output).save(listOf(videoUrl), "b", "123", baseDirectory = "zip").getOrThrow()
                assertEquals(1, zip.savedItems)
                player.complete().use { assertContentEquals(bytes, it.readAt(0, bytes.size)) }
                assertEquals(1, f.calls.value, "Video save must join playback instead of opening its URL again")
            }
        }
    }

    @Test fun saveFirstAndDisplayDuringDownloadShareOneRequest() = runBlocking {
        val started = CompletableDeferred<Unit>(); val proceed = CompletableDeferred<Unit>()
        using(Fixture(beforeResponse = { started.complete(Unit); proceed.await() })) { f ->
            coroutineScope {
                val saving = async { f.single() }
                started.await()
                val display = async(start = CoroutineStart.UNDISPATCHED) { f.session.acquire(request) }
                proceed.complete(Unit)
                saving.await()
                display.await().use { assertContentEquals(f.payload, it.readAt(0, f.payload.size)) }
                assertEquals(1, f.calls.value)
            }
        }
    }

    @Test fun cancellingSaveWaiterDoesNotCancelDisplayDownload() = runBlocking {
        val started = CompletableDeferred<Unit>(); val proceed = CompletableDeferred<Unit>()
        using(Fixture(beforeResponse = { started.complete(Unit); proceed.await() })) { f ->
            coroutineScope {
                val display = async { f.session.acquire(request) }
                started.await()
                val saving = async(start = CoroutineStart.UNDISPATCHED) { f.single() }
                saving.cancelAndJoin()
                proceed.complete(Unit)
                display.await().use { assertContentEquals(f.payload, it.readAt(0, f.payload.size)) }
                assertEquals(1, f.calls.value)
            }
        }
    }

    @Test fun clearDuringCopyKeepsTheLeasedRevisionReadable() = runBlocking {
        using { f ->
            withMediaSaveSource(f.client, f.output, url, f.session) { source ->
                f.session.clear()
                val bytes = ByteArray(f.payload.size)
                assertEquals(bytes.size, source.read(bytes))
                assertContentEquals(f.payload, bytes)
                assertEquals(-1, source.read(bytes))
            }
            assertEquals(1, f.calls.value)
        }
    }

    @Test fun cancellingCopyCleansPartialOutputAndRetainsDisplay() = runBlocking {
        using(Fixture(payload = ByteArray(700_000) { it.toByte() })) { f ->
            f.session.acquire(request).use { display ->
                var partialPath: String? = null
                val partialOutput = object : com.valoser.futacha.shared.util.FileSystem by f.output {
                    override suspend fun writeByteStream(path: String, block: suspend (com.valoser.futacha.shared.util.FileWriteSink) -> Unit): Result<Unit> = runCatching {
                        partialPath = path
                        block(object : com.valoser.futacha.shared.util.FileWriteSink {
                            override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
                                f.output.appendBytes(path, bytes.copyOfRange(offset, offset + length)).getOrThrow()
                            }
                        })
                    }
                }
                assertFailsWith<CancellationException> {
                    SingleMediaSaveService(f.client, partialOutput).saveMedia(url, "b", "123", baseDirectory = "cancel",
                        onProgress = { _, _ -> throw CancellationException("cancel copy") }).getOrThrow()
                }
                assertNotNull(partialPath)
                assertFalse(f.output.exists(partialPath), "Partial output must be deleted")
                assertContentEquals(f.payload.copyOfRange(0, 64), display.readAt(0, 64))
                assertEquals(1, f.calls.value)
            }
        }
    }

    @Test fun largerThanCacheQuotaStillSavesWhileOriginalIsPinned() = runBlocking {
        using(Fixture(payload = ByteArray(100_000) { it.toByte() }, quota = 1024)) { f ->
            val saved = f.single()
            assertContentEquals(f.payload, f.output.readBytes("single/${saved.relativePath}").getOrThrow())
            assertEquals(1, f.calls.value)
        }
    }

    @Test fun exportsDoNotStartPromptParsingEvenWhenEnabled() = runBlocking {
        using { f ->
            val gate = MediaFeatureGate().apply { update(MediaFeatureSettings(promptDisplayEnabled = true)) }
            val parses = MutableStateFlow(0)
            val prompts = PromptMediaSource(f.session, gate) { parses.update { it + 1 }; error("must not parse") }
            try {
                SingleMediaSaveService(f.client, f.output, prompts).saveMedia(url, "b", "123").getOrThrow()
                assertEquals(0, parses.value)
                assertEquals(1, f.calls.value)
            } finally { prompts.close() }
        }
    }

    @Test fun unavailableCacheFallsBackBeforeNetworkExactlyOnce() = runBlocking {
        using(Fixture(cacheUnavailable = true)) { f ->
            val saved = f.single()
            assertContentEquals(f.payload, f.output.readBytes("single/${saved.relativePath}").getOrThrow())
            assertEquals(1, f.calls.value)
        }
    }

    @Test fun permanentOriginalFailureDoesNotFallbackOrRepeatThreadRetries() = runBlocking {
        using(Fixture(status = HttpStatusCode.NotFound)) { f ->
            val saved = f.thread()
            assertEquals(SaveStatus.FAILED, saved.status)
            assertEquals(0, saved.imageCount)
            assertEquals(1, f.calls.value)
        }
    }

    @Test fun transientFailureUsesOnlyTheSharedSourcesThreeAttemptBudget() = runBlocking {
        using(Fixture(status = HttpStatusCode.ServiceUnavailable)) { f ->
            val saved = f.thread()
            assertEquals(SaveStatus.FAILED, saved.status)
            assertEquals(3, f.calls.value)
        }
    }

    @Test fun copyFailureReleasesLeaseWithoutFallbackOrDownload() = runBlocking {
        using { f ->
            var fallbackNetwork = 0
            val unusedClient = HttpClient(MockEngine { fallbackNetwork++; error("unexpected fallback") })
            try {
                assertFailsWith<com.valoser.futacha.shared.util.OriginalMediaSaveFailure> {
                    withMediaSaveSource(unusedClient, f.output, url, f.session) { source ->
                        assertTrue(source.read(ByteArray(64)) > 0)
                        throw IOException("destination full")
                    }
                }
                assertEquals(0, fallbackNetwork)
                assertEquals(1, f.calls.value)
                // closeAndAwait in fixture cleanup also checks that the failed copy released its lease.
                f.session.acquire(request.copy(allowNetwork = false)).use { assertTrue(it.fromCache) }
            } finally { unusedClient.close() }
        }
    }

    @Test fun headlessInitializerCannotOverrideAnExistingConfiguration() = runBlocking {
        using { f ->
            f.session.acquire(request).close()
            f.session.configureIfAbsent(OriginalMediaCacheConfiguration(f.directory.resolve("other"), 2048))
            f.session.acquire(request.copy(allowNetwork = false)).use { assertTrue(it.fromCache) }
            assertEquals(1, f.calls.value)
            assertFalse(FileSystem.SYSTEM.exists(f.directory.resolve("other")))
        }
    }

    @Test fun thumbnailsExternalHostsAndLocalFilesBypassOriginalAcquisition() = runBlocking {
        using { f ->
            val rejectSource = object : OriginalMediaSource by f.session {
                override suspend fun acquireForExport(request: OriginalMediaRequest): OriginalMediaStore.Lease =
                    error("ineligible resource acquired as an original")
            }
            val service = SingleMediaSaveService(f.client, f.output, rejectSource)
            for (source in listOf("https://may.2chan.net/b/thumb/a.jpg", "https://example.test/src/a.mp4",
                "https://example.test/src/a.jpg")) {
                assertTrue(service.saveMedia(source, "b", "123").isSuccess)
            }
            f.output.writeBytes("/local/original.jpg", f.payload).getOrThrow()
            assertTrue(service.saveMedia("/local/original.jpg", "b", "123").isSuccess)
            assertEquals(3, f.calls.value)
        }
    }
}
