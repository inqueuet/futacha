package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.mergeCompatibilityBoards
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * After a backup (or the old app's settings) was restored into the compatibility store, adds the boards it brought to the
 * boards of [appStateStore].
 *
 * ふたちゃ and ふたばー風 keep their boards in the AppStateStore, and the compatibility store only mirrors them: at the
 * next change of the board list the mirror drops every board the AppStateStore does not have. A restored board that lives
 * only in the compatibility store was dropped that way (taking the NG rules and tabs of the board with it). The boards
 * are merged in (the boards already there keep their place and are never removed); the replacing synchronisation of the
 * other direction is not used here. In としあき(仮), where the compatibility store is the owner, [appStateStore] is null
 * and nothing is done.
 *
 * The compatibility store is read before the AppStateStore is touched, so the store's lock is never held across both.
 * A failure is logged and leaves the restore's own result as it was.
 */
internal suspend fun mergeRestoredBoardsIntoAppState(store: CompatibilityStore, appStateStore: AppStateStore?) {
    val target = appStateStore ?: return
    try {
        val restored = store.boards.first()
        if (restored.isEmpty()) return
        target.updateBoards { current -> mergeCompatibilityBoards(current, restored) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Logger.e("CompatRestoredBoardsSync", "Failed to add the restored boards to the board list", error)
    }
}
