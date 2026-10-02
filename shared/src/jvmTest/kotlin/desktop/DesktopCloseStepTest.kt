package com.valoser.futacha.shared.desktop

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopCloseStepTest {
    @Test
    fun stepBlockedInASynchronousCallIsAbandonedAtItsLimit() = runBlocking {
        val release = CountDownLatch(1)
        val started = System.nanoTime()
        // A blocking wait ignores coroutine cancellation; the step must still return at its limit.
        withTimeout(5_000) { runDesktopCloseStep("stuck", 200) { release.await(10, TimeUnit.SECONDS) } }
        val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        release.countDown()
        assertTrue(elapsed < 2_000, "close step took ${elapsed}ms")
    }

    @Test
    fun laterStepsRunAfterAFailingSlowOrSelfCancelledStep() = runBlocking {
        val events = mutableListOf<String>()
        runDesktopCloseStep("failing", 1_000) { error("broken") }
        runDesktopCloseStep("slow", 100) { delay(10_000) }
        runDesktopCloseStep("self-cancelled", 1_000) { throw CancellationException("own") }
        runDesktopCloseStep("ok", 1_000) { events += "ok" }
        assertEquals(listOf("ok"), events)
    }

    @Test
    fun cancellingTheCallerStillStopsTheShutdown() = runBlocking {
        var reached = false
        val result = runCatching {
            withTimeout(200) {
                runDesktopCloseStep("long", 5_000) { delay(10_000) }
                reached = true
            }
        }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(false, reached)
    }
}
