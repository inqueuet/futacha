package com.valoser.futacha.shared.network

import io.ktor.http.Cookie
import io.ktor.util.date.GMTDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
}
