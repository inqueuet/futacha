package com.valoser.futacha.shared

import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.compatibilityHistorySharedMetadata
import com.valoser.futacha.shared.compat.mergeCompatibilityHistory
import com.valoser.futacha.shared.compat.shared.SharedBoardSide
import com.valoser.futacha.shared.compat.shared.synchronizeSharedBoards
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
        // The boards of the two stores as one list (D-B1, compat/shared/SharedBoards.kt), as on Android and the desktop.
        synchronizeSharedBoards(
            stateStore,
            compatibilityStore,
            SharedBoardSide.of(usesAppStateData),
            appStateBoards = stateStore.boards
        ) { error -> onFailure(if (usesAppStateData) "modern boards" else "compatibility boards", error) }
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
