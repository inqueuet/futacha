package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** Keeps cancellation out of the failure UI while always reopening the save gate. */
internal suspend fun <T> runCompatPageSaveWithCleanup(
    onFinished: () -> Unit,
    block: suspend () -> T
): T = try {
    block()
} finally {
    onFinished()
}

/**
 * The thread save running in としあき(仮) (one at a time, L48). It belongs to the workspace, not to the thread's
 * screen: the save goes on when that screen goes (another thread opened, the thread closed, settings), and the
 * thread's screen shows its progress and 中止 again when it comes back.
 */
@androidx.compose.runtime.Stable
internal class CompatPageSaves {
    var running: CompatRunningPageSave? by androidx.compose.runtime.mutableStateOf(null)
        private set

    fun start(
        tabKey: String,
        auxiliary: kotlinx.coroutines.flow.MutableStateFlow<com.valoser.futacha.shared.model.SaveProgress?>,
        page: kotlinx.coroutines.flow.StateFlow<com.valoser.futacha.shared.model.SaveProgress?>?
    ): CompatRunningPageSave = CompatRunningPageSave(tabKey, auxiliary, page).also { running = it }

    fun finish(save: CompatRunningPageSave) {
        if (running === save) running = null
    }
}

/** One running thread save: the thread it saves, the progress it reports, and its job for 中止. */
@androidx.compose.runtime.Stable
internal class CompatRunningPageSave(
    val tabKey: String,
    val auxiliary: kotlinx.coroutines.flow.MutableStateFlow<com.valoser.futacha.shared.model.SaveProgress?>,
    val page: kotlinx.coroutines.flow.StateFlow<com.valoser.futacha.shared.model.SaveProgress?>?
) {
    var job: kotlinx.coroutines.Job? by androidx.compose.runtime.mutableStateOf(null)
    var cancelRequested by androidx.compose.runtime.mutableStateOf(false)
}
