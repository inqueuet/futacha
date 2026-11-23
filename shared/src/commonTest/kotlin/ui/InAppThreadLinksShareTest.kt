package com.valoser.futacha.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InAppThreadLinksShareTest {
    @Test
    fun theBoardsFormAddressIsReadAsTheThreadPage() {
        assertEquals("https://may.2chan.net/b/res/1449244111.htm",
            InAppThreadLinks.normalized("https://may.2chan.net/b/futaba.php?res=1449244111"))
        assertEquals("https://may.2chan.net/b/res/1449244111.htm",
            InAppThreadLinks.normalized("may.2chan.net/b/futaba.php?res=1449244111"))
        assertNull(InAppThreadLinks.normalized("https://may.2chan.net/b/futaba.php?mode=cat"))
    }

    @Test
    fun aSharedTextGivesItsFirstThreadAddress() {
        assertEquals("https://may.2chan.net/b/res/1449244111.htm",
            InAppThreadLinks.firstIn("スレの題 - ふたば https://may.2chan.net/b/res/1449244111.htm"))
        assertEquals("https://img.2chan.net/b/res/123.htm",
            InAppThreadLinks.firstIn("見て（https://img.2chan.net/b/res/123.htm）。"))
        assertEquals("https://nijiurachan.net/b/news/thread/0a1b-2c",
            InAppThreadLinks.firstIn("https://example.com/x https://nijiurachan.net/b/news/thread/0a1b-2c"))
        assertNull(InAppThreadLinks.firstIn("https://may.2chan.net/b/futaba.php?mode=cat"))
        assertNull(InAppThreadLinks.firstIn("ただの文"))
    }
}
