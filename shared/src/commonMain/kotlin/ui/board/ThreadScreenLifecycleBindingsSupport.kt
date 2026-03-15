package com.valoser.futacha.shared.ui.board

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class ThreadScreenLifecycleBindings(
    val pauseReadAloud: () -> Unit,
    val onTextSpeakerDispose: () -> Unit,
    val onThreadChanged: () -> Unit,
    val onScreenDispose: () -> Unit,
    val onBackPressed: () -> Unit,
    val onInitialRefresh: () -> Unit
)

internal fun buildThreadScreenLifecycleBindings(
    coroutineScope: CoroutineScope,
    resolvePauseMessage: () -> String?,
    onShowPauseMessage: suspend (String) -> Unit,
    onStopReadAloud: () -> Unit,
    onCloseTextSpeaker: () -> Unit,
    onResetJobsForThreadChange: () -> Unit,
    onCancelAllJobs: () -> Unit,
    isDrawerOpen: () -> Boolean,
    onCloseDrawer: suspend () -> Unit,
    onPersistCurrentScrollPosition: () -> Unit,
    onBack: () -> Unit,
    onRefreshThread: () -> Unit
): ThreadScreenLifecycleBindings {
    return ThreadScreenLifecycleBindings(
        pauseReadAloud = {
            resolvePauseMessage()?.let { message ->
                coroutineScope.launch {
                    onShowPauseMessage(message)
                }
            }
        },
        onTextSpeakerDispose = {
            onStopReadAloud()
            onCloseTextSpeaker()
        },
        onThreadChanged = onResetJobsForThreadChange,
        onScreenDispose = {
            onPersistCurrentScrollPosition()
            onCancelAllJobs()
        },
        onBackPressed = {
            when (resolveThreadBackAction(isDrawerOpen())) {
                ThreadBackAction.CloseDrawer -> coroutineScope.launch { onCloseDrawer() }
                ThreadBackAction.NavigateBack -> {
                    onPersistCurrentScrollPosition()
                    onBack()
                }
            }
        },
        onInitialRefresh = onRefreshThread
    )
}
