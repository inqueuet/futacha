package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.compat.shared.SharedBoardSide
import com.valoser.futacha.shared.compat.shared.synchronizeSharedBoards
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
 * to としあき(仮). The boards' record keeps a deletion until it is done, so the next change also completes one that
 * failed.
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
        // The boards of the two stores as one list (D-B1): joined, and only a deletion in the mode's own list takes a
        // board away (compat/shared/SharedBoards.kt). A board only the other store has is no longer deleted (D1).
        synchronizeSharedBoards(
            stateStore,
            compatibilityStore,
            SharedBoardSide.of(usesAppStateData),
            appStateBoards = stateStore.observedBoards
        ) { failure -> onFailure("boards", failure) }
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
