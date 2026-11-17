package com.valoser.futacha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingPlatformDeepLinksTest {
    @Test
    fun recognizedChannelsAreLastWinsWithoutUnrelatedIntentClearingThem() {
        val first = PendingPlatformDeepLinks().withIncoming(
            ai = "futacha://ai?action=open_settings",
            thread = "https://may.2chan.net/b/res/100.htm"
        )
        val second = first.withIncoming(
            ai = null,
            thread = "https://may.2chan.net/b/res/200.htm"
        )
        val unrelated = second.withIncoming(ai = null, thread = null)

        assertEquals("futacha://ai?action=open_settings", unrelated.ai)
        assertEquals("https://may.2chan.net/b/res/200.htm", unrelated.thread)
    }

    @Test
    fun staleConsumptionCannotEraseANewerIntent() {
        val old = "https://may.2chan.net/b/res/100.htm"
        val current = "https://may.2chan.net/b/res/200.htm"
        val pending = PendingPlatformDeepLinks(thread = old)
            .withIncoming(ai = null, thread = current)

        assertEquals(pending, pending.consumeThread(old))
        assertNull(pending.consumeThread(current).thread)
    }

    @Test
    fun consumingOneChannelDoesNotEraseTheOther() {
        val ai = "futacha://ai?action=open_settings"
        val thread = "https://may.2chan.net/b/res/100.htm"
        val pending = PendingPlatformDeepLinks(ai = ai, thread = thread)

        val afterThread = pending.consumeThread(thread)
        assertEquals(ai, afterThread.ai)
        assertNull(afterThread.thread)
    }

    @Test
    fun deepLinkBoundaryRejectsLookalikeHostsAndOversizedPayloads() {
        assertTrue(isTrustedFutabaDeepLinkHost("may.2chan.net"))
        assertTrue(isTrustedFutabaDeepLinkHost("2chan.net"))
        assertFalse(isTrustedFutabaDeepLinkHost("evil2chan.net"))
        assertFalse(isTrustedFutabaDeepLinkHost("2chan.net.example.com"))

        val oversized = "x".repeat(MAX_PLATFORM_DEEP_LINK_CHARS + 1)
        assertNull(oversized.boundedPlatformDeepLinkOrNull())
        assertEquals("ok", "ok".boundedPlatformDeepLinkOrNull())
    }
}
