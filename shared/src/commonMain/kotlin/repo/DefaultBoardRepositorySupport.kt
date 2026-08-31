package com.valoser.futacha.shared.repo

import com.valoser.futacha.shared.model.CatalogFetchSettings
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.network.StoredCookie
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.isWithinEpochInterval
import io.ktor.http.Url
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException

internal data class DefaultBoardRepositoryOpImageKey(
    val board: String,
    val threadId: String
)

internal data class DefaultBoardRepositoryOpImageCacheEntry(
    val url: String?,
    val recordedAtMillis: Long,
    val ttlMillis: Long
)

internal data class DefaultBoardRepositoryCatalogTitleCacheEntry(
    val title: String?,
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

internal fun createDefaultBoardRepositoryCatalogTitleCache(
    maxEntries: Int
) = DefaultBoardRepositoryLruCache<DefaultBoardRepositoryOpImageKey, DefaultBoardRepositoryCatalogTitleCacheEntry>(maxEntries)

private val DEFAULT_BOARD_REPOSITORY_POSTING_COOKIE_NAMES = setOf("posttime", "ptmt")
private const val DEFAULT_BOARD_REPOSITORY_CATALOG_SETTINGS_COOKIE_NAME = "cxyl"

internal suspend fun hasDefaultBoardRepositoryCookies(
    cookieRepository: CookieRepository?,
    board: String
): Boolean {
    return cookieRepository?.let { repository ->
        repository.hasValidCookieFor(board, preferredNames = DEFAULT_BOARD_REPOSITORY_POSTING_COOKIE_NAMES)
    } ?: false
}

internal suspend fun hasDefaultBoardRepositoryCatalogSettingsCookie(
    cookieRepository: CookieRepository?,
    board: String,
    settings: CatalogFetchSettings
): Boolean {
    cookieRepository ?: return false
    val boardUrl = runCatching { Url(board) }.getOrNull() ?: return false
    val expectedValue = settings.normalized().toDefaultBoardRepositoryCxylCookieValue()
    return cookieRepository.listCookies().any { cookie ->
        cookie.name == DEFAULT_BOARD_REPOSITORY_CATALOG_SETTINGS_COOKIE_NAME &&
            cookie.value == expectedValue &&
            cookie.matchesDefaultBoardRepositoryScope(boardUrl)
    }
}

internal fun CatalogFetchSettings.toDefaultBoardRepositoryCxylCookieValue(): String {
    val normalized = normalized()
    // Futaba's last cxyl flag is the visited-history flag.  The catset
    // endpoint may respond with a short/default cookie (for example
    // `32x25x4x0x0`) even when the requested layout was larger.  The
    // compatibility APK writes the complete value itself, including the
    // final `1`, before fetching futaba.php?mode=cat.
    return "${normalized.columns}x${normalized.rows}x${normalized.titleLines}x0x" +
        if (normalized.showVisitedHistory) "1" else "0"
}

/**
 * catset is useful for obtaining posttime/ptmt, but it is not reliable for
 * the catalog layout: current boards commonly return a normalized/default
 * cxyl cookie.  Replace every matching cxyl cookie after catset so the next
 * catalog GET uses the exact layout requested by the caller.
 */
internal suspend fun persistDefaultBoardRepositoryCatalogSettingsCookie(
    cookieRepository: CookieRepository?,
    board: String,
    settings: CatalogFetchSettings
) {
    cookieRepository ?: return
    val boardUrl = runCatching { Url(BoardUrlResolver.resolveBoardBaseUrl(board)) }.getOrNull() ?: return
    val boardPath = boardUrl.encodedPath.trimEnd('/').ifBlank { "/" }
    cookieRepository.listCookies()
        .filter { cookie ->
            cookie.name == DEFAULT_BOARD_REPOSITORY_CATALOG_SETTINGS_COOKIE_NAME &&
                cookie.matchesDefaultBoardRepositoryScope(boardUrl)
        }
        .forEach { cookie ->
            cookieRepository.deleteCookie(cookie.domain, cookie.path, cookie.name)
        }
    cookieRepository.setCookie(
        requestUrl = boardUrl.toString(),
        name = DEFAULT_BOARD_REPOSITORY_CATALOG_SETTINGS_COOKIE_NAME,
        value = settings.normalized().toDefaultBoardRepositoryCxylCookieValue(),
        domain = boardUrl.host,
        path = boardPath
    )
}

private fun StoredCookie.matchesDefaultBoardRepositoryScope(url: Url): Boolean {
    val host = url.host.lowercase()
    val cookieDomain = domain.lowercase().removePrefix(".")
    val domainMatches = host == cookieDomain || host.endsWith(".$cookieDomain")
    if (!domainMatches) return false
    val requestPath = url.encodedPath.ifBlank { "/" }
    val cookiePath = path.trimEnd('/').ifBlank { "/" }
    return cookiePath == "/" || requestPath == cookiePath ||
        requestPath.startsWith("$cookiePath/")
}

internal suspend fun resolveDefaultBoardRepositoryCookieInitializationState(
    initializedBoards: MutableSet<String>,
    board: String,
    cookieRepository: CookieRepository?,
    boardInitMutex: Mutex,
    requireSetup: Boolean = false
): Boolean {
    var shouldInitialize = false
    boardInitMutex.withLock {
        if (requireSetup) {
            initializedBoards.remove(board)
            shouldInitialize = true
        } else if (!initializedBoards.contains(board)) {
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
    val message = error.message?.take(8 * 1024)?.lowercase().orEmpty()
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
    if (isWithinEpochInterval(now, entry.recordedAtMillis, entry.ttlMillis)) {
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
        !isWithinEpochInterval(now, entry.recordedAtMillis, entry.ttlMillis)
    }
}

internal fun resolveDefaultBoardRepositoryCachedCatalogTitle(
    cache: DefaultBoardRepositoryLruCache<DefaultBoardRepositoryOpImageKey, DefaultBoardRepositoryCatalogTitleCacheEntry>,
    key: DefaultBoardRepositoryOpImageKey,
    now: Long
): DefaultBoardRepositoryCatalogTitleCacheEntry? {
    val entry = cache[key] ?: return null
    if (isWithinEpochInterval(now, entry.recordedAtMillis, entry.ttlMillis)) {
        return entry
    }
    cache.remove(key)
    return null
}

internal fun saveDefaultBoardRepositoryCatalogTitleToCache(
    cache: DefaultBoardRepositoryLruCache<DefaultBoardRepositoryOpImageKey, DefaultBoardRepositoryCatalogTitleCacheEntry>,
    key: DefaultBoardRepositoryOpImageKey,
    title: String?,
    now: Long,
    hitTtlMillis: Long,
    missTtlMillis: Long
) {
    val ttl = if (title == null) missTtlMillis else hitTtlMillis
    cache[key] = DefaultBoardRepositoryCatalogTitleCacheEntry(
        title = title,
        recordedAtMillis = now,
        ttlMillis = ttl
    )
    cache.removeIf { _, entry ->
        !isWithinEpochInterval(now, entry.recordedAtMillis, entry.ttlMillis)
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

internal suspend fun resolveDefaultBoardRepositoryCatalogThreadTitle(
    threadId: String,
    logTag: String,
    allowFallbackHeadScan: Boolean,
    fetchInitialThreadHead: suspend () -> String,
    fetchFallbackThreadHead: suspend () -> String,
    extractTitle: suspend (String) -> String?
): String? {
    return try {
        val initialSnippet = fetchInitialThreadHead()
        extractTitle(initialSnippet) ?: if (allowFallbackHeadScan) {
            extractTitle(fetchFallbackThreadHead())
        } else {
            null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Logger.w(logTag, "Failed to resolve catalog title for thread $threadId: ${e.message}")
        null
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
