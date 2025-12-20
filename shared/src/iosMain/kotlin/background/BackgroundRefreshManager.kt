package com.valoser.futacha.shared.background

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.cinterop.ExperimentalForeignApi
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSLog

/**
 * Minimal BGTask scheduler helper. Note: actual execution timing is controlled by iOS.
 */
@OptIn(ExperimentalForeignApi::class)
object BackgroundRefreshManager {
    private const val TASK_ID = "com.valoser.futacha.refresh"
    private var registered = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var isEnabled = false

    fun configure(enabled: Boolean, onExecute: suspend () -> Unit) {
        isEnabled = enabled
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
        ) { task: BGTask? ->
            task?.let { handleTask(it, onExecute) }
        }
        if (!registered) {
            NSLog("Failed to register BGTask for $TASK_ID")
        }
    }

    private fun handleTask(task: BGTask, onExecute: suspend () -> Unit) {
        if (!isEnabled) {
            task.setTaskCompletedWithSuccess(false)
            cancel()
            return
        }
        val job = scope.launch {
            try {
                if (isEnabled) {
                    onExecute()
                    task.setTaskCompletedWithSuccess(true)
                } else {
                    task.setTaskCompletedWithSuccess(false)
                }
            } catch (t: Throwable) {
                NSLog("BGTask execution failed: ${t.message}")
                task.setTaskCompletedWithSuccess(false)
            }
            scheduleRefresh()
        }
        // Expiration handler: cancel work if iOS cuts us off
        task.expirationHandler = {
            job.cancel()
            task.setTaskCompletedWithSuccess(false)
        }
    }

    private fun scheduleRefresh() {
        if (!isEnabled) return
        val request = BGAppRefreshTaskRequest(TASK_ID)
        runCatching {
            BGTaskScheduler.sharedScheduler().submitTaskRequest(request, null)
        }.onFailure {
            NSLog("BGTask schedule failed: ${it.message}")
        }
    }

    fun cancel() {
        isEnabled = false
        if (!isSupported()) return
        BGTaskScheduler.sharedScheduler().cancelTaskRequestWithIdentifier(TASK_ID)
    }

    private fun isSupported(): Boolean {
        val versionString = NSProcessInfo.processInfo.operatingSystemVersionString
        val major = versionString.substringAfter("Version ").substringBefore(".").toIntOrNull() ?: 0
        return major >= 13
    }
}
