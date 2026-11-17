package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.SaveProgress
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex

private val userInitiatedThreadSaveMutex = Mutex()

class ThreadSaveAlreadyRunningException : IllegalStateException("別の保存を実行中です")

/**
 * Runs one user initiated thread save at a time across every screen and mode.
 *
 * Both platform protectors own a single notification/background-task surface.
 * Rejecting a second save avoids replacing the first save's cancellation target
 * after the user navigates to another thread while it is still running.
 */
suspend fun <T> runProtectedThreadSave(
    title: String,
    progress: StateFlow<SaveProgress?>,
    block: suspend () -> T
): T = runExclusiveUserThreadSave {
    withThreadSavePlatformProtection(title, progress, block)
}

internal suspend fun <T> runExclusiveUserThreadSave(block: suspend () -> T): T {
    if (!userInitiatedThreadSaveMutex.tryLock()) throw ThreadSaveAlreadyRunningException()
    return try {
        block()
    } finally {
        userInitiatedThreadSaveMutex.unlock()
    }
}

/**
 * Keeps a user initiated page save alive while the app is backgrounded.
 *
 * Android implements this with a foreground service, wake/wifi locks and a
 * cancellable progress notification. iOS uses a finite UIApplication
 * background task. JVM is a no-op used by host tests.
 */
expect suspend fun <T> withThreadSavePlatformProtection(
    title: String,
    progress: StateFlow<SaveProgress?>,
    block: suspend () -> T
): T
