package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.state.AppStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class ThreadScreenOverlayActionCallbacks(
    val onDismissPostActionSheet: () -> Unit,
    val isSaidaneEnabled: (Post) -> Boolean,
    val onDeleteDialogPasswordChange: (String) -> Unit,
    val onDeleteDialogImageOnlyChange: (Boolean) -> Unit,
    val onDeleteDialogDismiss: () -> Unit,
    val onDeleteDialogConfirm: (Post) -> Unit,
    val onQuoteSelectionDismiss: () -> Unit,
    val onReplySubmit: () -> Unit
)

internal data class ThreadScreenOverlayActionInputs(
    val currentPostOverlayState: () -> ThreadPostOverlayState,
    val setPostOverlayState: (ThreadPostOverlayState) -> Unit,
    val isSelfPost: (Post) -> Boolean,
    val replyDialogBinding: ThreadReplyDialogStateBinding,
    val effectiveBoardUrl: String,
    val threadId: String,
    val boardId: String,
    val stateStore: AppStateStore?,
    val coroutineScope: CoroutineScope,
    val updateLastUsedDeleteKey: (String) -> Unit,
    val showMessage: (String) -> Unit,
    val refreshThread: () -> Unit,
    val actionBindings: ThreadScreenActionBindings,
    val threadDeleteByUserActionCallbacks: ThreadDeleteByUserActionCallbacks,
    val threadReplyActionCallbacks: ThreadReplyActionCallbacks
)

internal fun buildThreadScreenOverlayActionCallbacks(
    inputs: ThreadScreenOverlayActionInputs
): ThreadScreenOverlayActionCallbacks {
    return ThreadScreenOverlayActionCallbacks(
        onDismissPostActionSheet = {
            inputs.setPostOverlayState(
                dismissThreadPostActionOverlay(inputs.currentPostOverlayState())
            )
        },
        isSaidaneEnabled = { post ->
            resolveThreadPostActionSheetState(
                isSelfPost = inputs.isSelfPost(post)
            ).isSaidaneEnabled
        },
        onDeleteDialogPasswordChange = { value ->
            inputs.setPostOverlayState(
                updateThreadDeleteOverlayInput(
                    currentState = inputs.currentPostOverlayState(),
                    password = value
                )
            )
        },
        onDeleteDialogImageOnlyChange = { value ->
            inputs.setPostOverlayState(
                updateThreadDeleteOverlayInput(
                    currentState = inputs.currentPostOverlayState(),
                    imageOnly = value
                )
            )
        },
        onDeleteDialogDismiss = {
            inputs.setPostOverlayState(
                dismissThreadDeleteOverlay(inputs.currentPostOverlayState())
            )
        },
        onDeleteDialogConfirm = { deleteTarget ->
            val submitOutcome = resolveThreadScreenDeleteSubmitOutcome(
                overlayState = inputs.currentPostOverlayState(),
                targetPost = deleteTarget,
                boardUrl = inputs.effectiveBoardUrl,
                threadId = inputs.threadId
            )
            if (submitOutcome.validationMessage != null) {
                inputs.showMessage(submitOutcome.validationMessage)
            } else {
                val deleteActionConfig = submitOutcome.actionConfig
                if (deleteActionConfig != null) {
                    inputs.setPostOverlayState(
                        submitOutcome.nextOverlayState ?: inputs.currentPostOverlayState()
                    )
                    submitOutcome.normalizedPassword?.let(inputs.updateLastUsedDeleteKey)
                    inputs.actionBindings.launch(
                        successMessage = "本人削除を実行しました",
                        failurePrefix = "本人削除に失敗しました",
                        onSuccess = { _: Unit -> inputs.refreshThread() }
                    ) {
                        performThreadDeleteByUserAction(
                            config = deleteActionConfig,
                            callbacks = inputs.threadDeleteByUserActionCallbacks
                        )
                    }
                }
            }
        },
        onQuoteSelectionDismiss = {
            inputs.setPostOverlayState(
                dismissThreadQuoteOverlay(inputs.currentPostOverlayState())
            )
        },
        onReplySubmit = {
            val submitState = inputs.replyDialogBinding.currentState()
            val submitOutcome = resolveThreadScreenReplySubmitOutcome(
                state = submitState,
                boardUrl = inputs.effectiveBoardUrl,
                threadId = inputs.threadId
            )
            if (submitOutcome.validationMessage != null) {
                inputs.showMessage(submitOutcome.validationMessage)
            } else {
                val replyActionConfig = submitOutcome.actionConfig
                val dismissedState = submitOutcome.dismissedState
                if (replyActionConfig != null && dismissedState != null) {
                    inputs.replyDialogBinding.setState(dismissedState)
                    submitOutcome.normalizedPassword?.let(inputs.updateLastUsedDeleteKey)
                    inputs.actionBindings.launch(
                        successMessage = "返信を送信しました",
                        failurePrefix = "返信の送信に失敗しました",
                        onSuccess = { thisNo ->
                            if (!thisNo.isNullOrBlank()) {
                                inputs.stateStore?.let { store ->
                                    inputs.coroutineScope.launch {
                                        store.addSelfPostIdentifier(
                                            threadId = inputs.threadId,
                                            identifier = thisNo,
                                            boardId = inputs.boardId.ifBlank { null }
                                        )
                                    }
                                }
                            }
                            val completedState = submitOutcome.completedState
                            if (completedState != null) {
                                inputs.replyDialogBinding.setState(completedState)
                                inputs.refreshThread()
                            }
                        }
                    ) {
                        performThreadReplyAction(
                            config = replyActionConfig,
                            callbacks = inputs.threadReplyActionCallbacks
                        )
                    }
                }
            }
        }
    )
}
