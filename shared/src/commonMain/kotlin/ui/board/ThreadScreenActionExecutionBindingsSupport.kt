package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.service.HistoryRefresher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.time.Clock

internal data class ThreadScreenActionStateBindings(
    val currentActionInProgress: () -> Boolean,
    val setActionInProgress: (Boolean) -> Unit,
    val currentLastBusyNoticeAtMillis: () -> Long,
    val setLastBusyNoticeAtMillis: (Long) -> Unit
)

internal data class ThreadScreenActionDependencies(
    val currentTimeMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    val busyNoticeIntervalMillis: Long,
    val showMessage: (String) -> Unit,
    val onDebugLog: (String) -> Unit,
    val onInfoLog: (String) -> Unit,
    val onErrorLog: (String, Throwable) -> Unit
)

internal class ThreadScreenActionBindings(
    private val coroutineScope: CoroutineScope,
    private val stateBindings: ThreadScreenActionStateBindings,
    private val dependencies: ThreadScreenActionDependencies
) {
    fun <T> launch(
        successMessage: String,
        failurePrefix: String,
        onSuccess: (T) -> Unit = {},
        block: suspend () -> ThreadActionRunResult<T>
    ): Job? {
        val launchResult = coroutineScope.launchManagedThreadAction(
            actionInProgress = stateBindings.currentActionInProgress(),
            lastBusyActionNoticeAtMillis = stateBindings.currentLastBusyNoticeAtMillis(),
            nowMillis = dependencies.currentTimeMillis(),
            busyNoticeIntervalMillis = dependencies.busyNoticeIntervalMillis,
            successMessage = successMessage,
            failurePrefix = failurePrefix,
            callbacks = ThreadActionRuntimeCallbacks(
                onActionInProgressChanged = stateBindings.setActionInProgress,
                onSuccess = onSuccess,
                onShowMessage = dependencies.showMessage,
                onDebugLog = dependencies.onDebugLog,
                onInfoLog = dependencies.onInfoLog,
                onErrorLog = dependencies.onErrorLog
            ),
            block = block
        )
        stateBindings.setLastBusyNoticeAtMillis(launchResult.nextLastBusyNoticeAtMillis)
        return launchResult.launchedJob
    }
}

internal fun buildThreadScreenActionBindings(
    coroutineScope: CoroutineScope,
    stateBindings: ThreadScreenActionStateBindings,
    dependencies: ThreadScreenActionDependencies
): ThreadScreenActionBindings {
    return ThreadScreenActionBindings(
        coroutineScope = coroutineScope,
        stateBindings = stateBindings,
        dependencies = dependencies
    )
}

internal data class ThreadScreenHistoryRefreshStateBindings(
    val currentIsHistoryRefreshing: () -> Boolean,
    val setIsHistoryRefreshing: (Boolean) -> Unit
)

internal data class ThreadScreenHistoryRefreshBindings(
    val handleHistoryRefresh: () -> Unit
)

internal data class ThreadScreenActionExecutionBindingsBundle(
    val actionBindings: ThreadScreenActionBindings,
    val historyRefreshBindings: ThreadScreenHistoryRefreshBindings,
    val readAloudBindings: ThreadScreenReadAloudBindings
)

internal data class ThreadScreenDeleteSubmitOutcome(
    val validationMessage: String? = null,
    val nextOverlayState: ThreadPostOverlayState? = null,
    val normalizedPassword: String? = null,
    val actionConfig: ThreadDeleteByUserActionConfig? = null
)

internal fun resolveThreadScreenDeleteSubmitOutcome(
    overlayState: ThreadPostOverlayState,
    targetPost: Post?,
    boardUrl: String,
    threadId: String
): ThreadScreenDeleteSubmitOutcome {
    val submitState = resolveThreadDeleteDialogSubmitState(
        password = overlayState.deleteDialogState.password,
        imageOnly = overlayState.deleteDialogState.imageOnly
    )
    if (submitState.validationMessage != null) {
        return ThreadScreenDeleteSubmitOutcome(
            validationMessage = submitState.validationMessage
        )
    }
    val confirmState = submitState.confirmState ?: return ThreadScreenDeleteSubmitOutcome()
    val post = targetPost ?: return ThreadScreenDeleteSubmitOutcome()
    return ThreadScreenDeleteSubmitOutcome(
        nextOverlayState = applyThreadDeleteConfirmState(
            currentState = overlayState,
            confirmState = confirmState
        ),
        normalizedPassword = confirmState.normalizedPassword,
        actionConfig = buildThreadDeleteByUserActionConfig(
            boardUrl = boardUrl,
            threadId = threadId,
            postId = post.id,
            password = confirmState.normalizedPassword,
            imageOnly = confirmState.imageOnly
        )
    )
}

