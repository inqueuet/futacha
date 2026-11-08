package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.ui.futaber.FutaberSharedTabsPlan
import com.valoser.futacha.shared.ui.futaber.FutaberTabKey
import com.valoser.futacha.shared.ui.futaber.futaberSharedTabsPlan

/** D-B5: the name this mode's marks go under on the shared tabs (`compat_tab_view.profile`). */
internal const val FTUTTER_TAB_PROFILE = "ftutter"

/**
 * This mode's tab strip (thread URLs, in order) as shared tabs, by the same rules as the futaber mode's. A URL whose board
 * is not registered, or not an official one, stays only in this mode. Protection stays with this mode (it is not a star).
 */
internal fun ftutterSharedTabsPlan(
    urls: List<String>,
    boards: List<BoardSummary>,
    history: List<ThreadHistoryEntry>,
    nowEpochMillis: Long
): FutaberSharedTabsPlan {
    val keys = urls.mapNotNull { url ->
        val board = ftutterBoardForUrl(boards, url) ?: return@mapNotNull null
        val threadId = ftutterThreadIdFromUrl(url) ?: return@mapNotNull null
        FutaberTabKey(board.id, threadId)
    }
    return futaberSharedTabsPlan(keys, emptyList(), boards, history, nowEpochMillis, FTUTTER_TAB_PROFILE)
}
