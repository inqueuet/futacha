package com.valoser.futacha.shared.ui.image

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.test.*

class M51ImageProcessingGateTest {
    @Test fun visibleOvertakesQueuedPrefetchAndHeavyWaitDoesNotReserveThumbnailSlot(): Unit = runBlocking {
        withTimeout(5000) {
            val gate = LowMemoryImageRequestGate()
            val releaseFirst = CompletableDeferred<Unit>()
            val releaseVisible = CompletableDeferred<Unit>()
            val order = mutableListOf<String>()
            val first = launch(start = CoroutineStart.UNDISPATCHED) {
                gate.withPermit(ImageRequestWorkload.ORIGINAL) { releaseFirst.await() }
            }
            val prefetch = launch(start = CoroutineStart.UNDISPATCHED) {
                gate.withPermit(ImageRequestWorkload.ORIGINAL, prefetch = true) { order += "prefetch" }
            }
            val visible = launch(start = CoroutineStart.UNDISPATCHED) {
                gate.withPermit(ImageRequestWorkload.VIDEO) { order += "visible"; releaseVisible.await() }
            }
            gate.withPermit(ImageRequestWorkload.BOUNDED) { order += "thumbnail" }
            assertEquals(listOf("thumbnail"), order)
            releaseFirst.complete(Unit); first.join(); yield()
            assertEquals(listOf("thumbnail", "visible"), order)
            releaseVisible.complete(Unit)
            visible.join(); prefetch.join()
            assertEquals(listOf("thumbnail", "visible", "prefetch"), order)
        }
    }

    @Test fun pressureAndIntrinsicBudgetsAreAdmittedTogetherAndRecoveryWakesWaiters(): Unit = runBlocking {
        withTimeout(5000) {
            val gate = LowMemoryImageRequestGate()
            fun policy(level: ImageMemoryPressureLevel) = resolveImageMemoryPressurePolicy(6, 32L * 1024 * 1024, null, level)
            val pressure = AdaptiveImageRequestGate(policy(ImageMemoryPressureLevel.CRITICAL))
            val never = CompletableDeferred<Unit>()
            var entered = 0
            val admissions = Channel<Unit>(Channel.UNLIMITED)
            fun start(prefetch: Boolean) = launch(start = CoroutineStart.UNDISPATCHED) {
                gate.withPermit(ImageRequestWorkload.BOUNDED, prefetch, pressureGate = pressure) { entered++; admissions.send(Unit); never.await() }
            }
            val first = start(false); val queued = start(true); val visible = start(false)
            admissions.receive()
            assertEquals(1, entered)
            pressure.updatePolicy(policy(ImageMemoryPressureLevel.NORMAL))
            admissions.receive()
            assertEquals(2, entered, "Recovery must wake one waiter without releasing active work; the process ceiling remains two")
            assertFalse(queued.isCompleted)
            first.cancelAndJoin(); admissions.receive()
            assertEquals(3, entered)
            visible.cancelAndJoin(); queued.cancelAndJoin()
            gate.withPermit(ImageRequestWorkload.ORIGINAL, pressureGate = pressure) { }
        }
    }

    @Test fun cancellationAndDecodeFailureReturnBothBudgets(): Unit = runBlocking {
        withTimeout(5000) {
            val gate = LowMemoryImageRequestGate()
            val pressure = AdaptiveImageRequestGate(resolveImageMemoryPressurePolicy(4, 32L * 1024 * 1024, null, ImageMemoryPressureLevel.CRITICAL))
            val never = CompletableDeferred<Unit>()
            val active = launch(start = CoroutineStart.UNDISPATCHED) {
                gate.withPermit(ImageRequestWorkload.ORIGINAL, pressureGate = pressure) { never.await() }
            }
            val queued = launch(start = CoroutineStart.UNDISPATCHED) {
                gate.withPermit(ImageRequestWorkload.ORIGINAL, true, pressureGate = pressure) { fail("Cancelled work entered") }
            }
            queued.cancelAndJoin(); active.cancelAndJoin()
            assertFailsWith<IllegalStateException> {
                gate.withPermit(ImageRequestWorkload.VIDEO, pressureGate = pressure) { error("native decode failed") }
            }
            gate.withPermit(ImageRequestWorkload.BOUNDED, pressureGate = pressure) { }
        }
    }

    @Test fun highRamPressureBudgetDoesNotAcquireAnUnrequestedLowRamCeiling(): Unit = runBlocking {
        withTimeout(5000) {
            val gate = LowMemoryImageRequestGate()
            val pressure = AdaptiveImageRequestGate(resolveImageMemoryPressurePolicy(8, 64L * 1024 * 1024, null, ImageMemoryPressureLevel.MODERATE))
            val never = CompletableDeferred<Unit>()
            var entered = 0
            val jobs = List(5) {
                launch(start = CoroutineStart.UNDISPATCHED) {
                    gate.withPermit(ImageRequestWorkload.BOUNDED, lowRam = false, pressureGate = pressure) { entered++; never.await() }
                }
            }
            assertEquals(4, entered)
            jobs.forEach { it.cancel() }; jobs.forEach { it.join() }
        }
    }

    @Test fun webKitResourceRemainsSingleWorkerOnHighRamAndSharesThePriorityQueue(): Unit = runBlocking {
        withTimeout(5000) {
            val gate = LowMemoryImageRequestGate()
            val webKit = Any()
            val releaseFirst = CompletableDeferred<Unit>()
            val releaseVisible = CompletableDeferred<Unit>()
            val visibleEntered = CompletableDeferred<Unit>()
            val order = mutableListOf<String>()
            val first = launch(start = CoroutineStart.UNDISPATCHED) {
                gate.withPermit(ImageRequestWorkload.VIDEO, lowRam = false, exclusiveResource = webKit) { releaseFirst.await() }
            }
            val prefetch = launch(start = CoroutineStart.UNDISPATCHED) {
                gate.withPermit(ImageRequestWorkload.VIDEO, prefetch = true, lowRam = false, exclusiveResource = webKit) { order += "prefetch" }
            }
            val visible = launch(start = CoroutineStart.UNDISPATCHED) {
                gate.withPermit(ImageRequestWorkload.VIDEO, lowRam = false, exclusiveResource = webKit) {
                    order += "visible"; visibleEntered.complete(Unit); releaseVisible.await()
                }
            }
            gate.withPermit(ImageRequestWorkload.BOUNDED, lowRam = false) { assertTrue(order.isEmpty()) }
            releaseFirst.complete(Unit); visibleEntered.await()
            assertEquals(listOf("visible"), order)
            releaseVisible.complete(Unit)
            first.join(); visible.join(); prefetch.join()
            assertEquals(listOf("visible", "prefetch"), order)
        }
    }

}
