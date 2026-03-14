package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.ImageData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

internal data class ThreadScreenMessageNgBindings(
    val messageRuntime: ThreadMessageRuntimeBindings,
    val ngMutationCallbacks: ThreadNgMutationCallbacks
)

internal data class ThreadScreenMessageNgInputs(
    val coroutineScope: CoroutineScope,
    val showSnackbar: suspend (String) -> Unit,
    val screenPreferencesCallbacks: ScreenPreferencesCallbacks,
    val stateStore: AppStateStore?,
    val onFallbackHeadersChanged: (List<String>) -> Unit,
    val onFallbackWordsChanged: (List<String>) -> Unit,
    val currentHeaders: () -> List<String>,
    val currentWords: () -> List<String>,
    val isFilteringEnabled: () -> Boolean,
    val setFilteringEnabled: (Boolean) -> Unit
)

internal data class ThreadScreenMessageFormBindingsBundle(
    val messageNgBindings: ThreadScreenMessageNgBindings,
    val formBindings: ThreadScreenFormBindings
)

internal fun buildThreadScreenMessageNgBindings(
    inputs: ThreadScreenMessageNgInputs
): ThreadScreenMessageNgBindings {
    val messageRuntime = buildThreadMessageRuntimeBindings(
        coroutineScope = inputs.coroutineScope,
        showSnackbar = inputs.showSnackbar,
        onManualSaveDirectoryChanged = inputs.screenPreferencesCallbacks.onManualSaveDirectoryChanged,
        onSaveDirectorySelectionChanged = inputs.screenPreferencesCallbacks.onSaveDirectorySelectionChanged,
        onOpenSaveDirectoryPicker = inputs.screenPreferencesCallbacks.onOpenSaveDirectoryPicker
    )
    val ngPersistenceBindings = buildThreadNgPersistenceBindings(
        coroutineScope = inputs.coroutineScope,
        stateStore = inputs.stateStore,
        onFallbackHeadersChanged = inputs.onFallbackHeadersChanged,
        onFallbackWordsChanged = inputs.onFallbackWordsChanged
    )
    return ThreadScreenMessageNgBindings(
        messageRuntime = messageRuntime,
        ngMutationCallbacks = buildThreadNgMutationCallbacks(
            currentHeaders = inputs.currentHeaders,
            currentWords = inputs.currentWords,
            isFilteringEnabled = inputs.isFilteringEnabled,
            setFilteringEnabled = inputs.setFilteringEnabled,
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

internal data class ThreadScreenFilterBindingInputs(
    val currentOptions: () -> Set<ThreadFilterOption>,
    val currentSortOption: () -> ThreadFilterSortOption?,
    val currentKeyword: () -> String,
    val setOptions: (Set<ThreadFilterOption>) -> Unit,
    val setSortOption: (ThreadFilterSortOption?) -> Unit,
    val setKeyword: (String) -> Unit
)

internal data class ThreadScreenReplyDraftInputs(
    val currentName: () -> String,
    val currentEmail: () -> String,
    val currentSubject: () -> String,
    val currentComment: () -> String,
    val currentPassword: () -> String,
    val currentImageData: () -> ImageData?,
    val setName: (String) -> Unit,
    val setEmail: (String) -> Unit,
    val setSubject: (String) -> Unit,
    val setComment: (String) -> Unit,
    val setPassword: (String) -> Unit,
    val setImageData: (ImageData?) -> Unit
)

internal data class ThreadScreenReplyDialogInputs(
    val isVisible: () -> Boolean,
    val setVisible: (Boolean) -> Unit
)

internal data class ThreadScreenFormInputs(
    val filterInputs: ThreadScreenFilterBindingInputs,
    val replyDraftInputs: ThreadScreenReplyDraftInputs,
    val replyDialogInputs: ThreadScreenReplyDialogInputs
)

internal fun buildThreadScreenFormBindings(
    inputs: ThreadScreenFormInputs
): ThreadScreenFormBindings {
    val threadFilterBinding = buildThreadFilterUiStateBinding(
        currentOptions = inputs.filterInputs.currentOptions,
        currentSortOption = inputs.filterInputs.currentSortOption,
        currentKeyword = inputs.filterInputs.currentKeyword,
        setOptions = inputs.filterInputs.setOptions,
        setSortOption = inputs.filterInputs.setSortOption,
        setKeyword = inputs.filterInputs.setKeyword
    )
    val replyDraftBinding = buildThreadReplyDraftBinding(
        currentName = inputs.replyDraftInputs.currentName,
        currentEmail = inputs.replyDraftInputs.currentEmail,
        currentSubject = inputs.replyDraftInputs.currentSubject,
        currentComment = inputs.replyDraftInputs.currentComment,
        currentPassword = inputs.replyDraftInputs.currentPassword,
        currentImageData = inputs.replyDraftInputs.currentImageData,
        setName = inputs.replyDraftInputs.setName,
        setEmail = inputs.replyDraftInputs.setEmail,
        setSubject = inputs.replyDraftInputs.setSubject,
        setComment = inputs.replyDraftInputs.setComment,
        setPassword = inputs.replyDraftInputs.setPassword,
        setImageData = inputs.replyDraftInputs.setImageData
    )
    return ThreadScreenFormBindings(
        threadFilterBinding = threadFilterBinding,
        replyDraftBinding = replyDraftBinding,
        replyDialogBinding = buildThreadReplyDialogStateBinding(
            isVisible = inputs.replyDialogInputs.isVisible,
            setVisible = inputs.replyDialogInputs.setVisible,
            draftBinding = replyDraftBinding
        )
    )
}

internal data class ThreadScreenMessageFormInputs(
    val messageNgInputs: ThreadScreenMessageNgInputs,
    val formInputs: ThreadScreenFormInputs
)

internal fun buildThreadScreenMessageFormBindingsBundle(
    inputs: ThreadScreenMessageFormInputs
): ThreadScreenMessageFormBindingsBundle {
    return ThreadScreenMessageFormBindingsBundle(
        messageNgBindings = buildThreadScreenMessageNgBindings(inputs.messageNgInputs),
        formBindings = buildThreadScreenFormBindings(inputs.formInputs)
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

internal data class ThreadScreenRuntimeJobInputs(
    val readAloudStateBindings: ThreadScreenReadAloudStateBindings,
    val onStopPlayback: () -> Unit,
    val currentAutoSaveJob: () -> Job?,
    val setAutoSaveJob: (Job?) -> Unit,
    val currentManualSaveJob: () -> Job?,
    val setManualSaveJob: (Job?) -> Unit,
    val currentSingleMediaSaveJob: () -> Job?,
    val setSingleMediaSaveJob: (Job?) -> Unit,
    val currentRefreshThreadJob: () -> Job?,
    val setRefreshThreadJob: (Job?) -> Unit,
    val setIsManualSaveInProgress: (Boolean) -> Unit,
    val setIsSingleMediaSaveInProgress: (Boolean) -> Unit,
    val onDismissReadAloudOverlay: () -> Unit
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
    inputs: ThreadScreenRuntimeJobInputs
): ThreadScreenRuntimeJobBindingsBundle {
    val readAloudRuntimeBindings = buildThreadReadAloudRuntimeBindings(
        currentState = inputs.readAloudStateBindings.currentState,
        setState = inputs.readAloudStateBindings.setState,
        onStopPlayback = inputs.onStopPlayback
    )
    return ThreadScreenRuntimeJobBindingsBundle(
        readAloudRuntimeBindings = readAloudRuntimeBindings,
        jobBindings = buildThreadScreenJobBindings(
            currentAutoSaveJob = inputs.currentAutoSaveJob,
            setAutoSaveJob = inputs.setAutoSaveJob,
            currentManualSaveJob = inputs.currentManualSaveJob,
            setManualSaveJob = inputs.setManualSaveJob,
            currentSingleMediaSaveJob = inputs.currentSingleMediaSaveJob,
            setSingleMediaSaveJob = inputs.setSingleMediaSaveJob,
            currentRefreshThreadJob = inputs.currentRefreshThreadJob,
            setRefreshThreadJob = inputs.setRefreshThreadJob,
            setIsManualSaveInProgress = inputs.setIsManualSaveInProgress,
            setIsSingleMediaSaveInProgress = inputs.setIsSingleMediaSaveInProgress,
            onStopReadAloud = readAloudRuntimeBindings.stop,
            onDismissReadAloudOverlay = inputs.onDismissReadAloudOverlay
        )
    )
}
