package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.DrawerState
import androidx.compose.material3.SnackbarHostState
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.SaveProgress
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class ThreadScreenInteractionUiAggregateBundle(
    val mediaBindings: ThreadScreenMediaBindings,
    val controllerBindings: ThreadScreenControllerBindingsBundle,
    val uiBindings: ThreadScreenUiBindingsBundle
)

internal data class ThreadScreenInteractionUiAggregateRuntimeInputs(
    val mediaInputs: ThreadScreenAggregateMediaInputs,
    val controllerActionInputs: ThreadScreenControllerActionInputs,
    val controllerInteractionRuntimeInputs: ThreadScreenControllerInteractionRuntimeInputs,
    val uiRuntimeInputs: ThreadScreenUiRuntimeInputs
)

internal data class ThreadScreenAggregateMediaInputs(
    val currentPreviewState: () -> ThreadMediaPreviewState,
    val setPreviewState: (ThreadMediaPreviewState) -> Unit,
    val currentMediaEntries: () -> List<MediaPreviewEntry>
)

internal data class ThreadScreenAggregateUiInputs(
    val onSearchQueryChange: (String) -> Unit,
    val onSearchClose: () -> Unit,
    val onBack: () -> Unit,
    val onOpenHistory: () -> Unit,
    val onSearch: () -> Unit,
    val onOpenGlobalSettings: () -> Unit,
    val replyDialogBinding: ThreadReplyDialogStateBinding,
    val mediaPreviewEntryCount: Int,
    val onSavePreviewMedia: (MediaPreviewEntry) -> Unit,
    val galleryPosts: List<Post>?,
    val onDismissGallery: () -> Unit,
    val onScrollToPostIndex: (Int) -> Unit,
    val threadFilterBinding: ThreadFilterUiStateBinding,
    val onDismissSettingsSheet: () -> Unit,
    val onDismissFilterSheet: () -> Unit,
    val onApplySettingsActionState: (ThreadSettingsActionState) -> Unit,
    val firstVisibleSegmentIndex: () -> Int,
    val onPauseReadAloud: () -> Unit,
    val onStopReadAloud: () -> Unit,
    val onShowReadAloudStoppedMessage: () -> Unit,
    val onDismissReadAloudControls: () -> Unit,
    val onDismissNgManagement: () -> Unit,
    val ngMutationCallbacks: ThreadNgMutationCallbacks,
    val onDismissSaveProgress: () -> Unit,
    val onCancelSaveProgress: () -> Unit,
    val onDismissGlobalSettings: () -> Unit,
    val screenPreferencesCallbacks: ScreenPreferencesCallbacks,
    val onOpenCookieManager: (() -> Unit)?,
    val onDismissCookieManagement: () -> Unit
)

internal data class ThreadScreenControllerInteractionRuntimeInputs(
    val coroutineScope: CoroutineScope,
    val lazyListState: LazyListState,
    val drawerState: DrawerState,
    val replyDialogBinding: ThreadReplyDialogStateBinding,
    val currentIsRefreshing: () -> Boolean,
    val currentUiState: () -> ThreadUiState,
    val currentModalOverlayState: () -> ThreadModalOverlayState,
    val setModalOverlayState: (ThreadModalOverlayState) -> Unit,
    val currentSheetOverlayState: () -> ThreadSheetOverlayState,
    val setSheetOverlayState: (ThreadSheetOverlayState) -> Unit,
    val currentPostOverlayState: () -> ThreadPostOverlayState,
    val setPostOverlayState: (ThreadPostOverlayState) -> Unit,
    val currentSearchIndex: () -> Int,
    val setCurrentSearchIndex: (Int) -> Unit,
    val currentSearchMatches: () -> List<ThreadSearchMatch>,
    val onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    val onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit,
    val onHistoryCleared: () -> Unit,
    val onBack: () -> Unit,
    val lastUsedDeleteKey: String,
    val currentSaidaneLabel: (Post) -> String?,
    val isSelfPost: (Post) -> Boolean,
    val onSaidaneLabelUpdated: (Post, String) -> Unit,
    val repository: BoardRepository,
    val effectiveBoardUrl: String,
    val threadId: String,
    val onStartRefresh: (Int, Int) -> Unit,
    val onHandleThreadSaveRequest: () -> Unit,
    val onShowMessage: (String) -> Unit,
    val showSnackbar: suspend (String) -> Unit,
    val onOpenExternalApp: () -> Unit,
    val onTogglePrivacy: () -> Unit
)

