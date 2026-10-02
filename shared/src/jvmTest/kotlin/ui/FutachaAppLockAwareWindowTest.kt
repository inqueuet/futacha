package com.valoser.futacha.shared.ui

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FutachaAppLockAwareWindowTest {
    private var frameTime = 0L

    private suspend fun pump(clock: BroadcastFrameClock, frames: Int = 5) {
        repeat(frames) {
            delay(5)
            Snapshot.sendApplyNotifications()
            frameTime += 16_000_000L
            clock.sendFrame(frameTime)
        }
    }

    private fun withComposition(
        content: @Composable () -> Unit,
        block: suspend CoroutineScope.(BroadcastFrameClock) -> Unit
    ) = runBlocking<Unit> {
        val clock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + clock)
        val job = launch(clock) { recomposer.runRecomposeAndApplyChanges() }
        val composition = Composition(object : AbstractApplier<Unit>(Unit) {
            override fun insertTopDown(index: Int, instance: Unit) {}
            override fun insertBottomUp(index: Int, instance: Unit) {}
            override fun remove(index: Int, count: Int) {}
            override fun move(from: Int, to: Int, count: Int) {}
            override fun onClear() {}
        }, recomposer)
        try {
            composition.setContent(content)
            block(clock)
        } finally {
            composition.dispose()
            recomposer.close()
            job.cancelAndJoin()
        }
    }

    @Test
    fun windowIsComposedWithoutAnAppLockProvider() {
        var composed = false
        withComposition(content = {
            FutachaAppLockAwareWindow { composed = true }
        }) { clock ->
            pump(clock)
            assertTrue(composed)
        }
    }

    @Test
    fun windowRequestedWhileLockedIsDeferredUntilUnlock() {
        var unlocked by mutableStateOf(false)
        var composedCount = 0
        withComposition(content = {
            CompositionLocalProvider(LocalFutachaAppUnlocked provides unlocked) {
                FutachaAppLockAwareWindow { composedCount++ }
            }
        }) { clock ->
            pump(clock)
            assertEquals(0, composedCount, "a window first requested while locked must not open above the lock")
            unlocked = true
            pump(clock)
            assertTrue(composedCount > 0, "the deferred window opens after unlock without being re-requested")
        }
    }

    @Test
    fun windowShownBeforeTheLockKeepsItsStateWhileLocked() {
        var unlocked by mutableStateOf(true)
        val draftInstances = mutableListOf<Any>()
        var composedWhileLocked = false
        withComposition(content = {
            CompositionLocalProvider(LocalFutachaAppUnlocked provides unlocked) {
                FutachaAppLockAwareWindow {
                    // Stands in for state remembered inside a dialog (draft, editor).
                    val draft = remember { Any() }
                    draftInstances += draft
                    if (!LocalFutachaAppUnlocked.current) composedWhileLocked = true
                }
            }
        }) { clock ->
            pump(clock)
            unlocked = false
            pump(clock)
            assertTrue(composedWhileLocked, "an already open window stays composed under the overlay")
            unlocked = true
            pump(clock)
            assertTrue(draftInstances.size >= 3)
            draftInstances.forEach { assertSame(draftInstances.first(), it) }
        }
    }

    @Test
    fun reopeningWhileLockedIsDeferredAgain() {
        var unlocked by mutableStateOf(true)
        var open by mutableStateOf(true)
        var composedWhileLocked = false
        withComposition(content = {
            CompositionLocalProvider(LocalFutachaAppUnlocked provides unlocked) {
                if (open) FutachaAppLockAwareWindow {
                    if (!LocalFutachaAppUnlocked.current) composedWhileLocked = true
                }
            }
        }) { clock ->
            pump(clock)
            open = false
            pump(clock)
            unlocked = false
            open = true
            pump(clock)
            assertFalse(composedWhileLocked, "a window closed and requested again while locked is a new window")
        }
    }

    @Test
    fun composeDecision() {
        assertTrue(shouldComposeLockAwareWindow(unlocked = true, shownWhileUnlocked = false))
        assertTrue(shouldComposeLockAwareWindow(unlocked = false, shownWhileUnlocked = true))
        assertFalse(shouldComposeLockAwareWindow(unlocked = false, shownWhileUnlocked = false))
    }
}
