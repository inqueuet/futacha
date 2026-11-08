package com.valoser.futacha.shared.compat.shared

import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.runBlocking
import java.nio.file.Files

/*
 * What the characterization tests of D-B0 (docs/futaber-evolution-data-2026-10-31.md §5) run on: the two stores each mode
 * writes (the compatibility store as the desktop and iOS keep it, in a temporary folder, and AppState in memory), three
 * boards and small thread pages. Each test uses one mode only and fixes what that mode stores and shows today, so that the
 * batches sharing the data can show a mode used alone is unchanged.
 */

internal object SingleMode {
    val may = BoardSummary("b", "may", "", "https://may.2chan.net/b/futaba.php", "")
    val img = BoardSummary("img", "img", "", "https://img.2chan.net/b/futaba.php", "")
    val tutorial = BoardSummary("t", "チュートリアル", "", "https://www.example.com/t/futaba.php", "")
    val boards = listOf(may, img, tutorial)

    fun threadUrl(board: BoardSummary, threadId: String): String =
        "${SharedKeys.board(board).canonicalBoardUrl}res/$threadId.htm"

    /** A tab of [board]'s thread as the compatibility store keeps it. */
    fun tab(board: BoardSummary, threadId: String, insertedAt: Long, replyCount: Int = 0, favorite: Boolean = false): CompatTab {
        val url = threadUrl(board, threadId)
        return CompatTab(
            compatTabKey(url), url, url, compatBoardKey(SharedKeys.board(board).canonicalBoardUrl!!), board.name, threadId,
            "スレ$threadId", replyCount = replyCount, checkedReplyCount = replyCount, favorite = favorite,
            insertedAtEpochMillis = insertedAt, contentUpdatedAtEpochMillis = insertedAt
        )
    }

    fun post(id: String, body: String, posterId: String? = null, author: String? = "としあき") = Post(
        id = id, author = author, subject = null, timestamp = "26/11/01(日)12:00:00", posterId = posterId,
        messageHtml = body, imageUrl = null, thumbnailUrl = null
    )

    fun page(threadId: String, posts: List<Post>) = ThreadPage(threadId, "may", null, null, posts)

    /** Runs [block] with an empty compatibility store holding [boards] (official ones) and an empty AppState. */
    fun withStores(
        boards: List<BoardSummary> = this.boards,
        block: suspend (compat: DesktopCompatibilityStore, state: AppStateStore) -> Unit
    ) = runBlocking<Unit> {
        val root = Files.createTempDirectory("futacha-single-mode").toFile()
        val state = AppStateStore(FakePlatformStateStorage())
        try {
            val compat = DesktopCompatibilityStore(JvmFileSystem(root))
            compat.initialize()
            compat.importModernBoards(boards.filter { SharedKeys.board(it).isOfficial })
            state.setBoards(boards)
            block(compat, state)
        } finally {
            state.close()
            root.deleteRecursively()
        }
    }
}
