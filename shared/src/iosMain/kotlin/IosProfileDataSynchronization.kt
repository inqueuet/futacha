package com.valoser.futacha.shared

import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.shared.SharedBoardSide
import com.valoser.futacha.shared.compat.shared.synchronizeSharedBoards
import com.valoser.futacha.shared.compat.shared.synchronizeSharedHistory
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.coroutineScope
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
    // A failed update must neither escape the Compose effect nor cancel its sibling collector: each pass below tells it
    // through onFailure and goes on.
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
        // The history of the four modes as one list, both ways (plan B3), as on Android and the desktop.
        synchronizeSharedHistory(stateStore, compatibilityStore, onFailure = onFailure)
    }
}
