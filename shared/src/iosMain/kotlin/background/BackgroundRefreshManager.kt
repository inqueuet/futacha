package com.valoser.futacha.shared.background

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateLock = Any()
    private var isEnabled = false

    fun configure(enabled: Boolean, onExecute: suspend () -> Unit) {
        synchronized(stateLock) {
            isEnabled = enabled
            ensureScope()
        }
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
        if (!isEnabledSafe()) {
            task.setTaskCompletedWithSuccess(false)
            cancel()
            return
        }
        val job = scope.launch {
            try {
                if (isEnabledSafe()) {
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
        if (!isEnabledSafe()) return
        val request = BGAppRefreshTaskRequest(TASK_ID)
        runCatching {
            BGTaskScheduler.sharedScheduler().submitTaskRequest(request, null)
        }.onFailure {
            NSLog("BGTask schedule failed: ${it.message}")
        }
    }

    fun cancel() {
        synchronized(stateLock) {
            isEnabled = false
            scope.coroutineContext[Job]?.cancel()
        }
        if (!isSupported()) return
        BGTaskScheduler.sharedScheduler().cancelTaskRequestWithIdentifier(TASK_ID)
    }

    private fun isSupported(): Boolean {
        val version = NSProcessInfo.processInfo.operatingSystemVersion
        return version.majorVersion >= 13
    }

    private fun isEnabledSafe(): Boolean = synchronized(stateLock) { isEnabled }

    private fun ensureScope() {
        val job = scope.coroutineContext[Job]
        if (job == null || !job.isActive) {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        }
    }
}
