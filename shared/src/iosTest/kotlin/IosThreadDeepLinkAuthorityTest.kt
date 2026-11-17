package com.valoser.futacha.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** S4-1: iOS custom-scheme thread links must not smuggle another host past canonicalization. */
class IosThreadDeepLinkAuthorityTest {
    @Test
    fun backslashAndUserinfoBypassesAreRejectedRawAndPercentEncoded() {
        listOf(
            "https://evil.com\\@may.2chan.net/b/res/1.htm",
            "futacha://thread?url=https://evil.com\\@may.2chan.net/b/res/1.htm",
            "futacha://thread?url=https%3A%2F%2Fevil.com%5C%40may.2chan.net%2Fb%2Fres%2F1.htm",
            "futacha://thread?url=https%3A%2F%2Fevil.com%5c%40may.2chan.net%2Fb%2Fres%2F1.htm",
            "futacha://thread?threadUrl=https%3A%2F%2Fevil.com%40may.2chan.net%2Fb%2Fres%2F1.htm",
            "futacha://thread?url=https%3A%2F%2Fmay.2chan.net%40evil.com%2Fb%2Fres%2F1.htm",
            "futacha://ai?action=open_thread&url=https%3A%2F%2Fevil.com%5C%40may.2chan.net%2Fb%2Fres%2F1.htm",
            "futacha://ai?action=open_thread_url&url=https%3A%2F%2Fevil.com%5C%40may.2chan.net%2Fb%2Fres%2F1.htm",
            "futacha://ai?action=open_thread&boardUrl=https%3A%2F%2Fevil.com%5C%40may.2chan.net%2Fb%2F&threadId=1"
        ).forEach { link ->
            assertNull(normalizeIosThreadDeepLink(link), link)
        }
    }

    @Test
    fun officialThreadLinksStillOpen() {
        val thread = "https://may.2chan.net/b/res/1.htm"
        assertEquals(thread, normalizeIosThreadDeepLink(thread))
        assertEquals(
            thread,
            normalizeIosThreadDeepLink("futacha://thread?url=https%3A%2F%2Fmay.2chan.net%2Fb%2Fres%2F1.htm")
        )
        // Only the canonical form is handed on, never the raw value.
        assertEquals(
            thread,
            normalizeIosThreadDeepLink("futacha://thread?url=HTTP%3A%2F%2FMAY.2chan.net%2F%2Fb%2Fres%2F1.htm%3Fx%3D1")
        )
        assertEquals(thread, normalizeIosThreadDeepLink("  http://MAY.2CHAN.NET/b/res/1.htm#r2  "))
    }
}
