package com.valoser.futacha.shared.compat.shared

import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.compatibilityHistorySharedMetadata
import com.valoser.futacha.shared.compat.mergeCompatibilityHistory
import com.valoser.futacha.shared.compat.toCompatHistoryEntry
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * The history of the four modes as one list (plan B3, audit F4/G1): the AppState history (ふたちゃ, ふたばー風,
 * ふたったー風) and the compatibility history (としあき(仮)) are copied into each other for as long as any mode's screen is
 * shown, on every OS. Before, the AppState history reached としあき(仮) only while that mode was used (Android) or at
 * start-up and at a switch (iOS, desktop), so a thread read in one mode was missing in the other until then.
 *
 * Only additions and changes travel this way: neither direction deletes. A deletion travels by the tombstones each store
 * keeps (the AppState one across restarts since B2) and by the deletion functions writing both stores, so a copy read
 * before a deletion cannot bring the row back (each side skips a row not visited after its deletion). A thread opened
 * again after its deletion carries a newer visit time, which lifts the tombstone on both sides.
 *
 * Writes converge: a direction writes only what differs ([planModernHistoryImport][com.valoser.futacha.shared.compat.planModernHistoryImport]
 * returns nothing for a repeated import, [AppStateStore.updateHistory] skips an unchanged list), and the reading
 * positions, which each mode keeps for its own layout, neither trigger a copy nor are copied over.
 *
 * A failed pass is told through [onFailure]; the next change is copied as usual.
 */
@OptIn(FlowPreview::class)
suspend fun synchronizeSharedHistory(
    stateStore: AppStateStore,
    compatibilityStore: CompatibilityStore,
    appStateHistory: Flow<List<ThreadHistoryEntry>> = stateStore.observedHistory,
    importDebounceMillis: Long = SHARED_HISTORY_IMPORT_DEBOUNCE_MILLIS,
    onFailure: (operation: String, failure: Throwable) -> Unit
): Unit = coroutineScope {
    suspend fun pass(operation: String, update: suspend () -> Unit) {
        runSuspendCatchingPreservingCancellation { update() }.onFailure { failure -> onFailure(operation, failure) }
    }
    launch {
        compatibilityStore.history
            .distinctUntilChangedBy(::compatibilityHistorySharedMetadata)
            // Up to 20,000 rows are mapped and compared here, on every saved reading position: off the screen's thread (plan 1-6).
            .flowOn(Dispatchers.Default)
            .collect { compatHistory ->
                pass("compatibility history") {
                    val boards = stateStore.boards.first()
                    stateStore.updateHistory { current -> mergeCompatibilityHistory(current, compatHistory, boards) }
                }
            }
    }
    launch {
        // A row opened or brought in on purpose forgets its deletion in としあき(仮)'s store too (plan 1-5), then is copied
        // there at once: the times alone failed after a clock was set back, and the copy below waits for a change.
        stateStore.historyOpens.collect { entries ->
            pass("opened history") {
                val urls = entries.mapNotNullTo(LinkedHashSet()) { it.toCompatHistoryEntry()?.canonicalUrl }
                if (urls.isEmpty()) return@pass
                compatibilityStore.forgetHistoryTombstones(urls)
                compatibilityStore.importModernHistory(appStateHistory.first())
            }
        }
    }
    launch {
        appStateHistory
            .distinctUntilChangedBy(::modernHistorySharedMetadata)
            .flowOn(Dispatchers.Default)
            // Opening and reading write the history often; one copy after it settles is enough.
            .debounce(importDebounceMillis)
            .collect { history -> pass("modern history") { compatibilityStore.importModernHistory(history) } }
    }
}

/**
 * After a settings backup restored history into the compatibility store (inside [withSharedBoardsRestore]): the AppState
 * history gets the rows at once, a row deleted earlier included (the person chose to restore it). The continuous copy
 * above skips a row deleted earlier, as it must for an old copy.
 */
suspend fun mergeRestoredHistoryIntoAppState(compatibilityStore: CompatibilityStore, stateStore: AppStateStore) {
    val compatHistory = compatibilityStore.history.first()
    if (compatHistory.isEmpty()) return
    val boards = stateStore.boards.first()
    stateStore.restoreHistory { current -> mergeCompatibilityHistory(current, compatHistory, boards) }
}

/** The AppState history's fields the compatibility history shares (not the reading positions: each mode keeps its own). */
internal data class ModernHistorySharedMetadata(
    val threadId: String,
    val boardId: String,
    val boardUrl: String,
    val boardName: String,
    val title: String,
    val titleImageUrl: String,
    val replyCount: Int,
    val lastVisitedEpochMillis: Long
)

internal fun modernHistorySharedMetadata(history: List<ThreadHistoryEntry>): List<ModernHistorySharedMetadata> =
    history.map { entry ->
        ModernHistorySharedMetadata(
            threadId = entry.threadId,
            boardId = entry.boardId,
            boardUrl = entry.boardUrl,
            boardName = entry.boardName,
            title = entry.title,
            titleImageUrl = entry.titleImageUrl,
            replyCount = entry.replyCount,
            lastVisitedEpochMillis = entry.lastVisitedEpochMillis
        )
    }

const val SHARED_HISTORY_IMPORT_DEBOUNCE_MILLIS = 500L
