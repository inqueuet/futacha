package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.SaveDirectorySelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

internal data class ThreadScreenStateRuntimeBindingsBundle(
    val messageRuntime: ThreadMessageRuntimeBindings,
    val threadNgMutationCallbacks: ThreadNgMutationCallbacks,
    val threadFilterBinding: ThreadFilterUiStateBinding,
    val replyDraftBinding: ThreadReplyDraftBinding,
    val replyDialogBinding: ThreadReplyDialogStateBinding,
    val readAloudRuntimeBindings: ThreadReadAloudRuntimeBindings,
    val jobBindings: ThreadScreenJobBindings
)

internal fun buildThreadScreenStateRuntimeBindingsBundle(
    currentReadAloudState: () -> ThreadReadAloudRuntimeState,
    setReadAloudState: (ThreadReadAloudRuntimeState) -> Unit,
    onStopPlayback: () -> Unit,
    currentAutoSaveJob: () -> Job?,
    setAutoSaveJob: (Job?) -> Unit,
    currentManualSaveJob: () -> Job?,
    setManualSaveJob: (Job?) -> Unit,
    currentSingleMediaSaveJob: () -> Job?,
    setSingleMediaSaveJob: (Job?) -> Unit,
    currentRefreshThreadJob: () -> Job?,
    setRefreshThreadJob: (Job?) -> Unit,
    setIsManualSaveInProgress: (Boolean) -> Unit,
    setIsSingleMediaSaveInProgress: (Boolean) -> Unit,
    onDismissReadAloudOverlay: () -> Unit,
    coroutineScope: CoroutineScope,
    showSnackbar: suspend (String) -> Unit,
    onManualSaveDirectoryChanged: (String) -> Unit,
    onSaveDirectorySelectionChanged: (SaveDirectorySelection) -> Unit,
    onOpenSaveDirectoryPicker: (() -> Unit)?,
    stateStore: AppStateStore?,
    onFallbackHeadersChanged: (List<String>) -> Unit,
    onFallbackWordsChanged: (List<String>) -> Unit,
    currentHeaders: () -> List<String>,
    currentWords: () -> List<String>,
    isFilteringEnabled: () -> Boolean,
    setFilteringEnabled: (Boolean) -> Unit,
    currentFilterOptions: () -> Set<ThreadFilterOption>,
    currentSortOption: () -> ThreadFilterSortOption?,
    currentKeyword: () -> String,
    setFilterOptions: (Set<ThreadFilterOption>) -> Unit,
    setSortOption: (ThreadFilterSortOption?) -> Unit,
    setKeyword: (String) -> Unit,
    currentReplyName: () -> String,
    currentReplyEmail: () -> String,
    currentReplySubject: () -> String,
    currentReplyComment: () -> String,
    currentReplyPassword: () -> String,
    currentReplyImageData: () -> ImageData?,
    setReplyName: (String) -> Unit,
    setReplyEmail: (String) -> Unit,
    setReplySubject: (String) -> Unit,
    setReplyComment: (String) -> Unit,
    setReplyPassword: (String) -> Unit,
    setReplyImageData: (ImageData?) -> Unit,
    isReplyDialogVisible: () -> Boolean,
    setReplyDialogVisible: (Boolean) -> Unit
): ThreadScreenStateRuntimeBindingsBundle {
    val runtimeJobBindingsBundle = buildThreadScreenRuntimeJobBindingsBundle(
        currentReadAloudState = currentReadAloudState,
        setReadAloudState = setReadAloudState,
        onStopPlayback = onStopPlayback,
        currentAutoSaveJob = currentAutoSaveJob,
        setAutoSaveJob = setAutoSaveJob,
        currentManualSaveJob = currentManualSaveJob,
        setManualSaveJob = setManualSaveJob,
        currentSingleMediaSaveJob = currentSingleMediaSaveJob,
        setSingleMediaSaveJob = setSingleMediaSaveJob,
        currentRefreshThreadJob = currentRefreshThreadJob,
        setRefreshThreadJob = setRefreshThreadJob,
        setIsManualSaveInProgress = setIsManualSaveInProgress,
        setIsSingleMediaSaveInProgress = setIsSingleMediaSaveInProgress,
        onDismissReadAloudOverlay = onDismissReadAloudOverlay
    )
    val messageFormBindingsBundle = buildThreadScreenMessageFormBindingsBundle(
        coroutineScope = coroutineScope,
        showSnackbar = showSnackbar,
        onManualSaveDirectoryChanged = onManualSaveDirectoryChanged,
        onSaveDirectorySelectionChanged = onSaveDirectorySelectionChanged,
        onOpenSaveDirectoryPicker = onOpenSaveDirectoryPicker,
        stateStore = stateStore,
        onFallbackHeadersChanged = onFallbackHeadersChanged,
        onFallbackWordsChanged = onFallbackWordsChanged,
        currentHeaders = currentHeaders,
        currentWords = currentWords,
        isFilteringEnabled = isFilteringEnabled,
        setFilteringEnabled = setFilteringEnabled,
        currentFilterOptions = currentFilterOptions,
        currentSortOption = currentSortOption,
        currentKeyword = currentKeyword,
        setFilterOptions = setFilterOptions,
        setSortOption = setSortOption,
        setKeyword = setKeyword,
        currentReplyName = currentReplyName,
        currentReplyEmail = currentReplyEmail,
        currentReplySubject = currentReplySubject,
        currentReplyComment = currentReplyComment,
        currentReplyPassword = currentReplyPassword,
        currentReplyImageData = currentReplyImageData,
        setReplyName = setReplyName,
        setReplyEmail = setReplyEmail,
        setReplySubject = setReplySubject,
        setReplyComment = setReplyComment,
        setReplyPassword = setReplyPassword,
        setReplyImageData = setReplyImageData,
        isReplyDialogVisible = isReplyDialogVisible,
        setReplyDialogVisible = setReplyDialogVisible
    )
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
