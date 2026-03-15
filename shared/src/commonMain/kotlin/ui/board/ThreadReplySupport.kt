package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.util.ImageData

internal fun buildDeletePasswordRequiredMessage(): String = "削除キーを入力してください"

internal fun buildReplyCommentRequiredMessage(): String = "コメントを入力してください"

internal data class ThreadReplyDraft(
    val name: String = "",
    val email: String = "",
    val subject: String = "",
    val comment: String = "",
    val password: String = "",
    val imageData: ImageData? = null
)

internal data class ThreadReplyDialogState(
    val isVisible: Boolean = false,
    val draft: ThreadReplyDraft = ThreadReplyDraft()
)

internal data class ThreadReplyDraftBinding(
    val currentDraft: () -> ThreadReplyDraft,
    val setDraft: (ThreadReplyDraft) -> Unit
)

internal fun buildThreadReplyDraftBinding(
    currentName: () -> String,
    currentEmail: () -> String,
    currentSubject: () -> String,
    currentComment: () -> String,
    currentPassword: () -> String,
    currentImageData: () -> ImageData?,
    setName: (String) -> Unit,
    setEmail: (String) -> Unit,
    setSubject: (String) -> Unit,
    setComment: (String) -> Unit,
    setPassword: (String) -> Unit,
    setImageData: (ImageData?) -> Unit
): ThreadReplyDraftBinding {
    return ThreadReplyDraftBinding(
        currentDraft = {
            ThreadReplyDraft(
                name = currentName(),
                email = currentEmail(),
                subject = currentSubject(),
                comment = currentComment(),
                password = currentPassword(),
                imageData = currentImageData()
            )
        },
        setDraft = { draft ->
            setName(draft.name)
            setEmail(draft.email)
            setSubject(draft.subject)
            setComment(draft.comment)
            setPassword(draft.password)
            setImageData(draft.imageData)
        }
    )
}

internal data class ThreadReplyDialogStateBinding(
    val currentState: () -> ThreadReplyDialogState,
    val setState: (ThreadReplyDialogState) -> Unit
)

internal fun buildThreadReplyDialogStateBinding(
    isVisible: () -> Boolean,
    setVisible: (Boolean) -> Unit,
    draftBinding: ThreadReplyDraftBinding
): ThreadReplyDialogStateBinding {
    return ThreadReplyDialogStateBinding(
        currentState = {
            ThreadReplyDialogState(
                isVisible = isVisible(),
                draft = draftBinding.currentDraft()
            )
        },
        setState = { state ->
            setVisible(state.isVisible)
            draftBinding.setDraft(state.draft)
        }
    )
}

internal data class ThreadReplyDialogCallbacks(
    val onCommentChange: (String) -> Unit,
    val onNameChange: (String) -> Unit,
    val onEmailChange: (String) -> Unit,
    val onSubjectChange: (String) -> Unit,
    val onPasswordChange: (String) -> Unit,
    val onImageSelected: (ImageData?) -> Unit,
    val onDismiss: () -> Unit,
    val onClear: () -> Unit
)

internal fun emptyThreadReplyDialogState(): ThreadReplyDialogState = ThreadReplyDialogState()

internal fun emptyThreadReplyDraft(): ThreadReplyDraft = ThreadReplyDraft()

internal fun buildThreadReplyDialogCallbacks(
    currentState: () -> ThreadReplyDialogState,
    setState: (ThreadReplyDialogState) -> Unit
): ThreadReplyDialogCallbacks {
    return ThreadReplyDialogCallbacks(
        onCommentChange = { value ->
            setState(updateThreadReplyDialogComment(currentState(), value))
        },
        onNameChange = { value ->
            setState(updateThreadReplyDialogName(currentState(), value))
        },
        onEmailChange = { value ->
            setState(updateThreadReplyDialogEmail(currentState(), value))
        },
        onSubjectChange = { value ->
            setState(updateThreadReplyDialogSubject(currentState(), value))
        },
        onPasswordChange = { value ->
            setState(updateThreadReplyDialogPassword(currentState(), value))
        },
        onImageSelected = { value ->
            setState(updateThreadReplyDialogImage(currentState(), value))
        },
        onDismiss = {
            setState(dismissThreadReplyDialog(currentState()))
        },
        onClear = {
            setState(clearThreadReplyDialog(currentState()))
        }
    )
}

internal fun applyReplyDraftDeleteKeyAutofill(
    draft: ThreadReplyDraft,
    lastUsedDeleteKey: String
): ThreadReplyDraft {
    return draft.copy(password = resolveDeleteKeyAutofill(draft.password, lastUsedDeleteKey))
}

