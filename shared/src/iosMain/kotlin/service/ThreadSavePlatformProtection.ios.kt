package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.SaveProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid
import kotlin.coroutines.coroutineContext

actual suspend fun <T> withThreadSavePlatformProtection(
    title: String,
    progress: StateFlow<SaveProgress?>,
    block: suspend () -> T
): T {
    val saveJob = coroutineContext[Job]
    var taskId: UIBackgroundTaskIdentifier = UIBackgroundTaskInvalid
    taskId = withContext(Dispatchers.Main.immediate) {
        UIApplication.sharedApplication.beginBackgroundTaskWithName("thread-save") {
            saveJob?.cancel()
        }
    }
    return try {
        block()
    } finally {
        if (taskId != UIBackgroundTaskInvalid) {
            withContext(Dispatchers.Main.immediate) {
                UIApplication.sharedApplication.endBackgroundTask(taskId)
            }
        }
    }
}
