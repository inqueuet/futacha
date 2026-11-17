package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.network.PersistentCookieStorage
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.http.Cookie
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Uses only pre-fix APIs so the same tests can establish the baseline. */
class FtutterM39CookieRegressionTest {
    @Test fun confirmationExplainsCookieSharingAndConflictExclusionWithoutExposingValues() {
        val plan = plan("ptmt=private-test-value; session=another-test-value")
        assertTrue(plan.summary.contains("Cookie 2件"))
        assertTrue(plan.summary.contains("全モード共有"))
        assertTrue(plan.summary.contains("同名"))
        assertFalse(plan.summary.contains("private-test-value"))
        assertFalse(plan.toString().contains("another-test-value"))
    }

    @Test fun existingHostOnlyCookieKeepsItsValueAndScopeWithoutADomainDuplicate() = runBlocking {
        val storage = PersistentCookieStorage(InMemoryFileSystem())
        val repository = CookieRepository(storage)
        storage.addCookie(Url("https://may.2chan.net/b/"),
            Cookie("ptmt", "existing", path = "/b", secure = true, httpOnly = true))
        val before = repository.listCookies()
        assertEquals(0, ftutterImportCookies(repository, "ptmt=imported"))
        assertEquals(before, repository.listCookies())
        assertEquals(listOf("existing"), repository.getCookiesFor("https://may.2chan.net/b/").map { it.value })
        assertTrue(repository.getCookiesFor("https://img.2chan.net/b/").isEmpty())
        assertTrue(repository.getCookiesFor("http://may.2chan.net/b/").isEmpty())
    }

    @Test fun existingDomainCookieAlsoWinsWhileNonconflictingNamesAreImported() = runBlocking {
        val storage = PersistentCookieStorage(InMemoryFileSystem())
        val repository = CookieRepository(storage)
        repository.setCookie("https://www.2chan.net/", "ptmt", "existing", domain = "2chan.net")
        assertEquals(1, ftutterImportCookies(repository, "ptmt=imported; session=new"))
        assertEquals(mapOf("ptmt" to "existing", "session" to "new"),
            repository.getCookiesFor("https://img.2chan.net/b/").associate { it.name to it.value })
    }

    @Test fun anExplicitImportReportsDiskFailureInsteadOfClaimingPersistence() = runBlocking<Unit> {
        val base = InMemoryFileSystem()
        val fs = object : FileSystem by base {
            override suspend fun writeString(path: String, content: String): Result<Unit> =
                Result.failure(IllegalStateException("test disk full"))
        }
        val repository = CookieRepository(PersistentCookieStorage(fs))
        assertFailsWith<IllegalStateException> { ftutterImportCookies(repository, "ptmt=temporary") }
        assertEquals("temporary", repository.getCookiesFor("https://may.2chan.net/b/").single().value)
    }

    @Test fun unrelatedSitesDoNotBlockTheImportAndValuesPersistForFutabaOnly() = runBlocking {
        val fs = InMemoryFileSystem()
        val storage = PersistentCookieStorage(fs)
        storage.addCookie(Url("https://2chan.net.example/"), Cookie("ptmt", "unrelated", path = "/"))
        val repository = CookieRepository(storage)
        assertEquals(1, ftutterImportCookies(repository, "ptmt=imported"))
        val reopened = CookieRepository(PersistentCookieStorage(fs))
        assertEquals("imported", reopened.getCookiesFor("https://may.2chan.net/b/").single().value)
        assertEquals("imported", reopened.getCookiesFor("https://img.2chan.net/b/").single().value)
        assertEquals("unrelated", reopened.getCookiesFor("https://2chan.net.example/").single().value)
        assertTrue(reopened.getCookiesFor("https://example.com/").isEmpty())
    }

    @Test fun anEmptyOrInvalidHeaderDoesNotOfferACookieImport() {
        for (header in listOf("", "not-a-cookie", "a=" + "x".repeat(2_049))) {
            assertFalse(plan(header).summary.contains("Cookie"))
        }
    }

    private fun plan(header: String) = ftutterBackupPlan(mapOf("cookie" to FtutterPrefValue.Text(header)),
        emptyList(), emptyList(), emptyMap(), 0)
}