internal fun buildThreadScreenControllerInteractionInputs(
    inputs: ThreadScreenControllerInteractionRuntimeInputs
): ThreadScreenControllerInteractionInputs {
    return ThreadScreenControllerInteractionInputs(
        isRefreshing = inputs.currentIsRefreshing,
        onOpenReplyDialog = {
            inputs.replyDialogBinding.setState(
                openThreadReplyDialog(
                    state = inputs.replyDialogBinding.currentState(),
                    lastUsedDeleteKey = inputs.lastUsedDeleteKey
                )
            )
        },
        onScrollTop = {
            inputs.coroutineScope.launch { inputs.lazyListState.animateScrollToItem(0) }
        },
        onScrollBottom = {
            inputs.coroutineScope.launch {
                val currentState = inputs.currentUiState()
                if (currentState is ThreadUiState.Success) {
                    val lastIndex = currentState.page.posts.size - 1
                    if (lastIndex >= 0) {
                        inputs.lazyListState.animateScrollToItem(lastIndex)
                    }
                }
            }
        },
        onShowRefreshBusyMessage = {
            inputs.onShowMessage(buildThreadRefreshBusyMessage())
        },
        onStartRefreshFromMenu = {
            inputs.onStartRefresh(
                inputs.lazyListState.firstVisibleItemIndex,
                inputs.lazyListState.firstVisibleItemScrollOffset
            )
        },
        onOpenGallery = {
            inputs.setModalOverlayState(
                openThreadGalleryOverlay(inputs.currentModalOverlayState())
            )
        },
        onDelegateToSaveHandler = inputs.onHandleThreadSaveRequest,
        onShowFilterSheet = {
            inputs.setSheetOverlayState(
                openThreadFilterOverlay(inputs.currentSheetOverlayState())
            )
        },
        onShowSettingsSheet = {
            inputs.setSheetOverlayState(
                openThreadSettingsOverlay(inputs.currentSheetOverlayState())
            )
        },
        onClearNgHeaderPrefill = {
            inputs.setPostOverlayState(
                inputs.currentPostOverlayState().copy(ngHeaderPrefill = null)
            )
        },
        onShowNgManagement = {
            inputs.setPostOverlayState(
                openThreadNgManagementOverlay(inputs.currentPostOverlayState())
            )
        },
        onOpenExternalApp = inputs.onOpenExternalApp,
        onShowReadAloudControls = {
            inputs.setSheetOverlayState(
                openThreadReadAloudOverlay(inputs.currentSheetOverlayState())
            )
        },
        onTogglePrivacy = inputs.onTogglePrivacy,
        currentSearchIndex = inputs.currentSearchIndex,
        setCurrentSearchIndex = inputs.setCurrentSearchIndex,
        currentSearchMatches = inputs.currentSearchMatches,
        onScrollToSearchMatchPostIndex = { targetPostIndex ->
            if (targetPostIndex != null) {
                inputs.coroutineScope.launch {
                    inputs.lazyListState.animateScrollToItem(targetPostIndex)
                }
            }
        },
        onCloseDrawerAfterHistorySelection = {
            inputs.coroutineScope.launch { inputs.drawerState.close() }
        },
        onHistoryEntrySelected = inputs.onHistoryEntrySelected,
        currentOverlayState = inputs.currentPostOverlayState,
        setOverlayState = inputs.setPostOverlayState,
        lastUsedDeleteKey = inputs.lastUsedDeleteKey,
        currentSaidaneLabel = inputs.currentSaidaneLabel,
        isSelfPost = inputs.isSelfPost,
        onSaidaneLabelUpdated = inputs.onSaidaneLabelUpdated,
        repository = inputs.repository,
        effectiveBoardUrl = inputs.effectiveBoardUrl,
        threadId = inputs.threadId,
        currentFirstVisibleItemIndex = { inputs.lazyListState.firstVisibleItemIndex },
        currentFirstVisibleItemOffset = { inputs.lazyListState.firstVisibleItemScrollOffset },
        onStartRefreshFromPull = inputs.onStartRefresh,
        onHistoryEntryDismissed = inputs.onHistoryEntryDismissed,
        onBoardClick = {
            inputs.coroutineScope.launch {
                inputs.drawerState.close()
                inputs.onBack()
            }
        },
        onHistoryBatchDeleteClick = {
            inputs.coroutineScope.launch {
                inputs.onHistoryCleared()
                showThreadMessage(
                    buildThreadHistoryBatchDeleteMessage(),
                    inputs.showSnackbar
                )
                inputs.drawerState.close()
            }
        },
        onHistorySettingsClick = {
            inputs.setModalOverlayState(
                openThreadGlobalSettingsOverlay(inputs.currentModalOverlayState())
            )
        }
    )
}

