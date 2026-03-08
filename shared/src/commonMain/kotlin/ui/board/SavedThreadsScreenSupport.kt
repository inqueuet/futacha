package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.SavedThread
import kotlinx.coroutines.TimeoutCancellationException

internal sealed interface SavedThreadsContentState {
    data object Loading : SavedThreadsContentState
    data class Error(val message: String) : SavedThreadsContentState
    data object Empty : SavedThreadsContentState
    data class Data(val threads: List<SavedThread>) : SavedThreadsContentState
}

internal fun resolveSavedThreadsContentState(
    isLoading: Boolean,
    loadError: String?,
    threads: List<SavedThread>
): SavedThreadsContentState {
    return when {
        isLoading -> SavedThreadsContentState.Loading
        loadError != null -> SavedThreadsContentState.Error(loadError)
        threads.isEmpty() -> SavedThreadsContentState.Empty
        else -> SavedThreadsContentState.Data(threads)
    }
}

internal fun buildSavedThreadsSummaryText(
    threadCount: Int,
    totalSize: Long,
    isLoading: Boolean
): String? {
    if (isLoading) return null
    return "$threadCount 件 / ${formatSize(totalSize)}"
}

internal fun buildSavedThreadsLoadErrorMessage(error: Throwable): String {
    return when (error) {
        is TimeoutCancellationException -> "読み込みがタイムアウトしました"
        else -> "読み込みエラー: ${error.message}"
    }
}

internal fun buildSavedThreadsDeleteMessage(result: Result<Unit>): String {
    return result.fold(
        onSuccess = { "削除しました" },
        onFailure = { err -> "削除に失敗しました: ${err.message}" }
    )
}
