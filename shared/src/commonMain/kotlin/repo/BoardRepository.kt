package com.valoser.futacha.shared.repo

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.CatalogPageContent
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.ThreadPageContent
import com.valoser.futacha.shared.network.BoardApi
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.parser.HtmlParser
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

interface BoardRepository {
    suspend fun getCatalog(
        board: String,
        mode: CatalogMode = CatalogMode.default
    ): List<CatalogItem>
    suspend fun getCatalogPage(
        board: String,
        mode: CatalogMode = CatalogMode.default
    ): CatalogPageContent = CatalogPageContent(
        items = getCatalog(board, mode)
    )
    suspend fun fetchOpImageUrl(board: String, threadId: String): String?
    suspend fun getThread(board: String, threadId: String): ThreadPage
    suspend fun getThreadContent(board: String, threadId: String): ThreadPageContent = ThreadPageContent(
        page = getThread(board, threadId)
    )
    suspend fun getThreadByUrl(threadUrl: String): ThreadPage
    suspend fun getThreadContentByUrl(threadUrl: String): ThreadPageContent = ThreadPageContent(
        page = getThreadByUrl(threadUrl)
    )
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
    ): String?

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
    ): String?

    /**
     * Close the repository and release resources (e.g., HTTP client connections)
     */
    fun close()

    /**
     * Close the repository asynchronously and return a Job to wait for completion if needed
     */
    fun closeAsync(): Job

    suspend fun clearOpImageCache(board: String? = null, threadId: String? = null)

    /**
     * Invalidate cookie initialization state for a board to force re-setup on next request.
     */
    suspend fun invalidateCookies(board: String)
}