internal data class ThreadScreenUiRuntimeInputs(
    val coroutineScope: CoroutineScope,
    val lazyListState: LazyListState,
    val drawerState: DrawerState,
    val currentModalOverlayState: () -> ThreadModalOverlayState,
    val setModalOverlayState: (ThreadModalOverlayState) -> Unit,
    val currentSheetOverlayState: () -> ThreadSheetOverlayState,
    val setSheetOverlayState: (ThreadSheetOverlayState) -> Unit,
    val currentPostOverlayState: () -> ThreadPostOverlayState,
    val setPostOverlayState: (ThreadPostOverlayState) -> Unit,
    val setSearchQuery: (String) -> Unit,
    val setSearchActive: (Boolean) -> Unit,
    val replyDialogBinding: ThreadReplyDialogStateBinding,
    val mediaPreviewEntryCount: Int,
    val onSavePreviewMedia: (MediaPreviewEntry) -> Unit,
    val galleryPosts: List<Post>?,
    val threadFilterBinding: ThreadFilterUiStateBinding,
    val firstVisibleSegmentIndex: () -> Int,
    val onPauseReadAloud: () -> Unit,
    val onStopReadAloud: () -> Unit,
    val onShowMessage: (String) -> Unit,
    val ngMutationCallbacks: ThreadNgMutationCallbacks,
    val currentManualSaveJob: () -> Job?,
    val setSaveProgress: (SaveProgress?) -> Unit,
    val screenPreferencesCallbacks: ScreenPreferencesCallbacks,
    val onOpenCookieManager: (() -> Unit)?,
    val onDismissCookieManagement: () -> Unit,
    val onBack: () -> Unit
)

