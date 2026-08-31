package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
internal actual fun CompatForegroundLifecycleEffect(onForegroundChanged: (Boolean) -> Unit) {
    LaunchedEffect(Unit) { onForegroundChanged(true) }
}
