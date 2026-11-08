package com.valoser.futacha.shared.compat.shared

import com.valoser.futacha.shared.compat.canonicalizeBoardUrl
import com.valoser.futacha.shared.compat.canonicalizeThreadUrl
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.network.BoardUrlResolver

/*
 * The keys the four modes write a board and a thread under, in one place (D-B0, docs/futaber-evolution-data-2026-10-31.md
 * §0.2 and §2). Nothing reads or writes data through these yet: each value is what the mode's own helper makes today, and
 * SharedKeysTest holds them equal, so a later batch can move a mode onto them without changing a stored key.
 *
 * - AppState (ふたちゃ・ふたばー風・ふたったー風 share it): the board list's id (a slug that depends on the order the boards
 *   were added) and `"boardId::threadId"` for a history row and the person's own post numbers.
 * - The compatibility store (としあき(仮), and ふたちゃ's tabs, drafts, NG rules): `compatBoardKey` / `compatTabKey`, hashes of
 *   the canonical addresses `https://host/path/` and `https://host/path/res/N.htm`. Only an official board has them.
 * - ふたばー風's own lists: the shared board key of an official board, else the board's id (M3), with the thread number.
 * - ふたったー風's own lists: the thread's address over https, and `host/path/` for a board.
 *
 * The identity of a board and a thread is the canonical address (§0.2-2); the other forms are what each mode stored.
 */

/** One board in the forms the modes store it under. */
data class SharedBoardKeys(
    /** The AppState board list's id. */
    val boardId: String,
    /** The board's address as registered. */
    val boardUrl: String,
    /** `https://host/path/` of an official board; null for another address (the bundled tutorial board). */
    val canonicalBoardUrl: String?,
    /** The compatibility store's key of an official board; null for another address. */
    val compatBoardKey: String?,
    /** What ふたばー風 writes into its tabs, favourites, posts seen and drafts: [compatBoardKey], else [boardId]. */
    val futaberStoredBoardId: String,
    /** ふたったー風's board form, `host/path/` in lower case (its NG rules' board and its new-thread drafts). */
    val ftutterBoardKey: String
) {
    val isOfficial: Boolean get() = compatBoardKey != null
}

/** One thread of a board in the forms the modes store it under. */
data class SharedThreadKeys(
    val board: SharedBoardKeys,
    val threadId: String,
    /** The thread's address resolved from the board's ([BoardUrlResolver]); the board's own address when that fails. */
    val threadUrl: String,
    /** `https://host/path/res/N.htm` of an official board's thread; null for another address. */
    val canonicalThreadUrl: String?,
    /** The compatibility store's tab key; null for a board that is not an official one. */
    val compatTabKey: String?,
    /** An AppState history row's identity, `boardId::threadId`. */
    val appStateHistoryKey: String,
    /** The key of the person's own post numbers in AppState, `boardId::threadId`. */
    val appStateSelfPostKey: String,
    /** ふたばー風's tab entry, `storedBoardId\nthreadId`. */
    val futaberTabEntry: String,
    /** ふたばー風's reply draft key, `r:storedBoardId:threadId`. */
    val futaberReplyDraftKey: String,
    /** ふたったー風's tab, protection, posts-read and hidden lists: [threadUrl] over https. */
    val ftutterThreadUrl: String,
    /** ふたったー風's reply draft key, `res:host/path/res/N.htm`. */
    val ftutterReplyDraftKey: String
)

object SharedKeys {
    /** The separator of AppState's history and own-post keys. */
    const val APP_STATE_SEPARATOR: String = "::"
    /** The separator of ふたばー風's tab entries. */
    const val FUTABER_TAB_SEPARATOR: Char = '\n'

    fun board(board: BoardSummary): SharedBoardKeys {
        val canonical = canonicalizeBoardUrl(board.url)
        val compatKey = canonical?.let(::compatBoardKey)
        return SharedBoardKeys(
            boardId = board.id,
            boardUrl = board.url,
            canonicalBoardUrl = canonical,
            compatBoardKey = compatKey,
            futaberStoredBoardId = compatKey ?: board.id,
            ftutterBoardKey = ftutterBoardKeyOf(board.url)
        )
    }

    fun thread(board: BoardSummary, threadId: String): SharedThreadKeys {
        val boardKeys = board(board)
        val threadUrl = runCatching { BoardUrlResolver.resolveThreadUrl(board.url, threadId) }.getOrDefault(board.url)
        val canonicalThreadUrl = boardKeys.canonicalBoardUrl?.let { "${it}res/$threadId.htm" }
        return SharedThreadKeys(
            board = boardKeys,
            threadId = threadId,
            threadUrl = threadUrl,
            canonicalThreadUrl = canonicalThreadUrl,
            compatTabKey = canonicalThreadUrl?.let(::compatTabKey),
            appStateHistoryKey = appStateKey(board.id, threadId),
            appStateSelfPostKey = appStateKey(board.id, threadId),
            futaberTabEntry = "${boardKeys.futaberStoredBoardId}$FUTABER_TAB_SEPARATOR$threadId",
            futaberReplyDraftKey = "r:${boardKeys.futaberStoredBoardId}:$threadId",
            ftutterThreadUrl = httpsOf(threadUrl),
            ftutterReplyDraftKey = "res:${boardKeys.ftutterBoardKey}res/$threadId.htm"
        )
    }

