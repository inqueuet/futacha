package com.valoser.futacha.shared.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

@Composable
actual fun rememberUrlLauncher(): (String) -> Unit {
    return remember {
        { url: String ->
            try {
                val request = resolveUrlLaunchRequest(url)
                if (request == null) {
                    Logger.e("UrlLauncher", "Failed to open URL: $url", null)
                    presentIosAlert(
                        title = "リンクを開けません",
                        message = "URL を確認してください。"
                    )
                } else {
                    val nsUrl = NSURL.URLWithString(request.normalizedUrl)
                    if (nsUrl != null && UIApplication.sharedApplication.canOpenURL(nsUrl)) {
                        UIApplication.sharedApplication.openURL(
                            nsUrl,
                            options = emptyMap<Any?, Any?>(),
                            completionHandler = null
                        )
                    } else {
                        Logger.e("UrlLauncher", "Failed to open URL: ${request.normalizedUrl}", null)
                        presentIosAlert(
                            title = "このリンクは開けません",
                            message = "対応するアプリが見つかりません。"
                        )
                    }
                }
            } catch (e: Exception) {
                Logger.e("UrlLauncher", "Failed to open URL: $url", e)
                presentIosAlert(
                    title = "リンクを開けません",
                    message = "もう一度お試しください。"
                )
            }
        }
    }
}
