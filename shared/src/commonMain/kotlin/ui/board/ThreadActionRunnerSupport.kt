package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.repo.BoardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class ThreadActionLaunchState(
    val shouldLaunch: Boolean,
    val nextLastBusyNoticeAtMillis: Long,
    val busyMessage: String? = null
)

internal fun resolveThreadActionLaunchState(
    actionInProgress: Boolean,
    lastBusyActionNoticeAtMillis: Long,
    nowMillis: Long,
    busyNoticeIntervalMillis: Long
): ThreadActionLaunchState {
    if (!actionInProgress) {
        return ThreadActionLaunchState(
            shouldLaunch = true,
            nextLastBusyNoticeAtMillis = lastBusyActionNoticeAtMillis
        )
    }
    val shouldNotifyBusy = nowMillis - lastBusyActionNoticeAtMillis >= busyNoticeIntervalMillis
    return ThreadActionLaunchState(
        shouldLaunch = false,
        nextLastBusyNoticeAtMillis = if (shouldNotifyBusy) nowMillis else lastBusyActionNoticeAtMillis,
        busyMessage = if (shouldNotifyBusy) buildThreadActionBusyMessage() else null
    )
}

internal sealed interface ThreadActionRunResult<out T> {
    data class Success<T>(val value: T) : ThreadActionRunResult<T>
    data class Failure(val error: Throwable) : ThreadActionRunResult<Nothing>
}

internal suspend fun <T> performThreadAction(
    block: suspend () -> T
): ThreadActionRunResult<T> {
    return try {
        ThreadActionRunResult.Success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        ThreadActionRunResult.Failure(error)
    }
}

internal fun buildThreadActionBusyMessage(): String = "処理中です…"

internal fun buildThreadActionStartLogMessage(
    successMessage: String,
    failurePrefix: String
): String {
    return "Starting thread action: success='$successMessage', failure='$failurePrefix'"
}

internal fun buildThreadActionSuccessLogMessage(
    successMessage: String
): String {
    return "Thread action succeeded: $successMessage"
}

internal fun buildThreadActionFailureLogMessage(
    failurePrefix: String
): String {
    return "Thread action failed: $failurePrefix"
}

internal data class ThreadActionRuntimeCallbacks<T>(
    val onActionInProgressChanged: (Boolean) -> Unit,
    val onSuccess: (T) -> Unit = {},
    val onShowMessage: (String) -> Unit,
    val onDebugLog: (String) -> Unit,
    val onInfoLog: (String) -> Unit,
    val onErrorLog: (String, Throwable) -> Unit
)

internal data class ThreadActionLaunchResult(
    val nextLastBusyNoticeAtMillis: Long,
    val launchedJob: Job? = null
)

internal fun <T> CoroutineScope.launchManagedThreadAction(
    actionInProgress: Boolean,
    lastBusyActionNoticeAtMillis: Long,
    nowMillis: Long,
    busyNoticeIntervalMillis: Long,
    successMessage: String,
    failurePrefix: String,
    callbacks: ThreadActionRuntimeCallbacks<T>,
    block: suspend () -> ThreadActionRunResult<T>
): ThreadActionLaunchResult {
    val launchState = resolveThreadActionLaunchState(
        actionInProgress = actionInProgress,
        lastBusyActionNoticeAtMillis = lastBusyActionNoticeAtMillis,
        nowMillis = nowMillis,
        busyNoticeIntervalMillis = busyNoticeIntervalMillis
    )
    if (!launchState.shouldLaunch) {
        launchState.busyMessage?.let(callbacks.onShowMessage)
        return ThreadActionLaunchResult(
            nextLastBusyNoticeAtMillis = launchState.nextLastBusyNoticeAtMillis
        )
    }
    val launchedJob = launch {
        callbacks.onActionInProgressChanged(true)
        callbacks.onDebugLog(
            buildThreadActionStartLogMessage(
                successMessage = successMessage,
                failurePrefix = failurePrefix
            )
        )
        when (val result = block()) {
            is ThreadActionRunResult.Success -> {
                callbacks.onInfoLog(buildThreadActionSuccessLogMessage(successMessage))
                callbacks.onSuccess(result.value)
                callbacks.onShowMessage(successMessage)
            }
            is ThreadActionRunResult.Failure -> {
                callbacks.onErrorLog(
                    buildThreadActionFailureLogMessage(failurePrefix),
                    result.error
                )
                callbacks.onShowMessage(
                    buildThreadActionFailureMessage(
                        failurePrefix = failurePrefix,
                        error = result.error
                    )
                )
            }
        }
        callbacks.onActionInProgressChanged(false)
    }
    return ThreadActionLaunchResult(
        nextLastBusyNoticeAtMillis = launchState.nextLastBusyNoticeAtMillis,
        launchedJob = launchedJob
    )
}

