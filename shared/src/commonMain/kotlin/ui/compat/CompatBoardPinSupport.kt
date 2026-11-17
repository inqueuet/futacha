package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.canonicalizeBoardUrl
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.board.toggleBoardSummaryPinned

/*
 * Board pins for としあき(仮) (feature-parity batch 2). The pin is the AppState's own [BoardSummary.pinned], the one
 * ふたちゃ shows on its board cards, so a pin set here shows there and the other way round. The compatibility store
 * has no pin field and its schema stays as it is: the pin is kept (and, for a board that is renamed here, preserved by
 * synchronizeModernBoardsFromCompatibility) in the AppState. It only marks the board; the order is not changed.
 * Known limits: the pin is not in the compatibility settings backup, and a board that is deleted and added again
 * starts unpinned.
 */

/** The compatibility board key a modern board stands for, or null when its URL is not an official board URL. */
internal fun BoardSummary.compatPinKey(): String? =
    canonicalizeBoardUrl(url)?.let(::compatBoardKey)

/** Keys of the compatibility boards whose AppState board is pinned. */
internal fun compatPinnedBoardKeys(boards: List<BoardSummary>): Set<String> =
    boards.filter { it.pinned }.mapNotNullTo(linkedSetOf()) { it.compatPinKey() }

/** [boards] with the pin of the board [compatKey] flipped; unchanged when no board stands for that key. */
internal fun toggleCompatBoardPinned(boards: List<BoardSummary>, compatKey: String): List<BoardSummary> {
    val index = boards.indexOfFirst { it.compatPinKey() == compatKey }
    return if (index < 0) boards else toggleBoardSummaryPinned(boards, index)
}

/** Long-press choices of a board row: the two that always exist, then the pin (only where it can be saved). */
internal fun compatBoardContextChoices(pinned: Boolean, pinAvailable: Boolean): List<String> = buildList {
    add("名前を変更")
    add("削除する")
    if (pinAvailable) add(if (pinned) COMPAT_BOARD_UNPIN_CHOICE else COMPAT_BOARD_PIN_CHOICE)
}

internal const val COMPAT_BOARD_PIN_CHOICE = "ピン留め"
internal const val COMPAT_BOARD_UNPIN_CHOICE = "ピン留めを解除"

internal class CompatBoardPins(val pinnedKeys: Set<String>, val toggle: (CompatBoard) -> Unit)

/**
 * The pins of the AppState boards for the board list, or null without an [stateStore] (tests, special hosts), where
 * the list offers no pin. [launch] runs the write the way the host runs its other store writes.
 */
@Composable
internal fun rememberCompatBoardPins(
    stateStore: AppStateStore?,
    launch: (suspend () -> Unit) -> Unit
): CompatBoardPins? {
    if (stateStore == null) return null
    val boards by stateStore.boards.collectAsState(emptyList())
    val latestLaunch by rememberUpdatedState(launch)
    val keys = remember(boards) { compatPinnedBoardKeys(boards) }
    return remember(stateStore, keys) {
        CompatBoardPins(keys) { board ->
            latestLaunch { stateStore.updateBoards { toggleCompatBoardPinned(it, board.key) } }
        }
    }
}

/** The board name with a small pin after it; used only for a pinned board, so an unpinned row is unchanged. */
@Composable
internal fun CompatPinnedBoardName(name: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, fontSize = 18.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
        Icon(
            Icons.Filled.PushPin,
            contentDescription = "ピン留め",
            modifier = Modifier.padding(start = 4.dp).size(16.dp).testTag("compat-board-pin-mark"),
            tint = LocalContentColor.current
        )
    }
}
