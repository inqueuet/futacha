package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.ThreadPageContent
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.ConditionalThreadFetchResult
import com.valoser.futacha.shared.ui.futaber.mht.FutaberMhtOpened

/**
 * Serves the page of an opened MHT file in place of the network: every way of asking for the thread
 * (by board and number, by address, conditionally) answers with the file's page, and nothing asks the
 * board. Posting, so/deleting and the rest still go to the real repository, as in a saved copy.
 */
internal class FutachaStaticThreadRepository(
    private val delegate: BoardRepository,
    private val page: ThreadPage
) : BoardRepository by delegate {
    override suspend fun getThread(board: String, threadId: String): ThreadPage = page
    override suspend fun getThreadContent(board: String, threadId: String): ThreadPageContent = ThreadPageContent(page = page)
    override suspend fun getThreadIfModified(
        board: String,
        threadId: String,
        validators: com.valoser.futacha.shared.network.HttpConditionalValidators?
    ): ConditionalThreadFetchResult = ConditionalThreadFetchResult.Modified(page, null)
    override suspend fun getThreadByUrl(threadUrl: String): ThreadPage = page
    override suspend fun getThreadContentByUrl(threadUrl: String): ThreadPageContent = ThreadPageContent(page = page)
    override suspend fun probeThreadExists(threadUrl: String): Boolean = true
    override suspend fun probeThreadGone(threadUrl: String): Boolean = false
}

/**
 * An opened MHT file shown in ふたちゃ's own thread screen. It is a read-only copy: the screen writes no
 * history, no tab, no shared snapshot and no auto-save, and does not go to the network for the thread.
 */
@Composable
internal fun FutachaMhtViewer(
    board: BoardSummary,
    opened: FutaberMhtOpened,
    screenContract: ScreenContract,
    dependencies: ThreadScreenDependencies,
    onBack: () -> Unit
) {
    val thread = opened.thread
    key(board.id, thread.threadId, thread.title) {
        CompositionLocalProvider(LocalFutachaStaticThreadView provides true) {
            ThreadScreen(
                board = board,
                screenContract = screenContract.copy(history = emptyList(), historyCallbacks = ScreenHistoryCallbacks()),
                threadId = thread.threadId,
                threadTitle = thread.title,
                initialReplyCount = opened.page.posts.size,
                onBack = onBack,
                dependencies = dependencies
            )
        }
    }
}
