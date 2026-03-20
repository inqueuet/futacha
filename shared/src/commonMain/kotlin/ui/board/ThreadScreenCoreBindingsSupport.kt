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

internal data class ThreadScreenStateRuntimeHandles(
    val messageRuntime: ThreadMessageRuntimeBindings,
    val threadNgMutationCallbacks: ThreadNgMutationCallbacks,
    val threadFilterBinding: ThreadFilterUiStateBinding,
    val replyDraftBinding: ThreadReplyDraftBinding,
    val replyDialogBinding: ThreadReplyDialogStateBinding,
    val readAloudRuntimeBindings: ThreadReadAloudRuntimeBindings,
    val jobBindings: ThreadScreenJobBindings
)

internal fun resolveThreadScreenStateRuntimeHandles(
    bundle: ThreadScreenStateRuntimeBindingsBundle
): ThreadScreenStateRuntimeHandles {
    return ThreadScreenStateRuntimeHandles(
        messageRuntime = bundle.messageRuntime,
        threadNgMutationCallbacks = bundle.threadNgMutationCallbacks,
        threadFilterBinding = bundle.threadFilterBinding,
        replyDraftBinding = bundle.replyDraftBinding,
        replyDialogBinding = bundle.replyDialogBinding,
        readAloudRuntimeBindings = bundle.readAloudRuntimeBindings,
        jobBindings = bundle.jobBindings
    )
}

internal fun buildThreadScreenStateRuntimeBindingsBundle(
    runtimeJobInputs: ThreadScreenRuntimeJobInputs,
    messageNgInputs: ThreadScreenMessageNgInputs,
    formInputs: ThreadScreenFormInputs
): ThreadScreenStateRuntimeBindingsBundle {
    val runtimeJobBindingsBundle = buildThreadScreenRuntimeJobBindingsBundle(runtimeJobInputs)
    val messageNgBindings = buildThreadScreenMessageNgBindings(messageNgInputs)
    val formBindings = buildThreadScreenFormBindings(formInputs)
    return ThreadScreenStateRuntimeBindingsBundle(
        messageRuntime = messageNgBindings.messageRuntime,
        threadNgMutationCallbacks = messageNgBindings.ngMutationCallbacks,
        threadFilterBinding = formBindings.threadFilterBinding,
        replyDraftBinding = formBindings.replyDraftBinding,
        replyDialogBinding = formBindings.replyDialogBinding,
        readAloudRuntimeBindings = runtimeJobBindingsBundle.readAloudRuntimeBindings,
        jobBindings = runtimeJobBindingsBundle.jobBindings
    )
}
