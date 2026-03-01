package com.valoser.futacha.shared.background

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSLog
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Minimal BGTask scheduler helper. Note: actual execution timing is controlled by iOS.
 */
object BackgroundRefreshManager {
    private const val TASK_ID = "com.valoser.futacha.refresh"
    private const val SCHEDULE_BACKOFF_MILLIS = 60_000L
    private const val MAX_SCHEDULE_RETRY_ATTEMPTS = 12
    private var registered = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var isEnabled = false
    @Volatile private var executeBlock: (suspend () -> Unit)? = null
    @Volatile private var activeTaskJob: Job? = null
    @Volatile private var retryScheduleJob: Job? = null
    @Volatile private var nextScheduleAllowedAtMillis: Long = 0L
    @Volatile private var hasPendingRefreshRequest: Boolean = false
    @Volatile private var scheduleRetryAttempts: Int = 0

    fun registerAtLaunch() {
        if (!isSupported()) return
        if (!isTaskIdentifierPermitted()) {
            NSLog("Skipping BGTask registration: '$TASK_ID' is not listed in BGTaskSchedulerPermittedIdentifiers")
            return
        }
        registerIfNeeded()
    }

    fun configure(enabled: Boolean, onExecute: suspend () -> Unit) {
        isEnabled = enabled
        executeBlock = onExecute
        if (enabled) {
            scheduleRetryAttempts = 0
        }
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
        hasPendingRefreshRequest = false
        var taskCompleted = false
        val completeTask: (Boolean) -> Unit = { success ->
            dispatch_async(dispatch_get_main_queue()) {
                if (!taskCompleted) {
                    taskCompleted = true
                    task.setTaskCompletedWithSuccess(success)
                }
            }
        }
        if (!isEnabled) {
            completeTask(true)
            cancel()
            return
        }
        val runningJob = activeTaskJob
        if (runningJob?.isActive == true) {
            NSLog("BGTask skipped: previous task is still running")
            completeTask(true)
            if (isEnabled) {
                scheduleRefresh()
            }
            return
        }
        val job = scope.launch {
            try {
                if (!isEnabled) {
                    completeTask(true)
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
        if (hasPendingRefreshRequest) return
        val now = currentEpochMillis()
        val remainingBackoff = nextScheduleAllowedAtMillis - now
        if (remainingBackoff > 0L) {
            scheduleRetryAttempt(remainingBackoff)
            return
        }
        hasPendingRefreshRequest = true
        dispatch_async(dispatch_get_main_queue()) {
            if (!isEnabled) {
                hasPendingRefreshRequest = false
                return@dispatch_async
            }
            val request = BGAppRefreshTaskRequest(TASK_ID)
            runCatching {
                val submitted = BGTaskScheduler.sharedScheduler().submitTaskRequest(request, null)
                if (!submitted) {
                    throw IllegalStateException("submitTaskRequest returned false")
                }
                nextScheduleAllowedAtMillis = 0L
                scheduleRetryAttempts = 0
                retryScheduleJob?.cancel()
                retryScheduleJob = null
            }.onFailure {
                NSLog("BGTask schedule failed: ${it.message}")
                val failureNow = currentEpochMillis()
                hasPendingRefreshRequest = false
                nextScheduleAllowedAtMillis = failureNow + SCHEDULE_BACKOFF_MILLIS
                scheduleRetryAttempts += 1
                if (scheduleRetryAttempts > MAX_SCHEDULE_RETRY_ATTEMPTS) {
                    NSLog(
                        "BGTask schedule retry limit reached ($MAX_SCHEDULE_RETRY_ATTEMPTS); waiting for next explicit enable/event"
                    )
                    return@onFailure
                }
                scheduleRetryAttempt(SCHEDULE_BACKOFF_MILLIS)
            }
        }
    }

    private fun scheduleRetryAttempt(delayMillis: Long) {
        if (!isEnabled) return
        if (scheduleRetryAttempts > MAX_SCHEDULE_RETRY_ATTEMPTS) return
        val activeRetry = retryScheduleJob
        if (activeRetry?.isActive == true) return
        val safeDelay = delayMillis.coerceAtLeast(1L)
        retryScheduleJob = scope.launch {
            delay(safeDelay)
            retryScheduleJob = null
            if (isEnabled) {
                scheduleRefresh()
            }
        }
    }

    fun cancel() {
        isEnabled = false
        executeBlock = null
        activeTaskJob?.cancel(CancellationException("Background refresh disabled"))
        activeTaskJob = null
        retryScheduleJob?.cancel(CancellationException("Background refresh retry disabled"))
        retryScheduleJob = null
        nextScheduleAllowedAtMillis = 0L
        hasPendingRefreshRequest = false
        scheduleRetryAttempts = 0
        if (!isSupported()) return
        dispatch_async(dispatch_get_main_queue()) {
            BGTaskScheduler.sharedScheduler().cancel(taskRequestWithIdentifier = TASK_ID)
        }
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
