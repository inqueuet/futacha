package com.valoser.futacha.shared.ui.futaber

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import platform.Foundation.NSNotificationCenter

private const val IOS_FUTABER_HOST_BACKGROUND_NOTIFICATION = "com.valoser.futacha.futaber-host-background"

@Composable
internal actual fun ApplyFutaberHostBackground(color: Color) {
    DisposableEffect(color) {
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = IOS_FUTABER_HOST_BACKGROUND_NOTIFICATION,
            `object` = null,
            userInfo = mapOf("red" to color.red, "green" to color.green, "blue" to color.blue)
        )
        onDispose {
            // Leaving the mode (or changing the colour): the host drops the colour until the next one arrives.
            NSNotificationCenter.defaultCenter.postNotificationName(
                aName = IOS_FUTABER_HOST_BACKGROUND_NOTIFICATION,
                `object` = null,
                userInfo = null
            )
        }
    }
}