    /** ふたばー風's new-thread draft key of [board], `c:storedBoardId`. */
    fun futaberBuildDraftKey(board: BoardSummary): String = "c:${board(board).futaberStoredBoardId}"

    /** ふたったー風's new-thread draft key of [board], `board:host/path/`. */
    fun ftutterBuildDraftKey(board: BoardSummary): String = "board:${ftutterBoardKeyOf(board.url)}"

    /**
     * AppState's key of a thread: `boardId::threadId` (both trimmed), the thread number alone without a board id. The
     * history rows of an old version without a board id are keyed by their address instead (`historyEntryIdentity`).
     */
    fun appStateKey(boardId: String, threadId: String): String {
        val board = boardId.trim()
        val thread = threadId.trim()
        return if (board.isEmpty()) thread else "$board$APP_STATE_SEPARATOR$thread"
    }

    // ---- From a stored form back to the board and the thread ----

    /** The registered board whose compatibility key is [compatBoardKey]. */
    fun boardOfCompatKey(compatBoardKey: String, boards: List<BoardSummary>): BoardSummary? =
        boards.firstOrNull { board(it).compatBoardKey == compatBoardKey }

    /**
     * The registered board ふたばー風's stored id names: a board's own id first (an older value), then the shared key of an
     * official board (they cannot look alike: the shared keys start with `compat_board_`).
     */
    fun boardOfFutaberStoredId(storedId: String, boards: List<BoardSummary>): BoardSummary? =
        boards.firstOrNull { it.id == storedId } ?: boardOfCompatKey(storedId, boards)

    /** The board and thread of AppState's `boardId::threadId`, or null when the board is not registered. */
    fun threadOfAppStateKey(key: String, boards: List<BoardSummary>): SharedThreadKeys? {
        val separator = key.lastIndexOf(APP_STATE_SEPARATOR)
        if (separator <= 0) return null
        val boardId = key.substring(0, separator)
        val threadId = key.substring(separator + APP_STATE_SEPARATOR.length)
        if (threadId.isBlank()) return null
        val board = boards.firstOrNull { it.id == boardId } ?: return null
        return thread(board, threadId)
    }

    /** The board and thread of ふたばー風's tab entry `storedBoardId\nthreadId`. */
    fun threadOfFutaberTabEntry(entry: String, boards: List<BoardSummary>): SharedThreadKeys? {
        val parts = entry.split(FUTABER_TAB_SEPARATOR)
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) return null
        val board = boardOfFutaberStoredId(parts[0], boards) ?: return null
        return thread(board, parts[1])
    }

    /**
     * The registered board and the thread of a thread address in any of the modes' forms (http or https, a board address
     * with `futaba.php`, ふたったー風's stored address, the compatibility store's canonical one). An official board is
     * matched by its canonical address; another one by ふたったー風's `host/path/` form.
     */
    fun threadOfUrl(url: String, boards: List<BoardSummary>): SharedThreadKeys? {
        val canonical = canonicalizeThreadUrl(url)
        if (canonical != null) {
            val board = boards.firstOrNull { board(it).canonicalBoardUrl == canonical.canonicalBoardUrl } ?: return null
            return thread(board, canonical.threadNo)
        }
        if (!url.contains("/res/")) return null
        val threadId = url.substringAfterLast("/res/").substringBefore('.').takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: return null
        val boardKey = ftutterBoardKeyOf(url)
        val board = boards.firstOrNull { ftutterBoardKeyOf(it.url) == boardKey } ?: return null
        return thread(board, threadId)
    }

    /** The canonical thread address of [url] (any of the modes' forms), or null when it is not an official board's thread. */
    fun canonicalThreadUrlOf(url: String): String? = canonicalizeThreadUrl(url)?.canonicalUrl

    // ---- The forms themselves ----

    /** ふたったー風's `host/path/` form of a board or thread address, in lower case. */
    fun ftutterBoardKeyOf(url: String): String {
        val base = if (url.contains("/res/")) url.substringBefore("/res/") + "/" else boardBaseOf(url)
        return base.substringAfter("://").lowercase()
    }

    private fun boardBaseOf(boardUrl: String): String {
        val withoutQuery = boardUrl.substringBefore('?')
        return if (withoutQuery.endsWith("/")) withoutQuery else withoutQuery.substringBeforeLast('/') + "/"
    }

    private fun httpsOf(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("http://", ignoreCase = true)) "https://" + trimmed.substring(7) else trimmed
    }
}
