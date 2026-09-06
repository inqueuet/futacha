package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter

@Composable
internal actual fun rememberCompatWatcherNotificationPermission(onResult: (Boolean) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound
        ) { granted, error -> scope.launch { onResult(granted && error == null) } }
    }
}

@Composable
internal actual fun rememberCompatWatcherNotificationSettings(): () -> Result<Unit> = {
    runCatching {
        val url = platform.Foundation.NSURL.URLWithString(platform.UIKit.UIApplicationOpenSettingsURLString)
            ?: error("通知設定を開けませんでした")
        platform.UIKit.UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any?>(), null)
    }
}
