package com.valoser.futacha.shared.media.analysis

import com.valoser.futacha.shared.media.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okio.*
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import kotlin.random.Random
import kotlin.test.*

class ModelStoreTest {
    @Test fun interruptedTransferResumesItsPersistedBytesOnTheNextDownload() = runBlocking {
        val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("model-resume-test-${Random.nextLong()}")
        val gate = MediaFeatureGate().apply { update(enabled) }
        val offsets = mutableListOf<Long>()
        val store = ModelStore(gate, { directory }, { object : ModelDownloader {
            override val supportsResume = true
            override suspend fun download(distribution: ModelDistribution, sink: BufferedSink) = error("use resume")
            override suspend fun resumeDownload(distribution: ModelDistribution, sink: BufferedSink, offset: Long) {
                offsets += offset
                if (offset == 0L) {
                    sink.write(payload, 0, 3); sink.emit()
                    throw IOException("connection lost")
                }
                sink.write(payload, offset.toInt(), payload.size - offset.toInt())
            }
        } }, models = listOf(spec()))
        try {
            val permit = gate.permit(MediaFeature.IMAGE_EDITOR)!!
            assertFailsWith<IOException> { store.download(id, permit) }
            val installed = store.download(id, permit)
            assertEquals(listOf(0L, 3L), offsets)
            assertContentEquals(payload, FileSystem.SYSTEM.read(installed.path) { readByteArray() })
            assertEquals(listOf(installed.path), FileSystem.SYSTEM.list(directory))
        } finally {
            store.closeAndAwait()
            FileSystem.SYSTEM.deleteRecursively(directory, mustExist = false)
        }
    }

    @Test fun recreatedStoreWaitsForTheResumablePartialInsteadOfWritingItConcurrently() = runBlocking {
        val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("model-resume-shared-test-${Random.nextLong()}")
        val gate = MediaFeatureGate().apply { update(enabled) }
        val firstWrote = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondOffsets = mutableListOf<Long>()
        fun resuming(onResume: suspend (BufferedSink, Long) -> Unit) = object : ModelDownloader {
            override val supportsResume = true
            override suspend fun download(distribution: ModelDistribution, sink: BufferedSink) = error("use resume")
            override suspend fun resumeDownload(distribution: ModelDistribution, sink: BufferedSink, offset: Long) = onResume(sink, offset)
        }
        val first = ModelStore(gate, { directory }, { resuming { sink, offset ->
            sink.write(payload, offset.toInt(), 3); sink.emit(); firstWrote.complete(Unit)
            releaseFirst.await()
            sink.write(payload, offset.toInt() + 3, payload.size - offset.toInt() - 3)
        } }, models = listOf(spec()))
        // A host recreated while the first download still runs, sharing the same model directory.
        val second = ModelStore(gate, { directory }, { resuming { sink, offset ->
            secondOffsets += offset
            sink.write(payload, offset.toInt(), payload.size - offset.toInt())
        } }, models = listOf(spec()))
        try {
            val permit = gate.permit(MediaFeature.IMAGE_EDITOR)!!
            withTimeout(10_000) {
                coroutineScope {
                    val a = async { first.download(id, permit) }
                    firstWrote.await()
                    val b = async { second.download(id, permit) }
                    delay(300)
                    assertEquals(emptyList(), secondOffsets, "The second store must not append to the partial being written")
                    releaseFirst.complete(Unit)
                    val installed = a.await()
                    assertEquals(installed.path, b.await().path)
                    assertEquals(emptyList(), secondOffsets, "The installed model is reused instead of downloaded again")
                    assertContentEquals(payload, FileSystem.SYSTEM.read(installed.path) { readByteArray() })
                    assertEquals(listOf(installed.path), FileSystem.SYSTEM.list(directory))
                }
            }
        } finally {
            first.closeAndAwait(); second.closeAndAwait()
            FileSystem.SYSTEM.deleteRecursively(directory, mustExist = false)
        }
    }

