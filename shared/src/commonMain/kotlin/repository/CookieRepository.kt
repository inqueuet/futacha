package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.network.PersistentCookieStorage
import com.valoser.futacha.shared.network.StoredCookie
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.Url
import io.ktor.util.date.GMTDate

class CookieRepository(
    private val storage: PersistentCookieStorage
) {
    suspend fun listCookies(): List<StoredCookie> = storage.listCookies()

    /** Cookies that the shared HTTP client would attach to [requestUrl]. */
    suspend fun getCookiesFor(requestUrl: String): List<Cookie> {
        val parsedUrl = Url(requestUrl)
        return storage.get(parsedUrl)
    }

    /**
     * Imports the `name=value; ...` header exposed by a platform WebView into
     * the same persistent jar used by the shared HTTP client. WebView cookie
     * APIs do not expose path/expiry through this header, matching the
     * reference app's conservative host-only `/` fallback.
     */
    suspend fun importCookieHeader(requestUrl: String, header: String?): Int {
        val parsedUrl = runCatching { Url(requestUrl) }.getOrElse { return 0 }
        if (parsedUrl.protocol.name !in setOf("http", "https")) return 0
        val cookies = parseWebViewCookieHeader(header)
        storage.commitEvenOnFailure {
            cookies.forEach { (name, value) ->
                storage.addCookie(
                    parsedUrl,
                    Cookie(
                        name = name,
                        value = value,
                        encoding = CookieEncoding.RAW,
                        path = "/",
                        secure = parsedUrl.protocol.name == "https"
                    )
                )
            }
        }
        return cookies.size
    }

    /**
     * Stores a cookie using the same persistent jar as the board client.  This is
     * intentionally exposed for the legacy compatibility screen's ptmt editor;
     * the value never passes through analytics or logging.
     */
    suspend fun setCookie(
        requestUrl: String,
        name: String,
        value: String,
        domain: String,
        path: String = "/",
        expiresAtMillis: Long? = null
    ) {
        val parsedUrl = Url(requestUrl)
        storage.addCookie(
            parsedUrl,
            Cookie(
                name = name,
                value = value,
                encoding = CookieEncoding.RAW,
                expires = expiresAtMillis?.let(::GMTDate),
                domain = domain,
                path = path
            )
        )
    }

    suspend fun deleteCookie(domain: String, path: String, name: String) {
        storage.removeCookie(domain, path, name)
    }

    suspend fun clearAll() {
        storage.clearAll()
    }

    suspend fun <T> commitOnSuccess(block: suspend () -> T): T {
        return storage.commitOnSuccess(block)
    }

    suspend fun <T> commitEvenOnFailure(block: suspend () -> T): T {
        return storage.commitEvenOnFailure(block)
    }

    suspend fun hasValidCookieFor(url: String, preferredNames: Set<String> = emptySet()): Boolean {
        val parsedUrl = runCatching { Url(url) }.getOrElse { return false }
        return storage.hasValidCookieFor(parsedUrl, preferredNames)
    }
}

internal fun parseWebViewCookieHeader(header: String?): Map<String, String> {
    if (header.isNullOrBlank() || header.length > 64 * 1024) return emptyMap()
    return header.split(';')
        .asSequence()
        .map(String::trim)
        .mapNotNull { pair ->
            val separator = pair.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            val name = pair.substring(0, separator).trim()
            val value = pair.substring(separator + 1).trim()
            if (name.isBlank() || name.length > 128 || value.length > 2_048 ||
                name.any { it <= ' ' || it == ';' || it == '=' } ||
                value.any { it == '\u0000' || it == '\r' || it == '\n' }
            ) {
                null
            } else {
                name to value
            }
        }
        .take(128)
        .toMap()
}
