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
import com.valoser.futacha.shared.state.launchFinalHistoryScrollPersistence
import com.valoser.futacha.shared.state.launchHistoryScrollPersistence
import com.valoser.futacha.shared.util.safeEpochElapsedMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val thumbnailUrl: String,
    /** False when the thread was opened without its title (from an address in a post): [title] is only a placeholder. */
    val titleKnown: Boolean = true
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
            coroutineScope.launchHistoryScrollPersistence {
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
            // Called from the thread screen's dispose path while the UI scope is being
            // cancelled; the final save must still run (D11).
            coroutineScope.launchFinalHistoryScrollPersistence {
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
        titleKnown = navigationState.selectedThreadTitle != null,
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
    minimumIntervalMillis: Long = 0L
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
        // A thread opened without its title keeps the title it already has in the history (re-audit M34: a failed load
        // of a thread opened from an address wrote "無題" over it).
        title = if (context.titleKnown) context.title else existingEntry?.title?.takeIf { it.isNotBlank() } ?: context.title,
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
    minimumIntervalMillis: Long = 0L
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

/**
 * [dismissHistoryEntry] for several rows (ふたったー風's history deletion): the other modes' copy of each row (or the
 * start-up merge brings it back), the rows themselves in one write, the person's own post numbers and the auto-saved
 * copies. The shared store is read once and only rows it holds are deleted there.
 */
internal suspend fun dismissHistoryEntries(
    stateStore: AppStateStore,
    autoSavedThreadRepository: SavedThreadRepository?,
    importedHistoryRepository: SavedThreadRepository? = null,
    compatibilityStore: CompatibilityStore? = null,
    entries: Collection<ThreadHistoryEntry>,
    onAutoSavedThreadDeleteFailure: (Throwable) -> Unit = {}
) {
    if (entries.isEmpty()) return
    // Once started it goes to the end (re-audit M27: cancelled half way, the other modes' rows were gone and this
    // mode's stayed), and each store is written once, not once per row.
    withContext(NonCancellable) {
        if (compatibilityStore != null) {
            val wanted = entries.mapNotNullTo(LinkedHashSet()) { it.toCompatHistoryEntry()?.canonicalUrl }
            if (wanted.isNotEmpty()) {
                val stored = compatibilityStore.history.first().mapTo(HashSet()) { it.canonicalUrl }
                val present = wanted.filter { it in stored }
                if (present.isNotEmpty()) compatibilityStore.deleteHistories(present)
            }
        }
        stateStore.removeHistoryEntries(entries)
        val threads = entries.map { it.threadId to resolveHistoryEntryBoardId(it) }.distinct()
        stateStore.removeSelfPostIdentifiersForThreads(threads)
        listOfNotNull(autoSavedThreadRepository, importedHistoryRepository)
            .distinct()
            .forEach { repository ->
                if (threads.size == 1) {
                    // One row: also the old and orphan folders of that thread, as ふたちゃ's own deletion does.
                    val (threadId, boardId) = threads.single()
                    repository.purgeThreadStorage(threadId = threadId, boardId = boardId)
                        .exceptionOrNull()?.let(onAutoSavedThreadDeleteFailure)
                } else {
                    // Many rows: one index rewrite per chunk (the folder scan per row made "すべて削除" slow, L20). A chunk
                    // that fails as a whole is retried row by row.
                    threads.chunked(DISMISS_HISTORY_PURGE_CHUNK_SIZE).forEach { chunk ->
                        repository.purgeIndexedThreadsStorage(chunk).onFailure {
                            chunk.forEach { (threadId, boardId) ->
                                repository.purgeIndexedThreadStorage(threadId = threadId, boardId = boardId)
                                    .exceptionOrNull()?.let(onAutoSavedThreadDeleteFailure)
                            }
                        }
                    }
                }
            }
    }
}

private const val DISMISS_HISTORY_PURGE_CHUNK_SIZE = 200

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
