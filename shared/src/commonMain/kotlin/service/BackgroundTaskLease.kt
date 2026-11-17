package com.valoser.futacha.shared.service

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

/**
 * Holds one platform background task and ends it exactly once.
 *
 * All access happens on [mainDispatcher]: the begin call, the expiration
 * handler (UIKit invokes it on the main thread) and the final end call, so
 * the identifier needs no further synchronisation.
 */
private class BackgroundTaskLease<Id : Any>(private val end: (Id) -> Unit) {
    var id: Id? = null

    fun endOnce() {
        val current = id ?: return
        id = null
        end(current)
    }
}

/**
 * Runs [block] inside a platform background task.
 *
 * The task must be ended even when the caller is cancelled: the identifier is
 * stored before [begin] returns to the caller, and the final end call runs in
 * [NonCancellable] so a cancelled `withContext` cannot skip it. Expiration
 * cancels the save and ends the task immediately, because the system
 * terminates apps whose expired tasks are still open.
 */
internal suspend fun <T, Id : Any> withBackgroundTaskLease(
    mainDispatcher: CoroutineDispatcher,
    begin: (onExpired: () -> Unit) -> Id?,
    end: (Id) -> Unit,
    block: suspend () -> T
): T {
    val saveJob = currentCoroutineContext()[Job]
    val lease = BackgroundTaskLease(end)
    try {
        withContext(mainDispatcher) {
            lease.id = begin {
                saveJob?.cancel()
                lease.endOnce()
            }
        }
        return block()
    } finally {
        withContext(NonCancellable + mainDispatcher) { lease.endOnce() }
    }
}
