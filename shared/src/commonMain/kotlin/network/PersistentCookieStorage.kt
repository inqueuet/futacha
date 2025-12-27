@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.Url
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max
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
    private val cookies = mutableMapOf<CookieKey, StoredCookie>()
    private var isLoaded = false
    private var transactionSnapshot: Map<CookieKey, StoredCookie>? = null
    private var transactionDepth = 0

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        mutex.withLock {
            ensureLoadedLocked()
            val now = currentTimeMillis()
            val domain = resolveDomain(cookie.domain, requestUrl.host) ?: return
            val path = normalizePath(cookie.path)
            val expiresAt = resolveExpiresAt(cookie, now) ?: return removeCookieLocked(domain, path, cookie.name)
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
            if (transactionDepth == 0) {
                saveLocked()
            }
        }
    }

    override suspend fun get(requestUrl: Url): List<Cookie> {
        val now = currentTimeMillis()
        return mutex.withLock {
            ensureLoadedLocked()
            purgeExpiredLocked(now)
            val host = requestUrl.host.lowercase()
            val path = normalizePath(requestUrl.encodedPath)
            val isSecure = requestUrl.isSecure()
            cookies.values.filter { stored ->
                domainMatches(host, stored.domain) &&
                    pathMatches(path, stored.path) &&
                    (!stored.secure || isSecure)
            }.map { stored ->
                Cookie(
                    name = stored.name,
                    value = stored.value,
                    encoding = CookieEncoding.RAW,
                    maxAge = stored.expiresAtMillis?.let { max(((it - now) / 1000).toInt(), 0) } ?: 0,
                    expires = stored.expiresAtMillis?.let { GMTDate(it) },
                    domain = stored.domain,
                    path = stored.path,
                    secure = stored.secure,
                    httpOnly = stored.httpOnly,
                    extensions = stored.extensions
                )
            }
        }
    }

    /**
     * Checks whether there is at least one valid (non-expired, domain/path/secure matching) cookie
     * for the given URL. If [preferredNames] is provided, only those cookie names will be considered.
     */
    suspend fun hasValidCookieFor(requestUrl: Url, preferredNames: Set<String> = emptySet()): Boolean {
        val now = currentTimeMillis()
        return mutex.withLock {
            ensureLoadedLocked()
            purgeExpiredLocked(now)
            val host = requestUrl.host.lowercase()
            val path = normalizePath(requestUrl.encodedPath)
            val isSecure = requestUrl.isSecure()
            val candidates = cookies.values.filter { stored ->
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
    }

    override fun close() {
        // no-op
    }

    suspend fun listCookies(): List<StoredCookie> = mutex.withLock {
        ensureLoadedLocked()
        purgeExpiredLocked(currentTimeMillis())
        cookies.values.sortedWith(compareBy({ it.domain }, { it.name }))
    }

    suspend fun removeCookie(domain: String, path: String, name: String) {
        mutex.withLock {
            ensureLoadedLocked()
            cookies.remove(CookieKey(domain.lowercase(), normalizePath(path), name))
            if (transactionDepth == 0) {
                saveLocked()
            }
        }
    }

    suspend fun clearAll() {
        mutex.withLock {
            ensureLoadedLocked()
            cookies.clear()
            if (transactionDepth == 0) {
                saveLocked()
            }
        }
    }

    /**
     * Run [block] while staging cookie changes; commit to disk only if it succeeds.
     *
     * Note: ブロック実行中はロックを保持しないため、他のコルーチンがクッキーを
     * 変更する可能性があります。ただし、失敗時はスナップショットにロールバックされ、
     * 成功時のみディスクに永続化されます。
     *
     * トランザクション中の操作（addCookie等）はメモリ上でのみ行われ、
     * ディスクへの書き込みはトランザクション完了時に一括で行われます。
     */
    suspend fun <T> commitOnSuccess(block: suspend () -> T): T {
        mutex.withLock {
            if (transactionDepth == 0) {
                transactionSnapshot = HashMap(cookies)
            }
            transactionDepth += 1
        }
        return runCatching { block() }
            .onSuccess { persistTransaction() }
            .onFailure { rollbackTransaction() }
            .getOrThrow()
    }

    private suspend fun persistTransaction() {
        mutex.withLock {
            if (transactionDepth <= 1) {
                if (transactionSnapshot != null) {
                    saveLocked()
                }
                transactionSnapshot = null
                transactionDepth = 0
            } else {
                transactionDepth -= 1
            }
        }
    }

    private suspend fun rollbackTransaction() {
        mutex.withLock {
            transactionSnapshot?.let { snapshot ->
                cookies.clear()
                cookies.putAll(snapshot)
            }
            transactionSnapshot = null
            transactionDepth = 0
        }
    }

    private suspend fun ensureLoadedLocked() {
        if (isLoaded) return
        if (!fileSystem.exists(storagePath)) {
            isLoaded = true
            return
        }
        val content = fileSystem.readString(storagePath).getOrNull().orEmpty()
        if (content.isNotBlank()) {
            // FIX: 複数世代バックアップ対応 - メインファイルが破損している場合、最大3世代のバックアップを試す
            val attemptPaths = listOf(
                storagePath to "main",
                "$storagePath.backup" to "backup",
                "$storagePath.backup2" to "backup2",
                "$storagePath.backup3" to "backup3"
            )

            var restored = false
            for ((path, label) in attemptPaths) {
                if (!fileSystem.exists(path)) continue

                val fileContent = fileSystem.readString(path).getOrNull().orEmpty()
                if (fileContent.isBlank()) continue

                runCatching<Unit> {
                    val parsed = json.decodeFromString<StoredCookieFile>(fileContent)
                    cookies.clear()
                    parsed.cookies.forEach { stored ->
                        val key = CookieKey(stored.domain.lowercase(), normalizePath(stored.path), stored.name)
                        cookies[key] = stored
                    }
                    if (label != "main") {
                        Logger.i("PersistentCookieStorage", "Successfully restored from $label")
                    }
                    restored = true
                }.onFailure { error ->
                    Logger.e("PersistentCookieStorage", "Failed to parse $label: ${error.message}")
                }

                if (restored) break
            }

            if (!restored) {
                Logger.e("PersistentCookieStorage", "All cookie files are corrupted, starting fresh")
            }
        }
        purgeExpiredLocked(currentTimeMillis())
        isLoaded = true
    }

    private suspend fun saveLocked() {
        val payload = StoredCookieFile(cookies = cookies.values.toList())
        val content = json.encodeToString(payload)
        val parentDir = storagePath.substringBeforeLast('/', "")
        if (parentDir.isNotEmpty()) {
            fileSystem.createDirectory(parentDir)
        }
        // FIX: 複数世代バックアップのローテーション（3世代保持）
        // backup2 -> backup3, backup -> backup2, main -> backupの順で移動
        if (fileSystem.exists(storagePath)) {
            val currentContent = fileSystem.readString(storagePath).getOrNull()
            if (currentContent != null && currentContent.isNotBlank()) {
                // 古いバックアップをローテーション
                val backup2Path = "$storagePath.backup2"
                val backup3Path = "$storagePath.backup3"
                if (fileSystem.exists(backup2Path)) {
                    fileSystem.readString(backup2Path).getOrNull()?.let { backup2Content ->
                        fileSystem.writeString(backup3Path, backup2Content)
                            .onFailure { error ->
                                Logger.w("PersistentCookieStorage", "Failed to rotate backup3: ${error.message}")
                            }
                    }
                }

                val backupPath = "$storagePath.backup"
                if (fileSystem.exists(backupPath)) {
                    fileSystem.readString(backupPath).getOrNull()?.let { backupContent ->
                        fileSystem.writeString(backup2Path, backupContent)
                            .onFailure { error ->
                                Logger.w("PersistentCookieStorage", "Failed to rotate backup2: ${error.message}")
                            }
                    }
                }

                // 現在のファイルをbackupに
                fileSystem.writeString(backupPath, currentContent)
                    .onFailure { error ->
                        Logger.w("PersistentCookieStorage", "Failed to create backup: ${error.message}")
                        // バックアップ作成に失敗した場合、メイン書き込みを中止
                        return
                    }
            }
        }

        // メインファイルに書き込み
        fileSystem.writeString(storagePath, content)
            .onFailure { error ->
                Logger.e("PersistentCookieStorage", "Failed to save cookie file: ${error.message}", error)
            }
    }

    private suspend fun purgeExpiredLocked(now: Long) {
        val expiredKeys = cookies.filterValues { stored ->
            val expires = stored.expiresAtMillis ?: return@filterValues false
            expires <= now
        }.keys
        if (expiredKeys.isNotEmpty()) {
            expiredKeys.forEach { cookies.remove(it) }
            if (transactionDepth == 0) {
                saveLocked()
            }
        }
    }

    /**
     * クッキーの有効期限を解決する。
     * - maxAge == 0 または maxAge < 0: クッキーを削除（nullを返す）
     * - maxAge > 0: 現在時刻 + maxAge
     * - maxAge == null: expiresを使用
     *
     * RFC 6265: 負のmax-ageは無効とみなし、クッキーを即座に削除する
     */
    private fun resolveExpiresAt(cookie: Cookie, now: Long): Long? {
        val maxAgeSeconds = cookie.maxAge
        if (maxAgeSeconds != null) {
            // max-age=0 または負の値はクッキー削除を意味する
            if (maxAgeSeconds <= 0) return null
            return now + maxAgeSeconds.seconds.inWholeMilliseconds
        }
        return cookie.expires?.timestamp
    }

    private fun resolveDomain(setCookieDomain: String?, requestHost: String): String? {
        val candidate = setCookieDomain?.ifBlank { null } ?: requestHost
        return candidate.lowercase()
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

    /**
     * RFC 6265準拠のパスマッチング
     * cookiePath が "/" の場合は常にマッチ
     * そうでない場合、requestPath が cookiePath と等しいか、cookiePath + "/" で始まる必要がある
     */
    private fun pathMatches(requestPath: String, cookiePath: String): Boolean {
        // 空のパスは"/"として扱う（RFC 6265 Section 5.1.4）
        val normalizedRequest = requestPath.ifEmpty { "/" }
        val normalizedCookie = cookiePath.ifEmpty { "/" }

        if (normalizedRequest == normalizedCookie) return true
        // cookiePath が "/" の場合は全てにマッチ
        if (normalizedCookie == "/") return true
        // requestPath が cookiePath で始まり、その後に "/" が続く場合のみマッチ
        // 例: cookiePath="/foo" の場合、requestPath="/foo/bar" はマッチするが "/foobar" はマッチしない
        return normalizedRequest.startsWith(normalizedCookie) &&
               (normalizedRequest.length > normalizedCookie.length && normalizedRequest[normalizedCookie.length] == '/')
    }

    private fun Url.isSecure(): Boolean = protocol.name.equals("https", ignoreCase = true)

    private suspend fun removeCookieLocked(domain: String, path: String, name: String) {
        cookies.remove(CookieKey(domain, path, name))
        if (transactionDepth == 0) {
            saveLocked()
        }
    }

    private data class CookieKey(
        val domain: String,
        val path: String,
        val name: String
    )
}
