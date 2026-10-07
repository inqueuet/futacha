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
        // Every run posts under its own id, so a later run never replaces the
        // previous one before the user saw it, and each match is listed (the
        // ledger records all of them as notified).
        val notificationId = nextNotificationId()
        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
        entries.take(MAX_INBOX_LINES).forEach { style.addLine("${it.boardName}: ${it.title}") }
        if (entries.size > MAX_INBOX_LINES) {
            style.setSummaryText("ほか ${entries.size - MAX_INBOX_LINES} 件")
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(style)
            .setContentIntent(contentPendingIntent(notificationId, entries))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        return runCatching {
            notificationManager.notify(notificationId, notification)
        }.isSuccess
    }

    /**
     * Whether notifications are turned off by the user (runtime permission or app
     * setting). Retrying such a match later cannot deliver it either.
     */
    fun isDeliveryDisabled(): Boolean =
        !canNotify() || !NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun canNotify(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    /** One match opens its thread; several matches just resume the app. */
    private fun contentPendingIntent(
        requestCode: Int,
        entries: List<CatalogWatchAlertMatch>
    ): PendingIntent {
        val threadIntent = entries.singleOrNull()?.let { match ->
            watchAlertThreadUrl(match.boardUrl, match.threadId)
                ?.let { url -> buildWatchAlertThreadContentIntent(context, url) }
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            threadIntent ?: buildWatchAlertContentIntent(context),
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
        const val MAX_INBOX_LINES = 5
        private val lastNotificationId = java.util.concurrent.atomic.AtomicInteger(0)

        /** Seconds since the epoch: distinct per run, never the fixed id of older versions. */
        fun nextNotificationId(): Int {
            val candidate = (System.currentTimeMillis() / 1000L).toInt().coerceAtLeast(1)
            return lastNotificationId.updateAndGet { previous ->
                if (candidate > previous) candidate else previous + 1
            }
        }
    }
}

/**
 * The thread page a single watch match points at, in the form MainActivity
 * accepts as an external thread link (a trusted 2chan host and
 * `/<board>/res/<number>.htm`); null when it cannot be built safely.
 */
internal fun watchAlertThreadUrl(boardUrl: String, threadId: String): String? {
    if (threadId.isEmpty() || !threadId.all { it in '0'..'9' }) return null
    val trimmed = boardUrl.trim().substringBefore('#').substringBefore('?')
    val scheme = listOf("https://", "http://").firstOrNull { trimmed.startsWith(it, ignoreCase = true) }
        ?: return null
    val rest = trimmed.substring(scheme.length)
    val host = rest.substringBefore('/').substringBefore(':')
    if (!isTrustedFutabaDeepLinkHost(host)) return null
    val path = rest.substringAfter('/', "")
    // A board URL may end in a page (futaba.php / futaba.htm) rather than a directory.
    val dir = if (path.substringAfterLast('/').contains('.')) path.substringBeforeLast('/', "") else path
    val board = dir.trim('/')
    if (board.isEmpty() || board.contains("/res")) return null
    return "$scheme$host/$board/res/$threadId.htm"
}

/** Opens one thread through the same external-link path a 2chan link would take. */
internal fun buildWatchAlertThreadContentIntent(context: Context, threadUrl: String): Intent =
    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(threadUrl), context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

/**
 * A watch alert is profile-neutral: tapping an old Modern notification may resume
 * the current root, but must not carry a profile override or replay a thread URL.
 */
internal fun buildWatchAlertContentIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
