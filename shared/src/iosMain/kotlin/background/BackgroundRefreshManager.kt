package com.valoser.futacha.shared.background

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSLog

/**
 * Minimal BGTask scheduler helper. Note: actual execution timing is controlled by iOS.
 */
object BackgroundRefreshManager {
    private const val TASK_ID = "com.valoser.futacha.refresh"
    private var registered = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun configure(enabled: Boolean, onExecute: suspend () -> Unit) {
        if (!isSupported()) {
            NSLog("BGTask not supported on this OS")
            return
        }
        if (enabled) {
            registerIfNeeded(onExecute)
            scheduleRefresh()
        } else {
            cancel()
        }
    }

    private fun registerIfNeeded(onExecute: suspend () -> Unit) {
        if (registered) return
        registered = BGTaskScheduler.sharedScheduler().registerForTaskWithIdentifier(
            identifier = TASK_ID,
            usingQueue = null
        ) { task: BGTask ->
            handleTask(task, onExecute)
        }
        if (!registered) {
            NSLog("Failed to register BGTask for $TASK_ID")
        }
    }

    private fun handleTask(task: BGTask, onExecute: suspend () -> Unit) {
        scope.launch {
            try {
                onExecute()
                task.setTaskCompletedWithSuccess(true)
            } catch (t: Throwable) {
                NSLog("BGTask execution failed: ${t.message}")
                task.setTaskCompletedWithSuccess(false)
            }
            scheduleRefresh()
        }
        // Expiration handler: cancel work if iOS cuts us off
        task.expirationHandler = {
            runBlocking { task.setTaskCompletedWithSuccess(false) }
        }
    }

    private fun scheduleRefresh() {
        val request = BGAppRefreshTaskRequest(TASK_ID)
        runCatching {
            BGTaskScheduler.sharedScheduler().submitTaskRequest(request, null)
        }.onFailure {
            NSLog("BGTask schedule failed: ${it.message}")
        }
    }

    fun cancel() {
        if (!isSupported()) return
        BGTaskScheduler.sharedScheduler().cancel(taskRequestWithIdentifier = TASK_ID)
    }

    private fun isSupported(): Boolean {
        val version = NSProcessInfo.processInfo.operatingSystemVersion
        return version.majorVersion.toInt() >= 13
    }
}
