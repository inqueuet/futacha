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
import kotlin.time.Duration.Companion.seconds
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
private data class StoredCookieFile(
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
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val mutex = Mutex()
    private val transactionMutex = Mutex()
    private val cookies = mutableMapOf<CookieKey, StoredCookie>()
    private var isLoaded = false
    private var transactionSnapshot: Map<CookieKey, StoredCookie>? = null
    private var activeTransactionId: Long? = null
    private var transactionSequence = 0L
    private var externalMutationDuringTransaction = false
    // FIX: パフォーマンス最適化 - 期限切れCookieの削除頻度を制限
    private var lastPurgeTimeMillis = 0L
    private val purgeIntervalMillis = 5 * 60 * 1000L // 5分間隔
    private val knownPublicSuffixes = setOf(
        "com", "net", "org", "edu", "gov", "io", "dev",
        "jp", "co.jp", "ne.jp", "or.jp", "go.jp", "ac.jp"
    )

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        var savePayload: String? = null
        mutex.withLock {
            ensureLoadedLocked()
            val now = currentTimeMillis()
            val domain = resolveDomain(cookie.domain, requestUrl.host)
            if (domain == null) {
                Logger.w("PersistentCookieStorage", "Rejected cookie '${cookie.name}' with invalid domain: ${cookie.domain} for host: ${requestUrl.host}")
                return
            }
            if (!isCookieNameAllowed(cookie.name) || !isCookieValueAllowed(cookie.value)) {
                Logger.w("PersistentCookieStorage", "Rejected cookie '${cookie.name}' due to invalid size/format")
                return
            }
            val path = normalizePath(cookie.path)
            if (shouldDeleteCookie(cookie, now)) {
                if (removeCookieLocked(domain, path, cookie.name)) {
                    savePayload = encodeSnapshotLocked()
                }
                return@withLock
            }
            val expiresAt = resolveExpiresAt(cookie, now)
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
            val transactionId = coroutineContext[CookieTransactionContext]?.id
            val isInActiveTransaction = activeTransactionId != null && transactionId == activeTransactionId
            if (activeTransactionId != null && !isInActiveTransaction) {
                externalMutationDuringTransaction = true
            }
            cookies[key] = stored
            enforceCookieCapacityLocked(now)
            if (transactionSnapshot == null || !isInActiveTransaction) {
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
            val path = normalizePath(requestUrl.encodedPath)
            val isSecure = requestUrl.isSecure()
            cookies.values.filter { stored ->
                !isExpiredAt(now, stored) &&
                domainMatches(host, stored.domain) &&
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
            val path = normalizePath(requestUrl.encodedPath)
            val isSecure = requestUrl.isSecure()
            val candidates = cookies.values.filter { stored ->
                !isExpiredAt(now, stored) &&
                domainMatches(host, stored.domain) &&
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
            val transactionId = coroutineContext[CookieTransactionContext]?.id
            val isInActiveTransaction = activeTransactionId != null && transactionId == activeTransactionId
            if (activeTransactionId != null && !isInActiveTransaction) {
                externalMutationDuringTransaction = true
            }
            cookies.remove(CookieKey(domain.lowercase(), normalizePath(path), name))
            if (transactionSnapshot == null || !isInActiveTransaction) {
                savePayload = encodeSnapshotLocked()
            }
        }
        savePayload?.let { persistSnapshot(it) }
    }

    suspend fun clearAll() {
        var savePayload: String? = null
        mutex.withLock {
            ensureLoadedLocked()
            val transactionId = coroutineContext[CookieTransactionContext]?.id
            val isInActiveTransaction = activeTransactionId != null && transactionId == activeTransactionId
            if (activeTransactionId != null && !isInActiveTransaction) {
                externalMutationDuringTransaction = true
            }
            cookies.clear()
            if (transactionSnapshot == null || !isInActiveTransaction) {
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
                transactionSnapshot = HashMap(cookies)
                externalMutationDuringTransaction = false
                transactionSequence += 1
                activeTransactionId = transactionSequence
                transactionSequence
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
            if (activeTransactionId != transactionId) return@withLock
            savePayload = encodeSnapshotLocked()
            transactionSnapshot = null
            activeTransactionId = null
            externalMutationDuringTransaction = false
        }
        savePayload?.let { persistSnapshot(it) }
    }

    private suspend fun rollbackTransaction(transactionId: Long?) {
        var savePayload: String? = null
        mutex.withLock {
            if (activeTransactionId != transactionId) return@withLock
            if (externalMutationDuringTransaction) {
                Logger.w(
                    "PersistentCookieStorage",
                    "Rolling back transaction while discarding external cookie mutations"
                )
            }
            transactionSnapshot?.let { snapshot ->
                cookies.clear()
                cookies.putAll(snapshot)
                savePayload = encodeSnapshotLocked()
            }
            transactionSnapshot = null
            activeTransactionId = null
            externalMutationDuringTransaction = false
        }
        savePayload?.let { persistSnapshot(it) }
    }

    private suspend fun ensureLoadedLocked() {
        if (isLoaded) return
        if (!fileSystem.exists(storagePath)) {
            isLoaded = true
            return
        }
        val content = readBoundedCookieFile(storagePath).orEmpty()
        if (content.isNotBlank()) {
            // FIX: エラーログを追加し、破損時はバックアップから復元を試みる
            runCatching<Unit> {
                val parsed = json.decodeFromString<StoredCookieFile>(content)
                cookies.clear()
                parsed.cookies.forEach { stored ->
                    if (!isStoredCookieAllowed(stored)) return@forEach
                    val key = CookieKey(stored.domain.lowercase(), normalizePath(stored.path), stored.name)
                    cookies[key] = stored
                }
                enforceCookieCapacityLocked(currentTimeMillis())
            }.onFailure { error ->
                Logger.e("PersistentCookieStorage", "Failed to parse cookie file: ${error.message}")
                // FIX: バックアップから復元を試みる
                val backupPath = "$storagePath.backup"
                if (fileSystem.exists(backupPath)) {
                    val backupContent = readBoundedCookieFile(backupPath).orEmpty()
                    runCatching<Unit> {
                        val parsed = json.decodeFromString<StoredCookieFile>(backupContent)
                        cookies.clear()
                        parsed.cookies.forEach { stored ->
                            if (!isStoredCookieAllowed(stored)) return@forEach
                            val key = CookieKey(stored.domain.lowercase(), normalizePath(stored.path), stored.name)
                            cookies[key] = stored
                        }
                        enforceCookieCapacityLocked(currentTimeMillis())
                        Logger.i("PersistentCookieStorage", "Successfully restored from backup")
                    }.onFailure { backupError ->
                        Logger.e("PersistentCookieStorage", "Backup restoration also failed: ${backupError.message}")
                    }
                }
            }
        }
        purgeExpiredLocked(currentTimeMillis(), force = true)
        isLoaded = true
    }

    private suspend fun readBoundedCookieFile(path: String): String? {
        val size = runCatching { fileSystem.getFileSize(path) }.getOrNull()
        if (size != null && size > MAX_COOKIE_FILE_BYTES) {
            Logger.w(
                "PersistentCookieStorage",
                "Skipping oversized cookie file '$path' (${size} bytes > $MAX_COOKIE_FILE_BYTES bytes)"
            )
            return null
        }
        return fileSystem.readString(path).getOrNull()
    }

    private fun encodeSnapshotLocked(): String {
        val payload = StoredCookieFile(cookies = cookies.values.toList())
        return json.encodeToString(payload)
    }

    private suspend fun persistSnapshot(content: String) {
        val parentDir = storagePath.substringBeforeLast('/', "")
        if (parentDir.isNotEmpty()) {
            fileSystem.createDirectory(parentDir)
        }
        // FIX: 保存前に現在のファイルをバックアップ
        val backupPath = "$storagePath.backup"
        if (fileSystem.exists(storagePath)) {
            val currentContent = fileSystem.readString(storagePath).getOrNull()
            if (currentContent != null && currentContent.isNotBlank()) {
                fileSystem.writeString(backupPath, currentContent)
                    .onFailure { error ->
                        Logger.w("PersistentCookieStorage", "Failed to create backup: ${error.message}")
                    }
            }
        }
        fileSystem.writeString(storagePath, content)
            .onFailure { error ->
                Logger.e("PersistentCookieStorage", "Failed to save cookie file: ${error.message}", error)
            }
            .getOrThrow()
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

    private fun shouldDeleteCookie(cookie: Cookie, now: Long): Boolean {
        val maxAgeSeconds = cookie.maxAge
        if (maxAgeSeconds == 0) return true
        val expiresAt = cookie.expires?.timestamp
        return expiresAt != null && expiresAt <= now
    }

    private fun resolveExpiresAt(cookie: Cookie, now: Long): Long? {
        val maxAgeSeconds = cookie.maxAge
        if (maxAgeSeconds != null) {
            if (maxAgeSeconds > 0) {
                return now + maxAgeSeconds.seconds.inWholeMilliseconds
            }
            if (maxAgeSeconds < 0) {
                return null
            }
        }
        return cookie.expires?.timestamp
    }

    private fun isExpiredAt(now: Long, cookie: StoredCookie): Boolean {
        val expires = cookie.expiresAtMillis ?: return false
        return expires <= now
    }

    private fun resolveDomain(setCookieDomain: String?, requestHost: String): String? {
        val request = requestHost.trim().trimEnd('.').lowercase()
        if (request.isBlank()) return null
        if (!isDomainAllowedForCookies(request)) return null
        val candidate = setCookieDomain
            ?.ifBlank { null }
            ?.trim()
            ?.trimStart('.')
            ?.trimEnd('.')
            ?.lowercase()
            ?: request
        if (!isDomainAllowedForCookies(candidate)) return null
        if (candidate in knownPublicSuffixes) return null
        val isSameHost = request == candidate
        val isParentDomain = request.endsWith(".$candidate") && !isLikelyIpAddress(candidate)
        return if (isSameHost || isParentDomain) candidate else null
    }

    private fun normalizePath(path: String?): String {
        val normalized = path?.trim().orEmpty()
        if (normalized.isBlank()) return "/"
        return if (normalized.startsWith("/")) normalized else "/$normalized"
    }

    private fun domainMatches(host: String, cookieDomain: String): Boolean {
        val normalizedDomain = cookieDomain.trimStart('.').lowercase()
        val normalizedHost = host.lowercase()
        return normalizedHost == normalizedDomain || normalizedHost.endsWith(".$normalizedDomain")
    }

    private fun isDomainAllowedForCookies(domain: String): Boolean {
        if (domain.isBlank() || domain.length > MAX_COOKIE_DOMAIN_LENGTH) return false
        if (domain.startsWith(".") || domain.endsWith(".")) return false
        if (!COOKIE_DOMAIN_REGEX.matches(domain)) return false
        val labels = domain.split('.').filter { it.isNotBlank() }
        if (labels.size < 2 && !isLikelyIpAddress(domain)) return false
        return true
    }

    private fun isLikelyIpAddress(value: String): Boolean {
        val parts = value.split('.')
        return parts.size == 4 && parts.all { part ->
            part.isNotBlank() && part.all { it.isDigit() } && part.toIntOrNull() in 0..255
        }
    }

    private fun isCookieNameAllowed(name: String): Boolean {
        if (name.isBlank()) return false
        if (name.length > MAX_COOKIE_NAME_LENGTH) return false
        return COOKIE_NAME_REGEX.matches(name)
    }

    private fun isCookieValueAllowed(value: String): Boolean {
        if (value.length > MAX_COOKIE_VALUE_LENGTH) return false
        if (value.any { it == '\u0000' || it == '\r' || it == '\n' }) return false
        return true
    }

    private fun isStoredCookieAllowed(stored: StoredCookie): Boolean {
        if (!isDomainAllowedForCookies(stored.domain)) return false
        if (!isCookieNameAllowed(stored.name)) return false
        if (!isCookieValueAllowed(stored.value)) return false
        return true
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
                    cookies.remove(CookieKey(domain, normalizePath(cookie.path), cookie.name))
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
        private const val MAX_COOKIE_NAME_LENGTH = 128
        private const val MAX_COOKIE_VALUE_LENGTH = 2048
        private const val MAX_COOKIE_DOMAIN_LENGTH = 253
        private const val MAX_COOKIE_FILE_BYTES = 1_048_576L // 1 MiB
        private val COOKIE_NAME_REGEX = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")
        private val COOKIE_DOMAIN_REGEX = Regex("^[A-Za-z0-9.-]+$")
    }
}
