package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.isSiteThreadId
import com.valoser.futacha.shared.compat.siteCanonicalBoardUrlOf
import com.valoser.futacha.shared.compat.siteThreadUrlOnBoard
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.model.BoardSummary

/**
 * The compatibility store identifies a board and a thread by a hash of the canonical URL
 * (`https://host/b/` and `https://host/b/res/123.htm`), the same way the other modes do.
 * Rules and settings are shared through these keys, so a key built from the raw board
 * address (`.../futaba.php`) would match nothing the other modes saved.
 */
internal fun futaberCanonicalBoardUrl(board: BoardSummary): String =
    siteCanonicalBoardUrlOf(board.url) ?: board.url

internal fun futaberCompatBoardKey(board: BoardSummary): String =
    compatBoardKey(futaberCanonicalBoardUrl(board))

/** Address of the thread in the canonical form; for a board that is not an official one, its own resolved address. */
internal fun futaberCompatThreadUrl(board: BoardSummary, threadId: String): String =
    siteCanonicalBoardUrlOf(board.url)?.takeIf { isSiteThreadId(it, threadId) }?.let { siteThreadUrlOnBoard(it, threadId) }
        ?: futaberHistoryThreadUrl(board, threadId)

internal fun futaberCompatTabKey(board: BoardSummary, threadId: String): String =
    compatTabKey(futaberCompatThreadUrl(board, threadId))


/**
 * The board key the shared NG rules of [board] are kept under, or null for a board the shared store has no place for
 * (an address that is not an official board, such as the bundled tutorial board). Rules are kept per board, and a board
 * is in the shared store only when it is an official one.
 */
internal fun futaberNgScopeKey(board: BoardSummary): String? =
    siteCanonicalBoardUrlOf(board.url)?.let(::compatBoardKey)
