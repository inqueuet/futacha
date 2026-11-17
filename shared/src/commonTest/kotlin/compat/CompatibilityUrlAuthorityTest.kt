package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** S4-1: only a plain official Futaba host may pass canonicalization. */
class CompatibilityUrlAuthorityTest {
    private val bypassThreadUrls = listOf(
        // Ktor/OkHttp/browsers end the authority at `\`: these go to evil.com.
        "https://evil.com\\@may.2chan.net/b/res/1.htm",
        "https://evil.com\\.2chan.net/b/res/1.htm",
        "https://evil.com\\may.2chan.net/b/res/1.htm",
        "https://may.2chan.net\\@evil.com/b/res/1.htm",
        // What futacha://thread?url=https%3A%2F%2Fevil.com%5C%40may.2chan.net%2F... decodes to.
        "https://evil.com\\@may.2chan.net/b/res/12345.htm?x=1#y",
        // Userinfo before or after the host.
        "https://evil.com@may.2chan.net/b/res/1.htm",
        "https://may.2chan.net@evil.com/b/res/1.htm",
        "https://user:pass@may.2chan.net/b/res/1.htm",
        "https://@may.2chan.net/b/res/1.htm",
        // Percent-encoded host characters.
        "https://evil.com%40may.2chan.net/b/res/1.htm",
        "https://evil.com%5C%40may.2chan.net/b/res/1.htm",
        "https://may%2e2chan.net/b/res/1.htm",
        // Ports (never part of a Futaba link) and malformed labels.
        "https://may.2chan.net:8080/b/res/1.htm",
        "https://may.2chan.net:443/b/res/1.htm",
        "https://.2chan.net/b/res/1.htm",
        "https://-may.2chan.net/b/res/1.htm",
        "https://may.2chan.net./b/res/1.htm",
        // Whitespace/control characters that other parsers drop or split on.
        "https://evil.com\t.2chan.net/b/res/1.htm",
        "https://evil.com\n.2chan.net/b/res/1.htm",
        "https://may.2chan.net/b\n/res/1.htm",
        "https://may.2chan.net/b /res/1.htm",
        // Backslash in the path.
        "https://may.2chan.net/\\evil.com/res/1.htm",
        "https://may.2chan.net/b\\..\\x/res/1.htm",
        // Non-ASCII look-alikes, including one that lowercases to ASCII (Kelvin sign).
        "https://mаy.2chan.net/b/res/1.htm",
        "https://K.2chan.net/b/res/1.htm"
    )

    @Test fun threadUrlBypassesAreRejected() {
        bypassThreadUrls.forEach { url ->
            assertNull(canonicalizeThreadUrl(url), url)
        }
    }

    @Test fun boardUrlBypassesAreRejected() {
        bypassThreadUrls
            .map { it.replace(Regex("res/[0-9]+\\.htm.*$"), "futaba.htm") }
            .plus(
                listOf(
                    "https://evil.com\\@may.2chan.net/b/",
                    "https://evil.com\\@may.2chan.net",
                    "https://evil.com@www.2chan.net/b/futaba.php?mode=cat"
                )
            )
            .forEach { url -> assertNull(canonicalizeBoardUrl(url), url) }
    }

    @Test fun officialUrlsAreStillCanonicalized() {
        assertEquals(
            "https://may.2chan.net/b/res/12345.htm",
            canonicalizeThreadUrl("  HTTPS://MAY.2CHAN.NET/b/res/12345.htm?q=a\\b c#x\\y  ")?.canonicalUrl
        )
        assertEquals(
            "https://img.2chan.net/b/res/1.htm",
            canonicalizeThreadUrl("http://img.2chan.net//b/res/1.htm/")?.canonicalUrl
        )
        assertEquals("https://2chan.net/b/", canonicalizeBoardUrl("https://2chan.net/b/futaba.htm"))
        assertEquals("https://www.2chan.net/", canonicalizeBoardUrl("https://www.2chan.net"))
        assertEquals("https://dec.2chan.net/up2/", canonicalizeBoardUrl("https://dec.2chan.net/up2/futaba.php?mode=cat"))
        assertEquals("https://jun-a.2chan.net/jun/", canonicalizeBoardUrl("https://jun-a.2chan.net/jun/"))
    }
}
