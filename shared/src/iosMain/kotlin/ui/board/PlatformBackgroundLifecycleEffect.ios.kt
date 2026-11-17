package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidEnterBackgroundNotification

@Composable
internal actual fun PlatformBackgroundLifecycleEffect(onBackground: () -> Unit) {
    val currentOnBackground by rememberUpdatedState(onBackground)
    DisposableEffect(Unit) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = null
        ) {
            currentOnBackground()
        }
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
        }
    }
}
