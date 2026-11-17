package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.*
import com.valoser.futacha.shared.desktop.DesktopLifecycle

@Composable
internal actual fun PlatformBackgroundLifecycleEffect(onBackground: () -> Unit) {
    val callback by rememberUpdatedState(onBackground)
    LaunchedEffect(Unit) { DesktopLifecycle.foreground.collect { if (!it) callback() } }
}
