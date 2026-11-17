package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.compat.CompatibilityStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * Preference holding the start lock's wrong-password counter and remaining cool-down, so that a
 * force-stop does not reset them. Shared by every mode (they all pass the same
 * [CompatibilityStore] to `FutachaApp`) and left out of settings backups.
 */
internal const val APP_LOCK_ATTEMPTS_PREFERENCE_KEY = "compat.appLock.attempts"

/** `"failedAttempts,remainingWaitMillis"`, or null for the empty state (the key is then removed). */
internal fun encodeAppLockAttemptState(state: AppLockAttemptState): String? {
    val attempts = state.failedAttempts.coerceIn(0, APP_LOCK_MAX_FAILURES_BEFORE_WAIT - 1)
    val remaining = state.lockoutRemainingMillis.coerceIn(0L, APP_LOCK_FAILURE_WAIT_MILLIS)
    if (attempts == 0 && remaining == 0L) return null
    return "$attempts,$remaining"
}

/** Out-of-range values are clamped; anything unreadable is the empty state. */
internal fun decodeAppLockAttemptState(raw: String?): AppLockAttemptState {
    val parts = raw?.trim()?.split(',') ?: return AppLockAttemptState()
    if (parts.size != 2) return AppLockAttemptState()
    val attempts = parts[0].trim().toIntOrNull() ?: return AppLockAttemptState()
    val remaining = parts[1].trim().toLongOrNull() ?: return AppLockAttemptState()
    return AppLockAttemptState(
        failedAttempts = attempts.coerceIn(0, APP_LOCK_MAX_FAILURES_BEFORE_WAIT - 1),
        lockoutRemainingMillis = remaining.coerceIn(0L, APP_LOCK_FAILURE_WAIT_MILLIS)
    )
}

/**
 * Reads and writes the persisted attempt state through [read] / [write] (a null write removes the
 * key). A write that would leave the stored value unchanged is skipped.
 */
internal class AppLockAttemptsStorage(
    private val read: suspend () -> String?,
    private val write: suspend (String?) -> Unit
) {
    private val mutex = Mutex()
    /** What is stored, as last read or written; null when nothing is. */
    private var stored: String? = null

    suspend fun load(): AppLockAttemptState = mutex.withLock {
        val raw = try {
            read()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }?.takeIf { it.isNotBlank() }
        stored = raw
        decodeAppLockAttemptState(raw)
    }

    /** Returns true when something was written. */
    suspend fun save(state: AppLockAttemptState): Boolean = mutex.withLock {
        val encoded = encodeAppLockAttemptState(state)
        if (encoded == stored) return@withLock false
        try {
            write(encoded)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return@withLock false
        }
        stored = encoded
        true
    }
}

internal fun createAppLockAttemptsStorage(store: CompatibilityStore): AppLockAttemptsStorage =
    AppLockAttemptsStorage(
        read = {
            store.isLoaded.first { it }
            store.loadPreference(APP_LOCK_ATTEMPTS_PREFERENCE_KEY)
        },
        write = { encoded ->
            store.savePreferences(mapOf(APP_LOCK_ATTEMPTS_PREFERENCE_KEY to encoded))
        }
    )
