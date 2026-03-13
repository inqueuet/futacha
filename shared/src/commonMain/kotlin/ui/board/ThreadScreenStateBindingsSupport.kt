package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.SaveDirectorySelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

internal data class ThreadScreenMessageNgBindings(
    val messageRuntime: ThreadMessageRuntimeBindings,
    val ngMutationCallbacks: ThreadNgMutationCallbacks
)

internal data class ThreadScreenMessageFormBindingsBundle(
    val messageNgBindings: ThreadScreenMessageNgBindings,
    val formBindings: ThreadScreenFormBindings
)

internal fun buildThreadScreenMessageNgBindings(
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
    setFilteringEnabled: (Boolean) -> Unit
): ThreadScreenMessageNgBindings {
    val messageRuntime = buildThreadMessageRuntimeBindings(
        coroutineScope = coroutineScope,
        showSnackbar = showSnackbar,
        onManualSaveDirectoryChanged = onManualSaveDirectoryChanged,
        onSaveDirectorySelectionChanged = onSaveDirectorySelectionChanged,
        onOpenSaveDirectoryPicker = onOpenSaveDirectoryPicker
    )
    val ngPersistenceBindings = buildThreadNgPersistenceBindings(
        coroutineScope = coroutineScope,
        stateStore = stateStore,
        onFallbackHeadersChanged = onFallbackHeadersChanged,
        onFallbackWordsChanged = onFallbackWordsChanged
    )
    return ThreadScreenMessageNgBindings(
        messageRuntime = messageRuntime,
        ngMutationCallbacks = buildThreadNgMutationCallbacks(
            currentHeaders = currentHeaders,
            currentWords = currentWords,
            isFilteringEnabled = isFilteringEnabled,
            setFilteringEnabled = setFilteringEnabled,
            persistHeaders = ngPersistenceBindings.persistHeaders,
            persistWords = ngPersistenceBindings.persistWords,
            showMessage = messageRuntime.showMessage
        )
    )
}

internal data class ThreadScreenFormBindings(
    val threadFilterBinding: ThreadFilterUiStateBinding,
    val replyDraftBinding: ThreadReplyDraftBinding,
    val replyDialogBinding: ThreadReplyDialogStateBinding
)

internal fun buildThreadScreenFormBindings(
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
): ThreadScreenFormBindings {
    val threadFilterBinding = buildThreadFilterUiStateBinding(
        currentOptions = currentFilterOptions,
        currentSortOption = currentSortOption,
        currentKeyword = currentKeyword,
        setOptions = setFilterOptions,
        setSortOption = setSortOption,
        setKeyword = setKeyword
    )
    val replyDraftBinding = buildThreadReplyDraftBinding(
        currentName = currentReplyName,
        currentEmail = currentReplyEmail,
        currentSubject = currentReplySubject,
        currentComment = currentReplyComment,
        currentPassword = currentReplyPassword,
        currentImageData = currentReplyImageData,
        setName = setReplyName,
        setEmail = setReplyEmail,
        setSubject = setReplySubject,
        setComment = setReplyComment,
        setPassword = setReplyPassword,
        setImageData = setReplyImageData
    )
    return ThreadScreenFormBindings(
        threadFilterBinding = threadFilterBinding,
        replyDraftBinding = replyDraftBinding,
        replyDialogBinding = buildThreadReplyDialogStateBinding(
            isVisible = isReplyDialogVisible,
            setVisible = setReplyDialogVisible,
            draftBinding = replyDraftBinding
        )
    )
}

internal fun buildThreadScreenMessageFormBindingsBundle(
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
): ThreadScreenMessageFormBindingsBundle {
    return ThreadScreenMessageFormBindingsBundle(
        messageNgBindings = buildThreadScreenMessageNgBindings(
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
            setFilteringEnabled = setFilteringEnabled
        ),
        formBindings = buildThreadScreenFormBindings(
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
    )
}

internal data class ThreadReadAloudRuntimeState(
    val job: Job?,
    val status: ReadAloudStatus,
    val currentIndex: Int,
    val cancelRequestedByUser: Boolean
)

internal data class ThreadReadAloudRuntimeBindings(
    val cancelActive: () -> Unit,
    val stop: () -> Unit,
    val pause: () -> String?
)

internal data class ThreadScreenRuntimeJobBindingsBundle(
    val readAloudRuntimeBindings: ThreadReadAloudRuntimeBindings,
    val jobBindings: ThreadScreenJobBindings
)

internal fun buildThreadReadAloudRuntimeBindings(
    currentState: () -> ThreadReadAloudRuntimeState,
    setState: (ThreadReadAloudRuntimeState) -> Unit,
    onStopPlayback: () -> Unit
): ThreadReadAloudRuntimeBindings {
    val cancelActive = {
        val state = currentState()
        setState(state.copy(cancelRequestedByUser = true))
        state.job?.cancel()
        onStopPlayback()
    }
    return ThreadReadAloudRuntimeBindings(
        cancelActive = cancelActive,
        stop = {
            cancelActive()
            setState(
                currentState().copy(
                    job = null,
                    status = ReadAloudStatus.Idle,
                    currentIndex = 0
                )
            )
        },
        pause = pause@{
            val pauseState = resolveReadAloudPauseState(currentState().status) ?: return@pause null
            cancelActive()
            setState(
                currentState().copy(
                    job = null,
                    status = pauseState.status
                )
            )
            pauseState.message
        }
    )
}

internal data class ThreadScreenJobBindings(
    val cancelAll: () -> Unit,
    val resetForThreadChange: () -> Unit
)

internal fun buildThreadScreenJobBindings(
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
    onStopReadAloud: () -> Unit,
    onDismissReadAloudOverlay: () -> Unit
): ThreadScreenJobBindings {
    val cancelAll: () -> Unit = {
        currentAutoSaveJob()?.cancel()
        currentManualSaveJob()?.cancel()
        currentSingleMediaSaveJob()?.cancel()
        currentRefreshThreadJob()?.cancel()
    }
    return ThreadScreenJobBindings(
        cancelAll = cancelAll,
        resetForThreadChange = {
            onStopReadAloud()
            onDismissReadAloudOverlay()
            cancelAll()
            setAutoSaveJob(null)
            setManualSaveJob(null)
            setSingleMediaSaveJob(null)
            setRefreshThreadJob(null)
            setIsManualSaveInProgress(false)
            setIsSingleMediaSaveInProgress(false)
        }
    )
}

internal fun buildThreadScreenRuntimeJobBindingsBundle(
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
    onDismissReadAloudOverlay: () -> Unit
): ThreadScreenRuntimeJobBindingsBundle {
    val readAloudRuntimeBindings = buildThreadReadAloudRuntimeBindings(
        currentState = currentReadAloudState,
        setState = setReadAloudState,
        onStopPlayback = onStopPlayback
    )
    return ThreadScreenRuntimeJobBindingsBundle(
        readAloudRuntimeBindings = readAloudRuntimeBindings,
        jobBindings = buildThreadScreenJobBindings(
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
            onStopReadAloud = readAloudRuntimeBindings.stop,
            onDismissReadAloudOverlay = onDismissReadAloudOverlay
        )
    )
}
