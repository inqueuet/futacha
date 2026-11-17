package com.valoser.futacha.shared.background

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.valoser.futacha.shared.util.AppDispatchers
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGProcessingTaskRequest
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo
import com.valoser.futacha.shared.util.logToSystem
import platform.Foundation.NSThread
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.time.Clock
import kotlin.coroutines.coroutineContext

/**
 * Minimal BGTask scheduler helper. Note: actual execution timing is controlled by iOS.
 * Submits a BGAppRefresh request (the one iOS runs periodically) and a BGProcessing
 * request (longer runtime, rarely run); whichever starts runs the shared single-flight refresh.
 */
@OptIn(ExperimentalForeignApi::class)
object BackgroundRefreshManager {
    private const val SCHEDULE_BACKOFF_MILLIS = 60_000L
    private const val MIN_REFRESH_INTERVAL_SECONDS = 15 * 60.0
    private const val MAX_SCHEDULE_RETRY_ATTEMPTS = 12
    private val registeredKinds = mutableSetOf<BackgroundRefreshTaskKind>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isEnabled = false
    /** Receives the kind of the running task: BGAppRefresh gets about 30 s, BGProcessing minutes. */
    private var executeBlock: (suspend (BackgroundRefreshTaskKind) -> Unit)? = null
    private var activeTaskJob: Job? = null
    private var retryScheduleJob: Job? = null
    private var nextScheduleAllowedAtMillis: Long = 0L
    private val submitBookkeeping = BackgroundRefreshSubmitBookkeeping()
    private var scheduleRetryAttempts: Int = 0
    private var configurationGeneration: Long = 0L

    fun registerAtLaunch() {
        runOnMain {
            registerAtLaunchOnMain()
        }
    }

    private fun registerAtLaunchOnMain() {
        if (!isSupported()) return
        logToSystem("Registering BGTasks at launch")
        registerIfNeeded()
    }

    internal fun configure(enabled: Boolean, onExecute: suspend (BackgroundRefreshTaskKind) -> Unit) {
        runOnMain {
            configureOnMain(enabled, onExecute)
        }
    }

    private fun configureOnMain(enabled: Boolean, onExecute: suspend (BackgroundRefreshTaskKind) -> Unit) {
        configurationGeneration += 1L
        logToSystem("BGTask configure(enabled=$enabled)")
        isEnabled = enabled
        executeBlock = onExecute
        if (enabled) {
            scheduleRetryAttempts = 0
        }
        if (!isSupported()) {
            logToSystem("BGTask not supported on this OS")
            return
        }
        if (enabled && BackgroundRefreshTaskKind.entries.none(::isTaskIdentifierPermitted)) {
            logToSystem("No BGTask identifier is permitted in Info.plist")
            isEnabled = false
            return
        }
        if (enabled) {
            registerIfNeeded()
            scheduleRefresh()
        } else {
            cancelOnMain()
        }
    }

    private fun registerIfNeeded() {
        BackgroundRefreshTaskKind.entries.forEach(::registerIfNeeded)
    }

    private fun registerIfNeeded(kind: BackgroundRefreshTaskKind) {
        val taskId = kind.identifier
        if (kind in registeredKinds) {
            logToSystem("BGTask already registered for $taskId")
            return
        }
        if (!isTaskIdentifierPermitted(kind)) {
            logToSystem("Skipping BGTask registration: '$taskId' is not listed in BGTaskSchedulerPermittedIdentifiers")
            return
        }
        val registered = BGTaskScheduler.sharedScheduler().registerForTaskWithIdentifier(
            identifier = taskId,
            usingQueue = null
        ) { task: BGTask? ->
            if (task != null) {
                handleTask(task, kind)
            } else {
                logToSystem("BGTask registration callback received null task for $taskId")
            }
        }
        if (!registered) {
            logToSystem("Failed to register BGTask for $taskId")
        } else {
            registeredKinds += kind
            logToSystem("Registered BGTask for $taskId")
        }
    }

    private fun handleTask(task: BGTask, kind: BackgroundRefreshTaskKind) {
        logToSystem("BGTask handler invoked for ${kind.identifier}")
        // BGTaskScheduler invokes this on a system background queue; hop to main
        // so all manager state stays main-confined like configure()/cancel().
        runOnMain {
            handleTaskOnMain(task, kind)
        }
    }

