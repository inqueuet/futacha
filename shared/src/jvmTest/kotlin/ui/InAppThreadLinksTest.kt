package com.valoser.futacha.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InAppThreadLinksTest {
    @Test
    fun typedThreadUrlsAreAcceptedAndBoardUrlsAreNot() {
        assertEquals("https://may.2chan.net/b/res/1447418590.htm", InAppThreadLinks.normalized("may.2chan.net/b/res/1447418590.htm"))
        assertEquals(
            "https://nijiurachan.net/b/aimg/thread/01a1d21b-947b-77ca-8a6f-a1f8b4d88975",
            InAppThreadLinks.normalized(" https://nijiurachan.net/b/aimg/thread/01a1d21b-947b-77ca-8a6f-a1f8b4d88975 ")
        )
        assertNull(InAppThreadLinks.normalized("https://may.2chan.net/b/futaba.php"))
        assertNull(InAppThreadLinks.normalized("https://example.com/b/res/1.htm"))
    }

    @Test
    fun openHandsTheLinkOverOnceAndConsumeClearsIt() {
        assertFalse(InAppThreadLinks.open("not a url"))
        assertTrue(InAppThreadLinks.open("may.2chan.net/b/res/1.htm"))
        val pending = InAppThreadLinks.pending.value
        assertEquals("https://may.2chan.net/b/res/1.htm", pending)
        InAppThreadLinks.consume(pending!!)
        assertNull(InAppThreadLinks.pending.value)
    }
}
