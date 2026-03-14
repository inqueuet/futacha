package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadMenuEntryId

internal data class ThreadScreenUiBindingsBundle(
    val topBarCallbacks: ThreadTopBarCallbacks,
    val actionBarCallbacks: ThreadActionBarCallbacks,
    val quoteSelectionConfirm: (List<String>) -> Unit,
    val replyDialogCallbacks: ThreadReplyDialogCallbacks,
    val mediaPreviewDialogCallbacks: ThreadMediaPreviewDialogCallbacks,
    val galleryCallbacks: ThreadGalleryCallbacks?,
    val settingsSheetCallbacks: ThreadSettingsSheetCallbacks,
    val filterSheetCallbacks: ThreadFilterSheetCallbacks,
    val readAloudControlCallbacks: ThreadReadAloudControlCallbacks,
    val ngManagementCallbacks: ThreadNgManagementCallbacks,
    val saveProgressDialogCallbacks: ThreadSaveProgressDialogCallbacks,
    val globalSettingsCallbacks: ThreadGlobalSettingsCallbacks,
    val cookieManagementCallbacks: ThreadCookieManagementCallbacks
)

internal data class ThreadScreenTopBarUiInputs(
    val searchNavigationCallbacks: ThreadSearchNavigationCallbacks,
    val onSearchQueryChange: (String) -> Unit,
    val onSearchClose: () -> Unit,
    val onBack: () -> Unit,
    val onOpenHistory: () -> Unit,
    val onSearch: () -> Unit,
    val onOpenGlobalSettings: () -> Unit,
    val onAction: (ThreadMenuEntryId) -> Unit
)

internal data class ThreadScreenOverlayUiInputs(
    val replyDialogBinding: ThreadReplyDialogStateBinding,
    val currentPostOverlayState: () -> ThreadPostOverlayState,
    val setPostOverlayState: (ThreadPostOverlayState) -> Unit,
    val mediaPreviewState: () -> ThreadMediaPreviewState,
    val setMediaPreviewState: (ThreadMediaPreviewState) -> Unit,
    val mediaPreviewEntryCount: Int,
    val onSavePreviewMedia: (MediaPreviewEntry) -> Unit,
    val galleryPosts: List<Post>?,
    val onDismissGallery: () -> Unit,
    val onScrollToPostIndex: (Int) -> Unit,
    val threadFilterBinding: ThreadFilterUiStateBinding,
    val onDismissSettingsSheet: () -> Unit,
    val onDismissFilterSheet: () -> Unit,
    val onApplySettingsActionState: (ThreadSettingsActionState) -> Unit,
    val onOpenNgManagement: () -> Unit,
    val onOpenExternalApp: () -> Unit,
    val onTogglePrivacy: () -> Unit,
    val firstVisibleSegmentIndex: () -> Int,
    val onSeekToReadAloudIndex: (Int, Boolean) -> Unit,
    val onPlayReadAloud: () -> Unit,
    val onPauseReadAloud: () -> Unit,
    val onStopReadAloud: () -> Unit,
    val onShowReadAloudStoppedMessage: () -> Unit,
    val onDismissReadAloudControls: () -> Unit,
    val onDismissNgManagement: () -> Unit,
    val ngMutationCallbacks: ThreadNgMutationCallbacks,
    val onDismissSaveProgress: () -> Unit,
    val onCancelSaveProgress: () -> Unit
)

internal data class ThreadScreenSettingsUiInputs(
    val onDismissGlobalSettings: () -> Unit,
    val screenPreferencesCallbacks: ScreenPreferencesCallbacks,
    val onOpenCookieManager: (() -> Unit)?,
    val onDismissCookieManagement: () -> Unit
)