    private fun handleTaskOnMain(task: BGTask, kind: BackgroundRefreshTaskKind) {
        val taskGeneration = configurationGeneration
        submitBookkeeping.markStarted(kind)
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
            // The other kind's task is running the same refresh: never run it twice.
            logToSystem("BGTask ${kind.identifier} skipped: previous task is still running")
            completeTask(true)
            if (isEnabled) {
                scheduleRefresh()
            }
            return
        }
        // Submit the next request before the work, as Apple recommends: when iOS
        // expires or kills this task, a request of this kind is still scheduled (H4-6).
        scheduleRefresh()
        val job = scope.launch {
            try {
                if (!isEnabled) {
                    completeTask(true)
                    return@launch
                }
                val block = executeBlock
                if (block == null) {
                    logToSystem("BGTask execution skipped: callback is null")
                    completeTask(false)
                    return@launch
                }
                logToSystem("BGTask execution started for ${kind.identifier}")
                withContext(AppDispatchers.io) {
                    block(kind)
                }
                logToSystem("BGTask execution finished successfully")
                completeTask(true)
            } catch (e: CancellationException) {
                logToSystem("BGTask execution cancelled: ${e.message}")
                completeTask(false)
                throw e
            } catch (t: Throwable) {
                logToSystem("BGTask execution failed: ${t.message}")
                completeTask(false)
            } finally {
                if (activeTaskJob === coroutineContext[Job]) {
                    activeTaskJob = null
                }
                if (isEnabled && configurationGeneration == taskGeneration) {
                    scheduleRefresh()
                }
            }
        }
        activeTaskJob = job
        // Expiration handler: cancel work if iOS cuts us off
        task.expirationHandler = {
            logToSystem("BGTask expired; cancelling active job")
            job.cancel(CancellationException("BGTask expired"))
            completeTask(false)
        }
    }

    private fun scheduleRefresh() {
        val kindsToSubmit = submitBookkeeping.kindsToSubmit(permittedKinds(), configurationGeneration)
        when (
            val action = resolveBackgroundRefreshScheduleAction(
                enabled = isEnabled,
                hasPendingRefreshRequest = kindsToSubmit.isEmpty(),
                nextScheduleAllowedAtMillis = nextScheduleAllowedAtMillis,
                nowEpochMillis = currentEpochMillis()
            )
        ) {
            BackgroundRefreshScheduleAction.SkipDisabled -> {
                logToSystem("BGTask schedule skipped: manager is disabled")
                return
            }
            BackgroundRefreshScheduleAction.SkipPending -> {
                logToSystem("BGTask schedule skipped: refresh request is already pending")
                return
            }
            is BackgroundRefreshScheduleAction.DelayRetry -> {
                logToSystem("BGTask schedule delayed by ${action.delayMillis}ms due to backoff")
                scheduleRetryAttempt(action.delayMillis)
                return
            }
            BackgroundRefreshScheduleAction.SubmitNow -> {
                logToSystem("BGTask schedule submitting request now")
            }
        }
        val scheduleGeneration = configurationGeneration
        submitBookkeeping.markQueued(kindsToSubmit, scheduleGeneration)
        dispatch_async(dispatch_get_main_queue()) {
            submitBookkeeping.finishQueued(kindsToSubmit, scheduleGeneration)
            if (!isEnabled || configurationGeneration != scheduleGeneration) {
                return@dispatch_async
            }
            var failure: Throwable? = null
            kindsToSubmit.forEach { kind ->
                runCatching {
                    val submitted = BGTaskScheduler.sharedScheduler().submitTaskRequest(createRequest(kind), null)
                    if (!submitted) {
                        throw IllegalStateException("submitTaskRequest returned false")
                    }
                    submitBookkeeping.markSubmitted(kind)
                    logToSystem("BGTask request submitted successfully for ${kind.identifier}")
                }.onFailure {
                    logToSystem("BGTask schedule failed for ${kind.identifier}: ${it.message}")
                    failure = it
                }
            }
            if (failure == null) {
                nextScheduleAllowedAtMillis = 0L
                scheduleRetryAttempts = 0
                retryScheduleJob?.cancel()
                retryScheduleJob = null
            } else run {
                // The retry submits only the kinds that failed; the others stay pending.
                val failureState = resolveBackgroundRefreshSubmitFailureState(
                    failureNowEpochMillis = currentEpochMillis(),
                    currentRetryAttempts = scheduleRetryAttempts,
                    scheduleBackoffMillis = SCHEDULE_BACKOFF_MILLIS,
                    maxRetryAttempts = MAX_SCHEDULE_RETRY_ATTEMPTS
                )
                nextScheduleAllowedAtMillis = failureState.nextScheduleAllowedAtMillis
                scheduleRetryAttempts = failureState.nextRetryAttempts
                if (!failureState.shouldScheduleRetry) {
                    logToSystem(
                        "BGTask schedule retry limit reached ($MAX_SCHEDULE_RETRY_ATTEMPTS); waiting for next explicit enable/event"
                    )
                    return@run
                }
                scheduleRetryAttempt(failureState.retryDelayMillis)
            }
        }
    }

    private fun createRequest(kind: BackgroundRefreshTaskKind) = when (kind) {
        BackgroundRefreshTaskKind.APP_REFRESH -> BGAppRefreshTaskRequest(kind.identifier).apply {
            earliestBeginDate = earliestRefreshDate()
        }
        BackgroundRefreshTaskKind.PROCESSING -> BGProcessingTaskRequest(kind.identifier).apply {
            requiresNetworkConnectivity = true
            requiresExternalPower = false
            earliestBeginDate = earliestRefreshDate()
        }
    }

    private fun earliestRefreshDate() = NSDate(
        timeIntervalSinceReferenceDate = NSDate().timeIntervalSinceReferenceDate + MIN_REFRESH_INTERVAL_SECONDS
    )

    private fun scheduleRetryAttempt(delayMillis: Long) {
        if (
            !shouldScheduleBackgroundRefreshRetry(
                enabled = isEnabled,
                retryAttempts = scheduleRetryAttempts,
                maxRetryAttempts = MAX_SCHEDULE_RETRY_ATTEMPTS,
                hasActiveRetryJob = retryScheduleJob?.isActive == true
            )
        ) {
            logToSystem(
                "BGTask retry scheduling skipped (enabled=$isEnabled, retryAttempts=$scheduleRetryAttempts, hasActiveRetry=${retryScheduleJob?.isActive == true})"
            )
            return
        }
        logToSystem("BGTask retry scheduled in ${normalizeBackgroundRefreshRetryDelay(delayMillis)}ms")
        val retryGeneration = configurationGeneration
        retryScheduleJob = scope.launch {
            delay(normalizeBackgroundRefreshRetryDelay(delayMillis))
            retryScheduleJob = null
            if (isEnabled && configurationGeneration == retryGeneration) {
                scheduleRefresh()
            }
        }
    }

    fun cancel() {
        runOnMain {
            cancelOnMain()
        }
    }

    private fun cancelOnMain() {
        configurationGeneration += 1L
        logToSystem("Cancelling BGTask manager state")
        isEnabled = false
        executeBlock = null
        activeTaskJob?.cancel(CancellationException("Background refresh disabled"))
        activeTaskJob = null
        retryScheduleJob?.cancel(CancellationException("Background refresh retry disabled"))
        retryScheduleJob = null
        nextScheduleAllowedAtMillis = 0L
        submitBookkeeping.clear()
        scheduleRetryAttempts = 0
        if (!isSupported()) return
        BackgroundRefreshTaskKind.entries.forEach {
            BGTaskScheduler.sharedScheduler().cancelTaskRequestWithIdentifier(it.identifier)
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (NSThread.isMainThread) {
            block()
        } else {
            dispatch_async(dispatch_get_main_queue()) {
                block()
            }
        }
    }

    private fun isSupported(): Boolean {
        return NSProcessInfo.processInfo.operatingSystemVersion.useContents {
            majorVersion.toInt() >= 13
        }
    }

    private fun isTaskIdentifierPermitted(kind: BackgroundRefreshTaskKind): Boolean {
        val value = NSBundle.mainBundle.objectForInfoDictionaryKey("BGTaskSchedulerPermittedIdentifiers")
        val identifiers = value as? List<*> ?: return false
        return identifiers.any { it as? String == kind.identifier }
    }

    /** Kinds that can be submitted: permitted in Info.plist and registered at launch. */
    private fun permittedKinds(): Set<BackgroundRefreshTaskKind> =
        BackgroundRefreshTaskKind.entries.filterTo(mutableSetOf()) {
            isTaskIdentifierPermitted(it) && it in registeredKinds
        }

    private fun currentEpochMillis(): Long =
        Clock.System.now().toEpochMilliseconds()
}
