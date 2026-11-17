package com.valoser.futacha.shared.mix

import com.valoser.futacha.shared.ui.board.mixOfferable
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MixOfferableTest {
    @Test fun needsTwoRealBoards() {
        assertFalse(mixOfferable(listOf("https://www.example.com/t/")))
        assertFalse(mixOfferable(listOf("https://may.2chan.net/b/", "https://www.example.com/t/")))
        assertTrue(mixOfferable(listOf("https://may.2chan.net/b/", "https://img.2chan.net/b/")))
    }
    @Test fun existingMixRemainsAccessibleWithZeroOrOneBoard() {
        val raw = """{"version":1,"mixes":[{"id":"m","name":"saved","members":["https://may.2chan.net/b/"],"createdAt":1}]}"""
        assertTrue(mixOfferable(emptyList(), raw))
        assertTrue(mixOfferable(listOf("https://may.2chan.net/b/"), raw))
        assertFalse(mixOfferable(emptyList(), "{bad"))
    }
}