internal fun buildThreadScreenAggregateUiInputs(
    inputs: ThreadScreenUiRuntimeInputs
): ThreadScreenAggregateUiInputs {
    return ThreadScreenAggregateUiInputs(
        onSearchQueryChange = inputs.setSearchQuery,
        onSearchClose = { inputs.setSearchActive(false) },
        onBack = inputs.onBack,
        onOpenHistory = {
            inputs.coroutineScope.launch { inputs.drawerState.open() }
        },
        onSearch = { inputs.setSearchActive(true) },
        onOpenGlobalSettings = {
            inputs.setModalOverlayState(
                openThreadGlobalSettingsOverlay(inputs.currentModalOverlayState())
            )
        },
        replyDialogBinding = inputs.replyDialogBinding,
        mediaPreviewEntryCount = inputs.mediaPreviewEntryCount,
        onSavePreviewMedia = inputs.onSavePreviewMedia,
        galleryPosts = inputs.galleryPosts,
        onDismissGallery = {
            inputs.setModalOverlayState(
                dismissThreadGalleryOverlay(inputs.currentModalOverlayState())
            )
        },
        onScrollToPostIndex = { index ->
            inputs.coroutineScope.launch {
                inputs.lazyListState.animateScrollToItem(index)
            }
        },
        threadFilterBinding = inputs.threadFilterBinding,
        onDismissSettingsSheet = {
            inputs.setSheetOverlayState(
                dismissThreadSettingsOverlay(inputs.currentSheetOverlayState())
            )
        },
        onDismissFilterSheet = {
            inputs.setSheetOverlayState(
                dismissThreadFilterOverlay(inputs.currentSheetOverlayState())
            )
        },
        onApplySettingsActionState = { actionState ->
            inputs.setSheetOverlayState(
                applyThreadSettingsActionOverlayState(
                    currentState = inputs.currentSheetOverlayState(),
                    actionState = actionState
                )
            )
        },
        firstVisibleSegmentIndex = inputs.firstVisibleSegmentIndex,
        onPauseReadAloud = inputs.onPauseReadAloud,
        onStopReadAloud = inputs.onStopReadAloud,
        onShowReadAloudStoppedMessage = {
            inputs.onShowMessage(buildReadAloudStoppedMessage())
        },
        onDismissReadAloudControls = {
            inputs.setSheetOverlayState(
                dismissThreadReadAloudOverlay(inputs.currentSheetOverlayState())
            )
        },
        onDismissNgManagement = {
            inputs.setPostOverlayState(
                dismissThreadNgManagementOverlay(inputs.currentPostOverlayState())
            )
        },
        ngMutationCallbacks = inputs.ngMutationCallbacks,
        onDismissSaveProgress = {
            inputs.setSaveProgress(null)
        },
        onCancelSaveProgress = {
            inputs.currentManualSaveJob()?.cancel()
            inputs.onShowMessage("保存をキャンセルしました")
        },
        onDismissGlobalSettings = {
            inputs.setModalOverlayState(
                dismissThreadGlobalSettingsOverlay(inputs.currentModalOverlayState())
            )
        },
        screenPreferencesCallbacks = inputs.screenPreferencesCallbacks,
        onOpenCookieManager = inputs.onOpenCookieManager,
        onDismissCookieManagement = inputs.onDismissCookieManagement
    )
}

