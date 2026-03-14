package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post

internal data class ThreadPostActionSelectionState(
    val targetPost: Post?,
    val isActionSheetVisible: Boolean
)

internal fun openThreadPostActionSheet(post: Post): ThreadPostActionSelectionState {
    return ThreadPostActionSelectionState(
        targetPost = post,
        isActionSheetVisible = true
    )
}

internal fun dismissThreadPostActionSheet(): ThreadPostActionSelectionState {
    return ThreadPostActionSelectionState(
        targetPost = null,
        isActionSheetVisible = false
    )
}

internal data class ThreadDeleteDialogState(
    val targetPost: Post?,
    val password: String,
    val imageOnly: Boolean
)

internal fun openThreadDeleteDialog(
    post: Post,
    lastUsedDeleteKey: String
): ThreadDeleteDialogState {
    return ThreadDeleteDialogState(
        targetPost = post,
        password = lastUsedDeleteKey,
        imageOnly = false
    )
}

internal fun dismissThreadDeleteDialog(): ThreadDeleteDialogState {
    return ThreadDeleteDialogState(
        targetPost = null,
        password = "",
        imageOnly = false
    )
}

internal data class ThreadDeleteConfirmState(
    val normalizedPassword: String,
    val imageOnly: Boolean,
    val nextDialogState: ThreadDeleteDialogState
)

internal fun confirmThreadDeleteDialog(
    password: String,
    imageOnly: Boolean
): ThreadDeleteConfirmState {
    return ThreadDeleteConfirmState(
        normalizedPassword = normalizeDeleteKeyForSubmit(password),
        imageOnly = imageOnly,
        nextDialogState = dismissThreadDeleteDialog()
    )
}

internal data class ThreadDeleteDialogSubmitState(
    val validationMessage: String? = null,
    val confirmState: ThreadDeleteConfirmState? = null
)

internal fun resolveThreadDeleteDialogSubmitState(
    password: String,
    imageOnly: Boolean
): ThreadDeleteDialogSubmitState {
    val validationMessage = validateThreadDeletePassword(password)
    return if (validationMessage != null) {
        ThreadDeleteDialogSubmitState(validationMessage = validationMessage)
    } else {
        ThreadDeleteDialogSubmitState(
            confirmState = confirmThreadDeleteDialog(
                password = password,
                imageOnly = imageOnly
            )
        )
    }
}

internal data class ThreadQuoteSelectionState(
    val targetPost: Post?
)

internal fun openThreadQuoteSelection(post: Post): ThreadQuoteSelectionState {
    return ThreadQuoteSelectionState(targetPost = post)
}

internal fun dismissThreadQuoteSelection(): ThreadQuoteSelectionState {
    return ThreadQuoteSelectionState(targetPost = null)
}

private fun resetThreadPostOverlayTransientState(
    currentState: ThreadPostOverlayState
): ThreadPostOverlayState {
    return currentState.copy(
        actionSheetState = dismissThreadPostActionSheet(),
        deleteDialogState = dismissThreadDeleteDialog(),
        quoteSelectionState = dismissThreadQuoteSelection()
    )
}

internal data class ThreadPostOverlayState(
    val actionSheetState: ThreadPostActionSelectionState = dismissThreadPostActionSheet(),
    val deleteDialogState: ThreadDeleteDialogState = dismissThreadDeleteDialog(),
    val quoteSelectionState: ThreadQuoteSelectionState = dismissThreadQuoteSelection(),
    val isNgManagementVisible: Boolean = false,
    val ngHeaderPrefill: String? = null
)

internal fun emptyThreadPostOverlayState(): ThreadPostOverlayState = ThreadPostOverlayState()

internal fun openThreadPostActionOverlay(
    currentState: ThreadPostOverlayState,
    post: Post
): ThreadPostOverlayState {
    return resetThreadPostOverlayTransientState(currentState).copy(
        actionSheetState = openThreadPostActionSheet(post)
    )
}

