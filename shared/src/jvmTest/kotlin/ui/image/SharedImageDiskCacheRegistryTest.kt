package com.valoser.futacha.shared.ui.image

import coil3.disk.DiskCache
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.test.runTest
import kotlin.test.assertFalse
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class SharedImageDiskCacheRegistryTest {
    private val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
        "futacha-shared-disk-cache-${Random.nextLong().toULong()}"

    @AfterTest
    fun cleanUp() {
        FileSystem.SYSTEM.deleteRecursively(directory)
    }

    @Test
    fun loadersOnTheSameDirectoryShareOneCacheUntilTheLastRelease() = runTest {
        val registry = SharedImageDiskCacheRegistry()
        val first = assertNotNull(registry.acquire(directory, 1_000_000L))
        // A replacement loader opened before the old one is shut down.
        val second = assertNotNull(registry.acquire("$directory/./".toPath(), 1_000_000L))
        assertSame(first, second)
        assertEquals(2, registry.holderCount(directory))

        registry.release(first)
        first.openEditor("still-open")?.abort()

        registry.release(second)
        assertEquals(0, registry.holderCount(directory))
        assertFailsWith<IllegalStateException> { first.openEditor("closed") }

        val reopened = assertNotNull(registry.acquire(directory, 2_000_000L))
        assertNotSame(first, reopened)
        assertEquals(2_000_000L, reopened.maxSize)
        registry.release(reopened)
    }
    @Test
    fun acquireWaitsForThePreviousCacheToClose() = runBlocking {
        val closing = CountDownLatch(1)
        val mayClose = CountDownLatch(1)
        val acquiring = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(3)
        var first = true
        val registry = SharedImageDiskCacheRegistry { path, maxBytes ->
            val cache = DiskCache.Builder().directory(path).maxSizeBytes(maxBytes).build()
            if (!first) cache else {
                first = false
                object : DiskCache by cache {
                    override fun shutdown() {
                        closing.countDown()
                        check(mayClose.await(10, SECONDS))
                        cache.shutdown()
                    }
                }
            }
        }
        try {
            val old = assertNotNull(registry.acquire(directory, 1_000_000))
            old.openEditor("establish-journal")?.abort()
            val released = executor.submit { runBlocking { registry.release(old) } }
            check(closing.await(5, SECONDS))
            val reopened = executor.submit<DiskCache?> {
                acquiring.countDown()
                runBlocking { registry.acquire(directory, 1_000_000) }
            }
            check(acquiring.await(5, SECONDS))
            assertFailsWith<TimeoutException> { reopened.get(1, SECONDS) }
            val other = executor.submit<DiskCache?> { runBlocking { registry.acquire(directory / "other", 1_000_000) } }
            registry.release(assertNotNull(other.get(5, SECONDS)))
            mayClose.countDown()
            released.get(5, SECONDS)
            val next = assertNotNull(reopened.get(5, SECONDS))
            assertNotSame(old, next)
            registry.release(next)
        } finally {
            mayClose.countDown()
            executor.shutdownNow()
            check(executor.awaitTermination(5, SECONDS))
        }
    }

    @Test
    fun newCapacityWaitsForTheOldLeaseEvenBeforeCloseStarts() = runTest {
        val registry = SharedImageDiskCacheRegistry()
        val old = assertNotNull(registry.acquire(directory, 1_000_000L))
        val next = async(start = CoroutineStart.UNDISPATCHED) {
            registry.acquire(directory, 2_000_000L)
        }
        assertFalse(next.isCompleted)
        registry.release(old)
        val resized = assertNotNull(next.await())
        assertNotSame(old, resized)
        assertEquals(2_000_000L, resized.maxSize)
        registry.release(resized)
    }

}
