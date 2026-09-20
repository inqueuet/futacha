package com.valoser.futacha.shared.media.source

import coil3.disk.DiskCache
import kotlinx.coroutines.*
import okio.Path.Companion.toPath
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class OriginalMediaSessionTest {
    private val request = OriginalMediaRequest("https://may.2chan.net/b/src/image.png")
    private val quota = 1024L * 1024

    @Test fun acquireWaitsForConfigurationAndRepeatedConfigurationKeepsCache(): Unit = runBlocking {
        val directory = Files.createTempDirectory("original-session").toFile()
        val calls = AtomicInteger()
        val cacheOpens = AtomicInteger()
        val session = OriginalMediaSession("test", { _, sink ->
            calls.incrementAndGet(); sink.writeUtf8("original")
            OriginalMediaInfo("image/png", 8, resolvedUrl = request.url)
        }, createCache = {
            cacheOpens.incrementAndGet()
            DiskCache.Builder().directory(it.directory).maxSizeBytes(it.maxBytes).build()
        })
        try {
            val waiting = async(start = CoroutineStart.UNDISPATCHED) { session.acquire(request) }
            assertFalse(waiting.isCompleted)
            assertEquals(0, calls.get())
            assertEquals(0, cacheOpens.get())
            val config = OriginalMediaCacheConfiguration(directory.path.toPath(), quota)
            session.configure(config)
            withTimeout(5000) { waiting.await() }.use { first ->
                repeat(3) { session.configure(config) }
                session.acquire(request).use { assertEquals(first.identity, it.identity) }
            }
            assertEquals(1, calls.get())
            assertEquals(1, cacheOpens.get())
        } finally { withTimeout(5000) { session.closeAndAwait() }; directory.deleteRecursively() }
    }

    @Test fun quotaChangeWaitsForOldLeasesBeforeReopeningTheSameDirectory(): Unit = runBlocking {
        val directory = Files.createTempDirectory("original-quota").toFile()
        val opens = AtomicInteger()
        val session = OriginalMediaSession("test", { _, sink ->
            sink.writeUtf8("bytes"); OriginalMediaInfo("image/png", 5, resolvedUrl = request.url)
        }, createCache = {
            opens.incrementAndGet()
            DiskCache.Builder().directory(it.directory).maxSizeBytes(it.maxBytes).build()
        })
        try {
            val config = OriginalMediaCacheConfiguration(directory.path.toPath(), quota)
            session.configure(config)
            val old = session.acquire(request)
            session.configure(config.copy(maxBytes = quota / 2))
            val waiting = async(start = CoroutineStart.UNDISPATCHED) { session.acquire(request.copy(allowNetwork = false)) }
            assertFalse(waiting.isCompleted)
            assertEquals(1, opens.get())
            assertEquals("bytes", old.readAt(0, 5).decodeToString())
            old.close()
            withTimeout(5000) { waiting.await() }.use { assertTrue(it.fromCache) }
            assertEquals(2, opens.get())
        } finally { withTimeout(5000) { session.closeAndAwait() }; directory.deleteRecursively() }
    }

    @Test fun relocatingCacheDropsOldFilesAndClearForcesFreshBytes(): Unit = runBlocking {
        val root = Files.createTempDirectory("original-relocate").toFile()
        val calls = AtomicInteger()
        val session = OriginalMediaSession("test", { _, sink ->
            val text = "v${calls.incrementAndGet()}"; sink.writeUtf8(text)
            OriginalMediaInfo("image/png", text.length.toLong(), resolvedUrl = request.url)
        })
        try {
            session.configure(OriginalMediaCacheConfiguration(root.resolve("first").path.toPath(), quota))
            val oldFile = session.acquire(request).use { it.file }
            session.configure(OriginalMediaCacheConfiguration(root.resolve("second").path.toPath(), quota))
            withTimeout(5000) { session.acquire(request) }.use { assertEquals("v2", it.readAt(0, 2).decodeToString()) }
            assertFalse(java.io.File(oldFile.toString()).exists())
            assertTrue(session.sizeBytes() > 0)
            session.clear()
            session.acquire(request).use { assertEquals("v3", it.readAt(0, 2).decodeToString()) }
        } finally { withTimeout(5000) { session.closeAndAwait() }; root.deleteRecursively() }
    }

    @Test fun shutdownReleasesAnAcquireWaitingForInitialConfiguration(): Unit = runBlocking {
        val session = OriginalMediaSession("test", { _, _ -> error("No network expected") })
        supervisorScope {
            val waiting = async(start = CoroutineStart.UNDISPATCHED) { session.acquire(request) }
            session.closeAndAwait()
            withTimeout(5000) { assertFailsWith<IllegalStateException> { waiting.await() } }
        }
    }
}
