package com.valoser.futacha.shared.media.analysis

import com.valoser.futacha.shared.media.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okio.*
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import kotlin.random.Random
import kotlin.test.*

class ModelStoreTest {
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
