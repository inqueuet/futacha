package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.compat.AimogeAvailability
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThreadSearchJumpTest {
    @Test
    fun futabaJumpMatchesOnlyTheSelectedThreadAndResNumber() {
        val state = ThreadSearchJumpState()
        state.select("https://may.2chan.net/b/res/123.htm", "456")
        val request = assertNotNull(state.pending)

        assertTrue(request.matches("https://may.2chan.net/b/res/123.htm#456"))
        assertFalse(request.matches("https://img.2chan.net/b/res/123.htm"))
        assertFalse(request.matches("https://may.2chan.net/b/res/124.htm"))
        assertTrue(request.matchesPost("456", null))
        assertFalse(request.matchesPost("123", 456))
        assertTrue(state.consume(request))
        assertFalse(state.consume(request))
        assertNull(state.pending)
    }

    @Test
    fun aimogeUsesSequenceIncludingTheOpeningPost() {
        val previous = AimogeAvailability.sitesEnabled
        AimogeAvailability.sitesEnabled = true
        try {
            val state = ThreadSearchJumpState()
            val url = "https://nijiurachan.net/b/ai2/thread/01a1ab6e-57e8-755b-a01c-66792ff13aef"
            state.select(url, "0")
            val request = assertNotNull(state.pending)

            assertTrue(request.matches(url))
            assertTrue(request.matchesPost("100493594", 0))
            assertFalse(request.matchesPost("0", 1))
            state.select(url, "1")
            assertFalse(state.consume(request))
            assertTrue(assertNotNull(state.pending).matchesPost("100493595", 1))
        } finally {
            AimogeAvailability.sitesEnabled = previous
        }
    }
}
