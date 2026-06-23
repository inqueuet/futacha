package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.repository.InMemoryFileSystem
import io.ktor.http.Cookie
import io.ktor.http.Url
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
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
    fun commitEvenOnFailure_persistsFailedMutationsAndRethrows() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val storage = PersistentCookieStorage(fileSystem, STORAGE_PATH)

        storage.addCookie(
            Url("https://dec.2chan.net/b/"),
            Cookie(name = "initial", value = "1", domain = "dec.2chan.net", path = "/")
        )

        assertFailsWith<IllegalStateException> {
            storage.commitEvenOnFailure {
                storage.addCookie(
                    Url("https://dec.2chan.net/b/"),
                    Cookie(name = "posttime", value = "2", domain = "dec.2chan.net", path = "/")
                )
                error("post failed")
            }
        }

        assertEquals(setOf("initial", "posttime"), storage.listCookies().map { it.name }.toSet())
        assertTrue(storage.hasValidCookieFor(Url("https://dec.2chan.net/b/res/123.htm"), preferredNames = setOf("posttime")))
    }

    @Test
    fun commitOnSuccess_doesNotSerializeBlockExecution() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val storage = PersistentCookieStorage(fileSystem, STORAGE_PATH)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondFinished = CompletableDeferred<Unit>()

        val first = async {
            storage.commitOnSuccess {
                firstStarted.complete(Unit)
                releaseFirst.await()
                storage.addCookie(
                    Url("https://dec.2chan.net/b/"),
                    Cookie(name = "first", value = "1", domain = "dec.2chan.net", path = "/")
                )
            }
        }
        firstStarted.await()

        val second = async {
            storage.commitOnSuccess {
                storage.addCookie(
                    Url("https://dec.2chan.net/b/"),
                    Cookie(name = "second", value = "2", domain = "dec.2chan.net", path = "/")
                )
                secondFinished.complete(Unit)
            }
        }

        withTimeout(1_000L) {
            secondFinished.await()
        }
        releaseFirst.complete(Unit)
        first.await()
        second.await()

        assertEquals(setOf("first", "second"), storage.listCookies().map { it.name }.toSet())
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
        val persisted = decodePersistedCookieFile(fileSystem.readString(STORAGE_PATH).getOrThrow())
        assertTrue(persisted.revision > 0L)
        assertTrue(persisted.cookies.isEmpty())
    }

    @Test
    fun concurrentAddCookie_persistsReloadableLatestSnapshot() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val storage = PersistentCookieStorage(fileSystem, STORAGE_PATH)

        (0 until 32).map { index ->
            async {
                storage.addCookie(
                    Url("https://dec.2chan.net/b/"),
                    Cookie(
                        name = "c$index",
                        value = "v$index",
                        domain = "dec.2chan.net",
                        path = "/"
                    )
                )
            }
        }.awaitAll()

        val persisted = decodePersistedCookieFile(fileSystem.readString(STORAGE_PATH).getOrThrow())
        assertEquals(32, persisted.cookies.size)
        assertTrue(persisted.revision >= 32L)

        val restoredStorage = PersistentCookieStorage(fileSystem, STORAGE_PATH)
        assertEquals(
            (0 until 32).map { "c$it" }.toSet(),
            restoredStorage.listCookies().map { it.name }.toSet()
        )
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
        private val COOKIE_JSON = Json { ignoreUnknownKeys = true; prettyPrint = false; encodeDefaults = true }

        private fun decodePersistedCookieFile(content: String): StoredCookieFile {
            return COOKIE_JSON.decodeFromString(StoredCookieFile.serializer(), content)
        }
    }
}