internal fun dismissThreadPostActionOverlay(
    currentState: ThreadPostOverlayState
): ThreadPostOverlayState {
    return currentState.copy(actionSheetState = dismissThreadPostActionSheet())
}

internal fun openThreadDeleteOverlay(
    currentState: ThreadPostOverlayState,
    post: Post,
    lastUsedDeleteKey: String
): ThreadPostOverlayState {
    return resetThreadPostOverlayTransientState(currentState).copy(
        deleteDialogState = openThreadDeleteDialog(post, lastUsedDeleteKey)
    )
}

internal fun dismissThreadDeleteOverlay(
    currentState: ThreadPostOverlayState
): ThreadPostOverlayState {
    return currentState.copy(deleteDialogState = dismissThreadDeleteDialog())
}

internal fun updateThreadDeleteOverlayInput(
    currentState: ThreadPostOverlayState,
    password: String = currentState.deleteDialogState.password,
    imageOnly: Boolean = currentState.deleteDialogState.imageOnly
): ThreadPostOverlayState {
    return currentState.copy(
        deleteDialogState = currentState.deleteDialogState.copy(
            password = password,
            imageOnly = imageOnly
        )
    )
}

internal fun applyThreadDeleteConfirmState(
    currentState: ThreadPostOverlayState,
    confirmState: ThreadDeleteConfirmState
): ThreadPostOverlayState {
    return currentState.copy(deleteDialogState = confirmState.nextDialogState)
}

internal fun openThreadQuoteOverlay(
    currentState: ThreadPostOverlayState,
    post: Post
): ThreadPostOverlayState {
    return resetThreadPostOverlayTransientState(currentState).copy(
        quoteSelectionState = openThreadQuoteSelection(post)
    )
}

internal fun dismissThreadQuoteOverlay(
    currentState: ThreadPostOverlayState
): ThreadPostOverlayState {
    return currentState.copy(quoteSelectionState = dismissThreadQuoteSelection())
}

internal data class ThreadNgRegistrationOverlayState(
    val overlayState: ThreadPostOverlayState,
    val message: String?
)

internal fun resolveThreadNgRegistrationOverlayState(
    currentState: ThreadPostOverlayState,
    post: Post
): ThreadNgRegistrationOverlayState {
    val actionState = resolveThreadNgRegistrationActionState(post)
    return ThreadNgRegistrationOverlayState(
        overlayState = currentState.copy(
            actionSheetState = dismissThreadPostActionSheet(),
            isNgManagementVisible = actionState.shouldShowNgManagement,
            ngHeaderPrefill = actionState.prefillValue
        ),
        message = actionState.message
    )
}

internal fun openThreadNgManagementOverlay(
    currentState: ThreadPostOverlayState,
    prefill: String? = null
): ThreadPostOverlayState {
    return currentState.copy(
        isNgManagementVisible = true,
        ngHeaderPrefill = prefill
    )
}

internal fun dismissThreadNgManagementOverlay(
    currentState: ThreadPostOverlayState
): ThreadPostOverlayState {
    return currentState.copy(
        isNgManagementVisible = false,
        ngHeaderPrefill = null
    )
}

internal data class ThreadSheetOverlayState(
    val isSettingsVisible: Boolean = false,
    val isFilterVisible: Boolean = false,
    val isReadAloudControlsVisible: Boolean = false
)

internal fun emptyThreadSheetOverlayState(): ThreadSheetOverlayState = ThreadSheetOverlayState()

private fun ThreadSheetOverlayState.withSettingsVisible(isVisible: Boolean): ThreadSheetOverlayState {
    return copy(isSettingsVisible = isVisible)
}

private fun ThreadSheetOverlayState.withFilterVisible(isVisible: Boolean): ThreadSheetOverlayState {
    return copy(isFilterVisible = isVisible)
}

