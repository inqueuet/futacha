package com.valoser.futacha.shared.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenExternalURLOptionsKey

@Composable
actual fun rememberUrlLauncher(): (String) -> Unit {
    return remember {
        { url: String ->
            try {
                val nsUrl = NSURL.URLWithString(url)
                if (nsUrl != null && UIApplication.sharedApplication.canOpenURL(nsUrl)) {
                    UIApplication.sharedApplication.openURL(
                        nsUrl,
                        options = emptyMap<UIApplicationOpenExternalURLOptionsKey, Any>(),
                        completionHandler = null
                    )
                } else {
                    Logger.e("UrlLauncher", "Failed to open URL: $url", null)
                }
            } catch (e: Exception) {
                Logger.e("UrlLauncher", "Failed to open URL: $url", e)
            }
        }
    }
}
