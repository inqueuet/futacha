package com.valoser.futacha.shared.ui.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeleteKeySupportTest {
    @Test
    fun sanitizeStoredDeleteKey_trimsAndClampsLength() {
        assertEquals("abc123", sanitizeStoredDeleteKey("  abc123  "))
        assertEquals("12345678", sanitizeStoredDeleteKey(" 1234567890 "))
    }

    @Test
    fun normalizeDeleteKeyForSubmit_trimsWithoutClamping() {
        assertEquals("abc123", normalizeDeleteKeyForSubmit("  abc123  "))
        assertEquals("1234567890", normalizeDeleteKeyForSubmit(" 1234567890 "))
    }

    @Test
    fun deleteKeySubmitHelpers_matchUiRules() {
        assertTrue(hasDeleteKeyForSubmit(" pass "))
        assertFalse(hasDeleteKeyForSubmit("   "))
        assertEquals("stored", resolveDeleteKeyAutofill("", "stored"))
        assertEquals("custom", resolveDeleteKeyAutofill("custom", "stored"))
    }
}