private fun ThreadSheetOverlayState.withReadAloudControlsVisible(isVisible: Boolean): ThreadSheetOverlayState {
    return copy(isReadAloudControlsVisible = isVisible)
}

internal fun openThreadSettingsOverlay(
    currentState: ThreadSheetOverlayState
): ThreadSheetOverlayState {
    return currentState.withSettingsVisible(true)
}

internal fun dismissThreadSettingsOverlay(
    currentState: ThreadSheetOverlayState
): ThreadSheetOverlayState {
    return currentState.withSettingsVisible(false)
}

internal fun openThreadFilterOverlay(
    currentState: ThreadSheetOverlayState
): ThreadSheetOverlayState {
    return currentState.withFilterVisible(true)
}

internal fun dismissThreadFilterOverlay(
    currentState: ThreadSheetOverlayState
): ThreadSheetOverlayState {
    return currentState.withFilterVisible(false)
}

internal fun openThreadReadAloudOverlay(
    currentState: ThreadSheetOverlayState
): ThreadSheetOverlayState {
    return currentState.withReadAloudControlsVisible(true)
}

internal fun dismissThreadReadAloudOverlay(
    currentState: ThreadSheetOverlayState
): ThreadSheetOverlayState {
    return currentState.withReadAloudControlsVisible(false)
}

internal fun applyThreadSettingsActionOverlayState(
    currentState: ThreadSheetOverlayState,
    actionState: ThreadSettingsActionState
): ThreadSheetOverlayState {
    var nextState = currentState
    if (actionState.closeSheet) {
        nextState = dismissThreadSettingsOverlay(nextState)
    }
    if (actionState.showReadAloudControls) {
        nextState = openThreadReadAloudOverlay(nextState)
    }
    if (actionState.reopenSettingsSheet) {
        nextState = openThreadSettingsOverlay(nextState)
    }
    return nextState
}

internal data class ThreadModalOverlayState(
    val isGalleryVisible: Boolean = false,
    val isGlobalSettingsVisible: Boolean = false,
    val isCookieManagementVisible: Boolean = false
)

internal fun emptyThreadModalOverlayState(): ThreadModalOverlayState = ThreadModalOverlayState()

private fun ThreadModalOverlayState.withGalleryVisible(isVisible: Boolean): ThreadModalOverlayState {
    return copy(isGalleryVisible = isVisible)
}

private fun ThreadModalOverlayState.withGlobalSettingsVisible(isVisible: Boolean): ThreadModalOverlayState {
    return copy(isGlobalSettingsVisible = isVisible)
}

private fun ThreadModalOverlayState.withCookieManagementVisible(isVisible: Boolean): ThreadModalOverlayState {
    return copy(isCookieManagementVisible = isVisible)
}

internal fun openThreadGalleryOverlay(
    currentState: ThreadModalOverlayState
): ThreadModalOverlayState {
    return currentState.withGalleryVisible(true)
}

internal fun dismissThreadGalleryOverlay(
    currentState: ThreadModalOverlayState
): ThreadModalOverlayState {
    return currentState.withGalleryVisible(false)
}

internal fun openThreadGlobalSettingsOverlay(
    currentState: ThreadModalOverlayState
): ThreadModalOverlayState {
    return currentState.copy(
        isGlobalSettingsVisible = true,
        isCookieManagementVisible = false
    )
}

internal fun dismissThreadGlobalSettingsOverlay(
    currentState: ThreadModalOverlayState
): ThreadModalOverlayState {
    return currentState.withGlobalSettingsVisible(false)
}

internal fun openThreadCookieManagementOverlay(
    currentState: ThreadModalOverlayState
): ThreadModalOverlayState {
    return currentState.copy(
        isGlobalSettingsVisible = false,
        isCookieManagementVisible = true
    )
}

internal fun dismissThreadCookieManagementOverlay(
    currentState: ThreadModalOverlayState
): ThreadModalOverlayState {
    return currentState.withCookieManagementVisible(false)
}
