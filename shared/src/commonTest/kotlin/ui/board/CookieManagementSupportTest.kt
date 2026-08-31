package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.network.StoredCookie
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

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

    @Test
    fun cookieReloadHelpers_onlyApplyLatestGeneration() {
        assertEquals(
            CookieReloadState(
                reloadGeneration = 4L,
                isLoading = true
            ),
            beginCookieReload(3L)
        )
        assertEquals(
            CookieReloadResult(
                cookies = listOf(cookie(name = "fresh")),
                isLoading = false,
                shouldApply = true
            ),
            applyCookieReloadResult(
                currentGeneration = 4L,
                requestGeneration = 4L,
                cookies = listOf(cookie(name = "fresh")),
                isLoading = true
            )
        )
        assertEquals(
            CookieReloadResult(
                cookies = listOf(cookie(name = "stale")),
                isLoading = true,
                shouldApply = false
            ),
            applyCookieReloadResult(
                currentGeneration = 5L,
                requestGeneration = 4L,
                cookies = listOf(cookie(name = "stale")),
                isLoading = true
            )
        )
    }

    @Test
    fun cookieManagementContentState_andMessages_followUiRules() {
        assertIs<CookieManagementContentState.Loading>(
            resolveCookieManagementContentState(
                isLoading = true,
                cookies = listOf(cookie())
            )
        )
        assertIs<CookieManagementContentState.Empty>(
            resolveCookieManagementContentState(
                isLoading = false,
                cookies = emptyList()
            )
        )
        val dataState = resolveCookieManagementContentState(
            isLoading = false,
            cookies = listOf(cookie(domain = "may.2chan.net"), cookie(name = "b", domain = "dec.2chan.net"))
        )
        assertIs<CookieManagementContentState.Data>(dataState)
        assertEquals(listOf("may.2chan.net", "dec.2chan.net"), dataState.sections.map { it.domain })
        assertFalse(shouldShowCookieClearAllAction(emptyList()))
        assertTrue(shouldShowCookieClearAllAction(listOf(cookie())))
        assertEquals("削除しました: cxyl", buildCookieDeleteMessage("cxyl"))
        assertEquals("すべてのCookieを削除しました", buildCookieClearAllMessage())
    }

    private fun cookie(
        name: String = "a",
        domain: String = "may.2chan.net"
    ): StoredCookie {
        return StoredCookie(
            name = name,
            value = "value-$name",
            domain = domain,
            path = "/",
            createdAtMillis = 1_700_000_000_000L
        )
    }
}
