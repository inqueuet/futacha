package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.network.PersistentCookieStorage
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class FtutterCookieReacquireTest {
    @Test fun onlyFutabasCookiesAreRemoved() = runBlocking {
        val repository = CookieRepository(PersistentCookieStorage(InMemoryFileSystem()))
        repository.setCookie("https://may.2chan.net/", "posttime", "1", domain = "may.2chan.net")
        repository.setCookie("https://www.2chan.net/", "cxyl", "2", domain = ".2chan.net")
        repository.setCookie("https://ascii2d.net/", "keep", "3", domain = "ascii2d.net")
        repository.setCookie("https://not2chan.net/", "keep2", "4", domain = "not2chan.net")
        assertEquals(2, ftutterReacquireCookies(repository))
        assertEquals(listOf("keep", "keep2"), repository.listCookies().map { it.name }.sorted())
    }

    @Test fun theDomainCheck() {
        assertTrue(ftutterIsFutabaCookieDomain(".2chan.net"))
        assertTrue(ftutterIsFutabaCookieDomain("IMG.2chan.net"))
        assertFalse(ftutterIsFutabaCookieDomain("not2chan.net"))
        assertFalse(ftutterIsFutabaCookieDomain("2chan.net.example.com"))
    }
}