    @Test fun rejectedResumedResponseDiscardsThePartialAndRestartsFromZeroOnce() = runBlocking {
        val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("model-resume-reject-test-${Random.nextLong()}")
        val gate = MediaFeatureGate().apply { update(enabled) }
        val spec = spec()
        val partial = directory.resolve("download-${spec.distribution.sha256}.part")
        FileSystem.SYSTEM.createDirectories(directory)
        FileSystem.SYSTEM.write(partial) { write(payload, 0, 3) }
        val ranges = mutableListOf<String?>()
        val client = HttpClient(MockEngine { request ->
            ranges += request.headers[HttpHeaders.Range]
            if (request.headers[HttpHeaders.Range] != null) {
                // Passes the status check but cannot continue the persisted bytes.
                respond("x", HttpStatusCode.PartialContent, headersOf(HttpHeaders.ContentRange, "bytes 0-0/1"))
            } else respond(payload, HttpStatusCode.OK)
        }) { configureModelDownloads() }
        val store = ModelStore(gate, { directory }, { KtorModelDownloader(client) }, models = listOf(spec))
        try {
            val installed = store.download(id, gate.permit(MediaFeature.IMAGE_EDITOR)!!)
            assertEquals(listOf("bytes=3-", null), ranges)
            assertContentEquals(payload, FileSystem.SYSTEM.read(installed.path) { readByteArray() })
            assertEquals(listOf(installed.path), FileSystem.SYSTEM.list(directory))
        } finally {
            store.closeAndAwait()
            FileSystem.SYSTEM.deleteRecursively(directory, mustExist = false)
        }
    }

    @Test fun firstUseRemovesResumablePartialsOfReplacedOrLongAbandonedDownloads() = runBlocking {
        val gate = MediaFeatureGate().apply { update(enabled) }
        val spec = spec()
        val foreign = "download-${"0".repeat(64)}.part"
        val current = "download-${spec.distribution.sha256}.part"
        for (expired in listOf(false, true)) {
            val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("model-partial-cleanup-test-${Random.nextLong()}")
            FileSystem.SYSTEM.createDirectories(directory)
            for (name in listOf(foreign, current, "download-user.part")) {
                FileSystem.SYSTEM.write(directory.resolve(name)) { writeUtf8("partial") }
            }
            val later = if (expired) 8L * 24 * 60 * 60 * 1000 else 0L
            val store = ModelStore(gate, { directory }, { error("no HTTP") }, models = listOf(spec),
                now = { kotlin.time.Clock.System.now().toEpochMilliseconds() + later })
            try {
                assertNull(store.verified(id, gate.permit(MediaFeature.IMAGE_EDITOR)!!))
                val expected = if (expired) setOf("download-user.part") else setOf(current, "download-user.part")
                assertEquals(expected, FileSystem.SYSTEM.list(directory).map { it.name }.toSet())
            } finally {
                store.closeAndAwait()
                FileSystem.SYSTEM.deleteRecursively(directory, mustExist = false)
            }
        }
    }

    private val enabled = MediaFeatureSettings(imageEditorEnabled = true, videoEditorEnabled = true)
    private val id = AnalysisModel.NUDE_NET
    private val payload = "verified onnx fixture".encodeToByteArray()
    private fun spec(payload: ByteArray = this.payload, distribution: ModelDistribution? = null): AnalysisModelSpec {
        val sha = payload.toByteString().sha256().hex()
        return AnalysisModelSpec(id, "test model", "test", "https://model.test", payload.size.toLong(), sha,
            distribution ?: ModelDistribution("https://model.test/model.onnx", payload.size.toLong(), sha))
    }
    private inner class Fixture(val spec: AnalysisModelSpec = spec(), download: suspend (BufferedSink) -> Unit = { it.write(payload) }) {
        val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("model-store-test-${Random.nextLong()}")
        val gate = MediaFeatureGate().apply { update(enabled) }
        val calls = MutableStateFlow(0)
        val directories = MutableStateFlow(0)
        val clients = MutableStateFlow(0)
        val closed = MutableStateFlow(0)
        val store = ModelStore(gate, directory = { directories.update { it + 1 }; directory }, downloader = {
            clients.update { it + 1 }
            object : ModelDownloader, AutoCloseable {
                override suspend fun download(distribution: ModelDistribution, sink: BufferedSink) {
                    calls.update { it + 1 }; download(sink)
                }
                override fun close() { closed.update { it + 1 } }
            }
        }, models = listOf(spec))
        fun permit(feature: MediaFeature = MediaFeature.IMAGE_EDITOR) = requireNotNull(gate.permit(feature))
        fun files() = FileSystem.SYSTEM.listOrNull(directory).orEmpty()
        suspend fun close() {
            withTimeout(5_000) { store.closeAndAwait() }
            FileSystem.SYSTEM.deleteRecursively(directory, mustExist = false)
        }
    }
    private suspend fun using(f: Fixture = Fixture(), block: suspend (Fixture) -> Unit) {
        try { withTimeout(10_000) { block(f) } } finally { f.close() }
    }

