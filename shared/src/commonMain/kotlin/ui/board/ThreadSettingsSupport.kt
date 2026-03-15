package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.ThreadMenuEntryId

internal data class ThreadSettingsSheetCallbacks(
    val onDismiss: () -> Unit,
    val onAction: (ThreadMenuEntryId) -> Unit
)

internal fun buildThreadSettingsSheetCallbacks(
    onDismiss: () -> Unit,
    onApplyActionState: (ThreadSettingsActionState) -> Unit,
    onOpenNgManagement: () -> Unit,
    onOpenExternalApp: () -> Unit,
    onTogglePrivacy: () -> Unit,
    onDelegateToMainActionHandler: (ThreadMenuEntryId) -> Unit
): ThreadSettingsSheetCallbacks {
    return ThreadSettingsSheetCallbacks(
        onDismiss = onDismiss,
        onAction = { menuEntryId ->
            val actionState = resolveThreadSettingsActionState(menuEntryId)
            onApplyActionState(actionState)
            if (actionState.showNgManagement) {
                onOpenNgManagement()
            }
            if (actionState.openExternalApp) {
                onOpenExternalApp()
            }
            if (actionState.togglePrivacy) {
                onTogglePrivacy()
            }
            if (actionState.delegateToMainActionHandler) {
                onDelegateToMainActionHandler(menuEntryId)
            }
        }
    )
}

internal fun buildThreadExternalAppUrl(
    effectiveBoardUrl: String,
    threadId: String
): String {
    val baseUrl = effectiveBoardUrl.trimEnd('/').removeSuffix("/futaba.php")
    return "$baseUrl/res/${threadId}.htm"
}
