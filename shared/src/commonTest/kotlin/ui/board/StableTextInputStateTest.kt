package com.valoser.futacha.shared.ui.board

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StableTextInputStateTest {
    @Test
    fun editClampedBackToTheSameTextShowsTheSavedText() {
        // The caller's limit is 3 and it already held "abc"; pasting "de" was dropped.
        val field = TextFieldValue("abcde", selection = TextRange(5))
        val corrected = stableTextRejectedEditValue(field, callerText = "abc", textAtEdit = "abc")
        assertEquals("abc", corrected?.text)
        assertEquals(TextRange(3), corrected?.selection)
    }

    @Test
    fun acceptedOrAlreadyMatchingEditsAreLeftAlone() {
        val field = TextFieldValue("abcd", selection = TextRange(4))
        assertNull(stableTextRejectedEditValue(field, callerText = "abcd", textAtEdit = "abc"))
        // The caller stored a different (clamped) text; the text-change effect shows it.
        assertNull(stableTextRejectedEditValue(field, callerText = "ab", textAtEdit = "abc"))
    }

    @Test
    fun selectionInsideTheSavedTextIsKept() {
        val field = TextFieldValue("abXc", selection = TextRange(1, 2))
        val corrected = stableTextRejectedEditValue(field, callerText = "abc", textAtEdit = "abc")
        assertEquals(TextRange(1, 2), corrected?.selection)
    }
}
