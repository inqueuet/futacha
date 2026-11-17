package com.valoser.futacha.shared.ui.image

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageMemoryPressureTest {
    @Test
    fun normalPolicyPreservesConfiguredQualityCacheAndParallelismAtEveryMemoryClass() {
        for (memoryClassMb in listOf(128, 256, 512)) {
            val policy = resolveImageMemoryPressurePolicy(
                baseParallelism = 6,
                baseMemoryCacheBytes = 64L * 1024L * 1024L,
                memoryClassMb = memoryClassMb,
                level = ImageMemoryPressureLevel.NORMAL
            )

            assertEquals(6, policy.maxParallelism)
            assertEquals(64L * 1024L * 1024L, policy.memoryCacheBytes)
            assertEquals(1, policy.heavyRequestWeight)
        }
    }

    @Test
    fun moderateAndCriticalPressureScale128256And512MiBConditionsInStages() {
        val moderate128 = policy(memoryClassMb = 128, ImageMemoryPressureLevel.MODERATE)
        val moderate256 = policy(memoryClassMb = 256, ImageMemoryPressureLevel.MODERATE)
        val moderate512 = policy(memoryClassMb = 512, ImageMemoryPressureLevel.MODERATE)
        val critical512 = policy(memoryClassMb = 512, ImageMemoryPressureLevel.CRITICAL)

        assertEquals(1, moderate128.maxParallelism)
        assertEquals(16L * 1024L * 1024L, moderate128.memoryCacheBytes)
        assertEquals(2, moderate256.maxParallelism)
        assertEquals((64L * 1024L * 1024L) / 3L, moderate256.memoryCacheBytes)
        assertEquals(3, moderate512.maxParallelism)
        assertEquals(32L * 1024L * 1024L, moderate512.memoryCacheBytes)
        assertEquals(1, critical512.maxParallelism)
        assertEquals(8L * 1024L * 1024L, critical512.memoryCacheBytes)
    }

    @Test
    fun requestWorkloadUsesDimensionsAndMediaTypeWithoutChangingRequestedSize() {
        assertEquals(
            ImageRequestWorkload.BOUNDED,
            classifyImageRequestWorkload(160, 160, false, "https://example.test/thumb.jpg")
        )
        assertEquals(
            ImageRequestWorkload.LARGE,
            classifyImageRequestWorkload(2_000, 1_500, false, "https://example.test/large.jpg")
        )
        assertEquals(
            ImageRequestWorkload.ORIGINAL,
            classifyImageRequestWorkload(null, null, true, "file:///viewer/photo.png")
        )
        assertEquals(
            ImageRequestWorkload.ANIMATED,
            classifyImageRequestWorkload(320, 240, false, "https://example.test/a.GIF?x=1")
        )
        assertEquals(
            ImageRequestWorkload.VIDEO,
            classifyImageRequestWorkload(320, 180, false, "https://example.test/a.webm#frame")
        )
    }

    @Test
    fun pressureSerializesLargeAnimatedAndVideoWorkThenRestoresConfiguredConcurrency() = runBlocking {
        val normal = policy(memoryClassMb = 512, ImageMemoryPressureLevel.NORMAL)
        val critical = policy(memoryClassMb = 512, ImageMemoryPressureLevel.CRITICAL)
        val gate = AdaptiveImageRequestGate(normal)

        suspend fun peakFor(workloads: List<ImageRequestWorkload>): Int {
            var running = 0
            var peak = 0
            val jobs = workloads.map { workload ->
                launch(start = CoroutineStart.UNDISPATCHED) {
                    gate.withPermit(workload) {
                        running += 1
                        peak = maxOf(peak, running)
                        yield()
                        running -= 1
                    }
                }
            }
            jobs.joinAll()
            return peak
        }

        gate.updatePolicy(critical)
        assertEquals(
            1,
            peakFor(
                listOf(
                    ImageRequestWorkload.LARGE,
                    ImageRequestWorkload.ANIMATED,
                    ImageRequestWorkload.VIDEO,
                    ImageRequestWorkload.ORIGINAL
                )
            )
        )

        gate.updatePolicy(normal)
        assertTrue(peakFor(List(4) { ImageRequestWorkload.BOUNDED }) > 1)
    }

    @Test
    fun cancelledHeavyWorkAndRepeatedWarningsDoNotLeakPermitsOrShortenRecovery() = runBlocking {
        val normal = policy(memoryClassMb = 256, ImageMemoryPressureLevel.NORMAL)
        val moderate = policy(memoryClassMb = 256, ImageMemoryPressureLevel.MODERATE)
        val gate = AdaptiveImageRequestGate(normal)
        gate.updatePolicy(moderate)
        val releaseActive = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val active = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.withPermit(ImageRequestWorkload.BOUNDED) {
                order += "active"
                releaseActive.await()
            }
        }
        val cancelled = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.withPermit(ImageRequestWorkload.ANIMATED) { order += "cancelled" }
        }
        cancelled.cancelAndJoin()
        releaseActive.complete(Unit)
        active.join()
        gate.withPermit(ImageRequestWorkload.VIDEO) { order += "after" }

        assertEquals(listOf("active", "after"), order)
        val first = ImageMemoryPressureSignal().withPressure(ImageMemoryPressureLevel.MODERATE)
        val repeated = first.withPressure(ImageMemoryPressureLevel.MODERATE)
        val escalated = repeated.withPressure(ImageMemoryPressureLevel.CRITICAL)
        assertTrue(repeated.generation > first.generation)
        assertEquals(ImageMemoryPressureLevel.CRITICAL, escalated.level)
        assertEquals(30_000L, imageMemoryPressureRecoveryDelayMillis(first.level))
        assertEquals(60_000L, imageMemoryPressureRecoveryDelayMillis(escalated.level))
    }

    private fun policy(
        memoryClassMb: Int,
        level: ImageMemoryPressureLevel
    ): ImageMemoryPressurePolicy = resolveImageMemoryPressurePolicy(
        baseParallelism = 6,
        baseMemoryCacheBytes = 64L * 1024L * 1024L,
        memoryClassMb = memoryClassMb,
        level = level
    )
}
