package com.valoser.futacha.shared.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Holds OS playback protection for a user-started speech session, including gaps between replies. */
class AndroidSpeechPlaybackService : Service() {
    private var mediaSession: MediaSession? = null
    private var focus: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var pausedForFocusLoss = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSessions()
            finishPlayback()
            return START_NOT_STICKY
        }
        instance = this
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "読み上げ", NotificationManager.IMPORTANCE_LOW))
            if (mediaSession == null) {
                mediaSession = MediaSession(this, "FutachaReadAloud").apply {
                    setCallback(object : MediaSession.Callback() {
                        override fun onStop() { stopSessions(); finishPlayback() }
                        override fun onPause() { stopSessions(); finishPlayback() }
                    })
                    setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "読み上げ").build())
                    setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_STOP or PlaybackState.ACTION_PAUSE)
                        .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f).build())
                    isActive = true
                }
            }
            val stop = PendingIntent.getService(this, 0, Intent(this, AndroidSpeechPlaybackService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("ふたちゃ 読み上げ")
                .setContentText("画面を消しても読み上げを続けます")
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(Notification.Action.Builder(null, "停止", stop).build())
                .setStyle(Notification.MediaStyle().setMediaSession(mediaSession!!.sessionToken).setShowActionsInCompactView(0))
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                notification.setContentIntent(PendingIntent.getActivity(this, 0, it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            // Fulfil every startForegroundService promise, even if its owner stopped during startup.
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(NOTIFICATION_ID, notification.build())
            if (sessions.isEmpty()) {
                finishPlayback()
                return START_NOT_STICKY
            }
            if (focus == null) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(speechAudioAttributes())
                    .setOnAudioFocusChangeListener { change -> onAudioFocusChange(change) }.build()
                if (getSystemService(AudioManager::class.java).requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    throw IOException("他の音声を再生中のため読み上げを開始できません")
                }
                focus = request
            }
            if (wakeLock == null) {
                wakeLock = getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "futacha:read-aloud").apply {
                        setReferenceCounted(false)
                        acquire()
                    }
            }
            sessions.values.forEach { it.ready.complete(Unit) }
        } catch (failure: Exception) {
            val pending = sessions.values.toList()
            sessions.clear()
            pending.forEach {
                if (!it.ready.completeExceptionally(failure)) it.onStop()
            }
            finishPlayback()
        }
        return START_NOT_STICKY
    }

    /**
     * Only a permanent loss (another player took the focus for good) ends the
     * reading. A short interruption (a call, a navigation prompt) pauses it and
     * resumes on GAIN; if the focus does not come back in time the reading stops
     * like before. A "can duck" loss is left to the system, which lowers our
     * volume itself, so the speech is not cut for a brief notification sound.
     */
    private fun onAudioFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                clearFocusPause()
                stopSessions()
                finishPlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                val pausable = sessions.values.toList()
                if (pausable.isEmpty() || pausable.any { it.onFocusPause == null || it.onFocusResume == null }) {
                    clearFocusPause()
                    stopSessions()
                    finishPlayback()
                    return
                }
                if (!pausedForFocusLoss) {
                    pausedForFocusLoss = true
                    pausable.forEach { it.onFocusPause?.invoke() }
                }
                mainHandler.removeCallbacks(giveUpAfterFocusLoss)
                mainHandler.postDelayed(giveUpAfterFocusLoss, FOCUS_LOSS_GIVE_UP_MILLIS)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                mainHandler.removeCallbacks(giveUpAfterFocusLoss)
                if (pausedForFocusLoss) {
                    pausedForFocusLoss = false
                    sessions.values.toList().forEach { it.onFocusResume?.invoke() }
                }
            }
        }
    }

    private val giveUpAfterFocusLoss = Runnable {
        clearFocusPause()
        stopSessions()
        finishPlayback()
    }

    private fun clearFocusPause() {
        mainHandler.removeCallbacks(giveUpAfterFocusLoss)
        pausedForFocusLoss = false
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSessions()
        finishPlayback()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        stopSessions()
        releaseResources()
        super.onDestroy()
    }

    private fun finishPlayback() {
        releaseResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseResources() {
        clearFocusPause()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
        mediaSession?.release()
        mediaSession = null
    }

    companion object {
        internal const val ACTION_STOP = "com.valoser.futacha.STOP_READ_ALOUD"
        private const val CHANNEL = "read_aloud"
        private const val NOTIFICATION_ID = 4204
        /** Shorter than the shortest per-utterance speak timeout, so giving up is a stop, not an error. */
        private const val FOCUS_LOSS_GIVE_UP_MILLIS = 25_000L
        private data class Session(
            val ready: CompletableDeferred<Unit>,
            val onStop: () -> Unit,
            val onFocusPause: (() -> Unit)? = null,
            val onFocusResume: (() -> Unit)? = null
        )
        private val sessions = ConcurrentHashMap<String, Session>()
        private val mainHandler = Handler(Looper.getMainLooper())
        private var instance: AndroidSpeechPlaybackService? = null

        /**
         * [onFocusPause] / [onFocusResume] let a session survive a short audio-focus
         * interruption; a session that passes neither is stopped by any focus loss.
         */
        internal suspend fun acquire(
            context: Context,
            onFocusPause: (() -> Unit)? = null,
            onFocusResume: (() -> Unit)? = null,
            // Last, so that `acquire(context) { ... }` still hands the trailing lambda to the stop callback.
            onStop: () -> Unit
        ): AutoCloseable {
            val id = UUID.randomUUID().toString()
            val session = Session(CompletableDeferred(), onStop, onFocusPause, onFocusResume)
            sessions[id] = session
            try {
                context.startForegroundService(Intent(context, AndroidSpeechPlaybackService::class.java))
                withTimeout(5_000) { session.ready.await() }
                return AutoCloseable { release(id) }
            } catch (timeout: TimeoutCancellationException) {
                release(id)
                // A TimeoutCancellationException is a CancellationException, which
                // the caller treats as the user's stop and ends silently.
                throw IOException("読み上げの準備がタイムアウトしました", timeout)
            } catch (failure: Throwable) {
                release(id)
                throw failure
            }
        }

        private fun release(id: String) {
            sessions.remove(id)
            mainHandler.post { if (sessions.isEmpty()) instance?.finishPlayback() }
        }

        private fun stopSessions() {
            val current = sessions.values.toList()
            sessions.clear()
            current.forEach {
                it.ready.cancel(CancellationException("読み上げを停止しました"))
                it.onStop()
            }
        }
    }
}

internal fun speechAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
