package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.repository.InMemoryFileSystem
import io.ktor.http.Cookie
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersistentCookieStorageTest {
    @Test
    fun get_and_hasValidCookieFor_applySecureDomainPathAndPreferredNameFilters() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val storage = PersistentCookieStorage(fileSystem, STORAGE_PATH)

        storage.addCookie(
            Url("https://dec.2chan.net/b/"),
            Cookie(
                name = "cxyl",
                value = "abc",
                domain = ".2chan.net",
                path = "/b",
                secure = true
            )
        )
        storage.addCookie(
            Url("https://dec.2chan.net/b/"),
            Cookie(
                name = "path_only",
                value = "def",
                domain = "dec.2chan.net",
                path = "/img"
            )
        )

        val secureCookies = storage.get(Url("https://may.2chan.net/b/res/123.htm"))
        val insecureCookies = storage.get(Url("http://may.2chan.net/b/res/123.htm"))
        val wrongPathCookies = storage.get(Url("https://dec.2chan.net/img/futaba.php"))

        assertEquals(listOf("cxyl"), secureCookies.map { it.name })
        assertTrue(insecureCookies.isEmpty())
        assertEquals(listOf("path_only"), wrongPathCookies.map { it.name })
        assertTrue(storage.hasValidCookieFor(Url("https://may.2chan.net/b/res/123.htm")))
        assertTrue(
            storage.hasValidCookieFor(
                Url("https://may.2chan.net/b/res/123.htm"),
                preferredNames = setOf("cxyl")
            )
        )
        assertFalse(
            storage.hasValidCookieFor(
                Url("https://may.2chan.net/b/res/123.htm"),
                preferredNames = setOf("nope")
            )
        )
    }

    @Test
    fun commitOnSuccess_rollsBackFailedMutationsAndPreservesPersistedSnapshot() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val storage = PersistentCookieStorage(fileSystem, STORAGE_PATH)

        storage.addCookie(
            Url("https://dec.2chan.net/b/"),
            Cookie(name = "initial", value = "1", domain = "dec.2chan.net", path = "/")
        )
        val snapshotBefore = fileSystem.readString(STORAGE_PATH).getOrThrow()

        assertFailsWith<IllegalStateException> {
            storage.commitOnSuccess {
                storage.addCookie(
                    Url("https://dec.2chan.net/b/"),
                    Cookie(name = "staged", value = "2", domain = "dec.2chan.net", path = "/")
                )
                error("boom")
            }
        }

        assertEquals(listOf("initial"), storage.listCookies().map { it.name })
        assertEquals(snapshotBefore, fileSystem.readString(STORAGE_PATH).getOrThrow())
    }

    @Test
    fun listCookies_restoresFromBackupWhenPrimaryFileIsCorrupted() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val seededStorage = PersistentCookieStorage(fileSystem, STORAGE_PATH)

        seededStorage.addCookie(
            Url("https://dec.2chan.net/b/"),
            Cookie(name = "backup_cookie", value = "ok", domain = "dec.2chan.net", path = "/")
        )

        val backupSnapshot = fileSystem.readString(STORAGE_PATH).getOrThrow()
        fileSystem.writeString("$STORAGE_PATH.backup", backupSnapshot).getOrThrow()
        fileSystem.writeString(STORAGE_PATH, "{broken-json").getOrThrow()

        val restoredStorage = PersistentCookieStorage(fileSystem, STORAGE_PATH)
        val cookies = restoredStorage.listCookies()

        assertEquals(listOf("backup_cookie"), cookies.map { it.name })
        assertTrue(
            restoredStorage.hasValidCookieFor(
                Url("https://dec.2chan.net/b/res/123.htm"),
                preferredNames = setOf("backup_cookie")
            )
        )
    }

    @Test
    fun removeCookie_and_clearAll_updatePersistedStorage() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val storage = PersistentCookieStorage(fileSystem, STORAGE_PATH)

        storage.addCookie(
            Url("https://dec.2chan.net/b/"),
            Cookie(name = "a", value = "1", domain = "dec.2chan.net", path = "/")
        )
        storage.addCookie(
            Url("https://dec.2chan.net/b/"),
            Cookie(name = "b", value = "2", domain = "dec.2chan.net", path = "/")
        )

        storage.removeCookie("dec.2chan.net", "/", "a")
        assertEquals(listOf("b"), storage.listCookies().map { it.name })
        assertFalse(
            storage.hasValidCookieFor(
                Url("https://dec.2chan.net/b/res/123.htm"),
                preferredNames = setOf("a")
            )
        )

        storage.clearAll()
        assertTrue(storage.listCookies().isEmpty())
        assertEquals("""{"version":1,"cookies":[]}""", fileSystem.readString(STORAGE_PATH).getOrThrow())
    }

    @Test
    fun listCookies_purgesExpiredCookiesLoadedFromDisk() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        fileSystem.writeString(
            STORAGE_PATH,
            """
                {"version":1,"cookies":[
                  {"name":"expired","value":"1","domain":"dec.2chan.net","path":"/","expiresAtMillis":0,"createdAtMillis":0},
                  {"name":"active","value":"2","domain":"dec.2chan.net","path":"/","createdAtMillis":1}
                ]}
            """.trimIndent()
        ).getOrThrow()

        val storage = PersistentCookieStorage(fileSystem, STORAGE_PATH)
        val cookies = storage.listCookies()

        assertEquals(listOf("active"), cookies.map { it.name })
        val persisted = fileSystem.readString(STORAGE_PATH).getOrThrow()
        assertTrue(persisted.contains("active"))
        assertFalse(persisted.contains("expired"))
    }

    @Test
    fun listCookies_enforcesPerDomainCapacityByEvictingOldestCookie() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val cookieJson = (0..64).joinToString(separator = ",") { index ->
            """
                {"name":"c$index","value":"v$index","domain":"dec.2chan.net","path":"/","createdAtMillis":$index}
            """.trimIndent()
        }
        fileSystem.writeString(
            STORAGE_PATH,
            """{"version":1,"cookies":[$cookieJson]}"""
        ).getOrThrow()

        val storage = PersistentCookieStorage(fileSystem, STORAGE_PATH)
        val cookies = storage.listCookies()
        val names = cookies.map { it.name }.toSet()

        assertEquals(64, cookies.size)
        assertFalse("c0" in names)
        assertTrue("c1" in names)
        assertTrue("c64" in names)
    }

    @Test
    fun listCookies_enforcesTotalCapacityByEvictingOldestCookie() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val cookieJson = (0..256).joinToString(separator = ",") { index ->
            """
                {"name":"t$index","value":"v$index","domain":"d$index.2chan.net","path":"/","createdAtMillis":$index}
            """.trimIndent()
        }
        fileSystem.writeString(
            STORAGE_PATH,
            """{"version":1,"cookies":[$cookieJson]}"""
        ).getOrThrow()

        val storage = PersistentCookieStorage(fileSystem, STORAGE_PATH)
        val cookies = storage.listCookies()
        val names = cookies.map { it.name }.toSet()

        assertEquals(256, cookies.size)
        assertFalse("t0" in names)
        assertTrue("t1" in names)
        assertTrue("t256" in names)
    }

    companion object {
        private const val STORAGE_PATH = "private/cookies/test-cookies.json"
    }
}
