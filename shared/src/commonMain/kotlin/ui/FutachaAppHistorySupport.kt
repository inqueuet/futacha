package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.state.AppStateHistoryScrollUpdateRequest
import com.valoser.futacha.shared.state.AppStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class FutachaHistoryMutationCallbacks(
    val onDismissHistoryEntry: (ThreadHistoryEntry) -> Unit,
    val onUpdateHistoryEntry: (ThreadHistoryEntry) -> Unit,
    val onClearHistory: () -> Unit
)

internal data class FutachaThreadMutationCallbacks(
    val onScrollPositionPersist: (String, Int, Int) -> Unit
)

internal data class FutachaThreadHistoryContext(
    val title: String,
    val threadUrl: String,
    val replyCount: Int,
    val thumbnailUrl: String
)

internal fun buildFutachaHistoryMutationCallbacks(
    coroutineScope: CoroutineScope,
    dismissHistoryEntry: suspend (ThreadHistoryEntry) -> Unit,
    updateHistoryEntry: suspend (ThreadHistoryEntry) -> Unit,
    clearHistory: suspend () -> Unit
): FutachaHistoryMutationCallbacks {
    return FutachaHistoryMutationCallbacks(
        onDismissHistoryEntry = { entry ->
            coroutineScope.launch { dismissHistoryEntry(entry) }
        },
        onUpdateHistoryEntry = { entry ->
            coroutineScope.launch { updateHistoryEntry(entry) }
        },
        onClearHistory = {
            coroutineScope.launch { clearHistory() }
        }
    )
}

internal fun buildFutachaThreadMutationCallbacks(
    coroutineScope: CoroutineScope,
    stateStore: AppStateStore,
    board: BoardSummary,
    historyContext: FutachaThreadHistoryContext
): FutachaThreadMutationCallbacks {
    return FutachaThreadMutationCallbacks(
        onScrollPositionPersist = { threadId, index, offset ->
            coroutineScope.launch {
                persistFutachaThreadScrollPosition(
                    stateStore = stateStore,
                    threadId = threadId,
                    index = index,
                    offset = offset,
                    board = board,
                    context = historyContext
                )
            }
        }
    )
}

internal fun buildFutachaThreadHistoryContext(
    board: BoardSummary,
    navigationState: FutachaNavigationState
): FutachaThreadHistoryContext {
    return FutachaThreadHistoryContext(
        title = navigationState.selectedThreadTitle ?: "無題",
        threadUrl = navigationState.selectedThreadUrl ?: board.url,
        replyCount = navigationState.selectedThreadReplies ?: 0,
        thumbnailUrl = navigationState.selectedThreadThumbnailUrl.orEmpty()
    )
}

internal fun findFutachaVisitedHistoryEntry(
    history: List<ThreadHistoryEntry>,
    threadId: String,
    boardId: String,
    boardUrl: String
): ThreadHistoryEntry? {
    return history.firstOrNull { historyEntry ->
        if (historyEntry.threadId != threadId) {
            false
        } else if (historyEntry.boardId.isNotBlank()) {
            historyEntry.boardId == boardId
        } else {
            historyEntry.boardUrl == boardUrl
        }
    }
}

internal fun shouldSkipFutachaVisitedHistoryUpdate(
    existingEntry: ThreadHistoryEntry?,
    boardId: String,
    currentTimeMillis: Long,
    minimumIntervalMillis: Long = 60_000L
): Boolean {
    return existingEntry != null &&
        existingEntry.boardId == boardId &&
        (currentTimeMillis - existingEntry.lastVisitedEpochMillis) < minimumIntervalMillis
}

internal fun buildFutachaVisitedHistoryEntry(
    threadId: String,
    board: BoardSummary,
    context: FutachaThreadHistoryContext,
    currentTimeMillis: Long,
    existingEntry: ThreadHistoryEntry?
): ThreadHistoryEntry {
    return ThreadHistoryEntry(
        threadId = threadId,
        boardId = board.id,
        title = context.title,
        titleImageUrl = context.thumbnailUrl,
        boardName = board.name,
        boardUrl = context.threadUrl,
        lastVisitedEpochMillis = currentTimeMillis,
        replyCount = context.replyCount,
        lastReadItemIndex = existingEntry?.lastReadItemIndex ?: 0,
        lastReadItemOffset = existingEntry?.lastReadItemOffset ?: 0
    )
}

internal suspend fun recordFutachaVisitedThread(
    stateStore: AppStateStore,
    history: List<ThreadHistoryEntry>,
    threadId: String,
    board: BoardSummary,
    context: FutachaThreadHistoryContext,
    currentTimeMillis: Long,
    minimumIntervalMillis: Long = 60_000L
): Boolean {
    val existingEntry = findFutachaVisitedHistoryEntry(
        history = history,
        threadId = threadId,
        boardId = board.id,
        boardUrl = context.threadUrl
    )
    if (
        shouldSkipFutachaVisitedHistoryUpdate(
            existingEntry = existingEntry,
            boardId = board.id,
            currentTimeMillis = currentTimeMillis,
            minimumIntervalMillis = minimumIntervalMillis
        )
    ) {
        return false
    }
    stateStore.prependOrReplaceHistoryEntry(
        buildFutachaVisitedHistoryEntry(
            threadId = threadId,
            board = board,
            context = context,
            currentTimeMillis = currentTimeMillis,
            existingEntry = existingEntry
        )
    )
    return true
}

internal suspend fun persistFutachaThreadScrollPosition(
    stateStore: AppStateStore,
    threadId: String,
    index: Int,
    offset: Int,
    board: BoardSummary,
    context: FutachaThreadHistoryContext
) {
    stateStore.updateHistoryScrollPosition(
        AppStateHistoryScrollUpdateRequest(
            threadId = threadId,
            index = index,
            offset = offset,
            boardId = board.id,
            title = context.title,
            titleImageUrl = context.thumbnailUrl,
            boardName = board.name,
            boardUrl = context.threadUrl,
            replyCount = context.replyCount
        )
    )
}

internal fun resolveHistoryEntryBoardId(entry: ThreadHistoryEntry): String? {
    val resolvedBoardId = entry.boardId
        .ifBlank { runCatching { BoardUrlResolver.resolveBoardSlug(entry.boardUrl) }.getOrDefault("") }
        .ifBlank { "" }
    return resolvedBoardId.ifBlank { null }
}

internal suspend fun dismissHistoryEntry(
    stateStore: AppStateStore,
    autoSavedThreadRepository: SavedThreadRepository?,
    entry: ThreadHistoryEntry,
    onAutoSavedThreadDeleteFailure: (Throwable) -> Unit = {}
) {
    val resolvedBoardId = resolveHistoryEntryBoardId(entry)
    stateStore.removeSelfPostIdentifiersForThread(
        threadId = entry.threadId,
        boardId = resolvedBoardId
    )
    stateStore.removeHistoryEntry(entry)
    autoSavedThreadRepository?.deleteThread(
        threadId = entry.threadId,
        boardId = resolvedBoardId
    )?.onFailure(onAutoSavedThreadDeleteFailure)
}

internal suspend fun clearHistory(
    stateStore: AppStateStore,
    autoSavedThreadRepository: SavedThreadRepository?,
    onSkippedThreadsCleared: () -> Unit,
    onAutoSavedThreadDeleteFailure: (Throwable) -> Unit = {}
) {
    stateStore.clearSelfPostIdentifiers()
    stateStore.setHistory(emptyList())
    onSkippedThreadsCleared()
    autoSavedThreadRepository?.deleteAllThreads()?.onFailure(onAutoSavedThreadDeleteFailure)
}
