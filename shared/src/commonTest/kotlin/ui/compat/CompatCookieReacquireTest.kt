package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.network.PersistentCookieStorage
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class CompatCookieReacquireTest {
    @Test fun futabasCookiesButTheHandSetPtmtAreRemoved() = runBlocking {
        val repository = CookieRepository(PersistentCookieStorage(InMemoryFileSystem()))
        repository.setCookie("https://may.2chan.net/", "posttime", "1", domain = "may.2chan.net")
        repository.setCookie("https://www.2chan.net/", "cxyl", "2", domain = ".2chan.net")
        repository.setCookie("https://www.2chan.net/", "ptmt", "5", domain = ".2chan.net")
        repository.setCookie("https://ascii2d.net/", "keep", "3", domain = "ascii2d.net")
        assertEquals(2, compatReacquireFutabaCookies(repository))
        assertEquals(listOf("keep", "ptmt"), repository.listCookies().map { it.name }.sorted())
    }

    @Test fun theRowIsInTheBackupSettingsNextToPtmt() {
        assertTrue(COMPAT_COOKIE_REACQUIRE_TEXT.contains("ptmtは残します"))
        val extensions = compatRootSettingsGroups("1.0").first { it.first == "ふたちゃ拡張" }.second
        assertTrue(extensions.any { it.preferenceKey == COMPAT_COOKIE_REACQUIRE_KEY })
    }
}
