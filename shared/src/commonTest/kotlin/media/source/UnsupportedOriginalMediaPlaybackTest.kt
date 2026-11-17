package com.valoser.futacha.shared.media.source

import coil3.disk.DiskCache
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okio.BufferedSink
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.*

/** Real store jobs/bytes; the compatibility extension lets the same tests exercise the previous plain-close behavior. */
class UnsupportedOriginalMediaPlaybackTest {
    private val request = OriginalMediaRequest("https://may.2chan.net/b/src/redirect.mp4")

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    private fun OriginalMediaPlayback.rejectAsUnsupported() = close()

    private class Transfer : OriginalMediaDownloader {
        val headers = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        val calls = MutableStateFlow(0)
        override suspend fun download(request: OriginalMediaRequest, sink: BufferedSink) = download(request, sink) {}
        override suspend fun download(request: OriginalMediaRequest, sink: BufferedSink, onHeaders: (OriginalMediaInfo) -> Unit): OriginalMediaInfo {
            calls.update { it + 1 }
            val info = OriginalMediaInfo("video/webm", 12, resolvedUrl = "https://may.2chan.net/b/src/resolved.WEBM")
            try {
                onHeaders(info)
                sink.writeUtf8("prefix").emit()
                headers.complete(Unit)
                finish.await()
                sink.writeUtf8("suffix")
                return info
            } finally { stopped.complete(Unit) }
        }
    }

    private suspend fun fixture(block: suspend (OriginalMediaStore, Transfer) -> Unit) {
        val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("unsupported-playback-${Random.nextLong()}")
        val transfer = Transfer()
        val store = OriginalMediaStore("unsupported-playback", {
            DiskCache.Builder().directory(directory).maxSizeBytes(1024 * 1024).build()
        }, transfer, playbackLingerMillis = 60_000)
        try { withTimeout(8_000) { block(store, transfer) } }
        finally {
            withTimeout(5_000) { store.closeAndAwait() }
            FileSystem.SYSTEM.deleteRecursively(directory, mustExist = false)
        }
    }

    @Test fun rejectingTheLastPlayerStopsTheUnsupportedTransferWithoutTheSixtySecondLinger(): Unit = runBlocking {
        fixture { store, transfer ->
            val player = store.acquireForPlayback(request)
            transfer.headers.await()
            assertEquals("video/webm", player.info().mimeType)
            player.rejectAsUnsupported()
            player.rejectAsUnsupported() // Idempotent, including after the owner has been released.
            withTimeout(1_000) { transfer.stopped.await() }
            assertEquals(1, transfer.calls.value)
        }
    }

    @Test fun anotherActivePlayerSurvivesRejectionAndItsNormalCloseStillLingersForRetry(): Unit = runBlocking {
        fixture { store, transfer ->
            val rejected = store.acquireForPlayback(request)
            val other = store.acquireForPlayback(request)
            transfer.headers.await()
            rejected.rejectAsUnsupported()
            assertEquals("prefix", other.readAt(0, 6).decodeToString())
            delay(40)
            assertFalse(transfer.stopped.isCompleted)
            other.close()
            delay(40)
            assertFalse(transfer.stopped.isCompleted)
            store.acquireForPlayback(request).use { retry ->
                transfer.finish.complete(Unit)
                retry.complete().use { assertEquals("prefixsuffix", it.readAt(0, 12).decodeToString()) }
            }
            assertEquals(1, transfer.calls.value)
        }
    }

    @Test fun retainedPlaybackPinsProtectTheTransferUntilTheLastPinIsClosed(): Unit = runBlocking {
        fixture { store, transfer ->
            val player = store.acquireForPlayback(request)
            val pin = player.retain()
            transfer.headers.await()
            player.rejectAsUnsupported()
            assertEquals("prefix", pin.readAt(0, 6).decodeToString())
            assertFalse(transfer.stopped.isCompleted)
            pin.close()
            withTimeout(1_000) { transfer.stopped.await() }
            assertEquals(1, transfer.calls.value)
        }
    }

    @Test fun rejectingACompletedPlayerDoesNotDeleteCachedBytesOrAnExportLease(): Unit = runBlocking {
        fixture { store, transfer ->
            val player = store.acquireForPlayback(request)
            transfer.headers.await()
            transfer.finish.complete(Unit)
            val export = player.complete()
            player.rejectAsUnsupported()
            export.use { assertEquals("prefixsuffix", it.readAt(0, 12).decodeToString()) }
            store.acquire(request.copy(allowNetwork = false)).use {
                assertTrue(it.fromCache)
                assertEquals("prefixsuffix", it.readAt(0, 12).decodeToString())
            }
            assertEquals(1, transfer.calls.value)
        }
    }
}
