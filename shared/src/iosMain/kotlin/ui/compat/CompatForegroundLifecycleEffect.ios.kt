package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification

@Composable
internal actual fun CompatForegroundLifecycleEffect(onForegroundChanged: (Boolean) -> Unit) {
    val currentCallback by rememberUpdatedState(onForegroundChanged)
    DisposableEffect(Unit) {
        currentCallback(true)
        val center = NSNotificationCenter.defaultCenter
        val backgroundObserver = center.addObserverForName(
            name = UIApplicationWillResignActiveNotification,
            `object` = null,
            queue = null
        ) { currentCallback(false) }
        val foregroundObserver = center.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = null
        ) { currentCallback(true) }
        onDispose {
            center.removeObserver(backgroundObserver)
            center.removeObserver(foregroundObserver)
            currentCallback(false)
        }
    }
}