internal fun buildThreadScreenUiBindingsBundle(
    topBarInputs: ThreadScreenTopBarUiInputs,
    overlayInputs: ThreadScreenOverlayUiInputs,
    settingsInputs: ThreadScreenSettingsUiInputs
): ThreadScreenUiBindingsBundle {
    return ThreadScreenUiBindingsBundle(
        topBarCallbacks = buildThreadScreenTopBarCallbacks(
            onSearchQueryChange = topBarInputs.onSearchQueryChange,
            onSearchPrev = topBarInputs.searchNavigationCallbacks.onSearchPrev,
            onSearchNext = topBarInputs.searchNavigationCallbacks.onSearchNext,
            onSearchSubmit = topBarInputs.searchNavigationCallbacks.onSearchSubmit,
            onSearchClose = topBarInputs.onSearchClose,
            onBack = topBarInputs.onBack,
            onOpenHistory = topBarInputs.onOpenHistory,
            onSearch = topBarInputs.onSearch,
            onMenuSettings = topBarInputs.onOpenGlobalSettings
        ),
        actionBarCallbacks = buildThreadScreenActionBarCallbacks(
            onAction = topBarInputs.onAction
        ),
        quoteSelectionConfirm = buildThreadScreenQuoteSelectionConfirmHandler(
            replyDialogBinding = overlayInputs.replyDialogBinding,
            currentOverlayState = overlayInputs.currentPostOverlayState,
            setOverlayState = overlayInputs.setPostOverlayState
        ),
        replyDialogCallbacks = buildThreadReplyDialogCallbacks(
            currentState = overlayInputs.replyDialogBinding.currentState,
            setState = overlayInputs.replyDialogBinding.setState
        ),
        mediaPreviewDialogCallbacks = buildThreadScreenMediaPreviewDialogCallbacks(
            mediaPreviewState = overlayInputs.mediaPreviewState,
            setMediaPreviewState = overlayInputs.setMediaPreviewState,
            totalCount = overlayInputs.mediaPreviewEntryCount,
            onSave = overlayInputs.onSavePreviewMedia
        ),
        galleryCallbacks = overlayInputs.galleryPosts?.let { posts ->
            buildThreadScreenGalleryCallbacks(
                currentPosts = posts,
                onDismiss = overlayInputs.onDismissGallery,
                onScrollToPostIndex = overlayInputs.onScrollToPostIndex
            )
        },
        settingsSheetCallbacks = buildThreadSettingsSheetCallbacks(
            onDismiss = overlayInputs.onDismissSettingsSheet,
            onApplyActionState = overlayInputs.onApplySettingsActionState,
            onOpenNgManagement = overlayInputs.onOpenNgManagement,
            onOpenExternalApp = overlayInputs.onOpenExternalApp,
            onTogglePrivacy = overlayInputs.onTogglePrivacy,
            onDelegateToMainActionHandler = topBarInputs.onAction
        ),
        filterSheetCallbacks = buildThreadFilterSheetCallbacks(
            currentState = overlayInputs.threadFilterBinding.currentState,
            setState = overlayInputs.threadFilterBinding.setState,
            onDismiss = overlayInputs.onDismissFilterSheet
        ),
        readAloudControlCallbacks = buildThreadReadAloudControlCallbacks(
            firstVisibleSegmentIndex = overlayInputs.firstVisibleSegmentIndex,
            onSeekToIndex = overlayInputs.onSeekToReadAloudIndex,
            onPlay = overlayInputs.onPlayReadAloud,
            onPause = overlayInputs.onPauseReadAloud,
            onStop = overlayInputs.onStopReadAloud,
            onShowStoppedMessage = overlayInputs.onShowReadAloudStoppedMessage,
            onDismiss = overlayInputs.onDismissReadAloudControls
        ),
        ngManagementCallbacks = buildThreadNgManagementCallbacks(
            onDismiss = overlayInputs.onDismissNgManagement,
            onAddHeader = overlayInputs.ngMutationCallbacks.onAddHeader,
            onAddWord = overlayInputs.ngMutationCallbacks.onAddWord,
            onRemoveHeader = overlayInputs.ngMutationCallbacks.onRemoveHeader,
            onRemoveWord = overlayInputs.ngMutationCallbacks.onRemoveWord,
            onToggleFiltering = overlayInputs.ngMutationCallbacks.onToggleFiltering
        ),
        saveProgressDialogCallbacks = buildThreadSaveProgressDialogCallbacks(
            onDismissRequest = overlayInputs.onDismissSaveProgress,
            onCancelRequest = overlayInputs.onCancelSaveProgress
        ),
        globalSettingsCallbacks = buildThreadGlobalSettingsCallbacks(
            onBack = settingsInputs.onDismissGlobalSettings,
            onOpenCookieManager = settingsInputs.onOpenCookieManager,
            preferencesCallbacks = settingsInputs.screenPreferencesCallbacks
        ),
        cookieManagementCallbacks = buildThreadCookieManagementCallbacks(
            onBack = settingsInputs.onDismissCookieManagement
        )
    )
}
