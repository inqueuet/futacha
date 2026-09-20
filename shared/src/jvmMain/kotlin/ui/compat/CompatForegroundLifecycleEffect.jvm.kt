package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import com.valoser.futacha.shared.desktop.DesktopLifecycle

@Composable
internal actual fun CompatForegroundLifecycleEffect(onForegroundChanged: (Boolean) -> Unit) {
    val callback = rememberUpdatedState(onForegroundChanged)
    LaunchedEffect(Unit) { DesktopLifecycle.foreground.collect { callback.value(it) } }
}
