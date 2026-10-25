package com.valoser.futacha.shared

import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.compatibilityBoardSynchronizationKeys
import com.valoser.futacha.shared.compat.compatibilityHistorySharedMetadata
import com.valoser.futacha.shared.compat.mergeCompatibilityHistory
import com.valoser.futacha.shared.compat.modernBoardsToCompatibility
import com.valoser.futacha.shared.compat.synchronizeModernBoardsFromCompatibility
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Continuous synchronization owned by the current iOS profile's LaunchedEffect. */
internal suspend fun synchronizeIosProfileData(
    stateStore: AppStateStore,
    compatibilityStore: CompatibilityStore,
    usesAppStateData: Boolean,
    onFailure: (String, Throwable) -> Unit = { operation, error ->
        Logger.e("MainViewController", "Failed to synchronize $operation", error)
    }
): Unit = coroutineScope {
    // A failed update must neither escape the Compose effect nor cancel its sibling collector.
    suspend fun synchronize(operation: String, update: suspend () -> Unit) {
        runSuspendCatchingPreservingCancellation { update() }
            .onFailure { error -> onFailure(operation, error) }
    }
    launch {
        if (!usesAppStateData) {
            compatibilityStore.boards.collect { compatBoards ->
                synchronize("compatibility boards") {
                    val current = stateStore.boards.first()
                    val synchronized = synchronizeModernBoardsFromCompatibility(
                        current, compatBoards, compatibilityStore.holdsBundledTutorialBoard
                    )
                    if (synchronized != current) stateStore.setBoards(synchronized)
                }
            }
        } else {
            var hasObservedLoadedModernBoards = false
            stateStore.boards.collect { modernBoards ->
                if (modernBoards.isNotEmpty()) hasObservedLoadedModernBoards = true
                if (!hasObservedLoadedModernBoards) return@collect
                synchronize("modern boards") {
                    val desired = modernBoardsToCompatibility(modernBoards)
                    val desiredKeys = compatibilityBoardSynchronizationKeys(modernBoards)
                    compatibilityStore.importModernBoards(modernBoards)
                    compatibilityStore.boards.first()
                        .filterNot { it.key in desiredKeys }
                        .forEach { compatibilityStore.deleteBoard(it.key) }
                    compatibilityStore.upsertBoards(desired)
                }
            }
        }
    }
    launch {
        compatibilityStore.history
            .distinctUntilChangedBy(::compatibilityHistorySharedMetadata)
            .collect { compatHistory ->
                synchronize("compatibility history") {
                    val boards = stateStore.boards.first()
                    stateStore.updateHistory { current ->
                        mergeCompatibilityHistory(current, compatHistory, boards)
                    }
                }
            }
    }
}
