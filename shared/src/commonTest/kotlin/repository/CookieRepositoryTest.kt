package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.network.PersistentCookieStorage
import io.ktor.http.Cookie
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CookieRepositoryTest {
    @Test
    fun hasValidCookieFor_returnsFalseForInvalidUrlAndMatchesPreferredNames() = runBlocking {
        val storage = PersistentCookieStorage(InMemoryFileSystem(), STORAGE_PATH)
        val repository = CookieRepository(storage)

        storage.addCookie(
            Url("https://dec.2chan.net/b/"),
            Cookie(name = "cxyl", value = "abc", domain = "dec.2chan.net", path = "/")
        )

        assertFalse(repository.hasValidCookieFor("not a url"))
        assertTrue(repository.hasValidCookieFor("https://dec.2chan.net/b/res/123.htm"))
        assertTrue(
            repository.hasValidCookieFor(
                "https://dec.2chan.net/b/res/123.htm",
                preferredNames = setOf("cxyl")
            )
        )
        assertFalse(
            repository.hasValidCookieFor(
                "https://dec.2chan.net/b/res/123.htm",
                preferredNames = setOf("sid")
            )
        )
    }

    @Test
    fun deleteCookie_and_clearAll_delegateToStorage() = runBlocking {
        val storage = PersistentCookieStorage(InMemoryFileSystem(), STORAGE_PATH)
        val repository = CookieRepository(storage)

        storage.addCookie(
            Url("https://dec.2chan.net/b/"),
            Cookie(name = "a", value = "1", domain = "dec.2chan.net", path = "/")
        )
        storage.addCookie(
            Url("https://dec.2chan.net/b/"),
            Cookie(name = "b", value = "2", domain = "dec.2chan.net", path = "/")
        )

        repository.deleteCookie("dec.2chan.net", "/", "a")
        assertEquals(listOf("b"), repository.listCookies().map { it.name })

        repository.clearAll()
        assertTrue(repository.listCookies().isEmpty())
    }

    @Test
    fun commitOnSuccess_rollsBackViaRepository() = runBlocking {
        val storage = PersistentCookieStorage(InMemoryFileSystem(), STORAGE_PATH)
        val repository = CookieRepository(storage)

        storage.addCookie(
            Url("https://dec.2chan.net/b/"),
            Cookie(name = "initial", value = "1", domain = "dec.2chan.net", path = "/")
        )

        assertFailsWith<IllegalStateException> {
            repository.commitOnSuccess {
                storage.addCookie(
                    Url("https://dec.2chan.net/b/"),
                    Cookie(name = "temp", value = "2", domain = "dec.2chan.net", path = "/")
                )
                error("boom")
            }
        }

        assertEquals(listOf("initial"), repository.listCookies().map { it.name })
    }

    companion object {
        private const val STORAGE_PATH = "private/cookies/repository-test-cookies.json"
    }
}
