package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.Foundation.NSNotificationCenter

private const val IOS_VIEWER_BARS_NOTIFICATION = "com.valoser.futacha.viewer-bars"

@Composable
internal actual fun ApplyCompatViewerSystemBars(hidden: Boolean) {
    DisposableEffect(hidden) {
        fun publish(value: Boolean) {
            NSNotificationCenter.defaultCenter.postNotificationName(
                aName = IOS_VIEWER_BARS_NOTIFICATION,
                `object` = null,
                userInfo = mapOf("hidden" to value)
            )
        }
        publish(hidden)
        onDispose {
            if (hidden) publish(false)
        }
    }
}
