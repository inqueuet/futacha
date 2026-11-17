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

    @Test
    fun commitEvenOnFailure_persistsViaRepository() = runBlocking {
        val storage = PersistentCookieStorage(InMemoryFileSystem(), STORAGE_PATH)
        val repository = CookieRepository(storage)

        assertFailsWith<IllegalStateException> {
            repository.commitEvenOnFailure {
                storage.addCookie(
                    Url("https://dec.2chan.net/b/"),
                    Cookie(name = "posttime", value = "1", domain = "dec.2chan.net", path = "/")
                )
                error("post failed")
            }
        }

        assertEquals(listOf("posttime"), repository.listCookies().map { it.name })
    }

    @Test
    fun webViewCookieBridgeExportsMatchingCookiesAndImportsBoundedHeader() = runBlocking {
        val storage = PersistentCookieStorage(InMemoryFileSystem(), STORAGE_PATH)
        val repository = CookieRepository(storage)
        storage.addCookie(
            Url("https://lens.google.com/uploadbyurl"),
            Cookie(name = "session", value = "before", path = "/", secure = true)
        )
        storage.addCookie(
            Url("https://example.com/"),
            Cookie(name = "other", value = "ignored", path = "/", secure = true)
        )

        assertEquals(
            listOf("session"),
            repository.getCookiesFor("https://lens.google.com/search").map { it.name }
        )
        assertEquals(
            2,
            repository.importCookieHeader(
                "https://lens.google.com/search",
                "session=after; token=value=with=equals; invalid"
            )
        )
        assertEquals(
            mapOf("session" to "after", "token" to "value=with=equals"),
            repository.getCookiesFor("https://lens.google.com/").associate { it.name to it.value }
        )
        assertEquals(0, repository.importCookieHeader("javascript:alert(1)", "x=1"))
        assertTrue(parseWebViewCookieHeader("x=" + "a".repeat(2_049)).isEmpty())
    }

    companion object {
        private const val STORAGE_PATH = "private/cookies/repository-test-cookies.json"
    }
}
