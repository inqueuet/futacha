package com.valoser.futacha.shared.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DesktopUrlTest {
    @Test
    fun plainUrlIsParsedUnchanged() {
        assertEquals("https://example.com/a/b?x=1#top", desktopUriOrNull("https://example.com/a/b?x=1#top").toString())
        assertEquals("mailto:a@example.com", desktopUriOrNull("mailto:a@example.com").toString())
    }

    @Test
    fun nonAsciiPathAndQueryArePercentEncoded() {
        val uri = assertNotNull(desktopUriOrNull("https://example.com/検索?q=ふたば"))
        assertEquals("https://example.com/%E6%A4%9C%E7%B4%A2?q=%E3%81%B5%E3%81%9F%E3%81%B0", uri.toString())
    }

    @Test
    fun charactersJavaUriRejectedAreEncoded() {
        val uri = assertNotNull(desktopUriOrNull("https://example.com/a|b[1]{2}^c?x=[y] z"))
        assertEquals("https://example.com/a%7Cb%5B1%5D%7B2%7D%5Ec?x=%5By%5D%20z", uri.toString())
    }

    @Test
    fun existingPercentEscapesAreKeptAndStrayPercentIsEncoded() {
        assertEquals(
            "https://example.com/a%20b|".replace("|", "%7C"),
            desktopUriOrNull("https://example.com/a%20b|").toString()
        )
        assertEquals("https://example.com/100%25", desktopUriOrNull("https://example.com/100%").toString())
    }

    @Test
    fun internationalisedHostBecomesPunycode() {
        val uri = assertNotNull(desktopUriOrNull("https://日本語.jp:8080/パス"))
        assertEquals("xn--wgv71a119e.jp", uri.host)
        assertEquals(8080, uri.port)
        assertEquals("/%E3%83%91%E3%82%B9", uri.rawPath)
    }

    @Test
    fun mailtoWithNonAsciiSubjectIsEncodedAndKeepsItsScheme() {
        val uri = assertNotNull(desktopUriOrNull("mailto:a@example.com?subject=件名"))
        assertEquals("mailto", uri.scheme)
        assertEquals("mailto:a@example.com?subject=%E4%BB%B6%E5%90%8D", uri.toString())
    }

    @Test
    fun blankOrHopelessInputIsNull() {
        assertNull(desktopUriOrNull("   "))
        assertNull(desktopUriOrNull(""))
    }

    @Test
    fun onlyWebAndMailLinksPassTheSchemeCheck() {
        val failure = runCatching { openDesktopUrl("file:///etc/passwd") }.exceptionOrNull()
        assertEquals("このリンクは開けません", failure?.message)
        val javascript = runCatching { openDesktopUrl("javascript:alert(1)") }.exceptionOrNull()
        assertEquals("このリンクは開けません", javascript?.message)
    }
}
