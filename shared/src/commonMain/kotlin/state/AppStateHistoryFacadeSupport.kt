package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlinx.coroutines.CoroutineScope

internal class AppStateHistoryFacade(
    private val setHistoryImpl: suspend (List<ThreadHistoryEntry>) -> Unit,
    private val upsertHistoryEntryImpl: suspend (ThreadHistoryEntry) -> Unit,
    private val prependOrReplaceHistoryEntryImpl: suspend (ThreadHistoryEntry) -> Unit,
    private val prependOrReplaceHistoryEntriesImpl: suspend (List<ThreadHistoryEntry>) -> Unit,
    private val mergeHistoryEntriesImpl: suspend (Collection<ThreadHistoryEntry>) -> Unit,
    private val removeHistoryEntryImpl: suspend (ThreadHistoryEntry) -> Unit,
    private val updateHistoryScrollPositionImpl: suspend (
        threadId: String,
        index: Int,
        offset: Int,
        boardId: String,
        title: String,
        titleImageUrl: String,
        boardName: String,
        boardUrl: String,
        replyCount: Int
    ) -> Unit,
    private val setScrollDebounceScopeImpl: suspend (CoroutineScope) -> Unit
) {
    suspend fun setScrollDebounceScope(scope: CoroutineScope) = setScrollDebounceScopeImpl(scope)

    suspend fun setHistory(history: List<ThreadHistoryEntry>) = setHistoryImpl(history)

    suspend fun upsertHistoryEntry(entry: ThreadHistoryEntry) = upsertHistoryEntryImpl(entry)

    suspend fun prependOrReplaceHistoryEntry(entry: ThreadHistoryEntry) = prependOrReplaceHistoryEntryImpl(entry)

    suspend fun prependOrReplaceHistoryEntries(entries: List<ThreadHistoryEntry>) = prependOrReplaceHistoryEntriesImpl(entries)

    suspend fun mergeHistoryEntries(entries: Collection<ThreadHistoryEntry>) = mergeHistoryEntriesImpl(entries)

    suspend fun removeHistoryEntry(entry: ThreadHistoryEntry) = removeHistoryEntryImpl(entry)

    suspend fun updateHistoryScrollPosition(
        threadId: String,
        index: Int,
        offset: Int,
        boardId: String,
        title: String,
        titleImageUrl: String,
        boardName: String,
        boardUrl: String,
        replyCount: Int
    ) = updateHistoryScrollPositionImpl(
        threadId,
        index,
        offset,
        boardId,
        title,
        titleImageUrl,
        boardName,
        boardUrl,
        replyCount
    )
}

internal fun buildAppStateHistoryFacade(
    setHistoryImpl: suspend (List<ThreadHistoryEntry>) -> Unit,
    upsertHistoryEntryImpl: suspend (ThreadHistoryEntry) -> Unit,
    prependOrReplaceHistoryEntryImpl: suspend (ThreadHistoryEntry) -> Unit,
    prependOrReplaceHistoryEntriesImpl: suspend (List<ThreadHistoryEntry>) -> Unit,
    mergeHistoryEntriesImpl: suspend (Collection<ThreadHistoryEntry>) -> Unit,
    removeHistoryEntryImpl: suspend (ThreadHistoryEntry) -> Unit,
    updateHistoryScrollPositionImpl: suspend (
        threadId: String,
        index: Int,
        offset: Int,
        boardId: String,
        title: String,
        titleImageUrl: String,
        boardName: String,
        boardUrl: String,
        replyCount: Int
    ) -> Unit,
    setScrollDebounceScopeImpl: suspend (CoroutineScope) -> Unit
): AppStateHistoryFacade {
    return AppStateHistoryFacade(
        setHistoryImpl = setHistoryImpl,
        upsertHistoryEntryImpl = upsertHistoryEntryImpl,
        prependOrReplaceHistoryEntryImpl = prependOrReplaceHistoryEntryImpl,
        prependOrReplaceHistoryEntriesImpl = prependOrReplaceHistoryEntriesImpl,
        mergeHistoryEntriesImpl = mergeHistoryEntriesImpl,
        removeHistoryEntryImpl = removeHistoryEntryImpl,
        updateHistoryScrollPositionImpl = updateHistoryScrollPositionImpl,
        setScrollDebounceScopeImpl = setScrollDebounceScopeImpl
    )
}
