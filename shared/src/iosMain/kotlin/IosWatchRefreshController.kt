package com.valoser.futacha.shared

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import platform.Foundation.NSLock
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Runs Watch-requested refreshes at most once per [minInterval] and lets the
 * Swift host keep the app awake until the run ends (C-8).
 *
 * In the background iOS suspended the app mid-run; the run stayed "active",
 * so every later Refresh coalesced into it and nothing was fetched again. The
 * host now holds a background task until [invokeWhenIdle] reports the end,
 * and [cancel] on its expiry ends the run so the next command starts afresh.
 */
internal class IosWatchRefreshController(
    private val scope: CoroutineScope,
    private val minInterval: Duration = IOS_WATCH_REFRESH_MIN_INTERVAL,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val run: suspend () -> Unit
) {
    private val lock = NSLock()
    private var job: Job? = null
    private var lastStartedAt: TimeMark? = null

    fun startIfAllowed(): IosWatchRefreshDecision = withLock {
        val decision = resolveIosWatchRefreshDecision(
            isRefreshRunning = job?.isActive == true,
            elapsedSinceLastStart = lastStartedAt?.elapsedNow(),
            minInterval = minInterval
        )
        if (decision != IosWatchRefreshDecision.Start) return@withLock decision
        lastStartedAt = timeSource.markNow()
        // A run cancelled on expiry may still be releasing the refresh lock;
        // wait for it so this run is not skipped as a duplicate.
        val previous = job
        val nextJob = scope.launch(start = CoroutineStart.LAZY) {
            try {
                previous?.join()
                run()
            } finally {
                withLock { if (job === coroutineContext[Job]) job = null }
            }
        }
        job = nextJob
        nextJob.start()
        decision
    }

    /** Calls [onIdle] once the current run (if any) has fully finished, cancelled or not. */
    fun invokeWhenIdle(onIdle: () -> Unit) {
        val current = withLock { job }
        if (current == null || current.isCompleted) {
            onIdle()
        } else {
            current.invokeOnCompletion { onIdle() }
        }
    }

    fun cancel() {
        withLock { job }?.cancel()
    }

    private inline fun <T> withLock(block: () -> T): T {
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }
}
