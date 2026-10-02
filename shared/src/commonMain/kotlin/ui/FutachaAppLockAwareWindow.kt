package com.valoser.futacha.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * Hosts a window-creating composable (Dialog, AlertDialog, ModalBottomSheet,
 * Popup) opened inside an app screen, so it cannot appear above the app-lock
 * overlay (C-2).
 *
 * Dialogs and sheets are separate windows. The overlay's own dialog window is
 * created when the lock engages, so it covers every window already open; a
 * window first composed while locked (a post or save result, a sheet opened by
 * a late callback) would be stacked above it instead. Such a window is deferred
 * until unlock. A window shown before the lock stays composed under the
 * overlay, so its state (drafts, editor state, running work) survives; nothing
 * is dismissed.
 *
 * Call it where the window is conditionally composed (inside the `if`/`let`
 * that opens it), so its lifetime matches the window's.
 */
@Composable
internal fun FutachaAppLockAwareWindow(content: @Composable () -> Unit) {
    val unlocked = LocalFutachaAppUnlocked.current
    val shownWhileUnlocked = remember { mutableStateOf(false) }
    SideEffect { if (unlocked) shownWhileUnlocked.value = true }
    if (shouldComposeLockAwareWindow(unlocked, shownWhileUnlocked.value)) content()
}

internal fun shouldComposeLockAwareWindow(unlocked: Boolean, shownWhileUnlocked: Boolean): Boolean =
    unlocked || shownWhileUnlocked
