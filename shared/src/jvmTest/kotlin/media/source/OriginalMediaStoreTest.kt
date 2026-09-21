package com.valoser.futacha.shared.media.source

import coil3.disk.DiskCache
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.*
import okio.FileHandle
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class OriginalMediaStoreTest {
    private val request = OriginalMediaRequest("https://example.test/src/original.png")

    private class Fixture(
        namespace: String = "test-profile-1",
        val directory: java.io.File = Files.createTempDirectory("original-media").toFile(),
        fileSystem: FileSystem = FileSystem.SYSTEM,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        decorateCache: (DiskCache) -> DiskCache = { it },
        downloader: OriginalMediaDownloader
    ) {
        val store = OriginalMediaStore(namespace, {
            decorateCache(DiskCache.Builder().directory(directory.path.toPath()).fileSystem(fileSystem).maxSizeBytes(1024 * 1024).build())
        }, downloader, dispatcher)

        suspend fun finish() {
            withTimeout(5000) { store.closeAndAwait() }
            directory.deleteRecursively()
        }
    }

    private fun info(size: Int, cacheable: Boolean = true) = OriginalMediaInfo(
        mimeType = "image/png", sizeBytes = size.toLong(), resolvedUrl = request.url, cacheable = cacheable
    )

    private class CloseFailingFileSystem : ForwardingFileSystem(FileSystem.SYSTEM) {
        val closeAttempts = AtomicInteger()

        override fun openReadOnly(file: Path): FileHandle {
            // Match production's share-delete handle so Windows can commit the
            // download before this wrapper injects the close failure.
            val handle = FileSystem.SYSTEM.openOriginalMediaReadHandle(file)
            return object : FileHandle(readWrite = false) {
                override fun protectedRead(fileOffset: Long, array: ByteArray, arrayOffset: Int, byteCount: Int) =
                    handle.read(fileOffset, array, arrayOffset, byteCount)
                override fun protectedSize() = handle.size()
                override fun protectedWrite(fileOffset: Long, array: ByteArray, arrayOffset: Int, byteCount: Int) =
                    error("Read-only handle")
                override fun protectedResize(size: Long) = error("Read-only handle")
                override fun protectedFlush() = error("Read-only handle")
                override fun protectedClose() {
                    handle.close()
                    closeAttempts.incrementAndGet()
                    throw IOException("close failed: EIO (I/O error)")
                }
            }
        }
    }

    private suspend fun withCloseFailure(
        downloader: OriginalMediaDownloader,
        block: suspend (Fixture, CloseFailingFileSystem) -> Unit
    ) {
        val uncaught = ConcurrentLinkedQueue<Throwable>()
        val dispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "original-media-close-test").apply {
                uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, failure -> uncaught.add(failure) }
            }
        }.asCoroutineDispatcher()
        val fileSystem = CloseFailingFileSystem()
        val fixture = Fixture(fileSystem = fileSystem, dispatcher = dispatcher, downloader = downloader)
        try {
            withTimeout(5000) { block(fixture, fileSystem) }
        } finally {
            try { fixture.finish() } finally { dispatcher.close() }
        }
        assertTrue(uncaught.isEmpty(), "Cleanup must not leak uncaught exceptions: $uncaught")
    }

    @Test fun readHandleCloseFailureStillReleasesSnapshotAndFinishesShutdown(): Unit = runBlocking {
        withCloseFailure({ _, sink -> sink.writeUtf8("data"); info(4, cacheable = false) }) { fixture, fileSystem ->
            val lease = fixture.store.acquire(request)
            val retained = lease.retain()
            val file = lease.file
            fixture.store.close()
            lease.close()
            lease.close()
            assertEquals(0, fileSystem.closeAttempts.get(), "The retained lease still pins the reader")
            assertTrue(FileSystem.SYSTEM.exists(file))
            retained.close()
            fixture.store.closeAndAwait()
            assertEquals(1, fileSystem.closeAttempts.get())
            assertFalse(FileSystem.SYSTEM.exists(file), "No-store data must be unpinned and removed")
        }
    }

    @Test fun cancelledDownloadCloseFailureDoesNotPreventShutdown(): Unit = runBlocking {
        val started = CompletableDeferred<Unit>()
        withCloseFailure({ _, sink ->
            sink.writeUtf8("partial")
            started.complete(Unit)
            awaitCancellation()
        }) { fixture, fileSystem ->
            val acquire = async { fixture.store.acquire(request) }
            started.await()
            acquire.cancelAndJoin()
            assertTrue(acquire.isCancelled)
            fixture.store.closeAndAwait()
            assertEquals(1, fileSystem.closeAttempts.get())
            assertEquals(0, fixture.store.sizeBytes())
        }
    }

    @Test fun readHandleCloseFailureDoesNotStopDownloadRetry(): Unit = runBlocking {
        val calls = AtomicInteger()
        val interrupted = java.net.ProtocolException("interrupted body")
        withCloseFailure(object : OriginalMediaDownloader {
            override suspend fun download(request: OriginalMediaRequest, sink: okio.BufferedSink): OriginalMediaInfo {
                if (calls.incrementAndGet() == 1) {
                    sink.writeUtf8("partial")
                    throw interrupted
                }
                sink.writeUtf8("complete")
                return info(8, cacheable = false)
            }
            override suspend fun retryAfter(retry: Int, failure: Throwable): Boolean {
                assertSame(interrupted, failure)
                return true
            }
        }) { fixture, fileSystem ->
            fixture.store.acquire(request).use { lease ->
                assertEquals("complete", FileSystem.SYSTEM.read(lease.file) { readUtf8() })
            }
            fixture.store.closeAndAwait()
            assertEquals(2, calls.get())
            assertEquals(2, fileSystem.closeAttempts.get())
            assertEquals(0, fixture.store.sizeBytes())
        }
    }

    @Test fun failedDownloadSnapshotCloseFailurePreservesCauseAndStillRemovesFile(): Unit = runBlocking {
        val indexFailure = IOException("Original media index write failed")
        val closes = AtomicInteger()
        val removals = AtomicInteger()
        val fixture = Fixture(decorateCache = { disk ->
            object : DiskCache by disk {
                override fun openEditor(key: String): DiskCache.Editor? {
                    if (key.startsWith("original-media-v1-")) throw indexFailure
                    val editor = disk.openEditor(key) ?: return null
                    return object : DiskCache.Editor by editor {
                        override fun commitAndOpenSnapshot(): DiskCache.Snapshot? {
                            val snapshot = editor.commitAndOpenSnapshot() ?: return null
                            return object : DiskCache.Snapshot by snapshot {
                                override fun close() {
                                    closes.incrementAndGet()
                                    snapshot.close()
                                    throw IOException("Snapshot close failed: EIO")
                                }
                            }
                        }
                    }
                }
                override fun remove(key: String): Boolean {
                    removals.incrementAndGet()
                    return disk.remove(key)
                }
            }
        }) { _, sink -> sink.writeUtf8("data"); info(4) }
        try {
            val failure = assertFailsWith<IOException> { fixture.store.acquire(request) }
            // Coroutine stack-trace recovery may copy the exception, keeping the original as its cause.
            assertEquals(indexFailure.message, failure.message)
            assertSame(indexFailure, failure.cause ?: failure)
            withTimeout(5000) { fixture.store.closeAndAwait() }
            assertEquals(1, closes.get(), "A failed close must not be attempted again in finally")
            assertEquals(1, removals.get(), "Discard must continue after snapshot close fails")
            assertEquals(0, fixture.store.sizeBytes())
        } finally { fixture.finish() }
    }

    @Test fun failedDownloadRemovalFailurePreservesCauseAndStopsRetry(): Unit = runBlocking {
        val interrupted = java.net.ProtocolException("Interrupted original media body")
        val downloads = AtomicInteger()
        val retries = AtomicInteger()
        val removals = AtomicInteger()
        val fixture = Fixture(decorateCache = { disk ->
            object : DiskCache by disk {
                override fun remove(key: String): Boolean {
                    removals.incrementAndGet()
                    throw IOException("Remove failed: EIO")
                }
            }
        }, downloader = object : OriginalMediaDownloader {
            override suspend fun download(request: OriginalMediaRequest, sink: okio.BufferedSink): OriginalMediaInfo {
                downloads.incrementAndGet()
                sink.writeUtf8("partial")
                throw interrupted
            }
            override suspend fun retryAfter(retry: Int, failure: Throwable): Boolean {
                retries.incrementAndGet()
                return true
            }
        })
        try {
            val failure = assertFailsWith<java.net.ProtocolException> { fixture.store.acquire(request) }
            assertEquals(interrupted.message, failure.message)
            assertSame(interrupted, failure.cause ?: failure)
            withTimeout(5000) { fixture.store.closeAndAwait() }
            assertEquals(1, downloads.get())
            assertEquals(1, removals.get())
            assertEquals(0, retries.get(), "Cannot retry before the failed file is discarded")
        } finally { fixture.finish() }
    }

    @Test fun concurrentReadersShareDownloadAndCancellingMetadataKeepsDisplayAlive(): Unit = runBlocking {
        val started = CompletableDeferred<Unit>()
        val proceed = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val fixture = Fixture { _, sink ->
            calls.incrementAndGet()
            started.complete(Unit)
            proceed.await()
            sink.writeUtf8("original")
            info(8)
        }
        try {
            withTimeout(5000) {
                val display = async(start = CoroutineStart.UNDISPATCHED) { fixture.store.acquire(request) }
                started.await()
                val metadata = async(start = CoroutineStart.UNDISPATCHED) { fixture.store.acquire(request) }
                val others = List(20) { async(start = CoroutineStart.UNDISPATCHED) { fixture.store.acquire(request) } }
                metadata.cancelAndJoin()
                proceed.complete(Unit)
                display.await().use { lease ->
                    others.awaitAll().forEach { other ->
                        other.use {
                            assertEquals(lease.file, it.file)
                            assertEquals(lease.identity, it.identity)
                            assertEquals("original", it.readAt(0, 8).decodeToString())
                        }
                    }
                    assertEquals("original", lease.readAt(0, 64).decodeToString())
                    assertContentEquals(byteArrayOf(), lease.readAt(8, 1))
                }
                assertEquals(1, calls.get())
            }
        } finally { fixture.finish() }
    }

    @Test fun lastWaiterCancellationDiscardsPartialFileAndNextRequestCanRetry(): Unit = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val fixture = Fixture { _, sink ->
            if (calls.incrementAndGet() == 1) {
                sink.writeUtf8("partial")
                started.complete(Unit)
                try { awaitCancellation() } finally { cancelled.complete(Unit) }
            }
            sink.writeUtf8("complete")
            info(8)
        }
        try {
            withTimeout(5000) {
                val first = async { fixture.store.acquire(request) }
                started.await()
                first.cancelAndJoin()
                cancelled.await()
                fixture.store.acquire(request).use {
                    assertEquals("complete", it.readAt(0, 64).decodeToString())
                }
                assertEquals(2, calls.get())
            }
        } finally { fixture.finish() }
    }

    @Test fun explicitReloadReplacesOriginalWithoutDeletingLeasedOldRevision(): Unit = runBlocking {
        val calls = AtomicInteger()
        val fixture = Fixture { _, sink ->
            sink.writeUtf8(if (calls.incrementAndGet() == 1) "old" else "new")
            info(3)
        }
        try {
            fixture.store.acquire(request).use { old ->
                fixture.store.acquire(request.copy(reloadToken = 7)).use { fresh ->
                    assertNotEquals(old.identity, fresh.identity)
                    assertEquals("old", old.readAt(0, 3).decodeToString())
                    assertEquals("new", fresh.readAt(0, 3).decodeToString())
                    fixture.store.acquire(request.copy(reloadToken = 7)).use { repeated ->
                        assertEquals(fresh.identity, repeated.identity)
                    }
                    assertEquals(2, calls.get())
                }
            }
        } finally { fixture.finish() }
    }

    @Test fun clearingCacheKeepsLeasedFileReadableButForcesNextDownload(): Unit = runBlocking {
        val calls = AtomicInteger()
        val fixture = Fixture { _, sink ->
            sink.writeUtf8("v${calls.incrementAndGet()}")
            info(2)
        }
        try {
            fixture.store.acquire(request).use { old ->
                fixture.store.clear()
                assertEquals("v1", old.readAt(0, 2).decodeToString())
                fixture.store.acquire(request).use { fresh ->
                    assertEquals("v2", fresh.readAt(0, 2).decodeToString())
                    assertNotEquals(old.identity, fresh.identity)
                }
            }
        } finally { fixture.finish() }
    }

    @Test fun cacheClearCancelsPendingDownloadAndCannotPublishItsStaleResult(): Unit = runBlocking {
        val started = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val fixture = Fixture { _, sink ->
            if (calls.incrementAndGet() == 1) {
                started.complete(Unit)
                awaitCancellation()
            }
            sink.writeUtf8("fresh")
            info(5)
        }
        try {
            withTimeout(5000) {
                val first = async { fixture.store.acquire(request) }
                started.await()
                fixture.store.clear()
                assertFailsWith<CancellationException> { first.await() }
                fixture.store.acquire(request).use { assertEquals("fresh", it.readAt(0, 5).decodeToString()) }
                assertEquals(2, calls.get())
            }
        } finally { fixture.finish() }
    }

    @Test fun completedBytesSurviveReopenAndArePartitionedByProfileHeadersAndQuery(): Unit = runBlocking {
        val calls = AtomicInteger()
        val downloader = OriginalMediaDownloader { _, sink ->
            sink.writeUtf8("data")
            calls.incrementAndGet()
            info(4)
        }
        val first = Fixture(downloader = downloader)
        try {
            first.store.acquire(request).close()
            first.store.closeAndAwait()
            val second = Fixture(downloader = downloader, directory = first.directory)
            try {
                second.store.acquire(request.copy(allowNetwork = false)).use {
                    assertTrue(it.fromCache)
                    assertEquals("data", it.readAt(0, 4).decodeToString())
                }
                assertEquals(1, calls.get())
                second.store.acquire(request.copy(headers = mapOf("Referer" to "https://example.test/a/"))).close()
                second.store.acquire(request.copy(url = "${request.url}?version=2")).close()
                assertEquals(3, calls.get())
            } finally { second.store.closeAndAwait() }
            val otherProfile = Fixture(downloader = downloader, namespace = "different-profile", directory = first.directory)
            try {
                otherProfile.store.acquire(request).close()
                assertEquals(4, calls.get())
            } finally { otherProfile.store.closeAndAwait() }
        } finally { first.finish() }
    }

    @Test fun noStoreOriginalIsSharedWhileLeasedAndRemovedAfterRelease(): Unit = runBlocking {
        val calls = AtomicInteger()
        val fixture = Fixture { _, sink ->
            calls.incrementAndGet()
            sink.writeUtf8("data")
            info(4, cacheable = false)
        }
        try {
            fixture.store.acquire(request).use { first ->
                fixture.store.acquire(request).use { assertEquals(first.identity, it.identity) }
                assertEquals(1, calls.get())
            }
            fixture.store.closeAndAwait()
            val reopened = Fixture(downloader = OriginalMediaDownloader { _, _ -> error("Must remain offline") }, directory = fixture.directory)
            try {
                assertFailsWith<okio.IOException> { reopened.store.acquire(request.copy(allowNetwork = false)) }
                assertEquals(0, reopened.store.sizeBytes())
            } finally { reopened.store.closeAndAwait() }
        } finally { fixture.finish() }
    }

    @Test fun truncatedBodyNeverBecomesACachedOriginal(): Unit = runBlocking {
        val fixture = Fixture { _, sink -> sink.writeUtf8("short"); info(99) }
        try {
            assertFailsWith<okio.IOException> { fixture.store.acquire(request) }
            assertFailsWith<okio.IOException> { fixture.store.acquire(request.copy(allowNetwork = false)) }
            assertEquals(0, fixture.store.sizeBytes())
        } finally { fixture.finish() }
    }

    @Test fun transientReloadDoesNotEraseThePersistentOriginal(): Unit = runBlocking {
        val calls = AtomicInteger()
        val fixture = Fixture { _, sink ->
            sink.writeUtf8("v${calls.incrementAndGet()}")
            info(2)
        }
        try {
            fixture.store.acquire(request).close()
            fixture.store.acquire(request.copy(persist = false, reloadToken = 9)).use {
                assertEquals("v2", it.readAt(0, 2).decodeToString())
            }
            fixture.store.closeAndAwait()
            val reopened = Fixture(directory = fixture.directory) { _, _ -> error("Must remain offline") }
            try {
                reopened.store.acquire(request.copy(allowNetwork = false)).use {
                    assertEquals("v1", it.readAt(0, 2).decodeToString())
                }
            } finally { reopened.store.closeAndAwait() }
            assertEquals(2, calls.get())
        } finally { fixture.finish() }
    }

    @Test fun shutdownWaitsForLeasesAndRepeatedCloseIsSafe(): Unit = runBlocking {
        val fixture = Fixture { _, sink -> sink.writeUtf8("data"); info(4) }
        try {
            val lease = fixture.store.acquire(request)
            fixture.store.close()
            val finished = async(start = CoroutineStart.UNDISPATCHED) { fixture.store.closeAndAwait() }
            assertFalse(finished.isCompleted)
            assertEquals("data", lease.readAt(0, 4).decodeToString())
            assertFailsWith<IllegalStateException> { fixture.store.acquire(request) }
            lease.close()
            lease.close()
            withTimeout(5000) { finished.await() }
            assertFailsWith<IllegalStateException> { lease.readAt(0, 1) }
        } finally { fixture.finish() }
    }

    @Test fun closeBeforeLazyDownloadStartsCannotLeaveAnUnfinishedEntry(): Unit = runBlocking {
        // Queue every store task until after the lazy job has been cancelled.
        val tasks = java.util.concurrent.ConcurrentLinkedQueue<Runnable>()
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) { tasks.add(block) }
        }
        val store = OriginalMediaStore("test", { error("Cache should not be opened") }, { _, _ -> error("No download") }, dispatcher)
        val acquire = async(start = CoroutineStart.UNDISPATCHED) { store.acquire(request) }
        store.close()
        acquire.cancel()
        withTimeout(5000) {
            while (tasks.isNotEmpty() || !acquire.isCompleted) {
                tasks.poll()?.run()
                yield()
            }
            val closing = async(start = CoroutineStart.UNDISPATCHED) { store.closeAndAwait() }
            while (!closing.isCompleted) { tasks.poll()?.run(); yield() }
            closing.await()
        }
    }

    @Test fun metadataReadIsBoundedAndRejectsOverflow(): Unit = runBlocking {
        val fixture = Fixture { _, sink -> sink.writeUtf8("data"); info(4) }
        try {
            fixture.store.acquire(request).use {
                assertFailsWith<IllegalArgumentException> { it.readAt(-1, 1) }
                assertFailsWith<IllegalArgumentException> { it.readAt(Long.MAX_VALUE, 2) }
                assertFailsWith<IllegalArgumentException> { it.readAt(0, 2 * 1024 * 1024 + 1) }
                assertContentEquals(byteArrayOf(), it.readAt(0, 0))
            }
        } finally { fixture.finish() }
    }

    @Test fun failedStreamingAttemptIsDiscardedBeforeSharedRetry(): Unit = runBlocking {
        val calls = AtomicInteger()
        val fixture = Fixture(downloader = object : OriginalMediaDownloader {
            override suspend fun download(request: OriginalMediaRequest, sink: okio.BufferedSink): OriginalMediaInfo {
                if (calls.incrementAndGet() < 3) {
                    sink.writeUtf8("broken prefix that must not survive")
                    throw java.net.ProtocolException("interrupted body")
                }
                sink.writeUtf8("complete")
                return info(8)
            }
            override suspend fun retryAfter(retry: Int, failure: Throwable) = true
        })
        try {
            fixture.store.acquire(request).use {
                assertEquals("complete", it.readAt(0, 100).decodeToString())
            }
            assertEquals(3, calls.get())
        } finally { fixture.finish() }
    }

    @Test fun retryBudgetStopsAtThreeAttempts(): Unit = runBlocking {
        val calls = AtomicInteger()
        val fixture = Fixture(downloader = object : OriginalMediaDownloader {
            override suspend fun download(request: OriginalMediaRequest, sink: okio.BufferedSink): OriginalMediaInfo {
                calls.incrementAndGet()
                sink.writeUtf8("partial")
                throw java.net.ProtocolException("interrupted body")
            }
            override suspend fun retryAfter(retry: Int, failure: Throwable) = true
        })
        try {
            assertFailsWith<java.net.ProtocolException> { fixture.store.acquire(request) }
            assertEquals(3, calls.get())
            assertEquals(0, fixture.store.sizeBytes())
        } finally { fixture.finish() }
    }

    @Test fun httpStatusRetryAndSizeRejectionUseDifferentPolicies(): Unit = runBlocking {
        val calls = AtomicInteger()
        val client = HttpClient(MockEngine {
            when (calls.incrementAndGet()) {
                1 -> respond("busy", HttpStatusCode.ServiceUnavailable)
                2 -> respond("done", HttpStatusCode.OK)
                else -> respond("oversized", HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, "9"))
            }
        })
        val downloader = KtorOriginalMediaDownloader(client, maxBytes = 4, waitBeforeRetry = { _, _ -> })
        val fixture = Fixture(downloader = downloader)
        try {
            fixture.store.acquire(request).use { assertEquals("done", it.readAt(0, 4).decodeToString()) }
            assertEquals(2, calls.get())
            assertFailsWith<okio.IOException> { fixture.store.acquire(request.copy(reloadToken = 1)) }
            assertEquals(3, calls.get(), "An oversized body must not trigger repeated downloads")
        } finally { fixture.finish(); downloader.close(); client.close() }
    }
}
