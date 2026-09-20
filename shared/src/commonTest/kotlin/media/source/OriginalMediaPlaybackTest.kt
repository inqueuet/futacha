package com.valoser.futacha.shared.media.source

import coil3.disk.DiskCache
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okio.BufferedSink
import okio.FileSystem
import okio.IOException
import kotlin.random.Random
import kotlin.test.*

/** Real growing disk file and rename/pinning contracts on Android host, JVM and iOS Native. */
class OriginalMediaPlaybackTest {
    private val request = OriginalMediaRequest("https://may.2chan.net/b/src/video.mp4")
    private inner class Transfer(val knownSize: Boolean = true, val fail: Boolean = false) : OriginalMediaDownloader {
        val prefixReady = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        val calls = MutableStateFlow(0)
        val retries = MutableStateFlow(0)
        override suspend fun download(request: OriginalMediaRequest, sink: BufferedSink) = download(request, sink) {}
        override suspend fun download(request: OriginalMediaRequest, sink: BufferedSink, onHeaders: (OriginalMediaInfo) -> Unit): OriginalMediaInfo {
            calls.update { it + 1 }
            val info = OriginalMediaInfo("video/mp4", if (knownSize) 12 else -1, etag = "v1", resolvedUrl = request.url)
            onHeaders(info)
            try {
                sink.writeUtf8("prefix").emit()
                prefixReady.complete(Unit)
                finish.await()
                if (fail) throw IOException("transfer failed")
                sink.writeUtf8("suffix")
                return info.copy(sizeBytes = 12)
            } finally { stopped.complete(Unit) }
        }
        override suspend fun retryAfter(retry: Int, failure: Throwable): Boolean { retries.update { it + 1 }; return true }
    }
    private suspend fun using(transfer: Transfer = Transfer(), block: suspend (OriginalMediaStore, Transfer) -> Unit) {
        val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("futacha-stream-test-${Random.nextLong()}")
        val store = OriginalMediaStore("stream-test", { DiskCache.Builder().directory(dir).maxSizeBytes(1024 * 1024).build() }, transfer)
        try { withTimeout(10_000) { block(store, transfer) } }
        finally { withTimeout(5_000) { store.closeAndAwait() }; FileSystem.SYSTEM.deleteRecursively(dir, mustExist = false) }
    }

    @Test fun playbackReadsPrefixBeforeCompletionAndDisplayExportShareOneTransfer(): Unit = runBlocking {
        using { store, transfer ->
            val player = store.acquireForPlayback(request)
            val other = player.retain()
            try {
                transfer.prefixReady.await()
                assertEquals(12, player.info().sizeBytes)
                assertEquals("prefix", player.readAt(0, 64).decodeToString())
                assertEquals("fix", other.readAt(3, 3).decodeToString())
                val tail = async(start = CoroutineStart.UNDISPATCHED) { player.readAt(6, 64) }
                val complete = async(start = CoroutineStart.UNDISPATCHED) { other.complete() }
                val saving = async(start = CoroutineStart.UNDISPATCHED) { store.acquireForExport(request) }
                assertFalse(tail.isCompleted); assertFalse(complete.isCompleted); assertFalse(saving.isCompleted)
                transfer.finish.complete(Unit)
                assertEquals("suffix", tail.await().decodeToString())
                complete.await().use { a -> saving.await().use { b ->
                    assertEquals(a.identity, b.identity)
                    assertEquals(a.file, b.file)
                    assertEquals("prefixsuffix", a.readAt(0, 64).decodeToString())
                } }
                assertTrue(player.readAt(12, 1).isEmpty())
                assertEquals("prefixsuffix", other.readAt(0, 64).decodeToString(), "Read handle survives cache commit/rename")
                assertEquals(1, transfer.calls.value)
            } finally { player.close(); other.close() }
        }
    }

    @Test fun unknownLengthRemainsUnknownUntilVerifiedEof(): Unit = runBlocking {
        using(Transfer(knownSize = false)) { store, transfer ->
            store.acquireForPlayback(request).use { player ->
                transfer.prefixReady.await()
                assertEquals(-1, player.info().sizeBytes)
                val size = async(start = CoroutineStart.UNDISPATCHED) { player.info(requireSize = true) }
                assertFalse(size.isCompleted)
                assertEquals("prefix", player.readAt(0, 64).decodeToString())
                transfer.finish.complete(Unit)
                assertEquals(12, size.await().sizeBytes)
                assertTrue(player.readAt(12, 64).isEmpty())
            }
        }
    }

