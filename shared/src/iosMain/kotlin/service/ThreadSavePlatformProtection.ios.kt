package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.SaveProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid

actual suspend fun <T> withThreadSavePlatformProtection(
    title: String,
    progress: StateFlow<SaveProgress?>,
    block: suspend () -> T
): T = withBackgroundTaskLease(
    mainDispatcher = Dispatchers.Main.immediate,
    begin = { onExpired ->
        UIApplication.sharedApplication.beginBackgroundTaskWithName("thread-save") { onExpired() }
            .takeIf { it != UIBackgroundTaskInvalid }
    },
    end = { taskId -> UIApplication.sharedApplication.endBackgroundTask(taskId) },
    block = block
)
