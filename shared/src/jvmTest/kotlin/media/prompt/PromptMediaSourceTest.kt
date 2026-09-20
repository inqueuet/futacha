package com.valoser.futacha.shared.media.prompt

import coil3.disk.DiskCache
import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.source.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toPath
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class PromptMediaSourceTest {
    private val request = OriginalMediaRequest("https://may.2chan.net/b/src/test.png")
    private val on = MediaFeatureSettings(promptDisplayEnabled = true)
    private fun result(value: String) = GenerationMetadata(listOf(GenerationCandidate("test", value, raw = value, isAi = true)), MetadataCoverage.PNG_METADATA)

    private suspend fun fixture(test: suspend (OriginalMediaStore, AtomicInteger) -> Unit) {
        val directory = Files.createTempDirectory("prompt-source").toFile()
        val calls = AtomicInteger()
        val store = OriginalMediaStore("test", {
            DiskCache.Builder().directory(directory.path.toPath()).maxSizeBytes(1024 * 1024).build()
        }, { request, sink ->
            val value = "v${calls.incrementAndGet()}"
            sink.writeUtf8(value)
            OriginalMediaInfo("image/png", value.length.toLong(), resolvedUrl = request.url)
        })
        try { withTimeout(10_000) { test(store, calls) } }
        finally { withTimeout(5_000) { store.closeAndAwait() }; directory.deleteRecursively() }
    }

    @Test fun localReadsAreGatedCoalescedRefreshedAndNeverDownload(): Unit = runBlocking {
        fixture { store, downloads ->
            val gate = MediaFeatureGate()
            val started = CompletableDeferred<Unit>(); val proceed = CompletableDeferred<Unit>()
            val reads = AtomicInteger(); val url = "/saved/original.png"
            val source = PromptMediaSource(store, gate, readLocalMetadata = {
                val count = reads.incrementAndGet()
                if (count == 1) { started.complete(Unit); proceed.await() }
                result("local$count")
            })
            try {
                source.inspectCached(url)
                assertEquals(0, reads.get()); assertNull(source.metadata(url))
                gate.update(on)
                source.inspectCached(url); started.await(); source.inspectCached(url)
                assertEquals(1, reads.get())
                proceed.complete(Unit)
                source.changes.first { source.metadata(url)?.candidates?.single()?.positive == "local1" }
                // Let completion release its in-flight slot before reopening.
                yield(); delay(10)
                source.inspectCached(url)
                source.changes.first { source.metadata(url)?.candidates?.single()?.positive == "local2" }
                gate.update(MediaFeatureSettings.Disabled)
                assertNull(source.metadata(url))
                source.inspectCached(url); assertEquals(2, reads.get())
                assertEquals(0, downloads.get())
            } finally { proceed.complete(Unit); source.close() }
        }
    }

    @Test fun disablingLocalPromptCancelsTheReaderAndDropsItsResult(): Unit = runBlocking {
        fixture { store, downloads ->
            val gate = MediaFeatureGate().apply { update(on) }
            val started = CompletableDeferred<Unit>(); val released = CompletableDeferred<Unit>()
            val source = PromptMediaSource(store, gate, readLocalMetadata = {
                started.complete(Unit)
                try { awaitCancellation() } finally { released.complete(Unit) }
            })
            try {
                source.inspectCached("content://saved/image.png"); started.await()
                gate.update(MediaFeatureSettings.Disabled); released.await()
                assertNull(source.metadata("content://saved/image.png")); assertEquals(0, downloads.get())
            } finally { source.close() }
        }
    }

    @Test fun offDoesNoParsingAndLateEnableOnlyReadsCachedOriginal(): Unit = runBlocking {
        fixture { store, calls ->
            val gate = MediaFeatureGate()
            val reads = AtomicInteger()
            val source = PromptMediaSource(store, gate) { lease -> reads.incrementAndGet(); result(lease.readAt(0, 2).decodeToString()) }
            try {
                source.acquire(request).close()
                assertEquals(0, reads.get())
                assertNull(source.metadata(request.url))
                gate.update(on)
                source.inspectCached(request.url)
                source.changes.first { source.metadata(request.url) != null }
                assertEquals("v1", source.metadata(request.url)!!.candidates.single().positive)
                assertEquals(1, reads.get())
                source.inspectCached(request.url)
                source.inspectCached(request.url + "?missing=1")
                assertEquals(1, reads.get())
                assertEquals(1, calls.get(), "Metadata must not download even a missing original")
            } finally { source.close() }
        }
    }

    @Test fun offCancelsOnlyMetadataLeaseAndFreshOnCanParseAgain(): Unit = runBlocking {
        fixture { store, calls ->
            val gate = MediaFeatureGate().apply { update(on) }
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            var shouldBlock = true
            val source = PromptMediaSource(store, gate) { lease ->
                if (shouldBlock) {
                    started.complete(Unit)
                    try { awaitCancellation() } finally { cancelled.complete(Unit) }
                }
                result(lease.readAt(0, 2).decodeToString())
            }
            val display = source.acquire(request)
            try {
                started.await()
                gate.update(MediaFeatureSettings.Disabled)
                cancelled.await()
                assertNull(source.metadata(request.url))
                assertEquals("v1", display.readAt(0, 2).decodeToString())
                shouldBlock = false
                gate.update(on)
                source.inspectCached(request.url)
                source.changes.first { source.metadata(request.url) != null }
                assertEquals("v1", source.metadata(request.url)!!.candidates.single().positive)
                assertEquals(1, calls.get())
            } finally { display.close(); source.close() }
        }
    }

    @Test fun refreshPublishesNewRevisionAndRetainedOldBytesStayReadable(): Unit = runBlocking {
        fixture { store, calls ->
            val gate = MediaFeatureGate().apply { update(on) }
            val source = PromptMediaSource(store, gate) { result(it.readAt(0, 2).decodeToString()) }
            val display = source.acquire(request)
            val retained = display.retain()
            display.close()
            try {
                source.changes.first { source.metadata(request.url) != null }
                source.acquire(request.copy(reloadToken = 99)).close()
                source.changes.first { source.metadata(request.url)?.candidates?.single()?.positive == "v2" }
                assertEquals("v1", retained.readAt(0, 2).decodeToString())
                source.bindSuccessfulUrl("https://may.2chan.net/b/src/guessed.jpg", request.url)
                assertEquals("v2", source.metadata("https://may.2chan.net/b/src/guessed.jpg")!!.candidates.single().positive)
                assertEquals(2, calls.get())
                source.clear()
                assertNull(source.metadata(request.url))
                assertEquals("v1", retained.readAt(0, 2).decodeToString())
            } finally { retained.close(); source.close() }
        }
    }

    @Test fun cacheOnlyProbeCannotPoisonConcurrentDisplayDownload(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val finishCacheMiss = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val directory = Files.createTempDirectory("prompt-cache-race").toFile()
        val store = OriginalMediaStore("test", {
            DiskCache.Builder().directory(directory.path.toPath()).maxSizeBytes(1024 * 1024).build()
        }, object : OriginalMediaDownloader {
            override suspend fun downloadCached(request: OriginalMediaRequest, sink: okio.BufferedSink): OriginalMediaInfo? {
                if (!request.allowNetwork) { entered.complete(Unit); finishCacheMiss.await() }
                return null
            }
            override suspend fun download(request: OriginalMediaRequest, sink: okio.BufferedSink): OriginalMediaInfo {
                calls.incrementAndGet(); sink.writeUtf8("ok")
                return OriginalMediaInfo("image/png", 2, resolvedUrl = request.url)
            }
        })
        try {
            withTimeout(5_000) {
                val probe = async { runCatching { store.acquire(request.copy(allowNetwork = false)).close() } }
                entered.await()
                val display = async { store.acquire(request) }
                display.await().use { assertEquals("ok", it.readAt(0, 2).decodeToString()) }
                finishCacheMiss.complete(Unit)
                probe.await()
                assertEquals(1, calls.get())
            }
        } finally { finishCacheMiss.complete(Unit); store.closeAndAwait(); directory.deleteRecursively() }
    }

    @Test fun permissionCycleDuringDisplayAcquireAcceptsTheSameOriginalWithNewPermission(): Unit = runBlocking {
        fixture { store, calls ->
            val gate = MediaFeatureGate().apply { update(on) }
            val entered = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val delayed = object : OriginalMediaSource by store {
                override suspend fun acquire(request: OriginalMediaRequest): OriginalMediaStore.Lease {
                    entered.complete(Unit); finish.await(); return store.acquire(request)
                }
            }
            val source = PromptMediaSource(delayed, gate) { result(it.readAt(0, 2).decodeToString()) }
            try {
                val display = async { source.acquire(request).close() }
                entered.await()
                val revision = source.changes.value
                gate.update(MediaFeatureSettings.Disabled)
                source.changes.first { it > revision }
                gate.update(on)
                finish.complete(Unit); display.await()
                source.changes.first { source.metadata(request.url) != null }
                assertEquals("v1", source.metadata(request.url)!!.candidates.single().positive)
                assertEquals(1, calls.get())
            } finally { finish.complete(Unit); source.close() }
        }
    }

    @Test fun playbackCompletionIsByteOnlyWhileOffAndCannotRepublishAfterClear(): Unit = runBlocking {
        fixture { store, calls ->
            val gate = MediaFeatureGate()
            val parses = AtomicInteger()
            val source = PromptMediaSource(store, gate) { parses.incrementAndGet(); result(it.readAt(0, 2).decodeToString()) }
            try {
                source.acquireForPlayback(request).use { playback ->
                    playback.complete().close()
                    assertEquals(0, parses.get())
                    gate.update(on)
                    playback.retain().use { it.complete().close() }
                    source.changes.first { source.metadata(request.url) != null }
                    assertEquals(1, parses.get())
                    source.clear()
                    playback.complete().use { assertEquals("v1", it.readAt(0, 2).decodeToString()) }
                    assertNull(source.metadata(request.url), "Cleared identity cannot return through a pinned player")
                    assertEquals(1, calls.get())
                }
            } finally { source.close() }
        }
    }

    @Test fun playbackJoiningDuringCacheClearCannotRestoreTheDiscardedIdentity(): Unit = runBlocking {
        fixture { store, _ ->
            val entered = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val clearingStore = object : OriginalMediaSource by store {
                override suspend fun clear() { entered.complete(Unit); finish.await(); store.clear() }
            }
            val source = PromptMediaSource(clearingStore, MediaFeatureGate().apply { update(on) }) { result("prompt") }
            try {
                source.acquire(request).close()
                source.changes.first { source.metadata(request.url) != null }
                val clearing = async { source.clear() }
                entered.await()
                source.acquireForPlayback(request).use { player ->
                    player.complete().close()
                    assertNull(source.metadata(request.url))
                    finish.complete(Unit); clearing.await()
                    player.complete().close()
                    assertNull(source.metadata(request.url))
                }
            } finally { finish.complete(Unit); source.close() }
        }
    }
}
