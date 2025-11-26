package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.network.PersistentCookieStorage
import com.valoser.futacha.shared.network.StoredCookie

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
}
