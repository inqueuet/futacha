package com.valoser.futacha.shared.service

import android.app.Application
import android.content.Context
import android.content.Intent
import com.valoser.futacha.shared.model.SaveProgress
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
    private val saves = ConcurrentHashMap<String, AndroidProtectedThreadSave>()

    fun register(save: AndroidProtectedThreadSave): String = UUID.randomUUID().toString().also {
        saves[it] = save
    }

    fun get(id: String): AndroidProtectedThreadSave? = saves[id]

    fun cancel(id: String) {
        saves[id]?.job?.cancel()
    }

    fun remove(id: String) {
        saves.remove(id)
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
        if (protectionStarted) {
            runCatching { context.stopService(Intent(context, AndroidThreadSaveForegroundService::class.java)) }
        }
    }
}
