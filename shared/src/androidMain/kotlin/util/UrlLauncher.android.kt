package com.valoser.futacha.shared.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberUrlLauncher(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { url: String ->
            try {
                val request = resolveUrlLaunchRequest(url)
                if (request == null) {
                    Logger.w("UrlLauncher", "Ignoring empty URL")
                    Toast.makeText(context, "リンクを開けません", Toast.LENGTH_SHORT).show()
                } else {
                    val parsed = runCatching { Uri.parse(request.normalizedUrl) }.getOrNull()
                    if (parsed == null || parsed.scheme.isNullOrBlank()) {
                        Logger.w("UrlLauncher", "Invalid URL: ${request.normalizedUrl}")
                        Toast.makeText(context, "リンクを開けません", Toast.LENGTH_SHORT).show()
                    } else {
                        val intent = when (request.target) {
                            UrlLaunchTarget.Mail -> Intent(Intent.ACTION_SENDTO, parsed)
                            UrlLaunchTarget.Browser -> Intent(Intent.ACTION_VIEW, parsed).apply {
                                addCategory(Intent.CATEGORY_BROWSABLE)
                            }
                        }
                        if (context !is Activity) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            Logger.w("UrlLauncher", "No activity can handle URL: ${request.normalizedUrl}")
                            Toast.makeText(context, "このリンクは開けません", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e("UrlLauncher", "Failed to open URL: $url", e)
                Toast.makeText(context, "リンクを開けません", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
