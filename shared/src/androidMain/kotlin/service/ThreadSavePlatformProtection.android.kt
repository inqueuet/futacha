package com.valoser.futacha.shared.service

import android.app.Application
import android.content.Context
import android.content.Intent
import com.valoser.futacha.shared.model.SaveProgress
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import kotlin.coroutines.coroutineContext

private var threadSaveApplicationContext: Context? = null

fun initializeAndroidThreadSavePlatformProtection(application: Application) {
    threadSaveApplicationContext = application.applicationContext
}

internal data class AndroidProtectedThreadSave(
    val title: String,
    val progress: StateFlow<SaveProgress?>,
    val job: Job
)

internal object AndroidProtectedThreadSaveRegistry {
    private val saves = MutableStateFlow<Map<String, AndroidProtectedThreadSave>>(emptyMap())

    /** The service observes removal to decide when it may stop itself. */
    val sessions: StateFlow<Map<String, AndroidProtectedThreadSave>> = saves.asStateFlow()

    fun register(save: AndroidProtectedThreadSave): String = UUID.randomUUID().toString().also { id ->
        saves.update { it + (id to save) }
    }

    fun get(id: String): AndroidProtectedThreadSave? = saves.value[id]

    fun cancel(id: String) {
        saves.value[id]?.job?.cancel()
    }

    fun remove(id: String) {
        saves.update { it - id }
    }
}

actual suspend fun <T> withThreadSavePlatformProtection(
    title: String,
    progress: StateFlow<SaveProgress?>,
    block: suspend () -> T
): T {
    val context = threadSaveApplicationContext ?: return block()
    val job = coroutineContext[Job] ?: return block()
    val sessionId = AndroidProtectedThreadSaveRegistry.register(
        AndroidProtectedThreadSave(title = title, progress = progress, job = job)
    )
    val startIntent = Intent(context, AndroidThreadSaveForegroundService::class.java).apply {
        action = AndroidThreadSaveForegroundService.ACTION_START
        putExtra(AndroidThreadSaveForegroundService.EXTRA_SESSION_ID, sessionId)
    }
    val protectionStarted = runCatching { context.startForegroundService(startIntent) }
        .onFailure { AndroidProtectedThreadSaveRegistry.remove(sessionId) }
        .isSuccess
    var completed = false
    return try {
        block().also { completed = true }
    } finally {
        AndroidProtectedThreadSaveRegistry.remove(sessionId)
        // Notification permission can be denied independently from the save itself.
        // A completed file save must never be reported as failed only because the
        // completion notification could not be posted.
        if (protectionStarted && completed) {
            runCatching { AndroidThreadSaveForegroundService.notifySaveDone(context) }
        }
        // Do not stopService here: a fast save can finish before the service
        // reached startForeground, and stopping a service that was started with
        // startForegroundService before it goes foreground crashes the app. The
        // service observes the session removal and stops itself once foreground.
    }
}
