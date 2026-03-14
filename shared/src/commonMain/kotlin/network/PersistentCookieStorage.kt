@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.Url
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.time.Clock

private fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()

@Serializable
data class StoredCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val expiresAtMillis: Long? = null,
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val extensions: Map<String, String> = emptyMap(),
    val createdAtMillis: Long = currentTimeMillis()
)

@Serializable
internal data class StoredCookieFile(
    val version: Int = 1,
    val cookies: List<StoredCookie>
)

private class CookieTransactionContext(val id: Long) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<CookieTransactionContext> = Key

    companion object Key : CoroutineContext.Key<CookieTransactionContext>
}

/**
 * Persistent cookie storage for `.2chan.net` (shared across subdomains).
 * - Saves cookies to disk as JSON.
 * - Filters expired cookies on load/get.
 * - Provides transaction helpers to persist only when a request succeeds.
 */
class PersistentCookieStorage(
    private val fileSystem: FileSystem,
    private val storagePath: String = "private/cookies/cookies.json"
) : CookiesStorage {
    private val transactionCoordinator =
        PersistentCookieTransactionCoordinator<CookieKey, StoredCookie>("PersistentCookieStorage")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false; encodeDefaults = true }
    private val mutex = Mutex()
    private val transactionMutex = Mutex()
    private val cookies = mutableMapOf<CookieKey, StoredCookie>()
    private var isLoaded = false
    // FIX: パフォーマンス最適化 - 期限切れCookieの削除頻度を制限
    private var lastPurgeTimeMillis = 0L
    private val purgeIntervalMillis = 5 * 60 * 1000L // 5分間隔

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        var savePayload: String? = null
        mutex.withLock {
            ensureLoadedLocked()
            val now = currentTimeMillis()
            val domain = resolvePersistentCookieDomain(cookie.domain, requestUrl.host)
            if (domain == null) {
                Logger.w("PersistentCookieStorage", "Rejected cookie '${cookie.name}' with invalid domain: ${cookie.domain} for host: ${requestUrl.host}")
                return
            }
            if (!isPersistentCookieNameAllowed(cookie.name) || !isPersistentCookieValueAllowed(cookie.value)) {
                Logger.w("PersistentCookieStorage", "Rejected cookie '${cookie.name}' due to invalid size/format")
                return
            }
            val path = normalizePersistentCookiePath(cookie.path)
            if (shouldDeletePersistentCookie(cookie, now)) {
                if (removeCookieLocked(domain, path, cookie.name)) {
                    savePayload = encodeSnapshotLocked()
                }
                return@withLock
            }
            val expiresAt = resolvePersistentCookieExpiresAt(cookie, now)
            val key = CookieKey(domain, path, cookie.name)
            val stored = StoredCookie(
                name = cookie.name,
                value = cookie.value,
                domain = domain,
                path = path,
                expiresAtMillis = expiresAt,
                secure = cookie.secure,
                httpOnly = cookie.httpOnly,
                extensions = cookie.extensions.mapNotNull { (key, value) ->
                    value?.let { key to it }
                }.toMap(),
                createdAtMillis = now
            )
            cookies[key] = stored
            enforceCookieCapacityLocked(now)
            if (transactionCoordinator.shouldPersistImmediately(coroutineContext[CookieTransactionContext]?.id)) {
                savePayload = encodeSnapshotLocked()
            }
        }
        savePayload?.let { persistSnapshot(it) }
    }

    override suspend fun get(requestUrl: Url): List<Cookie> {
        val now = currentTimeMillis()
        var savePayload: String? = null
        val result = mutex.withLock {
            ensureLoadedLocked()
            if (purgeExpiredLocked(now)) {
                savePayload = encodeSnapshotLocked()
            }
            val host = requestUrl.host.lowercase()
            val path = normalizePersistentCookiePath(requestUrl.encodedPath)
            val isSecure = requestUrl.isSecure()
            cookies.values.filter { stored ->
                !isExpiredAt(now, stored) &&
                persistentCookieDomainMatches(host, stored.domain) &&
                    pathMatches(path, stored.path) &&
                    (!stored.secure || isSecure)
            }.map { stored ->
                Cookie(
                    name = stored.name,
                    value = stored.value,
                    encoding = CookieEncoding.RAW,
                    maxAge = stored.expiresAtMillis?.let { max(((it - now) / 1000).toInt(), 0) },
                    expires = stored.expiresAtMillis?.let { GMTDate(it) },
                    domain = stored.domain,
                    path = stored.path,
                    secure = stored.secure,
                    httpOnly = stored.httpOnly,
                    extensions = stored.extensions
                )
            }
        }
        savePayload?.let { persistSnapshot(it) }
        return result
    }

    /**
     * Checks whether there is at least one valid (non-expired, domain/path/secure matching) cookie
     * for the given URL. If [preferredNames] is provided, only those cookie names will be considered.
     */
    suspend fun hasValidCookieFor(requestUrl: Url, preferredNames: Set<String> = emptySet()): Boolean {
        val now = currentTimeMillis()
        var savePayload: String? = null
        val result = mutex.withLock {
            ensureLoadedLocked()
            if (purgeExpiredLocked(now)) {
                savePayload = encodeSnapshotLocked()
            }
            val host = requestUrl.host.lowercase()
            val path = normalizePersistentCookiePath(requestUrl.encodedPath)
            val isSecure = requestUrl.isSecure()
            val candidates = cookies.values.filter { stored ->
                !isExpiredAt(now, stored) &&
                persistentCookieDomainMatches(host, stored.domain) &&
                    pathMatches(path, stored.path) &&
                    (!stored.secure || isSecure)
            }
            if (preferredNames.isNotEmpty()) {
                candidates.any { it.name in preferredNames }
            } else {
                candidates.isNotEmpty()
            }
        }
        savePayload?.let { persistSnapshot(it) }
        return result
    }

    override fun close() {
        // no-op
    }

    suspend fun listCookies(): List<StoredCookie> {
        var savePayload: String? = null
        val result = mutex.withLock {
            ensureLoadedLocked()
            if (purgeExpiredLocked(currentTimeMillis())) {
                savePayload = encodeSnapshotLocked()
            }
            cookies.values.sortedWith(compareBy({ it.domain }, { it.name }))
        }
        savePayload?.let { persistSnapshot(it) }
        return result
    }

    suspend fun removeCookie(domain: String, path: String, name: String) {
        var savePayload: String? = null
        mutex.withLock {
            ensureLoadedLocked()
            cookies.remove(CookieKey(domain.lowercase(), normalizePersistentCookiePath(path), name))
            if (transactionCoordinator.shouldPersistImmediately(coroutineContext[CookieTransactionContext]?.id)) {
                savePayload = encodeSnapshotLocked()
            }
        }
        savePayload?.let { persistSnapshot(it) }
    }

    suspend fun clearAll() {
        var savePayload: String? = null
        mutex.withLock {
            ensureLoadedLocked()
            cookies.clear()
            if (transactionCoordinator.shouldPersistImmediately(coroutineContext[CookieTransactionContext]?.id)) {
                savePayload = encodeSnapshotLocked()
            }
        }
        savePayload?.let { persistSnapshot(it) }
    }

    /**
     * Run [block] while staging cookie changes; commit to disk only if it succeeds.
     */
    suspend fun <T> commitOnSuccess(block: suspend () -> T): T {
        val inheritedTransactionId = coroutineContext[CookieTransactionContext]?.id
        if (inheritedTransactionId != null) {
            return block()
        }
        return transactionMutex.withLock {
            val transactionId = mutex.withLock {
                ensureLoadedLocked()
                transactionCoordinator.begin(cookies)
            }
            runCatching {
                withContext(CookieTransactionContext(transactionId)) {
                    block()
                }
            }.onSuccess {
                persistTransaction(transactionId)
            }.onFailure {
                rollbackTransaction(transactionId)
            }.getOrThrow()
        }
    }

    private suspend fun persistTransaction(transactionId: Long?) {
        var savePayload: String? = null
        mutex.withLock {
            savePayload = transactionCoordinator.commit(
                transactionId = transactionId,
                encodeSnapshot = ::encodeSnapshotLocked
            )
        }
        savePayload?.let { persistSnapshot(it) }
    }

    private suspend fun rollbackTransaction(transactionId: Long?) {
        var savePayload: String? = null
        mutex.withLock {
            savePayload = transactionCoordinator.rollback(
                transactionId = transactionId,
                restoreSnapshot = { snapshot ->
                cookies.clear()
                cookies.putAll(snapshot)
                },
                encodeSnapshot = ::encodeSnapshotLocked
            )
        }
        savePayload?.let { persistSnapshot(it) }
    }

    private suspend fun ensureLoadedLocked() {
        if (isLoaded) return
        if (!fileSystem.exists(storagePath)) {
            isLoaded = true
            return
        }
        loadPersistentCookieSnapshot(
            fileSystem = fileSystem,
            storagePath = storagePath,
            json = json,
            maxCookieFileBytes = MAX_COOKIE_FILE_BYTES,
            logTag = "PersistentCookieStorage"
        )?.let { loadResult ->
            cookies.clear()
            loadResult.cookies.forEach { stored ->
                if (!isStoredPersistentCookieAllowed(stored)) return@forEach
                val key = CookieKey(
                    stored.domain.lowercase(),
                    normalizePersistentCookiePath(stored.path),
                    stored.name
                )
                cookies[key] = stored
            }
            enforceCookieCapacityLocked(currentTimeMillis())
            if (loadResult.restoredFromBackup) {
                Logger.i("PersistentCookieStorage", "Successfully restored from backup")
            }
        }
        if (purgeExpiredLocked(currentTimeMillis(), force = true)) {
            persistSnapshot(encodeSnapshotLocked())
        }
        isLoaded = true
    }

    private fun encodeSnapshotLocked(): String {
        val payload = StoredCookieFile(cookies = cookies.values.toList())
        return json.encodeToString(payload)
    }

    private suspend fun persistSnapshot(content: String) {
        persistPersistentCookieSnapshot(
            fileSystem = fileSystem,
            storagePath = storagePath,
            content = content,
            logTag = "PersistentCookieStorage"
        )
    }

    private fun purgeExpiredLocked(now: Long, force: Boolean = false): Boolean {
        // FIX: パフォーマンス最適化 - 一定間隔でのみパージを実行
        // 毎回のget()で全Cookie走査するのは非効率なため、5分に1回に制限
        if (!force && now - lastPurgeTimeMillis < purgeIntervalMillis) {
            return false
        }
        lastPurgeTimeMillis = now

        val expiredKeys = cookies.filterValues { stored ->
            val expires = stored.expiresAtMillis ?: return@filterValues false
            expires <= now
        }.keys
        if (expiredKeys.isNotEmpty()) {
            expiredKeys.forEach { cookies.remove(it) }
            return true
        }
        return false
    }

    private fun isExpiredAt(now: Long, cookie: StoredCookie): Boolean {
        val expires = cookie.expiresAtMillis ?: return false
        return expires <= now
    }

    private fun enforceCookieCapacityLocked(now: Long) {
        purgeExpiredLocked(now, force = true)
        while (cookies.size > MAX_COOKIES_TOTAL) {
            evictOldestCookieLocked()
        }
        val groupedByDomain = cookies.values.groupBy { it.domain.lowercase() }
        groupedByDomain.forEach { (domain, domainCookies) ->
            val overflow = domainCookies.size - MAX_COOKIES_PER_DOMAIN
            if (overflow <= 0) return@forEach
            domainCookies
                .sortedBy { it.createdAtMillis }
                .take(overflow)
                .forEach { cookie ->
                    cookies.remove(CookieKey(domain, normalizePersistentCookiePath(cookie.path), cookie.name))
                }
        }
    }

    private fun evictOldestCookieLocked() {
        val oldest = cookies.entries.minByOrNull { it.value.createdAtMillis }?.key ?: return
        cookies.remove(oldest)
    }

    private fun pathMatches(requestPath: String, cookiePath: String): Boolean {
        if (requestPath == cookiePath) return true
        if (!requestPath.startsWith(cookiePath)) return false
        if (cookiePath.endsWith("/")) return true
        val boundaryIndex = cookiePath.length
        return requestPath.getOrNull(boundaryIndex) == '/'
    }

    private fun Url.isSecure(): Boolean = protocol.name.equals("https", ignoreCase = true)

    private fun removeCookieLocked(domain: String, path: String, name: String): Boolean {
        return cookies.remove(CookieKey(domain, path, name)) != null
    }

    private data class CookieKey(
        val domain: String,
        val path: String,
        val name: String
    )

    companion object {
        private const val MAX_COOKIES_TOTAL = 256
        private const val MAX_COOKIES_PER_DOMAIN = 64
        private const val MAX_COOKIE_FILE_BYTES = 1_048_576L // 1 MiB
    }
}
