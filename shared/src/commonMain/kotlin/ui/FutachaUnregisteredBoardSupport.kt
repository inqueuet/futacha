package com.valoser.futacha.shared.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.valoser.futacha.shared.compat.canonicalizeBoardUrl
import com.valoser.futacha.shared.compat.canonicalizeThreadUrl
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.ui.board.createCustomBoardSummary

/**
 * A thread whose board is not registered in ふたちゃ. [historyEntry] is set for
 * history / tab / watcher results (they all open through onHistoryEntrySelected);
 * saved threads leave it null and only get the old 「OK」.
 */
internal data class FutachaUnregisteredBoardRequest(
    val boardName: String,
    val historyEntry: ThreadHistoryEntry? = null
) {
    /** Official board URL the entry belongs to, or null when the button must not be offered. */
    val addableBoardUrl: String? get() = historyEntry?.let(::resolveFutachaAddableBoardUrl)
}

/** Only official Futaba boards (thread URL or board URL) can be added; tutorial / unofficial URLs give null. */
internal fun resolveFutachaAddableBoardUrl(entry: ThreadHistoryEntry): String? {
    val raw = entry.boardUrl.trim().takeIf(String::isNotEmpty) ?: return null
    return canonicalizeThreadUrl(raw)?.canonicalBoardUrl ?: canonicalizeBoardUrl(raw)
}

/** Board name for the added board: the name kept in the history, else `host/path` of the URL. */
internal fun futachaAddedBoardName(request: FutachaUnregisteredBoardRequest, boardUrl: String): String =
    request.boardName.trim().ifEmpty {
        boardUrl.substringAfter("://").trimEnd('/').ifEmpty { boardUrl }
    }

/**
 * Same add as the board screen's 「板を追加」 (normalizeBoardUrl + createCustomBoardSummary);
 * a board whose URL is already registered is left as is.
 */
internal fun addFutachaBoardIfMissing(
    boards: List<BoardSummary>,
    name: String,
    url: String
): List<BoardSummary> {
    val normalizedUrl = normalizeBoardUrl(url)
    if (boards.any { it.url.equals(normalizedUrl, ignoreCase = true) }) return boards
    return boards + createCustomBoardSummary(name = name, url = normalizedUrl, existingBoards = boards)
}

/** The existing 「スレッドを開けませんでした」 dialog, plus 「板を追加して開く」 when the board can be added. */
@Composable
internal fun FutachaUnregisteredBoardDialog(
    request: FutachaUnregisteredBoardRequest,
    onDismiss: () -> Unit,
    onAddAndOpen: (boardName: String, boardUrl: String, entry: ThreadHistoryEntry) -> Unit
) {
    val boardUrl = request.addableBoardUrl
    val entry = request.historyEntry
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("スレッドを開けませんでした") },
        text = { Text(buildFutachaUnregisteredBoardMessage(request.boardName)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        dismissButton = if (boardUrl != null && entry != null) {
            {
                TextButton(
                    modifier = Modifier.testTag("futacha-unregistered-board-add"),
                    onClick = { onAddAndOpen(futachaAddedBoardName(request, boardUrl), boardUrl, entry) }
                ) { Text("板を追加して開く") }
            }
        } else null
    ) }
}
