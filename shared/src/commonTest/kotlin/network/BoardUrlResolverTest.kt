package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.model.CatalogMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BoardUrlResolverTest {
    @Test
    fun resolveCatalogUrl_appendsModeAndSort() {
        assertEquals(
            "https://may.2chan.net/b/futaba.php?mode=cat",
            BoardUrlResolver.resolveCatalogUrl(
                "https://may.2chan.net/b/futaba.php",
                CatalogMode.Catalog
            )
        )
        assertEquals(
            "https://may.2chan.net/b/futaba.php?mode=cat&sort=1",
            BoardUrlResolver.resolveCatalogUrl(
                "https://may.2chan.net/b/futaba.php",
                CatalogMode.New
            )
        )
    }

    @Test
    fun resolveThreadUrl_normalizesBoardBaseAndRejectsUnsafeThreadId() {
        assertEquals(
            "https://may.2chan.net/b/res/12345.htm",
            BoardUrlResolver.resolveThreadUrl("https://may.2chan.net/b/futaba.php", "12345")
        )

        assertFailsWith<IllegalArgumentException> {
            BoardUrlResolver.resolveThreadUrl("https://may.2chan.net/b/futaba.php", "../12345")
        }
    }

    @Test
    fun resolveBoardBaseUrl_stripsFileSegment_and_keepsDirectorySegment() {
        assertEquals(
            "https://may.2chan.net/b",
            BoardUrlResolver.resolveBoardBaseUrl("https://may.2chan.net/b/futaba.php")
        )
        assertEquals(
            "https://dec.2chan.net/img/b",
            BoardUrlResolver.resolveBoardBaseUrl("https://dec.2chan.net/img/b/")
        )
    }

    @Test
    fun resolveSiteRoot_and_slug_handlePorts() {
        assertEquals(
            "https://example.com:8443",
            BoardUrlResolver.resolveSiteRoot("https://example.com:8443/test/futaba.php")
        )
        assertEquals(
            "b",
            BoardUrlResolver.resolveBoardSlug("https://may.2chan.net/b/futaba.php")
        )
    }

    @Test
    fun sanitizePostId_allowsDigitsOnly() {
        assertEquals("123", BoardUrlResolver.sanitizePostId(" 123 "))
        assertEquals("", BoardUrlResolver.sanitizePostId("12a3"))
        assertEquals("", BoardUrlResolver.sanitizePostId("../123"))
    }
}
