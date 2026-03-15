package com.valoser.futacha.shared.ui.board

internal data class ThreadScreenStateRuntimeBindingsBundle(
    val messageRuntime: ThreadMessageRuntimeBindings,
    val threadNgMutationCallbacks: ThreadNgMutationCallbacks,
    val threadFilterBinding: ThreadFilterUiStateBinding,
    val replyDraftBinding: ThreadReplyDraftBinding,
    val replyDialogBinding: ThreadReplyDialogStateBinding,
    val readAloudRuntimeBindings: ThreadReadAloudRuntimeBindings,
    val jobBindings: ThreadScreenJobBindings
)

internal data class ThreadScreenStateRuntimeInputs(
    val runtimeJobInputs: ThreadScreenRuntimeJobInputs,
    val messageFormInputs: ThreadScreenMessageFormInputs
)

internal data class ThreadScreenStateRuntimeAggregateInputs(
    val runtimeJobInputs: ThreadScreenRuntimeJobInputs,
    val messageNgInputs: ThreadScreenMessageNgInputs,
    val formInputs: ThreadScreenFormInputs
)

internal fun buildThreadScreenStateRuntimeBindingsBundle(
    inputs: ThreadScreenStateRuntimeAggregateInputs
): ThreadScreenStateRuntimeBindingsBundle {
    return buildThreadScreenStateRuntimeBindingsBundle(
        ThreadScreenStateRuntimeInputs(
            runtimeJobInputs = inputs.runtimeJobInputs,
            messageFormInputs = ThreadScreenMessageFormInputs(
                messageNgInputs = inputs.messageNgInputs,
                formInputs = inputs.formInputs
            )
        )
    )
}

internal fun buildThreadScreenStateRuntimeBindingsBundle(
    inputs: ThreadScreenStateRuntimeInputs
): ThreadScreenStateRuntimeBindingsBundle {
    val runtimeJobBindingsBundle = buildThreadScreenRuntimeJobBindingsBundle(inputs.runtimeJobInputs)
    val messageFormBindingsBundle = buildThreadScreenMessageFormBindingsBundle(inputs.messageFormInputs)
    return ThreadScreenStateRuntimeBindingsBundle(
        messageRuntime = messageFormBindingsBundle.messageNgBindings.messageRuntime,
        threadNgMutationCallbacks = messageFormBindingsBundle.messageNgBindings.ngMutationCallbacks,
        threadFilterBinding = messageFormBindingsBundle.formBindings.threadFilterBinding,
        replyDraftBinding = messageFormBindingsBundle.formBindings.replyDraftBinding,
        replyDialogBinding = messageFormBindingsBundle.formBindings.replyDialogBinding,
        readAloudRuntimeBindings = runtimeJobBindingsBundle.readAloudRuntimeBindings,
        jobBindings = runtimeJobBindingsBundle.jobBindings
    )
}