internal fun buildThreadScreenInteractionUiAggregateBundle(
    mediaInputs: ThreadScreenAggregateMediaInputs,
    controllerActionInputs: ThreadScreenControllerActionInputs,
    controllerInteractionInputs: ThreadScreenControllerInteractionInputs,
    uiInputs: ThreadScreenAggregateUiInputs
): ThreadScreenInteractionUiAggregateBundle {
    val mediaBindings = buildThreadScreenMediaBindings(
        currentPreviewState = mediaInputs.currentPreviewState,
        setPreviewState = mediaInputs.setPreviewState,
        currentEntries = mediaInputs.currentMediaEntries
    )
    val controllerBindings = buildThreadScreenControllerBindingsBundle(
        actionInputs = controllerActionInputs,
        interactionInputs = controllerInteractionInputs
    )
    val interactionBindings = controllerBindings.interactionBindings
    val readAloudBindings = controllerBindings.actionExecutionBindings.readAloudBindings
    return ThreadScreenInteractionUiAggregateBundle(
        mediaBindings = mediaBindings,
        controllerBindings = controllerBindings,
        uiBindings = buildThreadScreenUiBindingsBundle(
            topBarInputs = ThreadScreenTopBarUiInputs(
                searchNavigationCallbacks = interactionBindings.searchNavigationCallbacks,
                onSearchQueryChange = uiInputs.onSearchQueryChange,
                onSearchClose = uiInputs.onSearchClose,
                onBack = uiInputs.onBack,
                onOpenHistory = uiInputs.onOpenHistory,
                onSearch = uiInputs.onSearch,
                onOpenGlobalSettings = uiInputs.onOpenGlobalSettings,
                onAction = interactionBindings.menuEntryHandler
            ),
            overlayInputs = ThreadScreenOverlayUiInputs(
                replyDialogBinding = uiInputs.replyDialogBinding,
                currentPostOverlayState = controllerInteractionInputs.currentOverlayState,
                setPostOverlayState = controllerInteractionInputs.setOverlayState,
                mediaPreviewState = mediaInputs.currentPreviewState,
                setMediaPreviewState = mediaInputs.setPreviewState,
                mediaPreviewEntryCount = uiInputs.mediaPreviewEntryCount,
                onSavePreviewMedia = uiInputs.onSavePreviewMedia,
                galleryPosts = uiInputs.galleryPosts,
                onDismissGallery = uiInputs.onDismissGallery,
                onScrollToPostIndex = uiInputs.onScrollToPostIndex,
                threadFilterBinding = uiInputs.threadFilterBinding,
                onDismissSettingsSheet = uiInputs.onDismissSettingsSheet,
                onDismissFilterSheet = uiInputs.onDismissFilterSheet,
                onApplySettingsActionState = uiInputs.onApplySettingsActionState,
                onOpenNgManagement = controllerInteractionInputs.onShowNgManagement,
                onOpenExternalApp = controllerInteractionInputs.onOpenExternalApp,
                onTogglePrivacy = controllerInteractionInputs.onTogglePrivacy,
                firstVisibleSegmentIndex = uiInputs.firstVisibleSegmentIndex,
                onSeekToReadAloudIndex = readAloudBindings.seekReadAloudToIndex,
                onPlayReadAloud = readAloudBindings.startReadAloud,
                onPauseReadAloud = uiInputs.onPauseReadAloud,
                onStopReadAloud = uiInputs.onStopReadAloud,
                onShowReadAloudStoppedMessage = uiInputs.onShowReadAloudStoppedMessage,
                onDismissReadAloudControls = uiInputs.onDismissReadAloudControls,
                onDismissNgManagement = uiInputs.onDismissNgManagement,
                ngMutationCallbacks = uiInputs.ngMutationCallbacks,
                onDismissSaveProgress = uiInputs.onDismissSaveProgress,
                onCancelSaveProgress = uiInputs.onCancelSaveProgress
            ),
            settingsInputs = ThreadScreenSettingsUiInputs(
                onDismissGlobalSettings = uiInputs.onDismissGlobalSettings,
                screenPreferencesCallbacks = uiInputs.screenPreferencesCallbacks,
                onOpenCookieManager = uiInputs.onOpenCookieManager,
                onDismissCookieManagement = uiInputs.onDismissCookieManagement
            )
        )
    )
}

internal fun buildThreadScreenInteractionUiAggregateBundle(
    inputs: ThreadScreenInteractionUiAggregateRuntimeInputs
): ThreadScreenInteractionUiAggregateBundle {
    return buildThreadScreenInteractionUiAggregateBundle(
        mediaInputs = inputs.mediaInputs,
        controllerActionInputs = inputs.controllerActionInputs,
        controllerInteractionInputs = buildThreadScreenControllerInteractionInputs(
            inputs.controllerInteractionRuntimeInputs
        ),
        uiInputs = buildThreadScreenAggregateUiInputs(inputs.uiRuntimeInputs)
    )
}

internal data class ThreadScreenOverlayStateBindings(
    val currentModalOverlayState: () -> ThreadModalOverlayState,
    val setModalOverlayState: (ThreadModalOverlayState) -> Unit,
    val currentSheetOverlayState: () -> ThreadSheetOverlayState,
    val setSheetOverlayState: (ThreadSheetOverlayState) -> Unit,
    val currentPostOverlayState: () -> ThreadPostOverlayState,
    val setPostOverlayState: (ThreadPostOverlayState) -> Unit
)