class DefaultBoardRepository(
    private val api: BoardApi,
    private val parser: HtmlParser,
    private val opImageCacheTtlMillis: Long = DEFAULT_OP_IMAGE_CACHE_TTL_MILLIS,
    private val opImageCacheMaxEntries: Int = DEFAULT_OP_IMAGE_CACHE_MAX_ENTRIES,
    private val cookieRepository: CookieRepository? = null
) : BoardRepository {
    // Track which boards have been initialized with cookies.
    // Access only under boardInitMutex.withLock.
    private val initializedBoards = mutableSetOf<String>()
    // Fix: Use Mutex to prevent race condition when multiple coroutines
    // try to initialize the same board simultaneously
    private val boardInitMutex = Mutex()

    private val opImageCacheMutex = Mutex()
    private val opImageCache = createDefaultBoardRepositoryOpImageCache(opImageCacheMaxEntries)

    // Scope used by async close.
    private val closeScope = CoroutineScope(SupervisorJob() + AppDispatchers.io)

    // Protect close state and prevent duplicate close.
    private val closeMutex = Mutex()
    private val closeState = DefaultBoardRepositoryCloseState()

    companion object {
        private const val TAG = "DefaultBoardRepository"
        private const val OP_IMAGE_LINE_LIMIT = 65
        private const val OP_IMAGE_CONCURRENCY = 4
        private const val DEFAULT_OP_IMAGE_CACHE_TTL_MILLIS = 15 * 60 * 1000L // 15 minutes
        private const val DEFAULT_OP_IMAGE_MISS_CACHE_TTL_MILLIS = 30_000L // 30 seconds
        private const val DEFAULT_OP_IMAGE_CACHE_MAX_ENTRIES = 512
        // Timeout for semaphore acquisition to avoid permanent wait.
        private const val SEMAPHORE_TIMEOUT_MILLIS = 30_000L // 30 seconds
    }

    private val opImageSemaphore = Semaphore(OP_IMAGE_CONCURRENCY)

    /**
     * Ensures cookies are initialized for the given board.
     * This should be called before any operations that require cookies.
     */
    private suspend fun ensureCookiesInitialized(board: String) {
        initializeDefaultBoardRepositoryCookies(
            board = board,
            logTag = TAG,
            initializedBoards = initializedBoards,
            cookieRepository = cookieRepository,
            boardInitMutex = boardInitMutex,
            fetchCatalogSetup = { api.fetchCatalogSetup(it) }
        )
    }

    override suspend fun invalidateCookies(board: String) {
        boardInitMutex.withLock {
            initializedBoards.remove(board)
        }
    }

    private suspend fun <T> withRetryOnAuthFailure(board: String, block: suspend () -> T): T {
        return withDefaultBoardRepositoryAuthRetry(
            board = board,
            logTag = TAG,
            ensureCookiesInitialized = ::ensureCookiesInitialized,
            invalidateCookies = ::invalidateCookies,
            block = block
        )
    }

    private suspend fun runWithInitializedCookies(
        board: String,
        block: suspend () -> Unit
    ) {
        runDefaultBoardRepositoryWithInitializedCookies(
            board = board,
            cookieRepository = cookieRepository,
            ensureCookiesInitialized = ::ensureCookiesInitialized,
            block = block
        )
    }

    override suspend fun getCatalog(
        board: String,
        mode: CatalogMode
    ): List<CatalogItem> {
        return getCatalogPage(board, mode).items
    }

    override suspend fun getCatalogPage(
        board: String,
        mode: CatalogMode
    ): CatalogPageContent {
        return withRetryOnAuthFailure(board) {
            val html = withContext(AppDispatchers.io) {
                api.fetchCatalog(board, mode)
            }
            val baseUrl = BoardUrlResolver.resolveBoardBaseUrl(board)
            withContext(AppDispatchers.parsing) {
                CatalogPageContent(
                    items = parser.parseCatalog(html, baseUrl),
                    embeddedHtml = parser.extractCatalogEmbeddedHtml(html, baseUrl)
                )
            }
        }
    }

    override suspend fun fetchOpImageUrl(board: String, threadId: String): String? {
        if (threadId.isBlank()) return null
        val key = DefaultBoardRepositoryOpImageKey(board, threadId)
        getCachedOpImageUrl(key)?.let { return it.url }

        val fetchResult = fetchDefaultBoardRepositoryOpImageWithPermit(
            threadId = threadId,
            semaphoreTimeoutMillis = SEMAPHORE_TIMEOUT_MILLIS,
            semaphore = opImageSemaphore,
            logTag = TAG
        ) {
            withRetryOnAuthFailure(board) {
                resolveOpImageUrl(board, threadId)
            }
        }
        if (fetchResult == null) {
            return null
        }
        saveOpImageUrlToCache(key, fetchResult.url)
        return fetchResult.url
    }

    override suspend fun getThread(board: String, threadId: String): ThreadPage {
        return getThreadContent(board, threadId).page
    }

    override suspend fun getThreadContent(board: String, threadId: String): ThreadPageContent {
        return withRetryOnAuthFailure(board) {
            val html = withContext(AppDispatchers.io) {
                api.fetchThread(board, threadId)
            }
            withContext(AppDispatchers.parsing) {
                ThreadPageContent(
                    page = parser.parseThread(html),
                    embeddedHtml = parser.extractThreadEmbeddedHtml(
                        html = html,
                        baseUrl = BoardUrlResolver.resolveThreadUrl(board, threadId)
                    )
                )
            }
        }
    }

    override suspend fun getThreadByUrl(threadUrl: String): ThreadPage {
        return getThreadContentByUrl(threadUrl).page
    }

    override suspend fun getThreadContentByUrl(threadUrl: String): ThreadPageContent {
        val html = withContext(AppDispatchers.io) {
            api.fetchThreadByUrl(threadUrl)
        }
        return withContext(AppDispatchers.parsing) {
            ThreadPageContent(
                page = parser.parseThread(html),
                embeddedHtml = parser.extractThreadEmbeddedHtml(html, threadUrl)
            )
        }
    }

    override suspend fun voteSaidane(board: String, threadId: String, postId: String) {
        runWithInitializedCookies(board) {
            api.voteSaidane(board, threadId, postId)
        }
    }

    override suspend fun requestDeletion(board: String, threadId: String, postId: String, reasonCode: String) {
        runWithInitializedCookies(board) {
            api.requestDeletion(board, threadId, postId, reasonCode)
        }
    }

    override suspend fun deleteByUser(
        board: String,
        threadId: String,
        postId: String,
        password: String,
        imageOnly: Boolean
    ) {
        runWithInitializedCookies(board) {
            api.deleteByUser(board, threadId, postId, password, imageOnly)
        }
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
    ): String? {
        // Post operations are sensitive, maybe don't auto-retry if side effects occurred?
        // For now, we'll use standard init check but not full retry loop to avoid double-posting risk
        return runDefaultBoardRepositoryWithInitializedCookies(
            board = board,
            cookieRepository = cookieRepository,
            ensureCookiesInitialized = ::ensureCookiesInitialized
        ) {
            api.replyToThread(board, threadId, name, email, subject, comment, password, imageFile, imageFileName, textOnly)
        }
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
    ): String? {
        return runDefaultBoardRepositoryWithInitializedCookies(
            board = board,
            cookieRepository = cookieRepository,
            ensureCookiesInitialized = ::ensureCookiesInitialized
        ) {
            api.createThread(board, name, email, subject, comment, password, imageFile, imageFileName, textOnly)
        }
    }

    // Keep synchronous close lightweight and safe from main thread.
    @Deprecated(
        message = "Use closeAsync() instead for proper async cleanup",
        replaceWith = ReplaceWith("closeAsync()"),
        level = DeprecationLevel.WARNING
    )
    override fun close() {
        // Keep close() non-blocking for callers on UI thread.
        closeAsync()
    }

    // Async close for callers that need to await cleanup.
    override fun closeAsync(): Job {
        return closeScope.launch {
            val shouldClose = beginDefaultBoardRepositoryClose(
                closeMutex = closeMutex,
                closeState = closeState
            )
            if (!shouldClose) return@launch
            try {
                (api as? AutoCloseable)?.close()
            } catch (e: Exception) {
                Logger.e("DefaultBoardRepository", "Error closing API asynchronously: ${e.message}", e)
            } finally {
                opImageCacheMutex.withLock {
                    opImageCache.clear()
                }
                closeScope.cancel()
            }
        }
    }

    override suspend fun clearOpImageCache(board: String?, threadId: String?) {
        clearDefaultBoardRepositoryOpImageCache(
            cacheMutex = opImageCacheMutex,
            cache = opImageCache,
            board = board,
            threadId = threadId
        )
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun getCachedOpImageUrl(key: DefaultBoardRepositoryOpImageKey): DefaultBoardRepositoryOpImageCacheEntry? {
        val now = Clock.System.now().toEpochMilliseconds()
        return opImageCacheMutex.withLock {
            resolveDefaultBoardRepositoryCachedOpImageUrl(
                cache = opImageCache,
                key = key,
                now = now
            )
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun saveOpImageUrlToCache(key: DefaultBoardRepositoryOpImageKey, url: String?) {
        val now = Clock.System.now().toEpochMilliseconds()
        opImageCacheMutex.withLock {
            saveDefaultBoardRepositoryOpImageUrlToCache(
                cache = opImageCache,
                key = key,
                url = url,
                now = now,
                hitTtlMillis = opImageCacheTtlMillis,
                missTtlMillis = DEFAULT_OP_IMAGE_MISS_CACHE_TTL_MILLIS
            )
        }
    }

    private suspend fun resolveOpImageUrl(
        board: String,
        threadId: String
    ): String? {
        val baseUrl = BoardUrlResolver.resolveBoardBaseUrl(board)
        return resolveDefaultBoardRepositoryOpImageUrl(
            threadId = threadId,
            logTag = TAG,
            fetchThreadHead = {
                withContext(AppDispatchers.io) {
                    api.fetchThreadHead(board, threadId, OP_IMAGE_LINE_LIMIT)
                }
            },
            extractOpImageUrl = { snippet ->
                withContext(AppDispatchers.parsing) {
                    parser.extractOpImageUrl(snippet, baseUrl)
                }
            }
        )
    }
}