    @Test fun disabledStaleForeignAndPromptPermitsPerformNoIo() = runBlocking {
        using { f ->
            val stale = f.permit()
            f.gate.update(MediaFeatureSettings.Disabled)
            assertFailsWith<CancellationException> { f.store.download(id, stale) }
            f.gate.update(enabled)
            assertFailsWith<CancellationException> { f.store.verified(id, stale) }
            assertFailsWith<CancellationException> { f.store.import(id, stale) { error("must not open input") } }
            val other = MediaFeatureGate().apply { update(enabled.copy(promptDisplayEnabled = true)) }
            assertFailsWith<CancellationException> { f.store.download(id, other.permit(MediaFeature.IMAGE_EDITOR)!!) }
            assertFailsWith<IllegalArgumentException> { f.store.download(id, other.permit(MediaFeature.PROMPT)!!) }
            assertEquals(0, f.directories.value); assertEquals(0, f.clients.value); assertEquals(0, f.calls.value)
        }
    }

    @Test fun localVerificationDoesNotAcquireMissingModels() = runBlocking {
        using { f ->
            assertNull(f.store.verified(id, f.permit()))
            assertEquals(0, f.clients.value); assertTrue(f.files().isEmpty())
        }
    }

    @Test fun firstUseRemovesOnlyAbandonedModelPartsAndAnotherHostPreservesAnActiveImport() = runBlocking {
        val started = CompletableDeferred<Unit>(); val proceed = CompletableDeferred<Unit>()
        using(Fixture(download = { sink -> sink.writeUtf8("part"); sink.emit(); started.complete(Unit); proceed.await() })) { f ->
            val fs = FileSystem.SYSTEM
            fs.createDirectories(f.directory)
            for (name in listOf("NUDE_NET-abc.part", "NUDE_NET-abc.extracted.part", "user.part", "kept.onnx")) {
                fs.write(f.directory.resolve(name)) { writeUtf8("keep unless owned partial") }
            }
            assertNull(f.store.verified(id, f.permit()))
            assertEquals(setOf("user.part", "kept.onnx"), f.files().map { it.name }.toSet())
            coroutineScope {
                val task = launch { f.store.download(id, f.permit()) }
                started.await()
                val active = f.files().single { it.name.startsWith("NUDE_NET-") }
                val second = ModelStore(f.gate, { f.directory }, { error("no HTTP") }, models = listOf(f.spec))
                try {
                    assertNull(second.verified(id, f.permit()))
                    assertTrue(fs.exists(active))
                } finally { second.closeAndAwait(); task.cancelAndJoin() }
                assertFalse(fs.exists(active))
            }
            assertEquals(setOf("user.part", "kept.onnx"), f.files().map { it.name }.toSet())
        }
    }

    @Test fun bothEditorsShareOneDownloadAndImageOffDoesNotCancelVideo() = runBlocking {
        val started = CompletableDeferred<Unit>(); val proceed = CompletableDeferred<Unit>()
        using(Fixture(download = { sink -> started.complete(Unit); proceed.await(); sink.write(payload) })) { f ->
            coroutineScope {
                val image = async(start = CoroutineStart.UNDISPATCHED) { f.store.download(id, f.permit()) }
                started.await()
                val video = async(start = CoroutineStart.UNDISPATCHED) { f.store.download(id, f.permit(MediaFeature.VIDEO_EDITOR)) }
                f.gate.update(enabled.copy(imageEditorEnabled = false))
                image.join(); assertTrue(image.isCancelled)
                proceed.complete(Unit)
                val result = video.await()
                assertContentEquals(payload, FileSystem.SYSTEM.read(result.path) { readByteArray() })
                assertEquals(1, f.calls.value)
                assertEquals(result, f.store.verified(id, f.permit(MediaFeature.VIDEO_EDITOR)))
                assertEquals(listOf(result.path), f.files())
            }
        }
    }