internal data class ThreadScreenInteractionUiWiringInputs(
    val coroutineScope: CoroutineScope,
    val lazyListState: LazyListState,
    val drawerState: DrawerState,
    val snackbarHostState: SnackbarHostState,
    val overlayStateBindings: ThreadScreenOverlayStateBindings,
    val mediaPreviewState: () -> ThreadMediaPreviewState,
    val setMediaPreviewState: (ThreadMediaPreviewState) -> Unit,
    val mediaPreviewEntries: () -> List<MediaPreviewEntry>,
    val actionStateBindings: ThreadScreenActionStateBindings,
    val actionDependencies: ThreadScreenActionDependencies,
    val historyRefreshStateBindings: ThreadScreenHistoryRefreshStateBindings,
    val onHistoryRefresh: suspend () -> Unit,
    val readAloudStateBindings: ThreadScreenReadAloudStateBindings,
    val readAloudCallbacks: ThreadScreenReadAloudCallbacks,
    val readAloudDependencies: ThreadScreenReadAloudDependencies,
    val replyDialogBinding: ThreadReplyDialogStateBinding,
    val currentIsRefreshing: () -> Boolean,
    val currentUiState: () -> ThreadUiState,
    val currentSearchIndex: () -> Int,
    val setCurrentSearchIndex: (Int) -> Unit,
    val currentSearchMatches: () -> List<ThreadSearchMatch>,
    val onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    val onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit,
    val onHistoryCleared: () -> Unit,
    val onBack: () -> Unit,
    val lastUsedDeleteKey: String,
    val currentSaidaneLabel: (Post) -> String?,
    val isSelfPost: (Post) -> Boolean,
    val onSaidaneLabelUpdated: (Post, String) -> Unit,
    val repository: BoardRepository,
    val effectiveBoardUrl: String,
    val threadId: String,
    val onStartRefresh: (Int, Int) -> Unit,
    val onHandleThreadSaveRequest: () -> Unit,
    val onShowMessage: (String) -> Unit,
    val onOpenExternalApp: () -> Unit,
    val onTogglePrivacy: () -> Unit,
    val setSearchQuery: (String) -> Unit,
    val setSearchActive: (Boolean) -> Unit,
    val singleMediaSaveBindings: ThreadScreenSingleMediaSaveBindings,
    val currentSuccessState: ThreadUiState.Success?,
    val threadFilterBinding: ThreadFilterUiStateBinding,
    val firstVisibleSegmentIndex: () -> Int,
    val pauseReadAloud: () -> Unit,
    val stopReadAloud: () -> Unit,
    val threadNgMutationCallbacks: ThreadNgMutationCallbacks,
    val currentManualSaveJob: () -> Job?,
    val setSaveProgress: (SaveProgress?) -> Unit,
    val preferencesCallbacks: ScreenPreferencesCallbacks,
    val cookieRepository: CookieRepository?
)

