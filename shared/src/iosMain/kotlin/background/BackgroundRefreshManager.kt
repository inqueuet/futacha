package com.valoser.futacha.shared.background

import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSLog
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.time.Clock

/**
 * Minimal BGTask scheduler helper. Note: actual execution timing is controlled by iOS.
 */
@OptIn(ExperimentalForeignApi::class)
object BackgroundRefreshManager {
    private const val TASK_ID = "com.valoser.futacha.refresh"
    private const val SCHEDULE_BACKOFF_MILLIS = 60_000L
    private const val MAX_SCHEDULE_RETRY_ATTEMPTS = 12
    private var registered = false
    private val scope = CoroutineScope(SupervisorJob() + AppDispatchers.io)
    private var isEnabled = false
    private var executeBlock: (suspend () -> Unit)? = null
    private var activeTaskJob: Job? = null
    private var retryScheduleJob: Job? = null
    private var nextScheduleAllowedAtMillis: Long = 0L
    private var hasPendingRefreshRequest: Boolean = false
    private var scheduleRetryAttempts: Int = 0

    fun registerAtLaunch() {
        if (!isSupported()) return
        if (!isTaskIdentifierPermitted()) {
            NSLog("Skipping BGTask registration: '$TASK_ID' is not listed in BGTaskSchedulerPermittedIdentifiers")
            return
        }
        NSLog("Registering BGTask at launch for $TASK_ID")
        registerIfNeeded()
    }

    fun configure(enabled: Boolean, onExecute: suspend () -> Unit) {
        NSLog("BGTask configure(enabled=$enabled)")
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
        if (registered) {
            NSLog("BGTask already registered for $TASK_ID")
            return
        }
        if (!isTaskIdentifierPermitted()) {
            NSLog("Skipping BGTask registration: '$TASK_ID' is not listed in BGTaskSchedulerPermittedIdentifiers")
            return
        }
        registered = BGTaskScheduler.sharedScheduler().registerForTaskWithIdentifier(
            identifier = TASK_ID,
            usingQueue = null
        ) { task: BGTask? ->
            if (task != null) {
                handleTask(task)
            } else {
                NSLog("BGTask registration callback received null task for $TASK_ID")
            }
        }
        if (!registered) {
            NSLog("Failed to register BGTask for $TASK_ID")
        } else {
            NSLog("Registered BGTask for $TASK_ID")
        }
    }

    private fun handleTask(task: BGTask) {
        NSLog("BGTask handler invoked for $TASK_ID")
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
                NSLog("BGTask execution started")
                block()
                NSLog("BGTask execution finished successfully")
                completeTask(true)
            } catch (e: CancellationException) {
                NSLog("BGTask execution cancelled: ${e.message}")
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
            NSLog("BGTask expired; cancelling active job")
            job.cancel(CancellationException("BGTask expired"))
            completeTask(false)
        }
    }

    private fun scheduleRefresh() {
        when (
            val action = resolveBackgroundRefreshScheduleAction(
                enabled = isEnabled,
                hasPendingRefreshRequest = hasPendingRefreshRequest,
                nextScheduleAllowedAtMillis = nextScheduleAllowedAtMillis,
                nowEpochMillis = currentEpochMillis()
            )
        ) {
            BackgroundRefreshScheduleAction.SkipDisabled,
            BackgroundRefreshScheduleAction.SkipPending -> {
                NSLog("BGTask schedule skipped: refresh request is already pending")
                return
            }
            BackgroundRefreshScheduleAction.SkipDisabled -> {
                NSLog("BGTask schedule skipped: manager is disabled")
                return
            }
            is BackgroundRefreshScheduleAction.DelayRetry -> {
                NSLog("BGTask schedule delayed by ${action.delayMillis}ms due to backoff")
                scheduleRetryAttempt(action.delayMillis)
                return
            }
            BackgroundRefreshScheduleAction.SubmitNow -> {
                NSLog("BGTask schedule submitting request now")
            }
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
                NSLog("BGTask request submitted successfully for $TASK_ID")
                nextScheduleAllowedAtMillis = 0L
                scheduleRetryAttempts = 0
                retryScheduleJob?.cancel()
                retryScheduleJob = null
            }.onFailure {
                NSLog("BGTask schedule failed: ${it.message}")
                hasPendingRefreshRequest = false
                val failureState = resolveBackgroundRefreshSubmitFailureState(
                    failureNowEpochMillis = currentEpochMillis(),
                    currentRetryAttempts = scheduleRetryAttempts,
                    scheduleBackoffMillis = SCHEDULE_BACKOFF_MILLIS,
                    maxRetryAttempts = MAX_SCHEDULE_RETRY_ATTEMPTS
                )
                nextScheduleAllowedAtMillis = failureState.nextScheduleAllowedAtMillis
                scheduleRetryAttempts = failureState.nextRetryAttempts
                if (!failureState.shouldScheduleRetry) {
                    NSLog(
                        "BGTask schedule retry limit reached ($MAX_SCHEDULE_RETRY_ATTEMPTS); waiting for next explicit enable/event"
                    )
                    return@onFailure
                }
                scheduleRetryAttempt(failureState.retryDelayMillis)
            }
        }
    }

    private fun scheduleRetryAttempt(delayMillis: Long) {
        if (
            !shouldScheduleBackgroundRefreshRetry(
                enabled = isEnabled,
                retryAttempts = scheduleRetryAttempts,
                maxRetryAttempts = MAX_SCHEDULE_RETRY_ATTEMPTS,
                hasActiveRetryJob = retryScheduleJob?.isActive == true
            )
        ) {
            NSLog(
                "BGTask retry scheduling skipped (enabled=$isEnabled, retryAttempts=$scheduleRetryAttempts, hasActiveRetry=${retryScheduleJob?.isActive == true})"
            )
            return
        }
        NSLog("BGTask retry scheduled in ${normalizeBackgroundRefreshRetryDelay(delayMillis)}ms")
        retryScheduleJob = scope.launch {
            delay(normalizeBackgroundRefreshRetryDelay(delayMillis))
            retryScheduleJob = null
            if (isEnabled) {
                scheduleRefresh()
            }
        }
    }

    fun cancel() {
        NSLog("Cancelling BGTask manager state for $TASK_ID")
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
            BGTaskScheduler.sharedScheduler().cancelTaskRequestWithIdentifier(TASK_ID)
        }
    }

    private fun isSupported(): Boolean {
        return NSProcessInfo.processInfo.operatingSystemVersion.useContents {
            majorVersion.toInt() >= 13
        }
    }

    private fun isTaskIdentifierPermitted(): Boolean {
        val value = NSBundle.mainBundle.objectForInfoDictionaryKey("BGTaskSchedulerPermittedIdentifiers")
        val identifiers = value as? List<*> ?: return false
        return identifiers.any { it as? String == TASK_ID }
    }

    private fun currentEpochMillis(): Long =
        Clock.System.now().toEpochMilliseconds()
}