    @Test fun cancellingLastSubscriberWaitsForPartialCleanupAndAllowsRetry() = runBlocking {
        val started = CompletableDeferred<Unit>(); val attempts = MutableStateFlow(0)
        using(Fixture(download = { sink ->
            attempts.update { it + 1 }
            if (attempts.value == 1) { sink.writeUtf8("part"); sink.emit(); started.complete(Unit); awaitCancellation() }
            sink.write(payload)
        })) { f ->
            coroutineScope {
                val task = async { f.store.download(id, f.permit()) }
                started.await(); task.cancelAndJoin()
                assertTrue(f.files().isEmpty()); assertNull(f.store.progress(id).value)
                val result = f.store.download(id, f.permit())
                assertContentEquals(payload, FileSystem.SYSTEM.read(result.path) { readByteArray() })
                assertEquals(2, f.calls.value)
            }
        }
    }

    @Test fun sameSizeCorruptionIsRejectedAndExplicitDownloadRepairsIt() = runBlocking {
        using { f ->
            val result = f.store.download(id, f.permit())
            assertEquals(result, f.store.download(id, f.permit()))
            assertEquals(1, f.calls.value)
            FileSystem.SYSTEM.write(result.path) { write(ByteArray(payload.size) { 7 }) }
            assertNull(f.store.verified(id, f.permit()))
            assertEquals(result, f.store.download(id, f.permit()))
            assertContentEquals(payload, FileSystem.SYSTEM.read(result.path) { readByteArray() })
            assertEquals(2, f.calls.value)
        }
    }

    @Test fun presenceCheckUsesSizeOnlyAndNeverVouchesForContent() = runBlocking {
        using { f ->
            assertFalse(f.store.present(id, f.permit()))
            val result = f.store.download(id, f.permit())
            assertTrue(f.store.present(id, f.permit()))

            // Same size, wrong bytes: listed as present, but sessions still refuse it.
            FileSystem.SYSTEM.write(result.path) { write(ByteArray(payload.size) { 7 }) }
            assertTrue(f.store.present(id, f.permit()))
            assertNull(f.store.verified(id, f.permit()))

            FileSystem.SYSTEM.write(result.path) { write(ByteArray(payload.size - 1) { 7 }) }
            assertFalse(f.store.present(id, f.permit()))
        }
    }

    @Test fun offThenOnCancelsOldDownloadAndRequiresANewPermit() = runBlocking {
        val started = CompletableDeferred<Unit>()
        using(Fixture(download = { sink ->
            sink.writeUtf8("part"); sink.emit(); started.complete(Unit); awaitCancellation()
        })) { f ->
            coroutineScope {
                val old = f.permit()
                val task = async { f.store.download(id, old) }
                started.await()
                f.gate.update(MediaFeatureSettings.Disabled); f.gate.update(enabled)
                task.join(); assertTrue(task.isCancelled)
                assertFailsWith<CancellationException> { f.store.download(id, old) }
                assertTrue(f.files().isEmpty()); assertEquals(1, f.calls.value)
            }
        }
    }

    @Test fun invalidDownloadNeverPublishesOrLeavesTemporaryFiles() = runBlocking {
        for (invalid in listOf(payload.dropLast(1).toByteArray(), ByteArray(payload.size) { 5 }, payload + byteArrayOf(1))) {
            using(Fixture(download = { it.write(invalid) })) { f ->
                assertFails { f.store.download(id, f.permit()) }
                assertTrue(f.files().isEmpty()); assertNull(f.store.progress(id).value)
            }
        }
    }