    @Test fun closingReaderWakesItsPendingReadsAndDoesNotCancelAnotherConsumer(): Unit = runBlocking {
        using { store, transfer ->
            val player = store.acquireForPlayback(request)
            val other = player.retain()
            try {
                transfer.prefixReady.await()
                val tail = async(start = CoroutineStart.UNDISPATCHED) { runCatching { player.readAt(6, 1) } }
                val complete = async(start = CoroutineStart.UNDISPATCHED) { runCatching { player.complete() } }
                player.close(); player.close()
                assertIs<IllegalStateException>(tail.await().exceptionOrNull())
                assertIs<IllegalStateException>(complete.await().exceptionOrNull())
                assertFalse(transfer.stopped.isCompleted)
                assertEquals("prefix", other.readAt(0, 6).decodeToString())
                transfer.finish.complete(Unit)
                other.complete().use { assertEquals("prefixsuffix", it.readAt(0, 12).decodeToString()) }
                assertEquals(1, transfer.calls.value)
            } finally { player.close(); other.close() }
        }
    }

    @Test fun lastConsumerCloseCancelsDownloadAndDiscardsPartialOriginal(): Unit = runBlocking {
        using { store, transfer ->
            val player = store.acquireForPlayback(request)
            transfer.prefixReady.await()
            player.close()
            transfer.stopped.await()
            assertFailsWith<IOException> { store.acquire(request.copy(allowNetwork = false)) }
            transfer.finish.complete(Unit)
            store.acquire(request).use { assertEquals("prefixsuffix", it.readAt(0, 12).decodeToString()) }
            assertEquals(2, transfer.calls.value)
        }
    }

    @Test fun failedStreamNeverReturnsFalseEofOrSilentlyRetriesWithDifferentBytes(): Unit = runBlocking {
        using(Transfer(fail = true)) { store, transfer ->
            store.acquireForPlayback(request).use { player ->
                transfer.prefixReady.await()
                assertEquals("prefix", player.readAt(0, 6).decodeToString())
                val tail = async(start = CoroutineStart.UNDISPATCHED) { runCatching { player.readAt(6, 1) } }
                transfer.finish.complete(Unit)
                assertIs<IOException>(tail.await().exceptionOrNull())
                assertFailsWith<IOException> { player.complete() }
                assertFailsWith<IOException> { player.readAt(12, 1) }
                assertEquals(1, transfer.calls.value)
                assertEquals(0, transfer.retries.value)
            }
        }
    }

    @Test fun cacheClearKeepsCompletedPinnedBytesAndCreatesFreshRevision(): Unit = runBlocking {
        using { store, transfer ->
            transfer.finish.complete(Unit)
            store.acquireForPlayback(request).use { player ->
                val identity = player.complete().use { it.identity }
                store.clear()
                assertEquals("prefixsuffix", player.readAt(0, 12).decodeToString())
                player.complete().use { assertEquals(identity, it.identity) }
                store.acquire(request).use { assertNotEquals(identity, it.identity) }
                assertEquals(2, transfer.calls.value)
            }
        }
    }

    @Test fun cacheClearCancelsUnfinishedPlaybackAndPreventsStaleCommit(): Unit = runBlocking {
        using { store, transfer ->
            store.acquireForPlayback(request).use { player ->
                transfer.prefixReady.await()
                store.clear()
                assertFailsWith<CancellationException> { player.complete() }
                assertFailsWith<CancellationException> { player.readAt(6, 1) }
            }
            assertFailsWith<IOException> { store.acquire(request.copy(allowNetwork = false)) }
        }
    }

    @Test fun cachedFileStartsWithoutAnotherDownloadAndRejectsInvalidReadBounds(): Unit = runBlocking {
        using { store, transfer ->
            transfer.finish.complete(Unit)
            store.acquire(request).close()
            store.acquireForPlayback(request.copy(allowNetwork = false)).use { player ->
                player.complete().use { assertTrue(it.fromCache) }
                assertEquals("prefixsuffix", player.readAt(0, 12).decodeToString())
                assertTrue(player.readAt(Long.MAX_VALUE - 1, 1).isEmpty())
                assertFailsWith<IllegalArgumentException> { player.readAt(-1, 1) }
                assertFailsWith<IllegalArgumentException> { player.readAt(Long.MAX_VALUE, 1) }
                assertFailsWith<IllegalArgumentException> { player.readAt(0, 2 * 1024 * 1024 + 1) }
                assertEquals(1, transfer.calls.value)
            }
        }
    }
}