internal data class ThreadDeleteByUserActionConfig(
    val boardUrl: String,
    val threadId: String,
    val postId: String,
    val password: String,
    val imageOnly: Boolean
)

internal fun buildThreadDeleteByUserActionConfig(
    boardUrl: String,
    threadId: String,
    postId: String,
    password: String,
    imageOnly: Boolean
): ThreadDeleteByUserActionConfig {
    return ThreadDeleteByUserActionConfig(
        boardUrl = boardUrl,
        threadId = threadId,
        postId = postId,
        password = password,
        imageOnly = imageOnly
    )
}

internal data class ThreadDeleteByUserActionCallbacks(
    val deleteByUser: suspend (ThreadDeleteByUserActionConfig) -> Unit
)

internal fun buildThreadDeleteByUserActionCallbacks(
    repository: BoardRepository
): ThreadDeleteByUserActionCallbacks {
    return ThreadDeleteByUserActionCallbacks(
        deleteByUser = { config ->
            repository.deleteByUser(
                board = config.boardUrl,
                threadId = config.threadId,
                postId = config.postId,
                password = config.password,
                imageOnly = config.imageOnly
            )
        }
    )
}

internal suspend fun performThreadDeleteByUserAction(
    config: ThreadDeleteByUserActionConfig,
    callbacks: ThreadDeleteByUserActionCallbacks
): ThreadActionRunResult<Unit> {
    return performThreadAction {
        callbacks.deleteByUser(config)
    }
}

internal data class ThreadReplyActionConfig(
    val boardUrl: String,
    val threadId: String,
    val name: String,
    val email: String,
    val subject: String,
    val comment: String,
    val password: String,
    val imageBytes: ByteArray?,
    val imageFileName: String?,
    val textOnly: Boolean
)

internal fun buildThreadReplyActionConfig(
    boardUrl: String,
    threadId: String,
    draft: ThreadReplyDraft,
    normalizedPassword: String
): ThreadReplyActionConfig {
    return ThreadReplyActionConfig(
        boardUrl = boardUrl,
        threadId = threadId,
        name = draft.name,
        email = draft.email,
        subject = draft.subject,
        comment = draft.comment,
        password = normalizedPassword,
        imageBytes = draft.imageData?.bytes,
        imageFileName = draft.imageData?.fileName,
        textOnly = draft.imageData == null
    )
}

internal data class ThreadReplyActionCallbacks(
    val replyToThread: suspend (ThreadReplyActionConfig) -> String?
)

internal fun buildThreadReplyActionCallbacks(
    repository: BoardRepository
): ThreadReplyActionCallbacks {
    return ThreadReplyActionCallbacks(
        replyToThread = { config ->
            repository.replyToThread(
                board = config.boardUrl,
                threadId = config.threadId,
                name = config.name,
                email = config.email,
                subject = config.subject,
                comment = config.comment,
                password = config.password,
                imageFile = config.imageBytes,
                imageFileName = config.imageFileName,
                textOnly = config.textOnly
            )
        }
    )
}

internal suspend fun performThreadReplyAction(
    config: ThreadReplyActionConfig,
    callbacks: ThreadReplyActionCallbacks
): ThreadActionRunResult<String?> {
    return performThreadAction {
        callbacks.replyToThread(config)
    }
}
