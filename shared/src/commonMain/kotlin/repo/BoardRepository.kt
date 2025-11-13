package com.valoser.futacha.shared.repo

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.network.BoardApi
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.parser.HtmlParser
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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

    suspend fun clearOpImageCache(board: String? = null, threadId: String? = null)
}

class DefaultBoardRepository(
    private val api: BoardApi,
    private val parser: HtmlParser,
    private val opImageCacheTtlMillis: Long = DEFAULT_OP_IMAGE_CACHE_TTL_MILLIS,
    private val opImageCacheMaxEntries: Int = DEFAULT_OP_IMAGE_CACHE_MAX_ENTRIES
) : BoardRepository {
    // Track which boards have been initialized with cookies
    private val initializedBoards = mutableSetOf<String>()
    // Fix: Use Mutex to prevent race condition when multiple coroutines
    // try to initialize the same board simultaneously
    private val boardInitMutex = Mutex()

    private val opImageCacheMutex = Mutex()
    private val opImageCache = createOpImageCache()

    companion object {
        private const val TAG = "DefaultBoardRepository"
        private const val OP_IMAGE_LINE_LIMIT = 65
        private const val OP_IMAGE_CONCURRENCY = 4
        private const val DEFAULT_OP_IMAGE_CACHE_TTL_MILLIS = 15 * 60 * 1000L // 15 minutes
        private const val DEFAULT_OP_IMAGE_CACHE_MAX_ENTRIES = 512
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
        if (threadId.isBlank()) return null
        val key = OpImageKey(board, threadId)
        getCachedOpImageUrl(key)?.let { return it }
        ensureCookiesInitialized(board)
        val resolvedUrl = opImageSemaphore.withPermit {
            resolveOpImageUrl(board, threadId)
        }
        saveOpImageUrlToCache(key, resolvedUrl)
        return resolvedUrl
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
        if (opImageCacheMutex.tryLock()) {
            try {
                opImageCache.clear()
            } finally {
                opImageCacheMutex.unlock()
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

    private class LruCache<K, V>(private val maxEntries: Int) {
        private val cache = mutableMapOf<K, V>()
        private val accessOrder = mutableListOf<K>()

        operator fun get(key: K): V? {
            val value = cache[key]
            if (value != null) {
                accessOrder.remove(key)
                accessOrder.add(key)
            }
            return value
        }

        operator fun set(key: K, value: V) {
            if (cache.containsKey(key)) {
                accessOrder.remove(key)
            } else if (cache.size >= maxEntries) {
                val eldest = accessOrder.removeAt(0)
                cache.remove(eldest)
            }
            cache[key] = value
            accessOrder.add(key)
        }

        fun remove(key: K): V? {
            accessOrder.remove(key)
            return cache.remove(key)
        }

        fun clear() {
            cache.clear()
            accessOrder.clear()
        }

        fun removeIf(predicate: (K, V) -> Boolean) {
            val keysToRemove = mutableListOf<K>()
            for ((key, value) in cache) {
                if (predicate(key, value)) {
                    keysToRemove.add(key)
                }
            }
            for (key in keysToRemove) {
                remove(key)
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
        return runCatching {
            val snippet = api.fetchThreadHead(board, threadId, OP_IMAGE_LINE_LIMIT)
            parser.extractOpImageUrl(snippet, baseUrl)
        }.getOrElse { e ->
            Logger.w(TAG, "Failed to resolve OP image for thread $threadId: ${e.message}")
            null
        }
    }
}
