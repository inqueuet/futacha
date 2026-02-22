package com.valoser.futacha.shared.repo

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.ThreadPage
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
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

interface BoardRepository {
    suspend fun getCatalog(
        board: String,
        mode: CatalogMode = CatalogMode.default
    ): List<CatalogItem>
    suspend fun fetchOpImageUrl(board: String, threadId: String): String?
    suspend fun getThread(board: String, threadId: String): ThreadPage
    suspend fun getThreadByUrl(threadUrl: String): ThreadPage
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
    private val opImageCache = createOpImageCache()

    // Scope used by async close.
    private val closeScope = CoroutineScope(SupervisorJob() + AppDispatchers.io)

    // Protect close state and prevent duplicate close.
    private val closeMutex = Mutex()
    private var isClosed = false

    companion object {
        private const val TAG = "DefaultBoardRepository"
        private const val OP_IMAGE_LINE_LIMIT = 65
        private const val OP_IMAGE_CONCURRENCY = 4
        private const val DEFAULT_OP_IMAGE_CACHE_TTL_MILLIS = 15 * 60 * 1000L // 15 minutes
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
        var shouldInitialize = false
        boardInitMutex.withLock {
            if (!initializedBoards.contains(board)) {
                val hasExistingCookies = cookieRepository?.let { repository ->
                    // Prefer known futaba cookies, but fall back to any matching cookie for the host/path.
                    repository.hasValidCookieFor(board, preferredNames = setOf("posttime", "cxyl")) ||
                        repository.hasValidCookieFor(board)
                } ?: false
                if (hasExistingCookies) {
                    initializedBoards.add(board)
                    Logger.d(TAG, "Skipping catalog setup for board $board (existing cookies found)")
                } else {
                    shouldInitialize = true
                }
            }
        }

        if (!shouldInitialize) return

        var fetchedSetup = false
        try {
            api.fetchCatalogSetup(board)
            fetchedSetup = true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to initialize cookies for board $board", e)
            // Continue anyway - the operation might still work
        }

        val hasCookies = cookieRepository?.let { repository ->
            repository.hasValidCookieFor(board, preferredNames = setOf("posttime", "cxyl")) ||
                repository.hasValidCookieFor(board)
        } ?: false

        if (fetchedSetup || hasCookies) {
            boardInitMutex.withLock {
                initializedBoards.add(board)
            }
        } else {
            Logger.w(TAG, "Cookie initialization incomplete for board $board; will retry on next request")
        }
    }

    override suspend fun invalidateCookies(board: String) {
        boardInitMutex.withLock {
            initializedBoards.remove(board)
        }
    }

    private suspend fun <T> withRetryOnAuthFailure(board: String, block: suspend () -> T): T {
        try {
            ensureCookiesInitialized(board)
            return block()
        } catch (e: Exception) {
            // If it looks like an auth/cookie issue (e.g. 403 or redirect loop), retry once
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (!isLikelyCookieAuthFailure(e)) throw e

            Logger.w(TAG, "Operation failed for board $board, retrying with fresh cookies: ${e.message}")
            invalidateCookies(board)
            ensureCookiesInitialized(board)
            return block()
        }
    }

    private fun isLikelyCookieAuthFailure(error: Exception): Boolean {
        val statusCode = (error as? NetworkException)?.statusCode
        if (statusCode == 401 || statusCode == 403) return true
        val message = error.message?.lowercase().orEmpty()
        return message.contains("cookie") ||
            message.contains("forbidden") ||
            message.contains("auth") ||
            message.contains("認証")
    }

    private suspend fun runWithInitializedCookies(
        board: String,
        block: suspend () -> Unit
    ) {
        ensureCookiesInitialized(board)
        val exec: suspend () -> Unit = { block() }
        cookieRepository?.commitOnSuccess { exec() } ?: exec()
    }

    override suspend fun getCatalog(
        board: String,
        mode: CatalogMode
    ): List<CatalogItem> {
        return withRetryOnAuthFailure(board) {
            val html = api.fetchCatalog(board, mode)
            val baseUrl = BoardUrlResolver.resolveBoardBaseUrl(board)
            withContext(AppDispatchers.parsing) {
                parser.parseCatalog(html, baseUrl)
            }
        }
    }

