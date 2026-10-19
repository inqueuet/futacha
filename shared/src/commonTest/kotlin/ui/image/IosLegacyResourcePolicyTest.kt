package com.valoser.futacha.shared.ui.image

import com.valoser.futacha.shared.util.iosMemoryPerformanceProfile
import com.valoser.futacha.shared.util.readUnknownSizePickedFile
import kotlinx.coroutines.*
import kotlin.test.*

class IosLegacyResourcePolicyTest {
    @Test fun physicalRamBoundaryIncludesTwoGiBWithoutTreatingUnknownAsLow() {
        for (ram in listOf(1024, 2047, 2048)) assertTrue(iosMemoryPerformanceProfile(ram).isLowRam)
        for (ram in listOf(0, 2049, 3072)) assertFalse(iosMemoryPerformanceProfile(ram).isLowRam)
    }

    @Test fun twoLoadersShareThirtyTwoMiBAndKeepExplicitDiskQuotas() {
        val profile = iosMemoryPerformanceProfile(2048)
        fun config(directory: String) = resolveCacheConfig(false, profile,
            diskCacheBytesOverride = 123_456, parallelismOverride = 8, diskCacheDirectoryName = directory)
        val general = config(IMAGE_DISK_CACHE_DIR)
        val catalog = config(CATALOG_IMAGE_DISK_CACHE_DIR)
        assertEquals(24L * 1024 * 1024, general.memoryCacheBytes)
        assertEquals(8L * 1024 * 1024, catalog.memoryCacheBytes)
        assertEquals(profile.imageMemoryBudgetBytes, general.memoryCacheBytes + catalog.memoryCacheBytes)
        assertEquals(123_456L, general.diskCacheBytes)
        assertEquals(2, general.parallelism)
        assertEquals(2, catalog.parallelism)
        assertEquals(1, resolveCacheConfig(false, profile, parallelismOverride = 1).parallelism)
    }

    @Test fun higherRamKeepsExistingImageConfiguration() {
        val config = resolveCacheConfig(false, iosMemoryPerformanceProfile(3072), parallelismOverride = 8)
        assertEquals(64L * 1024 * 1024, config.memoryCacheBytes)
        assertEquals(8, config.parallelism)
    }

    @Test fun smallUnknownFileDoesNotAllocateItsHundredMiBLimit() {
        val expected = ByteArray(1024) { (it % 251).toByte() }
        var position = 0
        var largestBuffer = 0
        val actual = readUnknownSizePickedFile(100L * 1024 * 1024) { buffer, requested ->
            largestBuffer = maxOf(largestBuffer, buffer.size)
            val count = minOf(requested, expected.size - position, 137)
            expected.copyInto(buffer, 0, position, position + count)
            position += count
            count
        }
        assertContentEquals(expected, actual)
        assertTrue(largestBuffer <= 64 * 1024)
    }

    @Test fun unknownFileAllowsExactLimitButRejectsOneExtraByteAndReadFailure() {
        fun read(size: Int): ByteArray? {
            var remaining = size
            return readUnknownSizePickedFile(7) { buffer, requested ->
                val count = minOf(remaining, requested, 2)
                buffer.fill(42, 0, count)
                remaining -= count
                count
            }
        }
        assertContentEquals(ByteArray(7) { 42 }, read(7))
        assertNull(read(8))
        assertNull(read(0))
        assertNull(readUnknownSizePickedFile(7) { _, _ -> -1 })
        assertFailsWith<CancellationException> {
            readUnknownSizePickedFile(7) { _, _ -> throw CancellationException("closed") }
        }
    }

    @Test fun queuedHeavyWorkLeavesTheOtherLoaderThumbnailSlotAndCancellationReleasesPermits(): Unit = runBlocking {
        withTimeout(5_000) {
            val gate = LowMemoryImageRequestGate()
            val never = CompletableDeferred<Unit>()
            val first = launch(start = CoroutineStart.UNDISPATCHED) {
                gate.withPermit(ImageRequestWorkload.ORIGINAL) { never.await() }
            }
            var queuedEntered = false
            val queued = launch(start = CoroutineStart.UNDISPATCHED) {
                gate.withPermit(ImageRequestWorkload.VIDEO) { queuedEntered = true }
            }
            gate.withPermit(ImageRequestWorkload.BOUNDED) { assertFalse(queuedEntered) }
            queued.cancelAndJoin()
            first.cancelAndJoin()
            gate.withPermit(ImageRequestWorkload.ANIMATED) { }
            assertFailsWith<IllegalStateException> {
                gate.withPermit(ImageRequestWorkload.LARGE) { error("decode failed") }
            }
            gate.withPermit(ImageRequestWorkload.LARGE) { }
        }
    }

    @Test fun combinedBoundedRequestsNeverUseMoreThanTwoSlots(): Unit = runBlocking {
        withTimeout(5_000) {
            val gate = LowMemoryImageRequestGate()
            val never = CompletableDeferred<Unit>()
            var entered = 0
            fun start() = launch(start = CoroutineStart.UNDISPATCHED) {
                gate.withPermit(ImageRequestWorkload.BOUNDED) { entered++; never.await() }
            }
            val first = start(); val second = start(); val third = start()
            assertEquals(2, entered)
            first.cancelAndJoin()
            yield()
            assertEquals(3, entered)
            second.cancelAndJoin(); third.cancelAndJoin()
        }
    }
}
