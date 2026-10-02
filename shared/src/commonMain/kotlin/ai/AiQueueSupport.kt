package com.valoser.futacha.shared.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs [action] under this lock, waiting at most [waitMillis] for it. Returns null when the lock
 * was not acquired in time (or when [action] itself returns null). Only the wait is bounded here:
 * a request's own deadline belongs inside [action], so queueing never shortens it, but a caller
 * without an outer deadline can no longer wait behind a long request (a model download) forever.
 */
internal suspend fun <T> Mutex.withLockWithin(waitMillis: Long, action: suspend () -> T): T? {
    // Tracked outside the timeout: it can fire after lock() returned, while this coroutine owns the lock.
    var locked = false
    try {
        withTimeoutOrNull(waitMillis.coerceAtLeast(1L)) {
            lock()
            locked = true
        }
    } catch (e: CancellationException) {
        if (locked) unlock()
        throw e
    }
    if (!locked) return null
    return try {
        action()
    } finally {
        unlock()
    }
}
