package com.valoser.futacha

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

class HistoryRefreshService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    companion object {
        private const val TAG = "HistoryRefreshService"
        private const val NOTIFICATION_ID = 1001

        // FIX: サービスの実行状態を静的に保持
        @Volatile
        private var isRunning = false

        fun isServiceRunning(): Boolean = isRunning
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // FIX: 既に実行中の場合は何もしない
        if (loopJob?.isActive == true) {
            Logger.d(TAG, "Service already running, ignoring duplicate start")
            return START_STICKY
        }

        isRunning = true
        loopJob = serviceScope.launch {
            runRefreshLoop()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        loopJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun runRefreshLoop() {
        val app = application as? FutachaApplication ?: run {
            stopSelf()
            return
        }
        val refreshIntervalMillis = TimeUnit.MINUTES.toMillis(15)
        val refreshTimeoutMillis = TimeUnit.MINUTES.toMillis(5)

        while (serviceScope.isActive) {
            val enabled = runCatching { app.appStateStore.isBackgroundRefreshEnabled.first() }.getOrDefault(false)
            if (!enabled) {
                stopSelf()
                break
            }
            try {
                withTimeout(refreshTimeoutMillis) {
                    app.historyRefresher.refresh()
                }
            } catch (e: TimeoutCancellationException) {
                Logger.w(TAG, "Background refresh timed out after ${refreshTimeoutMillis}ms")
            } catch (t: Throwable) {
                Logger.e(TAG, "Background history refresh failed", t)
            }
            delay(refreshIntervalMillis)
        }
    }

    private fun buildNotification(): Notification {
        val channelId = ensureChannel()
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("バックグラウンド更新")
            .setContentText("履歴を15分ごとに更新しています")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .build()
    }

    private fun ensureChannel(): String {
        val channelId = "history_refresh"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                channelId,
                "バックグラウンド更新",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
        return channelId
    }

}
