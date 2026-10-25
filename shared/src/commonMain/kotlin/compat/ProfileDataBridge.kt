package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Android's continuous bridge of the boards and history between the AppState modes and としあき(仮), for as long
 * as the screen of the current mode is shown. The two profiles use different UI stores, but their boards,
 * history, and shared thread snapshots are one user dataset.
 *
 * A failed update is told through [onFailure] and the next change is bridged as usual (L96): one failure used
 * to stop the bridge until the Activity was made again, and a board deleted after that came back on the switch
 * to としあき(仮). Each update mirrors the whole list, so the next one also completes one that failed.
 */
suspend fun bridgeProfileBoardsAndHistory(
    stateStore: AppStateStore,
    compatibilityStore: CompatibilityStore,
    usesAppStateData: Boolean,
    onFailure: (operation: String, failure: Throwable) -> Unit
): Unit = coroutineScope {
    suspend fun bridge(operation: String, update: suspend () -> Unit) {
        runSuspendCatchingPreservingCancellation { update() }.onFailure { failure -> onFailure(operation, failure) }
    }
    launch {
        if (!usesAppStateData) {
            var hasObservedAuthoritativeCompatBoards = false
            compatibilityStore.boards.collect { compatBoards ->
                if (compatBoards.isNotEmpty()) hasObservedAuthoritativeCompatBoards = true
                if (!hasObservedAuthoritativeCompatBoards) return@collect
                bridge("boards") {
                    val current = stateStore.boards.first()
                    val synchronized = synchronizeModernBoardsFromCompatibility(
                        current, compatBoards, compatibilityStore.holdsBundledTutorialBoard
                    )
                    if (synchronized != current) stateStore.setBoards(synchronized)
                }
            }
        } else {
            var hasObservedLoadedModernBoards = false
            stateStore.observedBoards.collect { modernBoards ->
                if (modernBoards.isNotEmpty()) hasObservedLoadedModernBoards = true
                if (!hasObservedLoadedModernBoards) return@collect
                bridge("boards") {
                    val desired = modernBoardsToCompatibility(modernBoards)
                    val desiredKeys = desired.mapTo(mutableSetOf()) { it.key }
                    compatibilityStore.boards.first()
                        .filterNot { it.key in desiredKeys }
                        .forEach { compatibilityStore.deleteBoard(it.key) }
                    compatibilityStore.upsertBoards(desired)
                }
            }
        }
    }
    launch {
        compatibilityStore.history.collect { compatHistory ->
            bridge("history") {
                val boards = stateStore.boards.first()
                stateStore.updateHistory { current ->
                    mergeCompatibilityHistory(current, compatHistory, boards)
                }
            }
        }
    }
}
