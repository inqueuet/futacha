package com.valoser.futacha.shared.compat.shared

import com.valoser.futacha.shared.compat.AimogeAvailability
import com.valoser.futacha.shared.compat.aimogeBoardOnlyShapeOf
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.state.AppStateStore
import kotlinx.coroutines.flow.first

const val AIMOGE_BOARDS_STAGE_ID: String = "aimoge-boards-v1"

private const val AIMOGE_HOST = "nijiurachan.net"
private val aimogeLegacyBoardRegex = Regex("""^https?://nijiurachan\.net/b/([a-z0-9][a-z0-9_-]{0,63})/(?:futaba\.php)?$""", RegexOption.IGNORE_CASE)

/** The canonical address an earlier-kept あいもげ board moves to, or null when it is already canonical or cannot move. */
internal fun aimogeLegacyBoardTarget(url: String): String? {
    val trimmed = url.trim()
    val slug = aimogeLegacyBoardRegex.matchEntire(trimmed)?.groupValues?.get(1)?.lowercase() ?: return null
    val canonical = "https://$AIMOGE_HOST/b/$slug/"
    if (trimmed == canonical) return null
    return canonical.takeIf { aimogeBoardOnlyShapeOf(it) == it }
}

internal fun aimogeBoardIdForCanonical(canonical: String): String = "aimoge-" + canonical.removeSuffix("/").substringAfterLast('/')

/** The result of moving the earlier あいもげ boards: the new board list, the history and the board-keyed setting moves. */
internal data class AimogeBoardsMove(
    val boards: List<BoardSummary>,
    val history: List<ThreadHistoryEntry>,
    val settingMoves: Map<String, String>,
    /** Old board id to (new id, canonical address). */
    val idMoves: Map<String, Pair<String, String>>
)

/** [history] with the rows of a moved board (by its old id, or by an earlier-form board address) pointed at the new one. */
internal fun moveAimogeHistory(history: List<ThreadHistoryEntry>, idMoves: Map<String, Pair<String, String>>): List<ThreadHistoryEntry> =
    history.map { entry ->
        val move = idMoves[entry.boardId]
            ?: aimogeLegacyBoardTarget(entry.boardUrl)?.let { canonical -> aimogeBoardIdForCanonical(canonical) to canonical }
        if (move == null) entry else entry.copy(boardId = move.first, boardUrl = move.second)
    }

/**
 * Moves each earlier あいもげ board (kept by the old "add board" as `…/b/{slug}/futaba.php`, or with http) to the canonical
 * address and the `aimoge-{slug}` id, merges two of the same board into one (the first place and name are kept), and
 * moves what pointed at the old id (history rows, board-keyed settings). A board that cannot move stays as it is. Pure.
 */
internal fun planAimogeBoardsMove(boards: List<BoardSummary>, history: List<ThreadHistoryEntry>): AimogeBoardsMove {
    val idMoves = LinkedHashMap<String, Pair<String, String>>() // old id -> (new id, canonical)
    val result = ArrayList<BoardSummary>()
    val placed = HashSet<String>()
    for (board in boards) {
        val canonical = aimogeLegacyBoardTarget(board.url)
            ?: aimogeBoardOnlyShapeOf(board.url)?.takeIf { it == board.url.trim() && board.id != aimogeBoardIdForCanonical(it) }
        if (canonical == null) {
            // A canonical board already under its id is the place an earlier one merges into.
            aimogeBoardOnlyShapeOf(board.url)?.takeIf { it == board.url.trim() }?.let { placed += it }
            result += board
            continue
        }
        val newId = aimogeBoardIdForCanonical(canonical)
        if (board.id != newId) idMoves[board.id] = newId to canonical
        if (placed.add(canonical) && result.none { aimogeBoardOnlyShapeOf(it.url) == canonical && it.url.trim() == canonical }) {
            result += board.copy(id = newId, url = canonical)
        } else if (board.pinned) {
            val index = result.indexOfFirst { it.url.trim() == canonical }
            if (index >= 0) result[index] = result[index].copy(pinned = true)
        }
    }
    val settingMoves = idMoves.mapValues { (_, target) -> compatBoardKey(target.second) }
    return AimogeBoardsMove(result, moveAimogeHistory(history, idMoves), settingMoves, idMoves)
}

/**
 * `aimoge-boards-v1` (AM-B5): runs only while あいもげ is on ([SharedDataMigrations.stagesFor]). [readLegacy] lists the
 * earlier-form あいもげ boards (address and id), so it is empty once they moved and the stage runs again only when a
 * restore brings one back.
 */
internal class AimogeBoardsStage(private val stateStore: AppStateStore) : SharedDataMigrationStage {
    override val id: String = AIMOGE_BOARDS_STAGE_ID

    override suspend fun readLegacy(): String =
        stateStore.boards.first()
            .filter { board ->
                aimogeLegacyBoardTarget(board.url) != null ||
                    aimogeBoardOnlyShapeOf(board.url)?.let { it == board.url.trim() && board.id != aimogeBoardIdForCanonical(it) } == true
            }
            .joinToString("\n") { "${it.url}|${it.id}" }

    override suspend fun migrate(legacy: String) {
        if (!AimogeAvailability.sitesEnabled) return
        val move = planAimogeBoardsMove(stateStore.boards.first(), stateStore.history.first())
        stateStore.moveBoardKeyedSettings(move.settingMoves)
        stateStore.updateHistory { current -> moveAimogeHistory(current, move.idMoves) }
        stateStore.updateBoards { current -> planAimogeBoardsMove(current, emptyList()).boards }
    }
}