    override suspend fun fetchOpImageUrl(board: String, threadId: String): String? {
        if (threadId.isBlank()) return null
        val key = OpImageKey(board, threadId)
        getCachedOpImageUrl(key)?.let { return it }

        // Add timeout to avoid waiting forever for semaphore permit.
        val resolvedUrl = try {
            withTimeout(SEMAPHORE_TIMEOUT_MILLIS) {
                opImageSemaphore.withPermit {
                    withRetryOnAuthFailure(board) {
                        resolveOpImageUrl(board, threadId)
                    }
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Logger.w(TAG, "Timeout waiting for image fetch permit for thread $threadId")
            null
        }
        saveOpImageUrlToCache(key, resolvedUrl)
        return resolvedUrl
    }

    override suspend fun getThread(board: String, threadId: String): ThreadPage {
        return withRetryOnAuthFailure(board) {
            val html = api.fetchThread(board, threadId)
            withContext(AppDispatchers.parsing) {
                parser.parseThread(html)
            }
        }
    }

    override suspend fun getThreadByUrl(threadUrl: String): ThreadPage {
        val html = api.fetchThreadByUrl(threadUrl)
        return withContext(AppDispatchers.parsing) {
            parser.parseThread(html)
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
        ensureCookiesInitialized(board)
        val exec: suspend () -> String? = {
            api.replyToThread(board, threadId, name, email, subject, comment, password, imageFile, imageFileName, textOnly)
        }
        return cookieRepository?.commitOnSuccess { exec() } ?: exec()
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
        ensureCookiesInitialized(board)
        val exec: suspend () -> String? = {
            api.createThread(board, name, email, subject, comment, password, imageFile, imageFileName, textOnly)
        }
        return cookieRepository?.commitOnSuccess { exec() } ?: exec()
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
            val shouldClose = closeMutex.withLock {
                if (isClosed) {
                    false
                } else {
                    isClosed = true
                    true
                }
            }
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
        opImageCacheMutex.withLock {
            if (board == null && threadId == null) {
                opImageCache.clear()
                return
            }
            opImageCache.removeIf { key, _ ->
                val boardMatches = board == null || key.board == board
                val threadMatches = threadId == null || key.threadId == threadId
                boardMatches && threadMatches
            }
        }
    }

    private data class OpImageKey(val board: String, val threadId: String)

    private data class OpImageCacheEntry(val url: String?, val recordedAtMillis: Long)

    // Simple access-order LRU cache. Not thread-safe by itself.
    // All access must be guarded by opImageCacheMutex.
    private class LruCache<K, V>(private val maxEntries: Int) {
        private val cache = LinkedHashMap<K, V>()

        operator fun get(key: K): V? {
            val value = cache[key] ?: return null
            // Emulate access-order in commonMain by moving key to the end.
            cache.remove(key)
            cache[key] = value
            return value
        }

        operator fun set(key: K, value: V) {
            // Keep insertion order stable by re-inserting existing keys.
            cache.remove(key)
            cache[key] = value
            while (cache.size > maxEntries) {
                val eldestKey = cache.entries.firstOrNull()?.key ?: break
                cache.remove(eldestKey)
            }
        }

        fun remove(key: K): V? = cache.remove(key)

        fun clear() {
            cache.clear()
        }

        fun removeIf(predicate: (K, V) -> Boolean) {
            val iterator = cache.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (predicate(entry.key, entry.value)) {
                    iterator.remove()
                }
            }
        }

        fun forEach(action: (K, V) -> Unit) {
            cache.forEach { (key, value) -> action(key, value) }
        }

        val size: Int
            get() = cache.size
    }

    private fun createOpImageCache() = LruCache<OpImageKey, OpImageCacheEntry>(opImageCacheMaxEntries)

    @OptIn(ExperimentalTime::class)
    private suspend fun getCachedOpImageUrl(key: OpImageKey): String? {
        val now = Clock.System.now().toEpochMilliseconds()
        return opImageCacheMutex.withLock {
            val entry = opImageCache[key]
            if (entry == null) return@withLock null
            if (now - entry.recordedAtMillis <= opImageCacheTtlMillis) {
                return@withLock entry.url
            }
            opImageCache.remove(key)
            return@withLock null
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun saveOpImageUrlToCache(key: OpImageKey, url: String?) {
        val now = Clock.System.now().toEpochMilliseconds()
        opImageCacheMutex.withLock {
            opImageCache[key] = OpImageCacheEntry(url, now)
            purgeExpiredEntriesLocked(now)
        }
    }

    private fun purgeExpiredEntriesLocked(now: Long) {
        opImageCache.removeIf { _, entry ->
            now - entry.recordedAtMillis > opImageCacheTtlMillis
        }
    }

    private suspend fun resolveOpImageUrl(
        board: String,
        threadId: String
    ): String? {
        val baseUrl = BoardUrlResolver.resolveBoardBaseUrl(board)
        return try {
            val snippet = api.fetchThreadHead(board, threadId, OP_IMAGE_LINE_LIMIT)
            // Parse on background dispatcher.
            withContext(AppDispatchers.parsing) {
                parser.extractOpImageUrl(snippet, baseUrl)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.w(TAG, "Failed to resolve OP image for thread $threadId: ${e.message}")
            null
        }
    }
}


