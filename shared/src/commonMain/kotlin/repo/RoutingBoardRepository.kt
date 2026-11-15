package com.valoser.futacha.shared.repo

import com.valoser.futacha.shared.compat.BoardSite
import com.valoser.futacha.shared.compat.boardSiteOf
import com.valoser.futacha.shared.network.BoardPostingCapabilities
import com.valoser.futacha.shared.model.CatalogFetchSettings
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.CatalogPageContent
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.ThreadPageContent
import com.valoser.futacha.shared.network.HttpConditionalValidators
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Sends each call to [futaba] or [aimoge] by the board or thread address ([boardSiteOf]; while あいもげ is off every
 * call goes to [futaba], byte for byte as before). Every method is passed explicitly, so a new default method of
 * [BoardRepository] is never silently taken from one side only.
 */
class RoutingBoardRepository(
    private val futaba: BoardRepository,
    private val aimoge: BoardRepository
) : BoardRepository {
    private fun of(url: String): BoardRepository = if (boardSiteOf(url) == BoardSite.AIMOGE) aimoge else futaba

    /** The Futaba layout: あいもげ has no cxyl. */
    override suspend fun getCatalogFetchSettings(): CatalogFetchSettings = futaba.getCatalogFetchSettings()

    override suspend fun getPostingCapabilities(board: String, threadId: String?): BoardPostingCapabilities =
        of(board).getPostingCapabilities(board, threadId)

    override suspend fun getCatalog(board: String, mode: CatalogMode): List<CatalogItem> = of(board).getCatalog(board, mode)

    override suspend fun getCatalogWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings): List<CatalogItem> =
        of(board).getCatalogWithSettings(board, mode, settings)

    override suspend fun getCatalogPageWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings): CatalogPageContent =
        of(board).getCatalogPageWithSettings(board, mode, settings)

    override suspend fun getCatalogPage(board: String, mode: CatalogMode): CatalogPageContent = of(board).getCatalogPage(board, mode)

    override suspend fun fetchOpImageUrl(board: String, threadId: String): String? = of(board).fetchOpImageUrl(board, threadId)

    override suspend fun resolveCatalogDisplayTitle(board: String, item: CatalogItem, allowFallbackHeadScan: Boolean): String? =
        of(board).resolveCatalogDisplayTitle(board, item, allowFallbackHeadScan)

    override suspend fun getThread(board: String, threadId: String): ThreadPage = of(board).getThread(board, threadId)

    override suspend fun getThreadContent(board: String, threadId: String): ThreadPageContent =
        of(board).getThreadContent(board, threadId)

    override suspend fun getThreadIfModified(
        board: String,
        threadId: String,
        validators: HttpConditionalValidators?
    ): ConditionalThreadFetchResult = of(board).getThreadIfModified(board, threadId, validators)

    override suspend fun getThreadByUrl(threadUrl: String): ThreadPage = of(threadUrl).getThreadByUrl(threadUrl)

    override suspend fun getThreadContentByUrl(threadUrl: String): ThreadPageContent = of(threadUrl).getThreadContentByUrl(threadUrl)

    override suspend fun getArchivedThreadContent(board: String, threadId: String): ThreadPageContent? =
        of(board).getArchivedThreadContent(board, threadId)

    override suspend fun probeThreadExists(threadUrl: String): Boolean = of(threadUrl).probeThreadExists(threadUrl)

    override suspend fun probeThreadGone(threadUrl: String): Boolean = of(threadUrl).probeThreadGone(threadUrl)

    override suspend fun getThreadSummaries(board: String, threadIds: List<String>): List<SiteThreadSummary>? =
        of(board).getThreadSummaries(board, threadIds)

    override suspend fun getThreadDelta(board: String, threadId: String): ThreadPage? = of(board).getThreadDelta(board, threadId)

    override suspend fun voteSaidane(board: String, threadId: String, postId: String) = of(board).voteSaidane(board, threadId, postId)

    override suspend fun voteSaidaneResponse(board: String, threadId: String, postId: String): String =
        of(board).voteSaidaneResponse(board, threadId, postId)

    override suspend fun requestDeletion(board: String, threadId: String, postId: String, reasonCode: String) =
        of(board).requestDeletion(board, threadId, postId, reasonCode)

    override suspend fun deleteByUser(board: String, threadId: String, postId: String, password: String, imageOnly: Boolean) =
        of(board).deleteByUser(board, threadId, postId, password, imageOnly)

    override suspend fun requestDeletionResponse(board: String, threadId: String, postId: String, reasonCode: String): String =
        of(board).requestDeletionResponse(board, threadId, postId, reasonCode)

    override suspend fun deleteByUserResponse(
        board: String,
        threadId: String,
        postId: String,
        password: String,
        imageOnly: Boolean
    ): String = of(board).deleteByUserResponse(board, threadId, postId, password, imageOnly)

    override suspend fun replyToThread(
        board: String, threadId: String, name: String, email: String, subject: String, comment: String, password: String,
        imageFile: ByteArray?, imageFileName: String?, textOnly: Boolean, handwriting: Boolean
    ): String? = of(board).replyToThread(
        board, threadId, name, email, subject, comment, password, imageFile, imageFileName, textOnly, handwriting
    )

    override suspend fun createThread(
        board: String, name: String, email: String, subject: String, comment: String, password: String,
        imageFile: ByteArray?, imageFileName: String?, textOnly: Boolean, handwriting: Boolean
    ): String? = of(board).createThread(board, name, email, subject, comment, password, imageFile, imageFileName, textOnly, handwriting)

    override fun close() {
        try {
            futaba.close()
        } finally {
            aimoge.close()
        }
    }

    override fun closeAsync(): Job {
        val jobs = listOf(futaba.closeAsync(), aimoge.closeAsync())
        return CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { jobs.joinAll() }
    }

    override suspend fun clearOpImageCache(board: String?, threadId: String?) {
        if (board == null) {
            futaba.clearOpImageCache(null, threadId)
            aimoge.clearOpImageCache(null, threadId)
        } else {
            of(board).clearOpImageCache(board, threadId)
        }
    }

    override suspend fun invalidateCookies(board: String) = of(board).invalidateCookies(board)
}
