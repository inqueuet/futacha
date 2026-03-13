package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuEntryId
import com.valoser.futacha.shared.util.SaveDirectorySelection

internal fun buildThreadScreenReplyDialogCallbacks(
    replyDialogBinding: ThreadReplyDialogStateBinding
): ThreadReplyDialogCallbacks {
    return buildThreadReplyDialogCallbacks(
        currentState = replyDialogBinding.currentState,
        setState = replyDialogBinding.setState
    )
}

internal fun buildThreadScreenSettingsSheetCallbacks(
    onDismiss: () -> Unit,
    onApplyActionState: (ThreadSettingsActionState) -> Unit,
    onOpenNgManagement: () -> Unit,
    onOpenExternalApp: () -> Unit,
    onTogglePrivacy: () -> Unit,
    onDelegateToMainActionHandler: (ThreadMenuEntryId) -> Unit
): ThreadSettingsSheetCallbacks {
    return buildThreadSettingsSheetCallbacks(
        onDismiss = onDismiss,
        onApplyActionState = onApplyActionState,
        onOpenNgManagement = onOpenNgManagement,
        onOpenExternalApp = onOpenExternalApp,
        onTogglePrivacy = onTogglePrivacy,
        onDelegateToMainActionHandler = onDelegateToMainActionHandler
    )
}

internal fun buildThreadScreenFilterSheetCallbacks(
    threadFilterBinding: ThreadFilterUiStateBinding,
    onDismiss: () -> Unit
): ThreadFilterSheetCallbacks {
    return buildThreadFilterSheetCallbacks(
        currentState = threadFilterBinding.currentState,
        setState = threadFilterBinding.setState,
        onDismiss = onDismiss
    )
}

internal fun buildThreadScreenReadAloudControlCallbacks(
    firstVisibleSegmentIndex: () -> Int,
    onSeekToIndex: (Int, Boolean) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onShowStoppedMessage: () -> Unit,
    onDismiss: () -> Unit
): ThreadReadAloudControlCallbacks {
    return buildThreadReadAloudControlCallbacks(
        firstVisibleSegmentIndex = firstVisibleSegmentIndex,
        onSeekToIndex = onSeekToIndex,
        onPlay = onPlay,
        onPause = onPause,
        onStop = onStop,
        onShowStoppedMessage = onShowStoppedMessage,
        onDismiss = onDismiss
    )
}

internal fun buildThreadScreenNgManagementCallbacks(
    onDismiss: () -> Unit,
    mutationCallbacks: ThreadNgMutationCallbacks
): ThreadNgManagementCallbacks {
    return buildThreadNgManagementCallbacks(
        onDismiss = onDismiss,
        onAddHeader = mutationCallbacks.onAddHeader,
        onAddWord = mutationCallbacks.onAddWord,
        onRemoveHeader = mutationCallbacks.onRemoveHeader,
        onRemoveWord = mutationCallbacks.onRemoveWord,
        onToggleFiltering = mutationCallbacks.onToggleFiltering
    )
}

internal fun buildThreadScreenSaveProgressDialogCallbacks(
    onDismissRequest: () -> Unit,
    onCancelRequest: () -> Unit
): ThreadSaveProgressDialogCallbacks {
    return buildThreadSaveProgressDialogCallbacks(
        onDismissRequest = onDismissRequest,
        onCancelRequest = onCancelRequest
    )
}

internal fun buildThreadScreenGlobalSettingsCallbacks(
    onBack: () -> Unit,
    onBackgroundRefreshChanged: (Boolean) -> Unit,
    onLightweightModeChanged: (Boolean) -> Unit,
    onManualSaveDirectoryChanged: (String) -> Unit,
    onSaveDirectorySelectionChanged: (SaveDirectorySelection) -> Unit,
    onOpenSaveDirectoryPicker: (() -> Unit)?,
    onOpenCookieManager: (() -> Unit)?,
    onFileManagerSelected: ((packageName: String, label: String) -> Unit)?,
    onClearPreferredFileManager: (() -> Unit)?,
    onThreadMenuEntriesChanged: (List<ThreadMenuEntryConfig>) -> Unit,
    onCatalogNavEntriesChanged: (List<CatalogNavEntryConfig>) -> Unit
): ThreadGlobalSettingsCallbacks {
    return buildThreadGlobalSettingsCallbacks(
        onBack = onBack,
        onBackgroundRefreshChanged = onBackgroundRefreshChanged,
        onLightweightModeChanged = onLightweightModeChanged,
        onManualSaveDirectoryChanged = onManualSaveDirectoryChanged,
        onSaveDirectorySelectionChanged = onSaveDirectorySelectionChanged,
        onOpenSaveDirectoryPicker = onOpenSaveDirectoryPicker,
        onOpenCookieManager = onOpenCookieManager,
        onFileManagerSelected = onFileManagerSelected,
        onClearPreferredFileManager = onClearPreferredFileManager,
        onThreadMenuEntriesChanged = onThreadMenuEntriesChanged,
        onCatalogNavEntriesChanged = onCatalogNavEntriesChanged
    )
}

internal fun buildThreadScreenCookieManagementCallbacks(
    onBack: () -> Unit
): ThreadCookieManagementCallbacks {
    return buildThreadCookieManagementCallbacks(onBack = onBack)
}
