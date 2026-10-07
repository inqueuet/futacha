package com.valoser.futacha.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class FutachaBoardUrlNormalizeTest {
    @Test
    fun boardPageAndThreadUrlsBecomeTheBoardFutabaPhp() {
        assertEquals("https://may.2chan.net/b/futaba.php", normalizeBoardUrl("https://may.2chan.net/b/futaba.htm"))
        assertEquals("https://may.2chan.net/b/futaba.php", normalizeBoardUrl("may.2chan.net/b/futaba.htm"))
        assertEquals("https://may.2chan.net/b/futaba.php", normalizeBoardUrl("https://may.2chan.net/b/res/123456789.htm"))
        assertEquals("https://may.2chan.net/b/futaba.php", normalizeBoardUrl("https://may.2chan.net/b/res/123456789.html"))
        assertEquals("https://may.2chan.net/b/futaba.php", normalizeBoardUrl("https://may.2chan.net/b/res/123456789.htm?x=1#r5"))
        assertEquals("https://may.2chan.net/b/futaba.php", normalizeBoardUrl("https://may.2chan.net/b/futaba.htm#top"))
        assertEquals("http://may.2chan.net/img/futaba.php", normalizeBoardUrl("http://may.2chan.net/img/res/9.htm"))
    }

    @Test
    fun existingBoardUrlFormsAreUnchanged() {
        assertEquals("https://may.2chan.net/b/futaba.php", normalizeBoardUrl("may.2chan.net/b"))
        assertEquals("https://may.2chan.net/b/futaba.php", normalizeBoardUrl("https://may.2chan.net/b/"))
        assertEquals("https://may.2chan.net/b/futaba.php?mode=cat#frag", normalizeBoardUrl("https://may.2chan.net/b?mode=cat#frag"))
        assertEquals("http://may.2chan.net/b/futaba.php", normalizeBoardUrl("http://may.2chan.net/b/"))
        assertEquals("https://may.2chan.net/b/futaba.php", normalizeBoardUrl("https://may.2chan.net/b/futaba.php"))
        assertEquals("https://may.2chan.net/b/futaba.php?mode=cat", normalizeBoardUrl("https://may.2chan.net/b/futaba.php?mode=cat"))
        // Not a document name at the end of the path: kept as a directory.
        assertEquals("https://may.2chan.net/res/futaba.php", normalizeBoardUrl("https://may.2chan.net/res"))
    }
}
