package com.valoser.futacha.shared.ai

import kotlinx.coroutines.*
import kotlin.test.*

class OpenAiRequestQueueTest {
    private class FakeTime {
        var now = 0L
        var wait: suspend (Long) -> Unit = { now += it; yield() }
        fun queue() = OpenAiRequestQueue({ now }, { wait(it) }, { 100L })
    }

    @Test fun concurrentRequestsAreQueuedAtThreePerSecond() = runBlocking {
        val time = FakeTime()
        val queue = time.queue()
        val starts = mutableListOf<Long>()
        (1..7).map { async { queue.execute { starts += time.now } } }.awaitAll()
        assertEquals(List(7) { it * 334L }, starts)
    }

    @Test fun retryAfterIsMinimumAndBlocksOtherQueuedRequests() = runBlocking {
        val time = FakeTime()
        val queue = time.queue()
        val starts = mutableListOf<Long>()
        val first = async {
            queue.execute {
                starts += time.now
                if (starts.size == 1) throw OpenAiFailure("429", retryableRateLimit = true, retryAfterMillis = 5_000)
            }
        }
        val second = async { queue.execute { starts += time.now } }
        awaitAll(first, second)
        assertEquals(listOf(0L, 5_100L, 5_434L), starts)
    }

    @Test fun cancellationRemovesStaleWorkButKeepsSharedCooldown() = runBlocking {
        val time = FakeTime()
        val queue = time.queue()
        val waitingForRetry = CompletableDeferred<Unit>()
        time.wait = { waitingForRetry.complete(Unit); awaitCancellation() }
        val job = launch {
            queue.execute<Unit> { throw OpenAiFailure("429", retryableRateLimit = true, retryAfterMillis = 10_000) }
        }
        waitingForRetry.await()
        job.cancelAndJoin()
        time.wait = { time.now += it }
        queue.execute { assertEquals(10_100L, time.now) }
        val waitingForSlot = CompletableDeferred<Unit>()
        time.wait = { waitingForSlot.complete(Unit); awaitCancellation() }
        val waiting = launch { queue.execute { fail("Cancelled queued work must not send") } }
        waitingForSlot.await()
        waiting.cancelAndJoin()
    }

    @Test fun missingRetryAfterUsesBoundedExponentialBackoff() = runBlocking {
        val time = FakeTime()
        val queue = time.queue()
        val starts = mutableListOf<Long>()
        assertFailsWith<OpenAiFailure> {
            queue.execute<Unit> {
                starts += time.now
                throw OpenAiFailure("429", retryableRateLimit = true)
            }
        }
        assertEquals(listOf(0L, 1_100L, 3_200L, 7_300L), starts)
        queue.execute { assertEquals(15_400L, time.now) }
    }

    @Test fun longServerWaitIsNeverShortenedAndPermanentErrorsNeverRetry() = runBlocking {
        val time = FakeTime()
        val queue = time.queue()
        var calls = 0
        assertFailsWith<OpenAiFailure> {
            queue.execute<Unit> {
                calls++
                throw OpenAiFailure("429", retryableRateLimit = true, retryAfterMillis = 600_000)
            }
        }
        assertFailsWith<OpenAiFailure> { queue.execute { calls++ } }
        assertEquals(1, calls)
        assertEquals(0L, time.now)
        val other = time.queue()
        assertFailsWith<OpenAiFailure> { other.execute<Unit> { calls++; throw OpenAiFailure("quota") } }
        assertEquals(2, calls)
    }

    @Test fun parsesSecondsAndHttpDateWithoutAcceptingInvalidDelays() {
        assertEquals(2_501L, openAiRetryAfterMillis("2.5001", 0))
        assertEquals(0L, openAiRetryAfterMillis("0", 0))
        assertEquals(5_000L, openAiRetryAfterMillis("Thu, 01 Jan 1970 00:00:05 GMT", 0))
        assertNull(openAiRetryAfterMillis("bad", 0))
        assertNull(openAiRetryAfterMillis("-1", 0))
        assertNull(openAiRetryAfterMillis("NaN", 0))
    }
}
