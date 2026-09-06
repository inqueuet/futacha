package com.valoser.futacha.shared.ui.compat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
internal actual fun rememberCompatWatcherNotificationPermission(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
    return {
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) onResult(true)
        else launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
internal actual fun rememberCompatWatcherNotificationSettings(): () -> Result<Unit> {
    val context = LocalContext.current
    return {
        runCatching {
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            if (manager.getNotificationChannel("watch_alerts") == null) {
                manager.createNotificationChannel(android.app.NotificationChannel(
                    "watch_alerts", "監視ワード", android.app.NotificationManager.IMPORTANCE_DEFAULT
                ))
            }
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, "watch_alerts")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
