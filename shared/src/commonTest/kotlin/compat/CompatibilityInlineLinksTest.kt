package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs

class CompatibilityInlineLinksTest {
    @Test
    fun pathologicalLinkCountsAreBounded() {
        val html = List(2_000) { index -> "https://example.test/$index" }.joinToString(" ")

        assertTrue(compatInlineLinks(html).size <= 512)
    }

    @Test
    fun rawUrlIsMappedToDisplayedTextWithoutSentencePunctuation() {
        val links = compatInlineLinks("本文 https://example.test/a?q=1&x=2。")

        assertEquals(
            listOf(CompatInlineLink(3, 33, "https://example.test/a?q=1&x=2")),
            links
        )
    }

    @Test
    fun anchorWithNonUrlLabelOpensItsHttpHref() {
        val html = "本文 <a href=\"https://example.test/docs?a=1&amp;b=2\">公式サイト</a>"

        assertEquals(
            listOf(CompatInlineLink(3, 8, "https://example.test/docs?a=1&b=2")),
            compatInlineLinks(html)
        )
    }

    @Test
    fun bareApuSmallFilenameIsAnExternalMediaLink() {
        assertEquals(
            listOf(CompatInlineLink(3, 16, "https://dec.2chan.net/up2/src/fu1234567.jpg")),
            compatInlineLinks("本文 fu1234567.jpg")
        )
    }

    @Test
    fun legacySioAndVoirodaFilesOpenAtTheirReferenceEndpoints() {
        assertEquals(
            listOf(CompatInlineLink(3, 14, "http://www.nijibox6.com/futabafiles/001/src/sa12345.jpg")),
            compatInlineLinks("本文 sa12345.jpg")
        )
        assertEquals(
            listOf(CompatInlineLink(3, 10, "http://www.siokarabin.com/futabafiles/big/src/sz12345.html")),
            compatInlineLinks("本文 sz12345")
        )
        assertEquals(
            listOf(CompatInlineLink(3, 12, "https://voiroda.git-server.com/v/vo123.mp3")),
            compatInlineLinks("本文 vo123.mp3")
        )
    }

    @Test
    fun mailtoAndJavascriptLinksAreNotExternalBrowserTargets() {
        assertEquals(
            emptyList(),
            compatInlineLinks(
                "<a href=\"mailto:test@example.test\">mail</a> " +
                    "<a href=\"javascript:alert(1)\">危険</a>"
            )
        )
    }

    @Test
    fun futabaThreadLinksOpenInsideOnlyForRegisteredBoards() {
        val registered = resolveCompatInlineUrlRoute(
            "http://MAY.2CHAN.NET/b/res/12345.htm?ignored=1#reply",
            mapOf("https://may.2chan.net/b/" to "may-b")
        )
        val internal = assertIs<CompatInlineUrlRoute.RegisteredThread>(registered)
        assertEquals("may-b", internal.boardKey)
        assertEquals("https://may.2chan.net/b/res/12345.htm", internal.thread.canonicalUrl)

        assertIs<CompatInlineUrlRoute.UnregisteredThread>(
            resolveCompatInlineUrlRoute(
                "https://img.2chan.net/b/res/54321.htm",
                mapOf("https://may.2chan.net/b/" to "may-b")
            )
        )
        assertIs<CompatInlineUrlRoute.External>(
            resolveCompatInlineUrlRoute(
                "https://example.test/b/res/12345.htm",
                mapOf("https://may.2chan.net/b/" to "may-b")
            )
        )
    }

    @Test
    fun futabaLookalikeHostNeverRoutesInsideTheApp() {
        assertIs<CompatInlineUrlRoute.External>(
            resolveCompatInlineUrlRoute(
                "https://evil2chan.net/b/res/12345.htm",
                mapOf("https://evil2chan.net/b/" to "attacker")
            )
        )
    }
}