internal fun openThreadReplyDialog(
    state: ThreadReplyDialogState,
    lastUsedDeleteKey: String
): ThreadReplyDialogState {
    return state.copy(
        isVisible = true,
        draft = applyReplyDraftDeleteKeyAutofill(state.draft, lastUsedDeleteKey)
    )
}

internal fun dismissThreadReplyDialog(
    state: ThreadReplyDialogState
): ThreadReplyDialogState {
    return state.copy(isVisible = false)
}

internal fun updateThreadReplyDialogDraft(
    state: ThreadReplyDialogState,
    transform: (ThreadReplyDraft) -> ThreadReplyDraft
): ThreadReplyDialogState {
    return state.copy(draft = transform(state.draft))
}

private fun updateThreadReplyDialogField(
    state: ThreadReplyDialogState,
    transform: ThreadReplyDraft.() -> ThreadReplyDraft
): ThreadReplyDialogState {
    return updateThreadReplyDialogDraft(state) { draft -> draft.transform() }
}

internal fun updateThreadReplyDialogName(
    state: ThreadReplyDialogState,
    value: String
): ThreadReplyDialogState {
    return updateThreadReplyDialogField(state) { copy(name = value) }
}

internal fun updateThreadReplyDialogEmail(
    state: ThreadReplyDialogState,
    value: String
): ThreadReplyDialogState {
    return updateThreadReplyDialogField(state) { copy(email = value) }
}

internal fun updateThreadReplyDialogSubject(
    state: ThreadReplyDialogState,
    value: String
): ThreadReplyDialogState {
    return updateThreadReplyDialogField(state) { copy(subject = value) }
}

internal fun updateThreadReplyDialogComment(
    state: ThreadReplyDialogState,
    value: String
): ThreadReplyDialogState {
    return updateThreadReplyDialogField(state) { copy(comment = value) }
}

internal fun updateThreadReplyDialogPassword(
    state: ThreadReplyDialogState,
    value: String
): ThreadReplyDialogState {
    return updateThreadReplyDialogField(state) { copy(password = value) }
}

internal fun updateThreadReplyDialogImage(
    state: ThreadReplyDialogState,
    value: ImageData?
): ThreadReplyDialogState {
    return updateThreadReplyDialogField(state) { copy(imageData = value) }
}

internal fun clearThreadReplyDialog(
    state: ThreadReplyDialogState
): ThreadReplyDialogState {
    return state.copy(draft = emptyThreadReplyDraft())
}

internal fun clearThreadReplyDraftAfterSubmit(draft: ThreadReplyDraft): ThreadReplyDraft {
    return draft.copy(
        subject = "",
        comment = "",
        password = "",
        imageData = null
    )
}

internal fun appendQuoteSelectionToReplyDraft(
    draft: ThreadReplyDraft,
    selectedLines: List<String>
): ThreadReplyDraft? {
    val updatedComment = appendSelectedQuoteLines(draft.comment, selectedLines) ?: return null
    return draft.copy(comment = updatedComment)
}

internal fun appendQuoteSelectionToReplyDialog(
    state: ThreadReplyDialogState,
    selectedLines: List<String>
): ThreadReplyDialogState? {
    val updatedDraft = appendQuoteSelectionToReplyDraft(state.draft, selectedLines) ?: return null
    return state.copy(
        isVisible = true,
        draft = updatedDraft
    )
}

internal fun completeThreadReplyDialogSubmit(
    state: ThreadReplyDialogState
): ThreadReplyDialogState {
    return state.copy(
        isVisible = false,
        draft = clearThreadReplyDraftAfterSubmit(state.draft)
    )
}

internal data class ThreadReplyDialogSubmitState(
    val validationMessage: String? = null,
    val normalizedPassword: String? = null,
    val dismissedState: ThreadReplyDialogState? = null,
    val completedState: ThreadReplyDialogState? = null
)

internal fun resolveThreadReplyDialogSubmitState(
    state: ThreadReplyDialogState
): ThreadReplyDialogSubmitState {
    val validationMessage = validateThreadReplyForm(
        password = state.draft.password,
        comment = state.draft.comment
    )
    return if (validationMessage != null) {
        ThreadReplyDialogSubmitState(validationMessage = validationMessage)
    } else {
        val normalizedPassword = normalizeDeleteKeyForSubmit(state.draft.password)
        ThreadReplyDialogSubmitState(
            normalizedPassword = normalizedPassword,
            dismissedState = dismissThreadReplyDialog(state),
            completedState = completeThreadReplyDialogSubmit(state)
        )
    }
}

internal fun validateThreadReplyForm(password: String, comment: String): String? {
    if (!hasDeleteKeyForSubmit(password)) {
        return buildDeletePasswordRequiredMessage()
    }
    if (comment.trim().isBlank()) {
        return buildReplyCommentRequiredMessage()
    }
    return null
}
