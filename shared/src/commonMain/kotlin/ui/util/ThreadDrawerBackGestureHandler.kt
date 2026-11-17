package com.valoser.futacha.shared.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.valoser.futacha.shared.compat.CompatBackGestureBus
import com.valoser.futacha.shared.ui.compat.CompatForegroundLifecycleEffect

/** The Android host supplies edge information unavailable to ordinary BackHandler. */
@Composable
internal fun ThreadDrawerBackGestureHandler(enabled: Boolean, onOpenDrawer: () -> Unit) {
    val owner = remember { Any() }
    var foreground by remember { mutableStateOf(false) }
    CompatForegroundLifecycleEffect { visible ->
        // Invalidate a captured gesture immediately on Activity pause; don't
        // leave a background thread registered until the next recomposition.
        if (!visible) CompatBackGestureBus.unregister(owner)
        foreground = visible
    }
    val latestEnabled = rememberUpdatedState(enabled && foreground)
    val latestOpenDrawer = rememberUpdatedState(onOpenDrawer)
    DisposableEffect(owner, enabled, foreground) {
        if (enabled && foreground) {
            CompatBackGestureBus.register(owner) {
                if (latestEnabled.value) {
                    latestOpenDrawer.value()
                    true
                } else false
            }
        }
        onDispose { CompatBackGestureBus.unregister(owner) }
    }
}
