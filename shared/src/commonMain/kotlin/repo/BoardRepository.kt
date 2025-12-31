package com.valoser.futacha.shared.repo

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.network.BoardApi
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.parser.HtmlParser
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
    ): String

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
    // FIX: Track which boards have been initialized with cookies
    // Note: この Set は boardInitMutex で保護されており、スレッドセーフ
    // 単独でのアクセスは禁止し、必ず boardInitMutex.withLock 内でアクセスすること
    private val initializedBoards = mutableSetOf<String>()
    // Fix: Use Mutex to prevent race condition when multiple coroutines
    // try to initialize the same board simultaneously
    private val boardInitMutex = Mutex()

    private val opImageCacheMutex = Mutex()
    private val opImageCache = createOpImageCache()

    // FIX: close用のCoroutineScopeを追加
    private val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // FIX: close状態を管理（二重close防止）
    private val closeMutex = Mutex()
    private var isClosed = false

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

        try {
            api.fetchCatalogSetup(board)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to initialize cookies for board $board", e)
            // Continue anyway - the operation might still work
        }

        boardInitMutex.withLock {
            initializedBoards.add(board)
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
            // Futaba doesn't strictly return 403 for cookie issues, often just HTML errors
            // We'll aggressively retry once on any non-cancellation error
            if (e is kotlinx.coroutines.CancellationException) throw e
            
            Logger.w(TAG, "Operation failed for board $board, retrying with fresh cookies: ${e.message}")
            invalidateCookies(board)
            ensureCookiesInitialized(board)
            return block()
        }
    }

    override suspend fun getCatalog(
        board: String,
        mode: CatalogMode
    ): List<CatalogItem> {
        return withRetryOnAuthFailure(board) {
            val html = api.fetchCatalog(board, mode)
            val baseUrl = BoardUrlResolver.resolveBoardBaseUrl(board)
            parser.parseCatalog(html, baseUrl)
        }
    }

    override suspend fun fetchOpImageUrl(board: String, threadId: String): String? {
        if (threadId.isBlank()) return null
        val key = OpImageKey(board, threadId)
        getCachedOpImageUrl(key)?.let { return it }
        
        // Don't wrap caching logic in retry, only the fetch part
        val resolvedUrl = opImageSemaphore.withPermit {
            withRetryOnAuthFailure(board) {
                resolveOpImageUrl(board, threadId)
            }
        }
        saveOpImageUrlToCache(key, resolvedUrl)
        return resolvedUrl
    }

    override suspend fun getThread(board: String, threadId: String): ThreadPage {
        return withRetryOnAuthFailure(board) {
            val html = api.fetchThread(board, threadId)
            parser.parseThread(html)
        }
    }

    override suspend fun voteSaidane(board: String, threadId: String, postId: String) {
        withRetryOnAuthFailure(board) {
            api.voteSaidane(board, threadId, postId)
        }
    }

    override suspend fun requestDeletion(board: String, threadId: String, postId: String, reasonCode: String) {
        withRetryOnAuthFailure(board) {
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
        withRetryOnAuthFailure(board) {
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
    ): String {
        ensureCookiesInitialized(board)
        val exec: suspend () -> String = {
            api.createThread(board, name, email, subject, comment, password, imageFile, imageFileName, textOnly)
        }
        return cookieRepository?.commitOnSuccess { exec() } ?: exec()
    }

    // FIX: 同期的なクリーンアップ（メインスレッドから呼ばれても安全）
    @Deprecated(
        message = "Use closeAsync() instead for proper async cleanup",
        replaceWith = ReplaceWith("closeAsync()"),
        level = DeprecationLevel.WARNING
    )
    override fun close() {
        // FIX: runBlockingを削除してメインスレッドブロックを回避
        // 非同期クリーンアップはcloseAsync()に任せ、ここではスコープキャンセルのみ
        if (isClosed) return
        isClosed = true

        // スコープをキャンセル（進行中の非同期操作を停止）
        closeScope.cancel()

        // 同期的にクローズ可能なリソースのみ処理
        try {
            (api as? AutoCloseable)?.close()
        } catch (e: Exception) {
            Logger.e("DefaultBoardRepository", "Error closing API: ${e.message}", e)
        }
    }

    // FIX: 非同期版closeAsync（必要に応じて使用）
    override fun closeAsync(): Job {
        return closeScope.launch {
            closeMutex.withLock {
                // FIX: 二重close防止
                if (isClosed) return@launch
                isClosed = true
            }

            (api as? AutoCloseable)?.close()

            opImageCacheMutex.withLock {
                opImageCache.clear()
            }
        }.also {
            it.invokeOnCompletion {
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

    // 効率的なLRU実装: LinkedHashMapのアクセス順序機能を使用（O(n)操作を回避）
    // FIX: スレッドセーフでないため、外部でMutexによる保護が必須
    // このクラスのすべてのメソッドは opImageCacheMutex.withLock 内から呼び出す必要がある
    private class LruCache<K, V>(private val maxEntries: Int) {
        private val cache = object : LinkedHashMap<K, V>(maxEntries, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
                return size > maxEntries
            }
        }

        operator fun get(key: K): V? = cache[key]

        operator fun set(key: K, value: V) {
            cache[key] = value
        }

        fun remove(key: K): V? = cache.remove(key)

        fun clear() {
            cache.clear()
        }

        fun removeIf(predicate: (K, V) -> Boolean) {
            val iterator = cache.iterator()
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
        return runCatching {
            val snippet = api.fetchThreadHead(board, threadId, OP_IMAGE_LINE_LIMIT)
            // FIX: パース処理をバックグラウンドスレッドへ移動
            kotlinx.coroutines.withContext(Dispatchers.Default) {
                parser.extractOpImageUrl(snippet, baseUrl)
            }
        }.getOrElse { e ->
            Logger.w(TAG, "Failed to resolve OP image for thread $threadId: ${e.message}")
            null
        }
    }
}
