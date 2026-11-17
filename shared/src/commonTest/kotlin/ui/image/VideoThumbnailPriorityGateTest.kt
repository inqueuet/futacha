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

class VideoThumbnailPriorityGateTest {
    @Test
    fun visibleWorkPassesQueuedPrefetchWithoutIncreasingConcurrency() = runBlocking {
        val gate = VideoThumbnailPriorityGate()
        val releaseActive = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val active = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.withPermit(VideoThumbnailRequestPriority.PREFETCH) {
                order += "active"
                releaseActive.await()
            }
        }
        val prefetchOne = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.withPermit(VideoThumbnailRequestPriority.PREFETCH) { order += "prefetch-1" }
        }
        val visible = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.withPermit(VideoThumbnailRequestPriority.VISIBLE) { order += "visible" }
        }
        val prefetchTwo = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.withPermit(VideoThumbnailRequestPriority.PREFETCH) { order += "prefetch-2" }
        }

        releaseActive.complete(Unit)
        joinAll(active, prefetchOne, visible, prefetchTwo)

        assertEquals(listOf("active", "visible", "prefetch-1", "prefetch-2"), order)
    }

    @Test
    fun cancelledPrefetchLeavesNoWorkOrPermitLeak() = runBlocking {
        val gate = VideoThumbnailPriorityGate()
        val releaseActive = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val active = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.withPermit(VideoThumbnailRequestPriority.VISIBLE) {
                order += "active"
                releaseActive.await()
            }
        }
        val cancelled = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.withPermit(VideoThumbnailRequestPriority.PREFETCH) { order += "cancelled" }
        }
        val visible = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.withPermit(VideoThumbnailRequestPriority.VISIBLE) { order += "visible" }
        }

        cancelled.cancelAndJoin()
        releaseActive.complete(Unit)
        joinAll(active, visible)
        gate.withPermit(VideoThumbnailRequestPriority.VISIBLE) { order += "after" }

        assertEquals(listOf("active", "visible", "after"), order)
    }

    @Test
    fun oneFourAndEightColdRequestsRemainStrictlySingleWorker() = runBlocking {
        for (requestCount in listOf(1, 4, 8)) {
            val gate = VideoThumbnailPriorityGate()
            var running = 0
            var peakRunning = 0
            var completed = 0
            val jobs = List(requestCount) { index ->
                launch(start = CoroutineStart.UNDISPATCHED) {
                    gate.withPermit(
                        if (index == requestCount - 1) {
                            VideoThumbnailRequestPriority.VISIBLE
                        } else {
                            VideoThumbnailRequestPriority.PREFETCH
                        }
                    ) {
                        running += 1
                        peakRunning = maxOf(peakRunning, running)
                        yield()
                        completed += 1
                        running -= 1
                    }
                }
            }
            jobs.joinAll()
            assertEquals(requestCount, completed)
            assertEquals(1, peakRunning)
        }
    }
}
