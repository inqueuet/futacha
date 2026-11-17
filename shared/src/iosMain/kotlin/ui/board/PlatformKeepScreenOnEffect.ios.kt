package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication

private val iosKeepScreenOn = KeepScreenOnCounter { on -> UIApplication.sharedApplication.idleTimerDisabled = on }

@Composable
internal actual fun PlatformKeepScreenOnEffect(enabled: Boolean) {
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose {}
        iosKeepScreenOn.acquire()
        onDispose { iosKeepScreenOn.release() }
    }
}
