package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlinx.coroutines.flow.first

/**
 * ふたったー風's tab bar on a real store for tests (plan B7a): the screen's [FtutterTabState] with the store writer, its
 * writes run when [settle] is called (as the screen's `launch` does them later), and then the stores' snapshot is shown.
 * The old rows are kept in [rows], as the screen keeps them in its settings.
 */
internal class FtutterSyncTabBar(
    val store: CompatibilityStore?,
    private val boards: List<BoardSummary>,
    private val history: () -> List<ThreadHistoryEntry> = { emptyList() },
    val rows: MutableMap<String, String> = mutableMapOf(),
    var nowMillis: Long = 1_000L
) {
    val notices = mutableListOf<String>()
    private val queued = ArrayDeque<suspend () -> Unit>()
    private val writer = FtutterStoreTabWriter(
        store, { boards }, history,
        editRow = { key, transform -> rows[key] = encodeFtutterList(transform(decodeFtutterList(rows[key]).orEmpty())) },
        notice = { notices += it },
        now = { nowMillis++ }
    )
    lateinit var state: FtutterTabState
        private set

    suspend fun snapshot(): FtutterTabSnapshot = ftutterTabSnapshot(
        store?.tabs?.first().orEmpty(),
        store?.loadTabViews(FTUTTER_TAB_PROFILE).orEmpty(),
        decodeFtutterList(rows[FtutterKeys.TABS]).orEmpty(),
        decodeFtutterList(rows[FtutterKeys.PROTECTED]).orEmpty().toSet(),
        store?.loadSharedData()?.let(::ftutterConsumedTabs).orEmpty()
    )

    suspend fun start(): FtutterSyncTabBar {
        state = FtutterTabState(snapshot(), "", writer) { queued += it }
        return this
    }

    /** Runs the writes on their way, then shows what the stores hold. */
    suspend fun settle() {
        while (queued.isNotEmpty()) queued.removeFirst().invoke()
        state.sync(snapshot())
        state.showLatest()
    }
}
