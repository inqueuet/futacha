package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.analytics.analyticsBoardKind
import com.valoser.futacha.shared.analytics.analyticsCountBucket
import com.valoser.futacha.shared.analytics.analyticsResult
import com.valoser.futacha.shared.analytics.analyticsSessionContextId
import com.valoser.futacha.shared.analytics.analyticsTextHasUrl
import com.valoser.futacha.shared.analytics.analyticsTextLengthBucket
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.toCompatHistoryEntry
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.state.AppStateHistoryScrollUpdateRequest
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.safeEpochElapsedMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class FutachaHistoryMutationCallbacks(
    val onDismissHistoryEntry: (ThreadHistoryEntry) -> Unit,
    val onUpdateHistoryEntry: (ThreadHistoryEntry) -> Unit,
    val onClearHistory: () -> Unit
)

internal data class FutachaThreadMutationCallbacks(
    val onScrollPositionPersist: (String, Int, Int, String?) -> Unit,
    val onScrollPositionPersistImmediately: (String, Int, Int, String?) -> Unit
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
            AnalyticsTracker.event(
                "history_entry_deleted",
                historyEntryAnalyticsContext(entry) + mapOf("action_label" to "履歴を削除")
            )
            coroutineScope.launch { dismissHistoryEntry(entry) }
        },
        onUpdateHistoryEntry = { entry ->
            AnalyticsTracker.event(
                "history_entry_updated",
                historyEntryAnalyticsContext(entry) + mapOf("action_label" to "履歴を更新")
            )
            coroutineScope.launch { updateHistoryEntry(entry) }
        },
        onClearHistory = {
            AnalyticsTracker.event("history_cleared")
            coroutineScope.launch { clearHistory() }
        }
    )
}

private fun historyEntryAnalyticsContext(entry: ThreadHistoryEntry): Map<String, String> = mapOf(
    "board_context" to analyticsSessionContextId("board", entry.boardId, entry.boardUrl),
    "thread_context" to analyticsSessionContextId("thread", entry.boardUrl, entry.threadId),
    "title_length_bucket" to analyticsTextLengthBucket(entry.title),
    "title_has_url" to analyticsTextHasUrl(entry.title),
    "reply_count_bucket" to analyticsCountBucket(entry.replyCount)
)

