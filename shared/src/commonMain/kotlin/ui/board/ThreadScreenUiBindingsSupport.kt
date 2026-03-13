package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuEntryId
import com.valoser.futacha.shared.util.SaveDirectorySelection

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

internal fun buildThreadScreenUiBindingsBundle(
    searchNavigationCallbacks: ThreadSearchNavigationCallbacks,
    onSearchQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onSearch: () -> Unit,
    onOpenGlobalSettings: () -> Unit,
    onAction: (ThreadMenuEntryId) -> Unit,
    replyDialogBinding: ThreadReplyDialogStateBinding,
    currentPostOverlayState: () -> ThreadPostOverlayState,
    setPostOverlayState: (ThreadPostOverlayState) -> Unit,
    mediaPreviewState: () -> ThreadMediaPreviewState,
    setMediaPreviewState: (ThreadMediaPreviewState) -> Unit,
    mediaPreviewEntryCount: Int,
    onSavePreviewMedia: (MediaPreviewEntry) -> Unit,
    galleryPosts: List<Post>?,
    onDismissGallery: () -> Unit,
    onScrollToPostIndex: (Int) -> Unit,
    threadFilterBinding: ThreadFilterUiStateBinding,
    onDismissSettingsSheet: () -> Unit,
    onDismissFilterSheet: () -> Unit,
    onApplySettingsActionState: (ThreadSettingsActionState) -> Unit,
    onOpenNgManagement: () -> Unit,
    onOpenExternalApp: () -> Unit,
    onTogglePrivacy: () -> Unit,
    firstVisibleSegmentIndex: () -> Int,
    onSeekToReadAloudIndex: (Int, Boolean) -> Unit,
    onPlayReadAloud: () -> Unit,
    onPauseReadAloud: () -> Unit,
    onStopReadAloud: () -> Unit,
    onShowReadAloudStoppedMessage: () -> Unit,
    onDismissReadAloudControls: () -> Unit,
    onDismissNgManagement: () -> Unit,
    ngMutationCallbacks: ThreadNgMutationCallbacks,
    onDismissSaveProgress: () -> Unit,
    onCancelSaveProgress: () -> Unit,
    onDismissGlobalSettings: () -> Unit,
    onBackgroundRefreshChanged: (Boolean) -> Unit,
    onLightweightModeChanged: (Boolean) -> Unit,
    onManualSaveDirectoryChanged: (String) -> Unit,
    onSaveDirectorySelectionChanged: (SaveDirectorySelection) -> Unit,
    onOpenSaveDirectoryPicker: (() -> Unit)?,
    onOpenCookieManager: (() -> Unit)?,
    onFileManagerSelected: ((packageName: String, label: String) -> Unit)?,
    onClearPreferredFileManager: (() -> Unit)?,
    onThreadMenuEntriesChanged: (List<ThreadMenuEntryConfig>) -> Unit,
    onCatalogNavEntriesChanged: (List<CatalogNavEntryConfig>) -> Unit,
    onDismissCookieManagement: () -> Unit
): ThreadScreenUiBindingsBundle {
    return buildThreadScreenUiBindingsBundle(
        searchNavigationCallbacks = searchNavigationCallbacks,
        onSearchQueryChange = onSearchQueryChange,
        onSearchClose = onSearchClose,
        onBack = onBack,
        onOpenHistory = onOpenHistory,
        onSearch = onSearch,
        onOpenGlobalSettings = onOpenGlobalSettings,
        onAction = onAction,
        replyDialogBinding = replyDialogBinding,
        currentPostOverlayState = currentPostOverlayState,
        setPostOverlayState = setPostOverlayState,
        mediaPreviewState = mediaPreviewState,
        setMediaPreviewState = setMediaPreviewState,
        mediaPreviewEntryCount = mediaPreviewEntryCount,
        onSavePreviewMedia = onSavePreviewMedia,
        galleryPosts = galleryPosts,
        onDismissGallery = onDismissGallery,
        onScrollToPostIndex = onScrollToPostIndex,
        threadFilterBinding = threadFilterBinding,
        onDismissSettingsSheet = onDismissSettingsSheet,
        onDismissFilterSheet = onDismissFilterSheet,
        onApplySettingsActionState = onApplySettingsActionState,
        onOpenNgManagement = onOpenNgManagement,
        onOpenExternalApp = onOpenExternalApp,
        onTogglePrivacy = onTogglePrivacy,
        firstVisibleSegmentIndex = firstVisibleSegmentIndex,
        onSeekToReadAloudIndex = onSeekToReadAloudIndex,
        onPlayReadAloud = onPlayReadAloud,
        onPauseReadAloud = onPauseReadAloud,
        onStopReadAloud = onStopReadAloud,
        onShowReadAloudStoppedMessage = onShowReadAloudStoppedMessage,
        onDismissReadAloudControls = onDismissReadAloudControls,
        onDismissNgManagement = onDismissNgManagement,
        ngMutationCallbacks = ngMutationCallbacks,
        onDismissSaveProgress = onDismissSaveProgress,
        onCancelSaveProgress = onCancelSaveProgress,
        onDismissGlobalSettings = onDismissGlobalSettings,
        screenPreferencesCallbacks = ScreenPreferencesCallbacks(
            onBackgroundRefreshChanged = onBackgroundRefreshChanged,
            onLightweightModeChanged = onLightweightModeChanged,
            onManualSaveDirectoryChanged = onManualSaveDirectoryChanged,
            onSaveDirectorySelectionChanged = onSaveDirectorySelectionChanged,
            onOpenSaveDirectoryPicker = onOpenSaveDirectoryPicker,
            onFileManagerSelected = onFileManagerSelected,
            onClearPreferredFileManager = onClearPreferredFileManager,
            onThreadMenuEntriesChanged = onThreadMenuEntriesChanged,
            onCatalogNavEntriesChanged = onCatalogNavEntriesChanged
        ),
        onOpenCookieManager = onOpenCookieManager,
        onDismissCookieManagement = onDismissCookieManagement
    )
}

