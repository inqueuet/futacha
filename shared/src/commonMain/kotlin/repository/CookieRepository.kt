package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.network.PersistentCookieStorage
import com.valoser.futacha.shared.network.StoredCookie
import io.ktor.http.Url

class CookieRepository(
    private val storage: PersistentCookieStorage
) {
    suspend fun listCookies(): List<StoredCookie> = storage.listCookies()

    suspend fun deleteCookie(domain: String, path: String, name: String) {
        storage.removeCookie(domain, path, name)
    }

    suspend fun clearAll() {
        storage.clearAll()
    }

    suspend fun <T> commitOnSuccess(block: suspend () -> T): T {
        return storage.commitOnSuccess(block)
    }

    suspend fun hasValidCookieFor(url: String, preferredNames: Set<String> = emptySet()): Boolean {
        val parsedUrl = runCatching { Url(url) }.getOrElse { return false }
        return storage.hasValidCookieFor(parsedUrl, preferredNames)
    }
}