internal fun buildFutachaThreadMutationCallbacks(
    coroutineScope: CoroutineScope,
    stateStore: AppStateStore,
    board: BoardSummary,
    historyContext: FutachaThreadHistoryContext
): FutachaThreadMutationCallbacks {
    return FutachaThreadMutationCallbacks(
        onScrollPositionPersist = { threadId, index, offset, postId ->
            coroutineScope.launch {
                persistFutachaThreadScrollPosition(
                    stateStore = stateStore,
                    threadId = threadId,
                    index = index,
                    offset = offset,
                    postId = postId,
                    board = board,
                    context = historyContext
                )
            }
        },
        onScrollPositionPersistImmediately = { threadId, index, offset, postId ->
            coroutineScope.launch {
                persistFutachaThreadScrollPositionImmediately(
                    stateStore = stateStore,
                    threadId = threadId,
                    index = index,
                    offset = offset,
                    postId = postId,
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
        minimumIntervalMillis > 0L &&
        currentTimeMillis >= existingEntry.lastVisitedEpochMillis &&
        safeEpochElapsedMillis(currentTimeMillis, existingEntry.lastVisitedEpochMillis) < minimumIntervalMillis
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
        lastConfirmedAliveEpochMillis = existingEntry?.lastConfirmedAliveEpochMillis,
        replyCount = context.replyCount,
        lastReadItemIndex = existingEntry?.lastReadItemIndex ?: 0,
        lastReadItemOffset = existingEntry?.lastReadItemOffset ?: 0,
        hasAutoSave = existingEntry?.hasAutoSave ?: false,
        isAutoRefreshDisabled = existingEntry?.isAutoRefreshDisabled ?: false,
        hasSelfPost = existingEntry?.hasSelfPost ?: false,
        lastSelfPostEpochMillis = existingEntry?.lastSelfPostEpochMillis
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
        AnalyticsTracker.event(
            "history_visit_record",
            mapOf(
                "result" to "skipped_recent",
                "board_kind" to analyticsBoardKind(board.url),
                "board_context" to analyticsSessionContextId("board", board.id, board.url),
                "thread_context" to analyticsSessionContextId("thread", board.url, threadId),
                "title_length_bucket" to analyticsTextLengthBucket(context.title)
            )
        )
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
    AnalyticsTracker.event(
        "history_visit_record",
        mapOf(
            "result" to analyticsResult(true),
            "board_kind" to analyticsBoardKind(board.url),
            "reply_count_bucket" to analyticsCountBucket(context.replyCount),
            "board_context" to analyticsSessionContextId("board", board.id, board.url),
            "thread_context" to analyticsSessionContextId("thread", board.url, threadId),
            "title_length_bucket" to analyticsTextLengthBucket(context.title)
        )
    )
    return true
}

internal suspend fun persistFutachaThreadScrollPosition(
    stateStore: AppStateStore,
    threadId: String,
    index: Int,
    offset: Int,
    postId: String?,
    board: BoardSummary,
    context: FutachaThreadHistoryContext
) {
    stateStore.updateHistoryScrollPosition(
        AppStateHistoryScrollUpdateRequest(
            threadId = threadId,
            index = index,
            offset = offset,
            postId = postId,
            boardId = board.id,
            title = context.title,
            titleImageUrl = context.thumbnailUrl,
            boardName = board.name,
            boardUrl = context.threadUrl,
            replyCount = context.replyCount
        )
    )
}

internal suspend fun persistFutachaThreadScrollPositionImmediately(
    stateStore: AppStateStore,
    threadId: String,
    index: Int,
    offset: Int,
    postId: String?,
    board: BoardSummary,
    context: FutachaThreadHistoryContext
) {
    stateStore.updateHistoryScrollPositionImmediately(
        AppStateHistoryScrollUpdateRequest(
            threadId = threadId,
            index = index,
            offset = offset,
            postId = postId,
            boardId = board.id,
            title = context.title,
            titleImageUrl = context.thumbnailUrl,
            boardName = board.name,
            boardUrl = context.threadUrl,
            replyCount = context.replyCount,
            forcePersist = true
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
    importedHistoryRepository: SavedThreadRepository? = null,
    compatibilityStore: CompatibilityStore? = null,
    entry: ThreadHistoryEntry,
    onAutoSavedThreadDeleteFailure: (Throwable) -> Unit = {}
) {
    val resolvedBoardId = resolveHistoryEntryBoardId(entry)
    entry.toCompatHistoryEntry()?.let { compatibilityEntry ->
        compatibilityStore?.deleteHistory(compatibilityEntry.canonicalUrl)
    }
    // removeHistoryEntry cancels the matching delayed scroll write before the
    // durable mutation, so a disposed/current screen cannot recreate it.
    stateStore.removeHistoryEntry(entry)
    stateStore.removeSelfPostIdentifiersForThread(
        threadId = entry.threadId,
        boardId = resolvedBoardId
    )
    listOfNotNull(autoSavedThreadRepository, importedHistoryRepository)
        .distinct()
        .forEach { repository ->
            repository.purgeThreadStorage(
                threadId = entry.threadId,
                boardId = resolvedBoardId
            ).exceptionOrNull()?.let(onAutoSavedThreadDeleteFailure)
        }
}

internal suspend fun clearHistory(
    stateStore: AppStateStore,
    autoSavedThreadRepository: SavedThreadRepository?,
    importedHistoryRepository: SavedThreadRepository? = null,
    compatibilityStore: CompatibilityStore? = null,
    onSkippedThreadsCleared: () -> Unit,
    onAutoSavedThreadDeleteFailure: (Throwable) -> Unit = {}
) {
    stateStore.cancelAllPendingHistoryScrollUpdates()
    compatibilityStore?.clearHistory()
    // Logical deletion is committed before potentially slow recursive I/O.
    // Background refresh metadata merges are existing-only and therefore
    // cannot repopulate the list while payload cleanup is running.
    stateStore.clearHistory()
    stateStore.clearSelfPostIdentifiers()
    onSkippedThreadsCleared()
    listOfNotNull(autoSavedThreadRepository, importedHistoryRepository)
        .distinct()
        .forEach { repository ->
            repository.purgeAllStorage()
                .exceptionOrNull()
                ?.let(onAutoSavedThreadDeleteFailure)
        }
}
