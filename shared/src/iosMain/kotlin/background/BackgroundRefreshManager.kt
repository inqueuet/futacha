package com.valoser.futacha.shared.background

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSLog

/**
 * Minimal BGTask scheduler helper. Note: actual execution timing is controlled by iOS.
 */
object BackgroundRefreshManager {
    private const val TASK_ID = "com.valoser.futacha.refresh"
    private const val SCHEDULE_BACKOFF_MILLIS = 60_000L
    private var registered = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var isEnabled = false
    @Volatile private var executeBlock: (suspend () -> Unit)? = null
    @Volatile private var activeTaskJob: Job? = null
    @Volatile private var nextScheduleAllowedAtMillis: Long = 0L

    fun configure(enabled: Boolean, onExecute: suspend () -> Unit) {
        isEnabled = enabled
        executeBlock = onExecute
        if (!isSupported()) {
            NSLog("BGTask not supported on this OS")
            return
        }
        if (enabled && !isTaskIdentifierPermitted()) {
            NSLog("BGTask identifier '$TASK_ID' is not permitted in Info.plist")
            isEnabled = false
            return
        }
        if (enabled) {
            registerIfNeeded()
            scheduleRefresh()
        } else {
            cancel()
        }
    }

    private fun registerIfNeeded() {
        if (registered) return
        if (!isTaskIdentifierPermitted()) {
            NSLog("Skipping BGTask registration: '$TASK_ID' is not listed in BGTaskSchedulerPermittedIdentifiers")
            return
        }
        registered = BGTaskScheduler.sharedScheduler().registerForTaskWithIdentifier(
            identifier = TASK_ID,
            usingQueue = null
        ) { task: BGTask ->
            handleTask(task)
        }
        if (!registered) {
            NSLog("Failed to register BGTask for $TASK_ID")
        }
    }

    private fun handleTask(task: BGTask) {
        if (!isEnabled) {
            task.setTaskCompletedWithSuccess(false)
            cancel()
            return
        }
        var taskCompleted = false
        val completeTask: (Boolean) -> Unit = { success ->
            if (!taskCompleted) {
                taskCompleted = true
                task.setTaskCompletedWithSuccess(success)
            }
        }
        val runningJob = activeTaskJob
        if (runningJob?.isActive == true) {
            NSLog("BGTask skipped: previous task is still running")
            completeTask(false)
            if (isEnabled) {
                scheduleRefresh()
            }
            return
        }
        val job = scope.launch {
            try {
                if (!isEnabled) {
                    completeTask(false)
                    return@launch
                }
                val block = executeBlock
                if (block == null) {
                    NSLog("BGTask execution skipped: callback is null")
                    completeTask(false)
                    return@launch
                }
                block()
                completeTask(true)
            } catch (e: CancellationException) {
                completeTask(false)
                throw e
            } catch (t: Throwable) {
                NSLog("BGTask execution failed: ${t.message}")
                completeTask(false)
            } finally {
                activeTaskJob = null
                if (isEnabled) {
                    scheduleRefresh()
                }
            }
        }
        activeTaskJob = job
        // Expiration handler: cancel work if iOS cuts us off
        task.expirationHandler = {
            job.cancel(CancellationException("BGTask expired"))
            completeTask(false)
        }
    }

    private fun scheduleRefresh() {
        if (!isEnabled) return
        val now = currentEpochMillis()
        if (now < nextScheduleAllowedAtMillis) {
            return
        }
        val request = BGAppRefreshTaskRequest(TASK_ID)
        runCatching {
            BGTaskScheduler.sharedScheduler().submitTaskRequest(request, null)
            nextScheduleAllowedAtMillis = 0L
        }.onFailure {
            NSLog("BGTask schedule failed: ${it.message}")
            nextScheduleAllowedAtMillis = now + SCHEDULE_BACKOFF_MILLIS
        }
    }

    fun cancel() {
        isEnabled = false
        executeBlock = null
        activeTaskJob?.cancel(CancellationException("Background refresh disabled"))
        activeTaskJob = null
        nextScheduleAllowedAtMillis = 0L
        if (!isSupported()) return
        BGTaskScheduler.sharedScheduler().cancel(taskRequestWithIdentifier = TASK_ID)
    }

    private fun isSupported(): Boolean {
        val version = NSProcessInfo.processInfo.operatingSystemVersion
        return version.majorVersion.toInt() >= 13
    }

    private fun isTaskIdentifierPermitted(): Boolean {
        val value = NSBundle.mainBundle.objectForInfoDictionaryKey("BGTaskSchedulerPermittedIdentifiers")
        val identifiers = value as? List<*> ?: return false
        return identifiers.any { it as? String == TASK_ID }
    }

    private fun currentEpochMillis(): Long =
        kotlin.system.getTimeMillis()
}
