package com.valoser.futacha.wear.live

import android.annotation.SuppressLint
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.valoser.futacha.shared.watch.WatchAlert
import com.valoser.futacha.wear.R
import com.valoser.futacha.wear.WearMainActivity

object WatchAlertNotifier {
    // canPostNotifications() performs the runtime permission check before this
    // call. Keep the suppression local because lint cannot follow that helper
    // through NotificationManagerCompat.
    @SuppressLint("MissingPermission")
    fun notify(context: Context, alert: WatchAlert) {
        val appContext = context.applicationContext
        if (alert.matches.isEmpty() || !canPostNotifications(appContext)) return
        ensureChannel(appContext)
        val first = alert.matches.first()
        val title = if (alert.matches.size == 1) {
            "監視ワードに一致"
        } else {
            "監視ワードに ${alert.matches.size} 件一致"
        }
        val body = if (alert.matches.size == 1) {
            "${first.boardName}: ${first.title}"
        } else {
            "${first.boardName}: ${first.title} ほか"
        }
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // Tapping the alert opens the watch app (it did nothing before).
            .setContentIntent(buildContentIntent(appContext))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
    }

    private fun buildContentIntent(context: Context): PendingIntent {
        val intent = Intent(context, WearMainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "監視ワード",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "スマホ側で検知した監視ワード一致を通知します"
            }
        )
    }

    private const val CHANNEL_ID = "watch_alerts"
    private const val NOTIFICATION_ID = 2407
}
