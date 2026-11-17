package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.canonicalizeBoardUrl
import com.valoser.futacha.shared.model.BoardSummary

/*
 * D-B8: the person's own post numbers are kept in two places: AppState (`"boardId::threadId"`, read by ふたちゃ, ふたばー風
 * and ふたったー風) and, for としあき(仮), the preferences `compat.ownpost.<tabKey>.<no>`. としあき(仮) now shows both and
 * hands its own to AppState, so a post written in one mode is marked in all of them.
 */

/** The AppState board [boardUrl] is, or null when AppState does not hold it. */
internal fun compatSharedSelfPostBoard(boards: List<BoardSummary>, boardUrl: String): BoardSummary? {
    val canonical = canonicalizeBoardUrl(boardUrl) ?: return null
    return boards.firstOrNull { canonicalizeBoardUrl(it.url) == canonical }
}

/** The own post numbers AppState holds for [threadNo] of [boardUrl]. */
internal fun compatSharedSelfPostNos(
    boards: List<BoardSummary>,
    selfPosts: Map<String, List<String>>,
    boardUrl: String,
    threadNo: String
): Set<String> {
    val board = compatSharedSelfPostBoard(boards, boardUrl) ?: return emptySet()
    return selfPosts["${board.id}::$threadNo"].orEmpty().toSet()
}

/** The numbers of [own] AppState lacks, to hand to it (sorted for a steady order). */
internal fun compatSelfPostNosToShare(own: Set<String>, shared: Set<String>): List<String> =
    (own - shared).sortedWith(compareBy<String> { it.toLongOrNull() ?: Long.MAX_VALUE }.thenBy { it })
