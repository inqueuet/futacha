package com.valoser.futacha.shared.ui.util

import androidx.compose.runtime.*
import com.valoser.futacha.shared.desktop.DesktopBackDispatcher

@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    iosEdgeGestureEnabled: Boolean,
    onBack: () -> Unit
) {
    val current by rememberUpdatedState(onBack)
    val token = remember { Any() }
    DisposableEffect(enabled) {
        if (enabled) DesktopBackDispatcher.add(token) { current() }
        onDispose { DesktopBackDispatcher.remove(token) }
    }
}
