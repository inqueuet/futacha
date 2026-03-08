@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.valoser.futacha.shared.network

import io.ktor.http.Cookie
import kotlin.time.Duration.Companion.seconds

private const val MAX_COOKIE_NAME_LENGTH = 128
private const val MAX_COOKIE_VALUE_LENGTH = 2048
private const val MAX_COOKIE_DOMAIN_LENGTH = 253
private val COOKIE_NAME_REGEX = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")
private val COOKIE_DOMAIN_REGEX = Regex("^[A-Za-z0-9.-]+$")
private val KNOWN_PUBLIC_SUFFIXES = setOf(
    "com", "net", "org", "edu", "gov", "io", "dev",
    "jp", "co.jp", "ne.jp", "or.jp", "go.jp", "ac.jp"
)

internal fun shouldDeletePersistentCookie(cookie: Cookie, now: Long): Boolean {
    val maxAgeSeconds = cookie.maxAge
    if (maxAgeSeconds == 0) return true
    val expiresAt = cookie.expires?.timestamp
    return expiresAt != null && expiresAt <= now
}

internal fun resolvePersistentCookieExpiresAt(cookie: Cookie, now: Long): Long? {
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

internal fun resolvePersistentCookieDomain(setCookieDomain: String?, requestHost: String): String? {
    val request = requestHost.trim().trimEnd('.').lowercase()
    if (request.isBlank()) return null
    if (!isPersistentCookieDomainAllowed(request)) return null
    val candidate = setCookieDomain
        ?.ifBlank { null }
        ?.trim()
        ?.trimStart('.')
        ?.trimEnd('.')
        ?.lowercase()
        ?: request
    if (!isPersistentCookieDomainAllowed(candidate)) return null
    if (candidate in KNOWN_PUBLIC_SUFFIXES) return null
    val isSameHost = request == candidate
    val isParentDomain = request.endsWith(".$candidate") && !isLikelyPersistentCookieIpAddress(candidate)
    return if (isSameHost || isParentDomain) candidate else null
}

internal fun normalizePersistentCookiePath(path: String?): String {
    val normalized = path?.trim().orEmpty()
    if (normalized.isBlank()) return "/"
    return if (normalized.startsWith("/")) normalized else "/$normalized"
}

internal fun persistentCookieDomainMatches(host: String, cookieDomain: String): Boolean {
    val normalizedDomain = cookieDomain.trimStart('.').lowercase()
    val normalizedHost = host.lowercase()
    return normalizedHost == normalizedDomain || normalizedHost.endsWith(".$normalizedDomain")
}

internal fun isPersistentCookieDomainAllowed(domain: String): Boolean {
    if (domain.isBlank() || domain.length > MAX_COOKIE_DOMAIN_LENGTH) return false
    if (domain.startsWith(".") || domain.endsWith(".")) return false
    if (!COOKIE_DOMAIN_REGEX.matches(domain)) return false
    val labels = domain.split('.').filter { it.isNotBlank() }
    if (labels.size < 2 && !isLikelyPersistentCookieIpAddress(domain)) return false
    return true
}

internal fun isLikelyPersistentCookieIpAddress(value: String): Boolean {
    val parts = value.split('.')
    return parts.size == 4 && parts.all { part ->
        part.isNotBlank() && part.all { it.isDigit() } && part.toIntOrNull() in 0..255
    }
}

internal fun isPersistentCookieNameAllowed(name: String): Boolean {
    if (name.isBlank()) return false
    if (name.length > MAX_COOKIE_NAME_LENGTH) return false
    return COOKIE_NAME_REGEX.matches(name)
}

internal fun isPersistentCookieValueAllowed(value: String): Boolean {
    if (value.length > MAX_COOKIE_VALUE_LENGTH) return false
    if (value.any { it == '\u0000' || it == '\r' || it == '\n' }) return false
    return true
}

internal fun isStoredPersistentCookieAllowed(stored: StoredCookie): Boolean {
    if (!isPersistentCookieDomainAllowed(stored.domain)) return false
    if (!isPersistentCookieNameAllowed(stored.name)) return false
    if (!isPersistentCookieValueAllowed(stored.value)) return false
    return true
}
