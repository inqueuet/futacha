package com.valoser.futacha.shared.ui.image

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal enum class VideoThumbnailRequestPriority(internal val rank: Int) {
    PREFETCH(0),
    VISIBLE(1)
}

/**
 * A cancellation-safe, single-worker gate that lets visible thumbnails pass
 * queued catalog fallback work without increasing WebKit concurrency.
 */
internal class VideoThumbnailPriorityGate {
    private data class Waiter(
        val priority: VideoThumbnailRequestPriority,
        val sequence: Long,
        val signal: CompletableDeferred<Unit> = CompletableDeferred(),
        var granted: Boolean = false
    )

    private val mutex = Mutex()
    private val waiters = mutableListOf<Waiter>()
    private var isActive = false
    private var sequence = 0L

    suspend fun <T> withPermit(
        priority: VideoThumbnailRequestPriority,
        block: suspend () -> T
    ): T {
        val waiter = mutex.withLock {
            if (!isActive) {
                isActive = true
                null
            } else {
                Waiter(priority = priority, sequence = sequence++).also(waiters::add)
            }
        }
        if (waiter != null) {
            try {
                waiter.signal.await()
            } catch (error: Throwable) {
                withContext(NonCancellable) {
                    mutex.withLock {
                        val wasQueued = waiters.remove(waiter)
                        if (!wasQueued && waiter.granted) grantNextLocked()
                    }
                }
                throw error
            }
        }
        try {
            return block()
        } finally {
            withContext(NonCancellable) {
                mutex.withLock { grantNextLocked() }
            }
        }
    }

    private fun grantNextLocked() {
        val next = waiters.maxWithOrNull(
            compareBy<Waiter> { it.priority.rank }
                .thenBy { -it.sequence }
        )
        if (next == null) {
            isActive = false
            return
        }
        waiters.remove(next)
        next.granted = true
        next.signal.complete(Unit)
    }
}
