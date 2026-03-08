package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.network.StoredCookie
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class CookieManagementSupportTest {
    @Test
    fun buildCookieDomainSections_groupsByDomainWhilePreservingInputOrder() {
        val cookies = listOf(
            StoredCookie(name = "b", value = "2", domain = "may.2chan.net", path = "/"),
            StoredCookie(name = "a", value = "1", domain = "dec.2chan.net", path = "/"),
            StoredCookie(name = "c", value = "3", domain = "may.2chan.net", path = "/img")
        )

        val sections = buildCookieDomainSections(cookies)

        assertEquals(listOf("may.2chan.net", "dec.2chan.net"), sections.map { it.domain })
        assertEquals(listOf("b", "c"), sections[0].cookies.map { it.name })
        assertEquals(listOf("a"), sections[1].cookies.map { it.name })
    }

    @Test
    fun formatCookieExpiresLabel_returnsSessionForNull() {
        assertEquals("セッション", formatCookieExpiresLabel(null, TimeZone.UTC))
    }

    @Test
    fun formatCookieExpiresLabel_formatsTimestampInProvidedTimeZone() {
        assertEquals(
            "2026/01/02 03:04",
            formatCookieExpiresLabel(
                expiresAtMillis = 1767323040000L,
                timeZone = TimeZone.UTC
            )
        )
    }
}
