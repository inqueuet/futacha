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
import com.valoser.futacha.shared.watch.WATCH_READ_ALOUD_STATUS_MAX_AGE_MILLIS
import com.valoser.futacha.shared.watch.WatchReadAloudStatus
import com.valoser.futacha.shared.watch.WatchSnapshot
import com.valoser.futacha.shared.watch.WatchThreadSummary
import com.valoser.futacha.wear.R
import com.valoser.futacha.wear.WearMainActivity

object ReadAloudLiveUpdateNotifier {
    private const val CHANNEL_ID = "read_aloud_live_update"
    private const val NOTIFICATION_ID = 37_001
    private const val MIN_TIMEOUT_MILLIS = 1_000L

    // canPostNotifications() performs the runtime permission check before this
    // call. Keep the suppression local because lint cannot follow that helper
    // through NotificationManagerCompat.
    @SuppressLint("MissingPermission")
    fun update(context: Context, snapshot: WatchSnapshot?) {
        val appContext = context.applicationContext
        val activeThread = snapshot?.threads?.firstOrNull {
            it.freshReadAloudStatus() != null
        }
        if (activeThread == null) {
            cancel(appContext)
            return
        }
        if (!canPostNotifications(appContext)) {
            return
        }

        ensureChannel(appContext)
        NotificationManagerCompat.from(appContext).notify(
            NOTIFICATION_ID,
            buildNotification(appContext, activeThread)
        )
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
    }

    /**
     * The ongoing notification is only refreshed by status updates from the
     * phone. Without a timeout it stayed forever once they stopped (phone out
     * of range, app killed); expire it when the status is no longer fresh.
     */
    internal fun readAloudNotificationTimeoutMillis(
        status: WatchReadAloudStatus,
        nowMillis: Long = System.currentTimeMillis()
    ): Long = (status.updatedAtMillis + WATCH_READ_ALOUD_STATUS_MAX_AGE_MILLIS - nowMillis)
        .coerceIn(MIN_TIMEOUT_MILLIS, WATCH_READ_ALOUD_STATUS_MAX_AGE_MILLIS)

    private fun buildNotification(
        context: Context,
        thread: WatchThreadSummary
    ) = buildProgress(thread).let { progress ->
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(buildTitle(thread))
            .setContentText(thread.title)
            .setContentIntent(buildContentIntent(context))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .apply {
                thread.freshReadAloudStatus()?.let { status ->
                    setTimeoutAfter(readAloudNotificationTimeoutMillis(status))
                }
            }
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setLocalOnly(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(buildShortCriticalText(thread))
            .setProgress(progress.total, progress.current, false)
            .setStyle(buildProgressStyle(progress))
            .build()
    }

    private fun buildProgressStyle(
        progress: ReadAloudProgress
    ): NotificationCompat.ProgressStyle {
        return NotificationCompat.ProgressStyle()
            .addProgressSegment(NotificationCompat.ProgressStyle.Segment(progress.total))
            .setProgress(progress.current)
    }

    private fun buildProgress(thread: WatchThreadSummary): ReadAloudProgress {
        val status = thread.freshReadAloudStatus()
        val total = status?.totalPosts?.takeIf { it > 0 } ?: 100
        val current = status?.currentIndex
            ?.coerceIn(0, total - 1)
            ?.plus(1)
            ?: 0
        return ReadAloudProgress(total = total, current = current)
    }

    private fun buildTitle(thread: WatchThreadSummary): String {
        val status = thread.freshReadAloudStatus()
        val stateLabel = when (status?.state?.name) {
            "Paused" -> "読み上げ一時停止中"
            else -> "読み上げ中"
        }
        val progress = status?.let {
            val total = it.totalPosts.takeIf { total -> total > 0 } ?: return@let null
            "${(it.currentIndex + 1).coerceIn(1, total)}/$total"
        }
        return listOfNotNull(stateLabel, progress).joinToString(" ")
    }

    private fun buildShortCriticalText(thread: WatchThreadSummary): String {
        val status = thread.freshReadAloudStatus() ?: return "読上げ"
        val total = status.totalPosts.takeIf { it > 0 } ?: return "読上げ"
        return "${(status.currentIndex + 1).coerceIn(1, total)}/$total"
    }

    private fun buildContentIntent(context: Context): PendingIntent {
        val intent = Intent(context, WearMainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "読み上げ Live Update",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "読み上げ中のスレと進行状況を Wear OS 7 の Live Update として表示します"
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    private fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun WatchThreadSummary.freshReadAloudStatus(
        nowMillis: Long = System.currentTimeMillis()
    ): WatchReadAloudStatus? {
        val status = readAloudStatus ?: return null
        val ageMillis = nowMillis - status.updatedAtMillis
        return status.takeIf {
            status.updatedAtMillis > 0 &&
                ageMillis in 0..WATCH_READ_ALOUD_STATUS_MAX_AGE_MILLIS
            }
    }

    private data class ReadAloudProgress(
        val total: Int,
        val current: Int
    )
}
