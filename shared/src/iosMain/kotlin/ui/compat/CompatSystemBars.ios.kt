package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import platform.Foundation.NSNotificationCenter

private const val IOS_SYSTEM_BARS_NOTIFICATION = "com.valoser.futacha.system-bars"

@Composable
internal actual fun ApplyCompatSystemBars(
    statusBarColor: Color,
    navigationBarColor: Color,
    useDarkStatusBarIcons: Boolean,
    useDarkNavigationBarIcons: Boolean
) {
    SideEffect {
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = IOS_SYSTEM_BARS_NOTIFICATION,
            `object` = null,
            userInfo = mapOf(
                "red" to statusBarColor.red,
                "green" to statusBarColor.green,
                "blue" to statusBarColor.blue,
                "darkIcons" to useDarkStatusBarIcons
            )
        )
    }
}
