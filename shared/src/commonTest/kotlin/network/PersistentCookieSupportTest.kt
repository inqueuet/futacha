package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.repository.InMemoryFileSystem
import io.ktor.http.Cookie
import io.ktor.util.date.GMTDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistentCookieSupportTest {
    @Test
    fun domainHelpers_resolveAcceptsSameHostAndParentDomain() {
        assertEquals(
            "dec.2chan.net",
            resolvePersistentCookieDomain(null, "dec.2chan.net")
        )
        assertEquals(
            "2chan.net",
            resolvePersistentCookieDomain(".2chan.net", "dec.2chan.net")
        )
        assertEquals(
            "may.2chan.net",
            resolvePersistentCookieDomain("may.2chan.net", "may.2chan.net")
        )
    }

    @Test
    fun domainHelpers_rejectPublicSuffixMismatchesAndInvalidHosts() {
        assertNull(resolvePersistentCookieDomain(".com", "dec.2chan.net"))
        assertNull(resolvePersistentCookieDomain("img.2chan.net", "dec.2chan.net"))
        assertNull(resolvePersistentCookieDomain("bad domain", "dec.2chan.net"))
        assertNull(resolvePersistentCookieDomain(".2chan.net", ""))
    }

    @Test
    fun domainHelpers_matchAllowedDomainsAndIpAddresses() {
        assertTrue(persistentCookieDomainMatches("dec.2chan.net", "2chan.net"))
        assertTrue(persistentCookieDomainMatches("dec.2chan.net", ".2chan.net"))
        assertFalse(persistentCookieDomainMatches("example.net", "2chan.net"))

        assertTrue(isPersistentCookieDomainAllowed("2chan.net"))
        assertTrue(isPersistentCookieDomainAllowed("192.168.0.1"))
        assertFalse(isPersistentCookieDomainAllowed("localhost"))
        assertFalse(isPersistentCookieDomainAllowed(".2chan.net"))
        assertFalse(isPersistentCookieDomainAllowed("bad domain"))

        assertTrue(isLikelyPersistentCookieIpAddress("192.168.0.1"))
        assertFalse(isLikelyPersistentCookieIpAddress("999.168.0.1"))
        assertFalse(isLikelyPersistentCookieIpAddress("2chan.net"))
    }

    @Test
    fun pathHelper_normalizesBlankAndRelativePaths() {
        assertEquals("/", normalizePersistentCookiePath(null))
        assertEquals("/", normalizePersistentCookiePath(" "))
        assertEquals("/b", normalizePersistentCookiePath("b"))
        assertEquals("/b/res", normalizePersistentCookiePath("/b/res"))
    }

    @Test
    fun expirationHelpers_resolveAndDeleteFromMaxAgeAndExpires() {
        val now = 1_000L

        assertTrue(
            shouldDeletePersistentCookie(
                Cookie(name = "a", value = "1", maxAge = 0),
                now
            )
        )
        assertTrue(
            shouldDeletePersistentCookie(
                Cookie(name = "a", value = "1", expires = GMTDate(now)),
                now
            )
        )
        assertEquals(
            6_000L,
            resolvePersistentCookieExpiresAt(
                Cookie(name = "a", value = "1", maxAge = 5),
                now
            )
        )
        assertNull(
            resolvePersistentCookieExpiresAt(
                Cookie(name = "a", value = "1", maxAge = -1),
                now
            )
        )
        assertEquals(
            9_000L,
            resolvePersistentCookieExpiresAt(
                Cookie(name = "a", value = "1", expires = GMTDate(9_000L)),
                now
            )
        )
    }

    @Test
    fun validationHelpers_rejectInvalidNameValueAndStoredCookie() {
        assertTrue(isPersistentCookieNameAllowed("cxyl"))
        assertFalse(isPersistentCookieNameAllowed(""))
        assertFalse(isPersistentCookieNameAllowed("bad name"))

        assertTrue(isPersistentCookieValueAllowed("abc123"))
        assertFalse(isPersistentCookieValueAllowed("bad\nvalue"))

        assertTrue(
            isStoredPersistentCookieAllowed(
                StoredCookie(
                    name = "cxyl",
                    value = "abc",
                    domain = "2chan.net",
                    path = "/"
                )
            )
        )
        assertFalse(
            isStoredPersistentCookieAllowed(
                StoredCookie(
                    name = "bad name",
                    value = "abc",
                    domain = "2chan.net",
                    path = "/"
                )
            )
        )
        assertFalse(
            isStoredPersistentCookieAllowed(
                StoredCookie(
                    name = "cxyl",
                    value = "abc",
                    domain = "localhost",
                    path = "/"
                )
            )
        )
    }

    @Test
    fun snapshotHelpers_restoreFromBackupWhenPrimaryIsBroken() = kotlinx.coroutines.runBlocking {
        val fileSystem = InMemoryFileSystem()
        val json = Json { ignoreUnknownKeys = true; prettyPrint = false; encodeDefaults = true }
        val snapshot = """{"version":1,"cookies":[{"name":"ok","value":"1","domain":"dec.2chan.net","path":"/"}]}"""

        fileSystem.writeString("private/cookies/test.json", "{broken").getOrThrow()
        fileSystem.writeString("private/cookies/test.json.backup", snapshot).getOrThrow()

        val loaded = loadPersistentCookieSnapshot(
            fileSystem = fileSystem,
            storagePath = "private/cookies/test.json",
            json = json,
            maxCookieFileBytes = 1024L,
            logTag = "PersistentCookieSupportTest"
        )

        assertNotNull(loaded)
        assertTrue(loaded.restoredFromBackup)
        assertEquals(listOf("ok"), loaded.cookies.map { it.name })
    }

    @Test
    fun snapshotHelpers_persistSnapshotCreatesBackup() = kotlinx.coroutines.runBlocking {
        val fileSystem = InMemoryFileSystem()
        fileSystem.writeString("private/cookies/test.json", """{"version":1,"cookies":[]}""").getOrThrow()

        persistPersistentCookieSnapshot(
            fileSystem = fileSystem,
            storagePath = "private/cookies/test.json",
            content = """{"version":1,"cookies":[{"name":"next","value":"1","domain":"dec.2chan.net","path":"/"}]}""",
            logTag = "PersistentCookieSupportTest"
        )

        assertEquals(
            """{"version":1,"cookies":[]}""",
            fileSystem.readString("private/cookies/test.json.backup").getOrThrow()
        )
        assertTrue(fileSystem.readString("private/cookies/test.json").getOrThrow().contains("next"))
    }

    @Test
    fun transactionCoordinator_commitAndRollbackFollowTransactionState() = kotlinx.coroutines.runBlocking {
        val coordinator = PersistentCookieTransactionCoordinator<String, String>("PersistentCookieSupportTest")
        val snapshot = linkedMapOf("a" to "1")
        val transactionId = coordinator.begin(snapshot)

        assertFalse(coordinator.shouldPersistImmediately(transactionId))
        assertTrue(coordinator.shouldPersistImmediately(null))

        coordinator.mutableSnapshotFor(transactionId)?.put("a", "2")
        coordinator.rollback(transactionId = transactionId)

        assertNull(coordinator.snapshotFor(transactionId))

        val committedId = coordinator.begin(snapshot)
        coordinator.mutableSnapshotFor(committedId)?.put("b", "2")
        coordinator.mutableSnapshotFor(committedId)?.remove("a")
        val applied = linkedMapOf("a" to "1", "external" to "3")
        val commitPayload = coordinator.commit(
            transactionId = committedId,
            applyChanges = { baseSnapshot, stagedSnapshot ->
                (baseSnapshot.keys + stagedSnapshot.keys).forEach { key ->
                    val stagedValue = stagedSnapshot[key]
                    if (baseSnapshot[key] == stagedValue) return@forEach
                    if (stagedValue == null) {
                        applied.remove(key)
                    } else {
                        applied[key] = stagedValue
                    }
                }
            },
            createSnapshot = { "committed" }
        )

        assertEquals("committed", commitPayload)
        assertEquals(linkedMapOf("external" to "3", "b" to "2"), applied)
        assertTrue(coordinator.shouldPersistImmediately(null))
    }

    @Test
    fun transactionCoordinator_supportsMultipleActiveTransactions() {
        val coordinator = PersistentCookieTransactionCoordinator<String, String>("PersistentCookieSupportTest")
        val snapshot = linkedMapOf("a" to "1")
        val firstId = coordinator.begin(snapshot)
        val secondId = coordinator.begin(snapshot)

        coordinator.mutableSnapshotFor(firstId)?.put("first", "2")
        coordinator.mutableSnapshotFor(secondId)?.put("second", "3")

        assertFalse(coordinator.shouldPersistImmediately(firstId))
        assertFalse(coordinator.shouldPersistImmediately(secondId))
        assertEquals("2", coordinator.snapshotFor(firstId)?.get("first"))
        assertEquals("3", coordinator.snapshotFor(secondId)?.get("second"))

        coordinator.rollback(firstId)
        assertNull(coordinator.snapshotFor(firstId))
        assertEquals("3", coordinator.snapshotFor(secondId)?.get("second"))
    }
}
