package com.valoser.futacha.shared.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import com.valoser.futacha.shared.model.SaveProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AndroidThreadSaveForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressJob: Job? = null
    private var activeSessionId: String? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var lastNotificationAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        when (intent?.action) {
            ACTION_CANCEL -> {
                AndroidProtectedThreadSaveRegistry.cancel(sessionId)
                stopCurrentSession()
                return START_NOT_STICKY
            }
            ACTION_FINISH -> {
                if (activeSessionId == sessionId) {
                    notifyDone()
                    stopCurrentSession()
                }
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                if (activeSessionId == sessionId) stopCurrentSession()
                return START_NOT_STICKY
            }
        }

        val save = AndroidProtectedThreadSaveRegistry.get(sessionId)
        if (save == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        activeSessionId = sessionId
        startForeground(NOTIFICATION_ID, buildProgressNotification(sessionId, save.title, save.progress.value))
        acquireLocks()
        progressJob?.cancel()
        progressJob = scope.launch {
            save.progress.collectLatest { progress ->
                val now = SystemClock.elapsedRealtime()
                if (progress == null || now - lastNotificationAt < NOTIFICATION_INTERVAL_MS) return@collectLatest
                lastNotificationAt = now
                notificationManager().notify(
                    NOTIFICATION_ID,
                    buildProgressNotification(sessionId, save.title, progress)
                )
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        progressJob?.cancel()
        releaseLocks()
        scope.cancel()
        super.onDestroy()
    }

    private fun stopCurrentSession() {
        progressJob?.cancel()
        progressJob = null
        releaseLocks()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        notificationManager().createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "スレ保存", NotificationManager.IMPORTANCE_LOW).apply {
                description = THREAD_SAVE_NOTIFICATION_CHANNEL_DESCRIPTION
            }
        )
    }

    private fun buildProgressNotification(
        sessionId: String,
        title: String,
        progress: SaveProgress?
    ): Notification {
        val percent = progress?.getOverallProgressPercentage()?.coerceIn(0, 100) ?: 0
        val cancelIntent = PendingIntent.getService(
            this,
            sessionId.hashCode(),
            Intent(this, AndroidThreadSaveForegroundService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_SESSION_ID, sessionId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("スレを保存中")
            .setContentText(title.ifBlank { progress?.currentItem.orEmpty() })
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, percent, progress == null)
            .addAction(Notification.Action.Builder(null, "キャンセル", cancelIntent).build())
            .build()
    }

    private fun notifyDone() {
        notifySaveDone(this)
    }

    private fun acquireLocks() {
        runCatching {
            wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
                .apply {
                    setReferenceCounted(false)
                    acquire(WAKE_LOCK_TIMEOUT_MS)
                }
        }
        runCatching {
            @Suppress("DEPRECATION")
            wifiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
                ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, WAKE_LOCK_TAG)
                ?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
        }
    }

    private fun releaseLocks() {
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wifiLock = null
        wakeLock = null
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val ACTION_START = "com.valoser.futacha.action.THREAD_SAVE_START"
        const val ACTION_CANCEL = "com.valoser.futacha.action.THREAD_SAVE_CANCEL"
        const val ACTION_FINISH = "com.valoser.futacha.action.THREAD_SAVE_FINISH"
        const val ACTION_STOP = "com.valoser.futacha.action.THREAD_SAVE_STOP"
        const val EXTRA_SESSION_ID = "thread_save_session_id"
        private const val CHANNEL_ID = "thread_save"
        private const val NOTIFICATION_ID = 4101
        private const val NOTIFICATION_ID_DONE = 4102
        private const val NOTIFICATION_INTERVAL_MS = 700L
        private const val WAKE_LOCK_TIMEOUT_MS = 10_800_000L
        private const val WAKE_LOCK_TAG = "futacha:threadsave"

        fun notifySaveDone(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "スレ保存", NotificationManager.IMPORTANCE_LOW).apply {
                    description = THREAD_SAVE_NOTIFICATION_CHANNEL_DESCRIPTION
                }
            )
            manager.notify(
                NOTIFICATION_ID_DONE,
                Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("スレ保存完了")
                    .setContentText("保存が完了しました")
                    .setAutoCancel(true)
                    .build()
            )
        }
    }
}

const val THREAD_SAVE_NOTIFICATION_CHANNEL_DESCRIPTION =
    "スレッドをzipに保存している間だけ表示されます"