    @Test fun failedImportPreservesInstalledModelAndNeverConstructsHttpClient() = runBlocking {
        using { f ->
            var opens = 0; var closes = 0
            val source = Buffer().write(payload)
            val result = f.store.import(id, f.permit()) {
                opens++
                object : Source by source { override fun close() { closes++; source.close() } }
            }
            assertEquals(1, opens); assertEquals(1, closes)
            assertFails { f.store.import(id, f.permit()) { Buffer().write(ByteArray(payload.size)) } }
            assertEquals(result, f.store.verified(id, f.permit()))
            assertContentEquals(payload, FileSystem.SYSTEM.read(result.path) { readByteArray() })
            assertEquals(0, f.clients.value); assertEquals(listOf(result.path), f.files())
        }
    }

    @Test fun cancellingImportClosesSourceAndDeletesPartialFile() = runBlocking {
        using { f ->
            var closed = false
            val source = object : Source {
                override fun read(sink: Buffer, byteCount: Long): Long { sink.writeByte(1); throw CancellationException("selected resource cancelled") }
                override fun close() { closed = true }
                override fun timeout() = Timeout.NONE
            }
            assertFailsWith<CancellationException> { f.store.import(id, f.permit()) { source } }
            assertTrue(closed); assertTrue(f.files().isEmpty()); assertEquals(0, f.clients.value)
        }
    }

    @Test fun verifiedWheelExtractsOnlyPinnedEntryAndChecksBothHashes() = runBlocking {
        val archive = requireNotNull(WHEEL.decodeBase64()).toByteArray()
        val distribution = ModelDistribution("https://model.test/model.whl", archive.size.toLong(), archive.toByteString().sha256().hex(), "model/model.onnx")
        using(Fixture(spec(distribution = distribution), download = { it.write(archive) })) { f ->
            val result = f.store.download(id, f.permit())
            assertContentEquals(payload, FileSystem.SYSTEM.read(result.path) { readByteArray() })
            assertEquals(listOf(result.path), f.files())
            assertFalse(FileSystem.SYSTEM.exists(f.directory.parent!!.resolve("outside.onnx")))
        }
        for (broken in listOf(distribution.copy(sha256 = "0".repeat(64)), distribution.copy(zipEntry = "missing.onnx"))) {
            using(Fixture(spec(distribution = broken), download = { it.write(archive) })) { f ->
                assertFails { f.store.download(id, f.permit()) }; assertTrue(f.files().isEmpty())
            }
        }
        using(Fixture(spec(ByteArray(payload.size), distribution), download = { it.write(archive) })) { f ->
            assertFails { f.store.download(id, f.permit()) }; assertTrue(f.files().isEmpty())
        }
    }

    @Test fun closeCancelsActiveDownloadAndDisposesTransportAfterCleanup() = runBlocking {
        val started = CompletableDeferred<Unit>(); val finished = CompletableDeferred<Unit>()
        using(Fixture(download = {
            it.writeUtf8("part"); it.emit(); started.complete(Unit)
            try { awaitCancellation() } finally { finished.complete(Unit) }
        })) { f ->
            coroutineScope {
                val task = async { f.store.download(id, f.permit()) }
                started.await(); f.store.closeAndAwait(); task.join()
                assertTrue(finished.isCompleted); assertTrue(task.isCancelled)
                assertTrue(f.files().isEmpty()); assertEquals(1, f.closed.value)
                assertFailsWith<CancellationException> { f.store.download(id, f.permit()) }
            }
        }
    }

    private companion object {
        const val WHEEL = "UEsDBBQAAAAIANoGMl2P2FtSFwAAABUAAAAQAAAAbW9kZWwvbW9kZWwub25ueCtLLcpMy0xNUcjPy6tQSMusKCktSgUAUEsDBBQAAAAIANoGMl2HcuC0CwAAAAkAAAAPAAAALi4vb3V0c2lkZS5vbm54y0zPyy9KVchNBQBQSwECFAMUAAAACADaBjJdj9hbUhcAAAAVAAAAEAAAAAAAAAAAAAAAgAEAAAAAbW9kZWwvbW9kZWwub25ueFBLAQIUAxQAAAAIANoGMl2HcuC0CwAAAAkAAAAPAAAAAAAAAAAAAACAAUUAAAAuLi9vdXRzaWRlLm9ubnhQSwUGAAAAAAIAAgB7AAAAfQAAAAAA"
    }
}
