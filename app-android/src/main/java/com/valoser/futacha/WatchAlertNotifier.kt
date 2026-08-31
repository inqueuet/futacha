package com.valoser.futacha

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
import com.valoser.futacha.shared.service.CatalogWatchAlertMatch

internal class WatchAlertNotifier(
    private val context: Context
) {
    // canNotify() performs the runtime POST_NOTIFICATIONS check. The lint
    // checker cannot follow that helper through NotificationManagerCompat, so
    // keep the suppression local to this guarded notification boundary.
    @SuppressLint("MissingPermission")
    fun notifyMatches(entries: List<CatalogWatchAlertMatch>): Boolean {
        val notificationManager = NotificationManagerCompat.from(context)
        if (entries.isEmpty() || !canNotify() || !notificationManager.areNotificationsEnabled()) {
            return false
        }
        ensureChannel()
        val first = entries.first()
        val title = if (entries.size == 1) {
            "監視ワードに一致しました"
        } else {
            "監視ワードに ${entries.size} 件一致しました"
        }
        val body = if (entries.size == 1) {
            "${first.boardName}: ${first.title}"
        } else {
            "${first.boardName}: ${first.title} ほか"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(mainActivityPendingIntent())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        return runCatching {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }.isSuccess
    }

    private fun canNotify(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun mainActivityPendingIntent(): PendingIntent {
        return PendingIntent.getActivity(
            context,
            0,
            buildWatchAlertContentIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "監視ワード",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "監視ワードに一致した新着スレを通知します"
            }
        )
    }

    private companion object {
        const val CHANNEL_ID = "watch_alerts"
        const val NOTIFICATION_ID = 2407
    }
}

/**
 * A watch alert is profile-neutral: tapping an old Modern notification may resume
 * the current root, but must not carry a profile override or replay a thread URL.
 */
internal fun buildWatchAlertContentIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
