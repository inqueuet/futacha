package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.util.SaveDirectorySelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class ThreadMessageRuntimeBindings(
    val showMessage: (String) -> Unit,
    val showOptionalMessage: (String?) -> Unit,
    val applySaveErrorState: (ThreadManualSaveErrorState) -> Unit
)

internal fun buildThreadMessageRuntimeBindings(
    coroutineScope: CoroutineScope,
    showSnackbar: suspend (String) -> Unit,
    onManualSaveDirectoryChanged: (String) -> Unit,
    onSaveDirectorySelectionChanged: (SaveDirectorySelection) -> Unit,
    onOpenSaveDirectoryPicker: (() -> Unit)?
): ThreadMessageRuntimeBindings {
    return ThreadMessageRuntimeBindings(
        showMessage = { message ->
            coroutineScope.launch {
                showThreadMessage(
                    message = message,
                    showMessage = showSnackbar
                )
            }
        },
        showOptionalMessage = { message ->
            coroutineScope.launch {
                showOptionalThreadMessage(
                    message = message,
                    showMessage = showSnackbar
                )
            }
        },
        applySaveErrorState = { errorState ->
            coroutineScope.launch {
                applyThreadSaveErrorState(
                    errorState = errorState,
                    onManualSaveDirectoryChanged = onManualSaveDirectoryChanged,
                    onSaveDirectorySelectionChanged = onSaveDirectorySelectionChanged,
                    onShowMessage = showSnackbar,
                    onOpenSaveDirectoryPicker = onOpenSaveDirectoryPicker
                )
            }
        }
    )
}

internal data class ThreadNgPersistenceBindings(
    val persistHeaders: (List<String>) -> Unit,
    val persistWords: (List<String>) -> Unit
)

internal fun buildThreadNgPersistenceBindings(
    coroutineScope: CoroutineScope,
    stateStore: AppStateStore?,
    onFallbackHeadersChanged: (List<String>) -> Unit,
    onFallbackWordsChanged: (List<String>) -> Unit
): ThreadNgPersistenceBindings {
    return ThreadNgPersistenceBindings(
        persistHeaders = { updated ->
            if (stateStore != null) {
                coroutineScope.launch {
                    stateStore.setNgHeaders(updated)
                }
            } else {
                onFallbackHeadersChanged(updated)
            }
        },
        persistWords = { updated ->
            if (stateStore != null) {
                coroutineScope.launch {
                    stateStore.setNgWords(updated)
                }
            } else {
                onFallbackWordsChanged(updated)
            }
        }
    )
}

internal suspend fun showThreadMessage(
    message: String,
    showMessage: suspend (String) -> Unit
) {
    showMessage(message)
}

internal suspend fun showOptionalThreadMessage(
    message: String?,
    showMessage: suspend (String) -> Unit
) {
    if (!message.isNullOrBlank()) {
        showMessage(message)
    }
}

internal suspend fun applyThreadSaveErrorState(
    errorState: ThreadManualSaveErrorState,
    onManualSaveDirectoryChanged: (String) -> Unit,
    onSaveDirectorySelectionChanged: (SaveDirectorySelection) -> Unit,
    onShowMessage: suspend (String) -> Unit,
    onOpenSaveDirectoryPicker: (() -> Unit)?
) {
    if (errorState.shouldResetManualSaveDirectory) {
        onManualSaveDirectoryChanged(DEFAULT_MANUAL_SAVE_ROOT)
    }
    if (errorState.shouldResetSaveDirectorySelection) {
        onSaveDirectorySelectionChanged(SaveDirectorySelection.MANUAL_INPUT)
    }
    onShowMessage(errorState.message)
    if (errorState.shouldOpenDirectoryPicker) {
        onOpenSaveDirectoryPicker?.invoke()
    }
}