internal fun buildThreadScreenUiBindingsBundle(
    searchNavigationCallbacks: ThreadSearchNavigationCallbacks,
    onSearchQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onSearch: () -> Unit,
    onOpenGlobalSettings: () -> Unit,
    onAction: (ThreadMenuEntryId) -> Unit,
    replyDialogBinding: ThreadReplyDialogStateBinding,
    currentPostOverlayState: () -> ThreadPostOverlayState,
    setPostOverlayState: (ThreadPostOverlayState) -> Unit,
    mediaPreviewState: () -> ThreadMediaPreviewState,
    setMediaPreviewState: (ThreadMediaPreviewState) -> Unit,
    mediaPreviewEntryCount: Int,
    onSavePreviewMedia: (MediaPreviewEntry) -> Unit,
    galleryPosts: List<Post>?,
    onDismissGallery: () -> Unit,
    onScrollToPostIndex: (Int) -> Unit,
    threadFilterBinding: ThreadFilterUiStateBinding,
    onDismissSettingsSheet: () -> Unit,
    onDismissFilterSheet: () -> Unit,
    onApplySettingsActionState: (ThreadSettingsActionState) -> Unit,
    onOpenNgManagement: () -> Unit,
    onOpenExternalApp: () -> Unit,
    onTogglePrivacy: () -> Unit,
    firstVisibleSegmentIndex: () -> Int,
    onSeekToReadAloudIndex: (Int, Boolean) -> Unit,
    onPlayReadAloud: () -> Unit,
    onPauseReadAloud: () -> Unit,
    onStopReadAloud: () -> Unit,
    onShowReadAloudStoppedMessage: () -> Unit,
    onDismissReadAloudControls: () -> Unit,
    onDismissNgManagement: () -> Unit,
    ngMutationCallbacks: ThreadNgMutationCallbacks,
    onDismissSaveProgress: () -> Unit,
    onCancelSaveProgress: () -> Unit,
    onDismissGlobalSettings: () -> Unit,
    screenPreferencesCallbacks: ScreenPreferencesCallbacks,
    onOpenCookieManager: (() -> Unit)?,
    onDismissCookieManagement: () -> Unit
): ThreadScreenUiBindingsBundle {
    return ThreadScreenUiBindingsBundle(
        topBarCallbacks = buildThreadScreenTopBarCallbacks(
            onSearchQueryChange = onSearchQueryChange,
            onSearchPrev = searchNavigationCallbacks.onSearchPrev,
            onSearchNext = searchNavigationCallbacks.onSearchNext,
            onSearchSubmit = searchNavigationCallbacks.onSearchSubmit,
            onSearchClose = onSearchClose,
            onBack = onBack,
            onOpenHistory = onOpenHistory,
            onSearch = onSearch,
            onMenuSettings = onOpenGlobalSettings
        ),
        actionBarCallbacks = buildThreadScreenActionBarCallbacks(
            onAction = onAction
        ),
        quoteSelectionConfirm = buildThreadScreenQuoteSelectionConfirmHandler(
            replyDialogBinding = replyDialogBinding,
            currentOverlayState = currentPostOverlayState,
            setOverlayState = setPostOverlayState
        ),
        replyDialogCallbacks = buildThreadScreenReplyDialogCallbacks(
            replyDialogBinding = replyDialogBinding
        ),
        mediaPreviewDialogCallbacks = buildThreadScreenMediaPreviewDialogCallbacks(
            mediaPreviewState = mediaPreviewState,
            setMediaPreviewState = setMediaPreviewState,
            totalCount = mediaPreviewEntryCount,
            onSave = onSavePreviewMedia
        ),
        galleryCallbacks = galleryPosts?.let { posts ->
            buildThreadScreenGalleryCallbacks(
                currentPosts = posts,
                onDismiss = onDismissGallery,
                onScrollToPostIndex = onScrollToPostIndex
            )
        },
        settingsSheetCallbacks = buildThreadScreenSettingsSheetCallbacks(
            onDismiss = onDismissSettingsSheet,
            onApplyActionState = onApplySettingsActionState,
            onOpenNgManagement = onOpenNgManagement,
            onOpenExternalApp = onOpenExternalApp,
            onTogglePrivacy = onTogglePrivacy,
            onDelegateToMainActionHandler = onAction
        ),
        filterSheetCallbacks = buildThreadScreenFilterSheetCallbacks(
            threadFilterBinding = threadFilterBinding,
            onDismiss = onDismissFilterSheet
        ),
        readAloudControlCallbacks = buildThreadScreenReadAloudControlCallbacks(
            firstVisibleSegmentIndex = firstVisibleSegmentIndex,
            onSeekToIndex = onSeekToReadAloudIndex,
            onPlay = onPlayReadAloud,
            onPause = onPauseReadAloud,
            onStop = onStopReadAloud,
            onShowStoppedMessage = onShowReadAloudStoppedMessage,
            onDismiss = onDismissReadAloudControls
        ),
        ngManagementCallbacks = buildThreadScreenNgManagementCallbacks(
            onDismiss = onDismissNgManagement,
            mutationCallbacks = ngMutationCallbacks
        ),
        saveProgressDialogCallbacks = buildThreadScreenSaveProgressDialogCallbacks(
            onDismissRequest = onDismissSaveProgress,
            onCancelRequest = onCancelSaveProgress
        ),
        globalSettingsCallbacks = buildThreadScreenGlobalSettingsCallbacks(
            onBack = onDismissGlobalSettings,
            onOpenCookieManager = onOpenCookieManager,
            preferencesCallbacks = screenPreferencesCallbacks
        ),
        cookieManagementCallbacks = buildThreadScreenCookieManagementCallbacks(
            onBack = onDismissCookieManagement
        )
    )
}
