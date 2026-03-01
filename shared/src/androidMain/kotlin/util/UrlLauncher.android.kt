package com.valoser.futacha.shared.util

import android.app.Activity
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
                val trimmed = url.trim()
                if (trimmed.isEmpty()) {
                    Logger.w("UrlLauncher", "Ignoring empty URL")
                    Toast.makeText(context, "リンクを開けません", Toast.LENGTH_SHORT).show()
                } else {
                    val parsed = runCatching { Uri.parse(trimmed) }.getOrNull()
                    if (parsed == null || parsed.scheme.isNullOrBlank()) {
                        Logger.w("UrlLauncher", "Invalid URL: $trimmed")
                        Toast.makeText(context, "リンクを開けません", Toast.LENGTH_SHORT).show()
                    } else {
                        val intent = Intent(Intent.ACTION_VIEW, parsed)
                        if (context !is Activity) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        val canHandle = intent.resolveActivity(context.packageManager) != null
                        if (!canHandle) {
                            Logger.w("UrlLauncher", "No activity can handle URL: $trimmed")
                            Toast.makeText(context, "このリンクは開けません", Toast.LENGTH_SHORT).show()
                        } else {
                            context.startActivity(intent)
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
