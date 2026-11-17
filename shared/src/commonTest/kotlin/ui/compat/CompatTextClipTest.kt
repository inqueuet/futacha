package com.valoser.futacha.shared.ui.compat

import kotlin.test.*

class CompatTextClipTest {
    @Test fun plainTitlesKeepTheirFirstFourCharacters() {
        assertEquals("ふくろう", compatTakeCharacters("ふくろうの話", 4))
        assertEquals("abc", compatTakeCharacters("abc", 4))
        assertEquals("", compatTakeCharacters(null, 4))
        assertEquals("", compatTakeCharacters("abc", 0))
    }

    @Test fun anEmojiOrItsSequenceIsNeverCutLikeFutabaViewer1820() {
        // Surrogate pair: take(4) would leave half of 🐱.
        assertEquals("ねこ🐱", compatTakeCharacters("ねこ🐱だ", 3))
        assertEquals("ab👍🏽c", compatTakeCharacters("ab👍🏽cd", 4)) // skin tone
        assertEquals("👨‍👩‍👧x", compatTakeCharacters("👨‍👩‍👧xyz", 2)) // ZWJ family
        assertEquals("🇯🇵🇺🇸", compatTakeCharacters("🇯🇵🇺🇸🇫🇷", 2)) // flags pair up
        assertEquals("❤️a", compatTakeCharacters("❤️ab", 2)) // variation selector
        assertEquals("1️⃣2", compatTakeCharacters("1️⃣23", 2)) // keycap
        assertEquals("か゚き", compatTakeCharacters("か゚きく", 2)) // combining semi-voiced mark
        assertEquals("🏴󠁧󠁢󠁳󠁣󠁴󠁿a", compatTakeCharacters("🏴󠁧󠁢󠁳󠁣󠁴󠁿ab", 2)) // tag sequence
    }
}
