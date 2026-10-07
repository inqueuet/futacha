package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.ThreadPageContent
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.ui.board.formatLastVisited
import com.valoser.futacha.shared.ui.board.formatSize

/** What the saved-thread (保存箱) list shows: the rows, or why there are none. */
internal data class FutaberSavedBoxState(
    val threads: List<SavedThread> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

/**
 * A saved thread opened for reading. It is shown by the thread screen, but nothing about the
 * visit is written to the history, tabs or reading position: the copy may be older than the
 * thread, or the thread may be gone.
 */
internal data class FutaberOfflineView(
    val board: BoardSummary,
    val ref: FutaberThreadRef,
    val page: ThreadPage
)

/**
 * Serves one saved page in place of the network for that thread. Everything else (posting,
 * deleting, catalog) still goes to the real repository, so a reply from a saved copy is a
 * reply to the live thread.
 */
internal class FutaberOfflineRepository(
    private val delegate: BoardRepository,
    private val threadId: String,
    private val page: ThreadPage
) : BoardRepository by delegate {
    override suspend fun getThread(board: String, threadId: String): ThreadPage =
        if (threadId == this.threadId) page else delegate.getThread(board, threadId)

    override suspend fun getThreadContent(board: String, threadId: String): ThreadPageContent =
        if (threadId == this.threadId) ThreadPageContent(page = page) else delegate.getThreadContent(board, threadId)
}

/**
 * The board a saved thread belongs to. The shared page save stores the compatibility board
 * key as the board id; saves made elsewhere may store this app's board id, so both match.
 * Null when that board is not registered.
 */
internal fun futaberBoardForSaved(thread: SavedThread, boards: List<BoardSummary>): BoardSummary? =
    boards.firstOrNull { it.id == thread.boardId }
        ?: boards.firstOrNull { futaberCompatBoardKey(it) == thread.boardId }

internal fun futaberRefForSaved(thread: SavedThread, board: BoardSummary): FutaberThreadRef =
    FutaberThreadRef(board.id, thread.threadId, thread.title.ifBlank { "(無題)" }, "", thread.postCount)

/** Left of the second line of a row: the board, and what is missing from the copy or the board. */
internal fun futaberSavedBoardLine(thread: SavedThread, openable: Boolean): String =
    thread.boardName +
        (if (thread.isHtmlMissing || thread.incompleteMediaCount > 0) "（一部未保存）" else "") +
        (if (openable) "" else "（板が未登録）")

/** Right of the second line of a row: when it was saved and how big it is. */
internal fun futaberSavedDetail(thread: SavedThread): String =
    "${formatLastVisited(thread.savedAt)}  ${formatSize(thread.totalSize)}"

/** Newest save first. */
internal fun futaberSavedOrder(threads: List<SavedThread>): List<SavedThread> =
    threads.sortedByDescending { it.savedAt }
