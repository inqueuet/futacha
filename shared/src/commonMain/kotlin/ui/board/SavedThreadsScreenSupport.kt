package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.repository.SavedThreadRepository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

internal data class SavedThreadsSnapshot(
    val threads: List<SavedThread>,
    val totalSize: Long
)

internal data class SavedThreadsDeleteUiOutcome(
    val updatedSnapshot: SavedThreadsSnapshot? = null,
    val message: String
)

internal suspend fun loadSavedThreadsSnapshot(
    repository: SavedThreadRepository,
    timeoutMillis: Long = 15_000L
): Result<SavedThreadsSnapshot> {
    return runCatching {
        withTimeout(timeoutMillis) {
            val index = repository.loadIndex()
            SavedThreadsSnapshot(
                threads = index.threads,
                totalSize = index.totalSize
            )
        }
    }
}

internal suspend fun deleteSavedThreadAndReload(
    repository: SavedThreadRepository,
    thread: SavedThread,
    timeoutMillis: Long = 15_000L
): Result<SavedThreadsSnapshot> {
    return runCatching {
        repository.deleteThread(
            threadId = thread.threadId,
            boardId = thread.boardId.ifBlank { null }
        ).getOrThrow()
        loadSavedThreadsSnapshot(
            repository = repository,
            timeoutMillis = timeoutMillis
        ).getOrThrow()
    }
}

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

internal fun resolveSavedThreadsDeleteUiOutcome(
    result: Result<SavedThreadsSnapshot>
): SavedThreadsDeleteUiOutcome {
    return result.fold(
        onSuccess = { snapshot ->
            SavedThreadsDeleteUiOutcome(
                updatedSnapshot = snapshot,
                message = buildSavedThreadsDeleteMessage(Result.success(Unit))
            )
        },
        onFailure = { error ->
            SavedThreadsDeleteUiOutcome(
                updatedSnapshot = null,
                message = buildSavedThreadsDeleteMessage(Result.failure(error))
            )
        }
    )
}
