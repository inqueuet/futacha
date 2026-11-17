package com.valoser.futacha.shared

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource

/** C-8: a refresh suspended in the background must not swallow every later Refresh. */
class IosWatchRefreshControllerTest {
    @Test
    fun expiryCancelsTheStalledRunReportsIdleAndLetsTheNextRefreshStart() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val time = TestTimeSource()
        val runs = AtomicInt(0)
        val controller = IosWatchRefreshController(scope, minInterval = 2.minutes, timeSource = time) {
            runs.incrementAndGet()
            awaitCancellation() // the app was suspended mid-run
        }
        try {
            assertEquals(IosWatchRefreshDecision.Start, controller.startIfAllowed())
            waitUntil { runs.value == 1 }
            assertEquals(IosWatchRefreshDecision.CoalesceIntoRunning, controller.startIfAllowed())

            val idle = CompletableDeferred<Unit>()
            controller.invokeWhenIdle { idle.complete(Unit) }
            controller.cancel() // background-task expiration handler
            withTimeout(5_000) { idle.await() }

            time += 2.minutes
            assertEquals(IosWatchRefreshDecision.Start, controller.startIfAllowed())
            waitUntil { runs.value == 2 }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun idleIsReportedAtOnceWithoutARunAndAfterANormalFinish() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val finish = CompletableDeferred<Unit>()
        val controller = IosWatchRefreshController(scope, timeSource = TestTimeSource()) { finish.await() }
        try {
            var immediate = false
            controller.invokeWhenIdle { immediate = true }
            assertEquals(true, immediate)

            controller.startIfAllowed()
            val idle = CompletableDeferred<Unit>()
            controller.invokeWhenIdle { idle.complete(Unit) }
            assertEquals(false, idle.isCompleted)
            finish.complete(Unit)
            withTimeout(5_000) { idle.await() }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun aNewRunWaitsForTheCancelledRunToReleaseItsResources() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val time = TestTimeSource()
        val cleanupGate = CompletableDeferred<Unit>()
        val events = AtomicReference<List<String>>(emptyList())
        val calls = AtomicInt(0)
        val controller = IosWatchRefreshController(scope, minInterval = 2.minutes, timeSource = time) {
            if (calls.incrementAndGet() == 1) {
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) { cleanupGate.await() }
                    synchronizedAdd(events, "first released")
                }
            } else {
                synchronizedAdd(events, "second started")
            }
        }
        try {
            controller.startIfAllowed()
            waitUntil { calls.value == 1 }
            controller.cancel()
            time += 2.minutes
            assertEquals(IosWatchRefreshDecision.Start, controller.startIfAllowed())
            delay(100)
            assertEquals(1, calls.value, "the second run must not overlap the first run's cleanup")
            cleanupGate.complete(Unit)
            waitUntil { calls.value == 2 }
            waitUntil { events.value.size == 2 }
            assertEquals(listOf("first released", "second started"), events.value)
        } finally {
            scope.cancel()
        }
    }

    private suspend fun waitUntil(condition: () -> Boolean) {
        withTimeout(5_000) { while (!condition()) delay(5) }
    }

    private fun synchronizedAdd(events: AtomicReference<List<String>>, event: String) {
        while (true) {
            val current = events.value
            if (events.compareAndSet(current, current + event)) return
        }
    }
}
