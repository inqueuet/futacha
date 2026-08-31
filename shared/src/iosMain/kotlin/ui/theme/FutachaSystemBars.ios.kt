package com.valoser.futacha.shared.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import platform.Foundation.NSNotificationCenter

private const val IOS_SYSTEM_BARS_NOTIFICATION = "com.valoser.futacha.system-bars"

@Composable
internal actual fun ApplyFutachaSystemBars(
    systemBarColor: Color,
    useDarkIcons: Boolean
) {
    SideEffect {
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = IOS_SYSTEM_BARS_NOTIFICATION,
            `object` = null,
            userInfo = mapOf(
                "red" to systemBarColor.red,
                "green" to systemBarColor.green,
                "blue" to systemBarColor.blue,
                "darkIcons" to useDarkIcons
            )
        )
    }
}