internal data class ThreadScreenReplySubmitOutcome(
    val validationMessage: String? = null,
    val normalizedPassword: String? = null,
    val dismissedState: ThreadReplyDialogState? = null,
    val completedState: ThreadReplyDialogState? = null,
    val actionConfig: ThreadReplyActionConfig? = null
)

internal fun resolveThreadScreenReplySubmitOutcome(
    state: ThreadReplyDialogState,
    boardUrl: String,
    threadId: String
): ThreadScreenReplySubmitOutcome {
    val submitResolution = resolveThreadReplyDialogSubmitState(state)
    if (submitResolution.validationMessage != null) {
        return ThreadScreenReplySubmitOutcome(
            validationMessage = submitResolution.validationMessage
        )
    }
    val normalizedPassword = submitResolution.normalizedPassword ?: return ThreadScreenReplySubmitOutcome()
    return ThreadScreenReplySubmitOutcome(
        normalizedPassword = normalizedPassword,
        dismissedState = submitResolution.dismissedState,
        completedState = submitResolution.completedState,
        actionConfig = buildThreadReplyActionConfig(
            boardUrl = boardUrl,
            threadId = threadId,
            draft = state.draft,
            normalizedPassword = normalizedPassword
        )
    )
}

internal fun buildThreadScreenHistoryRefreshBindings(
    coroutineScope: CoroutineScope,
    stateBindings: ThreadScreenHistoryRefreshStateBindings,
    onHistoryRefresh: suspend () -> Unit,
    showMessage: suspend (String) -> Unit
): ThreadScreenHistoryRefreshBindings {
    return ThreadScreenHistoryRefreshBindings(
        handleHistoryRefresh = handleHistoryRefresh@{
            when (resolveThreadHistoryRefreshAvailability(stateBindings.currentIsHistoryRefreshing())) {
                ThreadHistoryRefreshAvailability.Busy -> return@handleHistoryRefresh
                ThreadHistoryRefreshAvailability.Ready -> Unit
            }
            stateBindings.setIsHistoryRefreshing(true)
            coroutineScope.launch {
                try {
                    onHistoryRefresh()
                    showMessage(buildThreadHistoryRefreshSuccessMessage())
                } catch (e: HistoryRefresher.RefreshAlreadyRunningException) {
                    showMessage(buildThreadHistoryRefreshAlreadyRunningMessage())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    showMessage(buildThreadHistoryRefreshFailureMessage(e))
                } finally {
                    stateBindings.setIsHistoryRefreshing(false)
                }
            }
        }
    )
}

internal fun buildThreadScreenActionExecutionBindingsBundle(
    coroutineScope: CoroutineScope,
    actionStateBindings: ThreadScreenActionStateBindings,
    actionDependencies: ThreadScreenActionDependencies,
    historyRefreshStateBindings: ThreadScreenHistoryRefreshStateBindings,
    onHistoryRefresh: suspend () -> Unit,
    showHistoryRefreshMessage: suspend (String) -> Unit,
    readAloudStateBindings: ThreadScreenReadAloudStateBindings,
    readAloudCallbacks: ThreadScreenReadAloudCallbacks,
    readAloudDependencies: ThreadScreenReadAloudDependencies
): ThreadScreenActionExecutionBindingsBundle {
    return ThreadScreenActionExecutionBindingsBundle(
        actionBindings = buildThreadScreenActionBindings(
            coroutineScope = coroutineScope,
            stateBindings = actionStateBindings,
            dependencies = actionDependencies
        ),
        historyRefreshBindings = buildThreadScreenHistoryRefreshBindings(
            coroutineScope = coroutineScope,
            stateBindings = historyRefreshStateBindings,
            onHistoryRefresh = onHistoryRefresh,
            showMessage = showHistoryRefreshMessage
        ),
        readAloudBindings = buildThreadScreenReadAloudBindings(
            coroutineScope = coroutineScope,
            stateBindings = readAloudStateBindings,
            callbacks = readAloudCallbacks,
            dependencies = readAloudDependencies
        )
    )
}