internal fun buildThreadScreenInteractionUiWiring(
    inputs: ThreadScreenInteractionUiWiringInputs
): ThreadScreenInteractionUiAggregateBundle {
    val overlayBindings = inputs.overlayStateBindings
    return buildThreadScreenInteractionUiAggregateBundle(
        ThreadScreenInteractionUiAggregateRuntimeInputs(
            mediaInputs = ThreadScreenAggregateMediaInputs(
                currentPreviewState = inputs.mediaPreviewState,
                setPreviewState = inputs.setMediaPreviewState,
                currentMediaEntries = inputs.mediaPreviewEntries
            ),
            controllerActionInputs = ThreadScreenControllerActionInputs(
                coroutineScope = inputs.coroutineScope,
                actionStateBindings = inputs.actionStateBindings,
                actionDependencies = inputs.actionDependencies,
                historyRefreshStateBindings = inputs.historyRefreshStateBindings,
                onHistoryRefresh = inputs.onHistoryRefresh,
                showHistoryRefreshMessage = inputs.snackbarHostState::showSnackbar,
                readAloudStateBindings = inputs.readAloudStateBindings,
                readAloudCallbacks = inputs.readAloudCallbacks,
                readAloudDependencies = inputs.readAloudDependencies
            ),
            controllerInteractionRuntimeInputs = ThreadScreenControllerInteractionRuntimeInputs(
                coroutineScope = inputs.coroutineScope,
                lazyListState = inputs.lazyListState,
                drawerState = inputs.drawerState,
                replyDialogBinding = inputs.replyDialogBinding,
                currentIsRefreshing = inputs.currentIsRefreshing,
                currentUiState = inputs.currentUiState,
                currentModalOverlayState = overlayBindings.currentModalOverlayState,
                setModalOverlayState = overlayBindings.setModalOverlayState,
                currentSheetOverlayState = overlayBindings.currentSheetOverlayState,
                setSheetOverlayState = overlayBindings.setSheetOverlayState,
                currentPostOverlayState = overlayBindings.currentPostOverlayState,
                setPostOverlayState = overlayBindings.setPostOverlayState,
                currentSearchIndex = inputs.currentSearchIndex,
                setCurrentSearchIndex = inputs.setCurrentSearchIndex,
                currentSearchMatches = inputs.currentSearchMatches,
                onHistoryEntrySelected = inputs.onHistoryEntrySelected,
                onHistoryEntryDismissed = inputs.onHistoryEntryDismissed,
                onHistoryCleared = inputs.onHistoryCleared,
                onBack = inputs.onBack,
                lastUsedDeleteKey = inputs.lastUsedDeleteKey,
                currentSaidaneLabel = inputs.currentSaidaneLabel,
                isSelfPost = inputs.isSelfPost,
                onSaidaneLabelUpdated = inputs.onSaidaneLabelUpdated,
                repository = inputs.repository,
                effectiveBoardUrl = inputs.effectiveBoardUrl,
                threadId = inputs.threadId,
                onStartRefresh = inputs.onStartRefresh,
                onHandleThreadSaveRequest = inputs.onHandleThreadSaveRequest,
                onShowMessage = inputs.onShowMessage,
                showSnackbar = inputs.snackbarHostState::showSnackbar,
                onOpenExternalApp = inputs.onOpenExternalApp,
                onTogglePrivacy = inputs.onTogglePrivacy
            ),
            uiRuntimeInputs = ThreadScreenUiRuntimeInputs(
                coroutineScope = inputs.coroutineScope,
                lazyListState = inputs.lazyListState,
                drawerState = inputs.drawerState,
                currentModalOverlayState = overlayBindings.currentModalOverlayState,
                setModalOverlayState = overlayBindings.setModalOverlayState,
                currentSheetOverlayState = overlayBindings.currentSheetOverlayState,
                setSheetOverlayState = overlayBindings.setSheetOverlayState,
                currentPostOverlayState = overlayBindings.currentPostOverlayState,
                setPostOverlayState = overlayBindings.setPostOverlayState,
                setSearchQuery = inputs.setSearchQuery,
                setSearchActive = inputs.setSearchActive,
                replyDialogBinding = inputs.replyDialogBinding,
                mediaPreviewEntryCount = inputs.mediaPreviewEntries().size,
                onSavePreviewMedia = inputs.singleMediaSaveBindings.savePreviewMedia,
                galleryPosts = inputs.currentSuccessState?.page?.posts,
                threadFilterBinding = inputs.threadFilterBinding,
                firstVisibleSegmentIndex = inputs.firstVisibleSegmentIndex,
                onPauseReadAloud = inputs.pauseReadAloud,
                onStopReadAloud = inputs.stopReadAloud,
                onShowMessage = inputs.onShowMessage,
                ngMutationCallbacks = inputs.threadNgMutationCallbacks,
                currentManualSaveJob = inputs.currentManualSaveJob,
                setSaveProgress = inputs.setSaveProgress,
                screenPreferencesCallbacks = inputs.preferencesCallbacks,
                onOpenCookieManager = inputs.cookieRepository?.let {
                    {
                        overlayBindings.setModalOverlayState(
                            openThreadCookieManagementOverlay(overlayBindings.currentModalOverlayState())
                        )
                    }
                },
                onDismissCookieManagement = {
                    overlayBindings.setModalOverlayState(
                        dismissThreadCookieManagementOverlay(overlayBindings.currentModalOverlayState())
                    )
                },
                onBack = inputs.onBack
            )
        )
    )
}
