package com.valoser.futacha.shared.repo

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.network.BoardApi
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.parser.HtmlParser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import com.valoser.futacha.shared.util.Logger

interface BoardRepository {
    suspend fun getCatalog(
        board: String,
        mode: CatalogMode = CatalogMode.default
    ): List<CatalogItem>
    suspend fun fetchOpImageUrl(board: String, threadId: String): String?
    suspend fun getThread(board: String, threadId: String): ThreadPage
    suspend fun voteSaidane(board: String, threadId: String, postId: String)
    suspend fun requestDeletion(board: String, threadId: String, postId: String, reasonCode: String)
    suspend fun deleteByUser(
        board: String,
        threadId: String,
        postId: String,
        password: String,
        imageOnly: Boolean
    )
    suspend fun replyToThread(
        board: String,
        threadId: String,
        name: String,
        email: String,
        subject: String,
        comment: String,
        password: String,
        imageFile: ByteArray?,
        imageFileName: String?,
        textOnly: Boolean
    )

    suspend fun createThread(
        board: String,
        name: String,
        email: String,
        subject: String,
        comment: String,
        password: String,
        imageFile: ByteArray?,
        imageFileName: String?,
        textOnly: Boolean
    ): String

    /**
     * Close the repository and release resources (e.g., HTTP client connections)
     */
    fun close()
}

class DefaultBoardRepository(
    private val api: BoardApi,
    private val parser: HtmlParser
) : BoardRepository {
    // Track which boards have been initialized with cookies
    private val initializedBoards = mutableSetOf<String>()
    // Fix: Use Mutex to prevent race condition when multiple coroutines
    // try to initialize the same board simultaneously
    private val boardInitMutex = Mutex()

    companion object {
        private const val TAG = "DefaultBoardRepository"
        private const val OP_IMAGE_LINE_LIMIT = 65
        private const val OP_IMAGE_CONCURRENCY = 4
    }

    private val opImageSemaphore = Semaphore(OP_IMAGE_CONCURRENCY)

    /**
     * Ensures cookies are initialized for the given board.
     * This should be called before any operations that require cookies.
     */
    private suspend fun ensureCookiesInitialized(board: String) {
        // Fix: Wrap the check-then-act in a mutex to prevent race conditions
        boardInitMutex.withLock {
            if (!initializedBoards.contains(board)) {
                try {
                    api.fetchCatalogSetup(board)
                    initializedBoards.add(board)
                } catch (e: Exception) {
                    Logger.e(TAG, "Failed to initialize cookies for board $board", e)
                    // Continue anyway - the operation might still work
                }
            }
        }
    }

    override suspend fun getCatalog(
        board: String,
        mode: CatalogMode
    ): List<CatalogItem> {
        ensureCookiesInitialized(board)
        val html = api.fetchCatalog(board, mode)
        val baseUrl = BoardUrlResolver.resolveBoardBaseUrl(board)
        return parser.parseCatalog(html, baseUrl)
    }

    override suspend fun fetchOpImageUrl(board: String, threadId: String): String? {
        ensureCookiesInitialized(board)
        if (threadId.isBlank()) return null
        return opImageSemaphore.withPermit {
            resolveOpImageUrl(board, threadId)
        }
    }

    override suspend fun getThread(board: String, threadId: String): ThreadPage {
        ensureCookiesInitialized(board)
        val html = api.fetchThread(board, threadId)
        return parser.parseThread(html)
    }

    override suspend fun voteSaidane(board: String, threadId: String, postId: String) {
        ensureCookiesInitialized(board)
        api.voteSaidane(board, threadId, postId)
    }

    override suspend fun requestDeletion(board: String, threadId: String, postId: String, reasonCode: String) {
        ensureCookiesInitialized(board)
        api.requestDeletion(board, threadId, postId, reasonCode)
    }

    override suspend fun deleteByUser(
        board: String,
        threadId: String,
        postId: String,
        password: String,
        imageOnly: Boolean
    ) {
        ensureCookiesInitialized(board)
        api.deleteByUser(board, threadId, postId, password, imageOnly)
    }

    override suspend fun replyToThread(
        board: String,
        threadId: String,
        name: String,
        email: String,
        subject: String,
        comment: String,
        password: String,
        imageFile: ByteArray?,
        imageFileName: String?,
        textOnly: Boolean
    ) {
        ensureCookiesInitialized(board)
        api.replyToThread(board, threadId, name, email, subject, comment, password, imageFile, imageFileName, textOnly)
    }

    override suspend fun createThread(
        board: String,
        name: String,
        email: String,
        subject: String,
        comment: String,
        password: String,
        imageFile: ByteArray?,
        imageFileName: String?,
        textOnly: Boolean
    ): String {
        ensureCookiesInitialized(board)
        return api.createThread(board, name, email, subject, comment, password, imageFile, imageFileName, textOnly)
    }

    override fun close() {
        // Close the underlying API if it supports cleanup
        (api as? AutoCloseable)?.close()
    }

    private suspend fun resolveOpImageUrl(
        board: String,
        threadId: String
    ): String? {
        val baseUrl = BoardUrlResolver.resolveBoardBaseUrl(board)
        return runCatching {
            val snippet = api.fetchThreadHead(board, threadId, OP_IMAGE_LINE_LIMIT)
            parser.extractOpImageUrl(snippet, baseUrl)
        }.getOrElse { e ->
            Logger.w(TAG, "Failed to resolve OP image for thread $threadId: ${e.message}")
            null
        }
    }
}
