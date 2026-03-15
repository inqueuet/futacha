package com.valoser.futacha.shared.repo

import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.repository.CookieRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class DefaultBoardRepositoryOpImageKey(
    val board: String,
    val threadId: String
)

internal data class DefaultBoardRepositoryOpImageCacheEntry(
    val url: String?,
    val recordedAtMillis: Long,
    val ttlMillis: Long
)

internal data class DefaultBoardRepositoryCloseState(
    var isClosed: Boolean = false
)

internal class DefaultBoardRepositoryLruCache<K, V>(
    private val maxEntries: Int
) {
    private val cache = LinkedHashMap<K, V>()

    operator fun get(key: K): V? {
        val value = cache[key] ?: return null
        cache.remove(key)
        cache[key] = value
        return value
    }

    operator fun set(key: K, value: V) {
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
}

internal fun createDefaultBoardRepositoryOpImageCache(
    maxEntries: Int
) = DefaultBoardRepositoryLruCache<DefaultBoardRepositoryOpImageKey, DefaultBoardRepositoryOpImageCacheEntry>(maxEntries)

internal suspend fun hasDefaultBoardRepositoryCookies(
    cookieRepository: CookieRepository?,
    board: String
): Boolean {
    return cookieRepository?.let { repository ->
        repository.hasValidCookieFor(board, preferredNames = setOf("posttime", "cxyl")) ||
            repository.hasValidCookieFor(board)
    } ?: false
}

internal suspend fun resolveDefaultBoardRepositoryCookieInitializationState(
    initializedBoards: MutableSet<String>,
    board: String,
    cookieRepository: CookieRepository?,
    boardInitMutex: Mutex
): Boolean {
    var shouldInitialize = false
    boardInitMutex.withLock {
        if (!initializedBoards.contains(board)) {
            if (hasDefaultBoardRepositoryCookies(cookieRepository, board)) {
                initializedBoards.add(board)
            } else {
                shouldInitialize = true
            }
        }
    }
    return shouldInitialize
}

internal suspend fun markDefaultBoardRepositoryBoardInitialized(
    initializedBoards: MutableSet<String>,
    board: String,
    boardInitMutex: Mutex
) {
    boardInitMutex.withLock {
        initializedBoards.add(board)
    }
}

internal fun isDefaultBoardRepositoryLikelyCookieAuthFailure(error: Exception): Boolean {
    val statusCode = (error as? NetworkException)?.statusCode
    if (statusCode == 401 || statusCode == 403) return true
    val message = error.message?.lowercase().orEmpty()
    return message.contains("cookie") ||
        message.contains("forbidden") ||
        message.contains("auth") ||
        message.contains("認証")
}

internal fun resolveDefaultBoardRepositoryCachedOpImageUrl(
    cache: DefaultBoardRepositoryLruCache<DefaultBoardRepositoryOpImageKey, DefaultBoardRepositoryOpImageCacheEntry>,
    key: DefaultBoardRepositoryOpImageKey,
    now: Long
): DefaultBoardRepositoryOpImageCacheEntry? {
    val entry = cache[key] ?: return null
    if (now - entry.recordedAtMillis <= entry.ttlMillis) {
        return entry
    }
    cache.remove(key)
    return null
}

internal fun saveDefaultBoardRepositoryOpImageUrlToCache(
    cache: DefaultBoardRepositoryLruCache<DefaultBoardRepositoryOpImageKey, DefaultBoardRepositoryOpImageCacheEntry>,
    key: DefaultBoardRepositoryOpImageKey,
    url: String?,
    now: Long,
    hitTtlMillis: Long,
    missTtlMillis: Long
) {
    val ttl = if (url == null) missTtlMillis else hitTtlMillis
    cache[key] = DefaultBoardRepositoryOpImageCacheEntry(
        url = url,
        recordedAtMillis = now,
        ttlMillis = ttl
    )
    purgeExpiredDefaultBoardRepositoryOpImageEntries(cache, now)
}

internal fun purgeExpiredDefaultBoardRepositoryOpImageEntries(
    cache: DefaultBoardRepositoryLruCache<DefaultBoardRepositoryOpImageKey, DefaultBoardRepositoryOpImageCacheEntry>,
    now: Long
) {
    cache.removeIf { _, entry ->
        now - entry.recordedAtMillis > entry.ttlMillis
    }
}

internal suspend fun beginDefaultBoardRepositoryClose(
    closeMutex: Mutex,
    closeState: DefaultBoardRepositoryCloseState
): Boolean {
    return closeMutex.withLock {
        if (closeState.isClosed) {
            false
        } else {
            closeState.isClosed = true
            true
        }
    }
}

internal fun shouldClearDefaultBoardRepositoryOpImageCacheEntry(
    key: DefaultBoardRepositoryOpImageKey,
    board: String?,
    threadId: String?
): Boolean {
    val boardMatches = board == null || key.board == board
    val threadMatches = threadId == null || key.threadId == threadId
    return boardMatches && threadMatches
}

internal suspend fun clearDefaultBoardRepositoryOpImageCache(
    cacheMutex: Mutex,
    cache: DefaultBoardRepositoryLruCache<DefaultBoardRepositoryOpImageKey, DefaultBoardRepositoryOpImageCacheEntry>,
    board: String?,
    threadId: String?
) {
    cacheMutex.withLock {
        if (board == null && threadId == null) {
            cache.clear()
            return@withLock
        }
        cache.removeIf { key, _ ->
            shouldClearDefaultBoardRepositoryOpImageCacheEntry(key, board, threadId)
        }
    }
}
