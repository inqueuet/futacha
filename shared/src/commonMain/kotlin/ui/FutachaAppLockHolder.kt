package com.valoser.futacha.shared.ui

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlin.time.ComparableTimeMark
import kotlin.time.TimeSource

/**
 * App-lock state that is readable outside composition (C-1).
 *
 * Android pauses recomposition while the activity is stopped, so a value taken
 * from composition ([LocalFutachaAppUnlocked], `rememberUpdatedState`) keeps
 * saying "unlocked" after ON_STOP. The background lifecycle callback writes
 * [lockSession] synchronously, and every command path (bridge collector,
 * platform commands, router, confirmation buttons) reads [isUnlocked] or
 * waits in [awaitUnlocked] instead of a composition snapshot.
 *
 * The app counts as unlocked only when the session was unlocked AND the last
 * composition actually showed the app content ([setContentVisible]), so a
 * read error or a re-read of the lock setting also keeps commands waiting.
 *
 * Writers run on the main thread; readers may run anywhere.
 */
internal class FutachaAppLockHolder {
    private data class Snapshot(
        val sessionUnlocked: Boolean = false,
        val contentVisible: Boolean = false
    ) {
        val unlocked: Boolean get() = sessionUnlocked && contentVisible
    }

    private val state = MutableStateFlow(Snapshot())
    private val sessionState = MutableStateFlow(false)

    @kotlin.concurrent.Volatile
    private var lastPasswordUnlockAt: ComparableTimeMark? = null

    /** Session flag for the lock gate in composition. */
    val sessionUnlocked: StateFlow<Boolean> = sessionState.asStateFlow()

    val isUnlocked: Boolean get() = state.value.unlocked

    fun lockSession() {
        sessionState.value = false
        state.update { it.copy(sessionUnlocked = false) }
    }

    /** The user entered the password on the lock screen. */
    fun unlockSession() {
        lastPasswordUnlockAt = TimeSource.Monotonic.markNow()
        openSession()
    }

    /** No password is set, so the session needs no unlock. */
    fun openSessionWithoutLock() {
        openSession()
    }

    /** Published from composition: whether the app content (not the lock screen) is shown. */
    fun setContentVisible(visible: Boolean) {
        if (state.value.contentVisible == visible) return
        state.update { it.copy(contentVisible = visible) }
    }

    suspend fun awaitUnlocked() {
        state.first { it.unlocked }
    }

    /**
     * Whether a command that arrived at [arrivedAt] waited behind the lock
     * screen: the password was entered after it arrived.
     */
    fun wasHeldByLock(arrivedAt: ComparableTimeMark): Boolean {
        val unlockedAt = lastPasswordUnlockAt ?: return false
        return unlockedAt > arrivedAt
    }

    private fun openSession() {
        sessionState.value = true
        state.update { it.copy(sessionUnlocked = true) }
    }
}

/**
 * The lock holder of the enclosing [FutachaApp]. Null outside the app (previews,
 * tests); treat that as unlocked, matching [LocalFutachaAppUnlocked]'s default.
 */
internal val LocalFutachaAppLockHolder = staticCompositionLocalOf<FutachaAppLockHolder?> { null }
