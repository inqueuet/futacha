package com.valoser.futacha.shared

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * S4-3: `futacha://ai` links (also open_thread / open_thread_url) are left to
 * the AI command path, where the "AIアプリ操作" setting applies; thread links
 * keep the thread path.
 */
class IosAiLinkRoutingTest {
    @Test
    fun aiLinksAreRecognizedAndThreadLinksAreNot() {
        listOf(
            "futacha://ai?action=open_thread&url=https%3A%2F%2Fmay.2chan.net%2Fb%2Fres%2F1.htm",
            "  FUTACHA://AI?action=open_thread_url&url=https%3A%2F%2Fmay.2chan.net%2Fb%2Fres%2F1.htm",
            "futacha://ai/open_thread?boardUrl=https%3A%2F%2Fmay.2chan.net%2Fb%2F&threadId=1",
            "futacha://ai"
        ).forEach { assertTrue(isIosFutachaAiLink(it), it) }
        listOf(
            "futacha://thread?url=https%3A%2F%2Fmay.2chan.net%2Fb%2Fres%2F1.htm",
            "futacha://aix?action=open_thread",
            "https://may.2chan.net/b/res/1.htm"
        ).forEach { assertFalse(isIosFutachaAiLink(it), it) }
    }
}
