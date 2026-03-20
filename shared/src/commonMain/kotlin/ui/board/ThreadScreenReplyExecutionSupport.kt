package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.confirmPostingNotice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal data class ThreadScreenReplySubmitDependencies(
    val replyDialogBinding: ThreadReplyDialogStateBinding,
    val effectiveBoardUrl: String,
    val threadId: String,
    val boardId: String,
    val coroutineScope: CoroutineScope,
    val stateStore: AppStateStore?,
    val updateLastUsedDeleteKey: suspend (String) -> Unit,
    val actionBindings: ThreadScreenActionBindings,
    val threadReplyActionCallbacks: ThreadReplyActionCallbacks,
    val refreshThread: () -> Unit,
    val showMessage: suspend (String) -> Unit
)

internal fun handleThreadScreenReplySubmit(deps: ThreadScreenReplySubmitDependencies) {
    val submitState = deps.replyDialogBinding.currentState()
    val submitOutcome = resolveThreadScreenReplySubmitOutcome(
        state = submitState,
        boardUrl = deps.effectiveBoardUrl,
        threadId = deps.threadId
    )
    if (submitOutcome.validationMessage != null) {
        deps.coroutineScope.launch { deps.showMessage(submitOutcome.validationMessage) }
        return
    }
    val replyActionConfig = submitOutcome.actionConfig ?: return
    val dismissedState = submitOutcome.dismissedState ?: return
    deps.coroutineScope.launch {
        if (!checkPostingNoticeIfNeeded(deps.stateStore)) return@launch
        deps.replyDialogBinding.setState(dismissedState)
        submitOutcome.normalizedPassword?.let { deps.updateLastUsedDeleteKey(it) }
        deps.actionBindings.launch(
            successMessage = "返信を送信しました",
            failurePrefix = "返信の送信に失敗しました",
            onSuccess = { thisNo ->
                handleReplySuccess(
                    thisNo = thisNo,
                    stateStore = deps.stateStore,
                    coroutineScope = deps.coroutineScope,
                    threadId = deps.threadId,
                    boardId = deps.boardId,
                    replyDialogBinding = deps.replyDialogBinding,
                    completedState = submitOutcome.completedState,
                    refreshThread = deps.refreshThread
                )
            }
        ) {
            performThreadReplyAction(
                config = replyActionConfig,
                callbacks = deps.threadReplyActionCallbacks
            )
        }
    }
}

private suspend fun checkPostingNoticeIfNeeded(stateStore: AppStateStore?): Boolean {
    val needsPostingNotice = stateStore?.hasShownPostingNotice?.first() == false
    if (needsPostingNotice) {
        val accepted = confirmPostingNotice()
        if (!accepted) return false
        (stateStore ?: return false).setHasShownPostingNotice(true)
    }
    return true
}

private fun handleReplySuccess(
    thisNo: String?,
    stateStore: AppStateStore?,
    coroutineScope: CoroutineScope,
    threadId: String,
    boardId: String,
    replyDialogBinding: ThreadReplyDialogStateBinding,
    completedState: ThreadReplyDialogState?,
    refreshThread: () -> Unit
) {
    if (!thisNo.isNullOrBlank()) {
        stateStore?.let { store ->
            coroutineScope.launch {
                store.addSelfPostIdentifier(
                    threadId = threadId,
                    identifier = thisNo,
                    boardId = boardId.ifBlank { null }
                )
            }
        }
    }
    if (completedState != null) {
        replyDialogBinding.setState(completedState)
        refreshThread()
    }
}
