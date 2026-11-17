package com.valoser.futacha.shared.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppStateStringListValidationTest {
    @Test
    fun normalizationDropsInvalidAndDuplicateEntries() {
        val oversized = "x".repeat(APP_STATE_STRING_ENTRY_MAX_CHARS + 1)

        assertEquals(
            listOf("one", "two"),
            normalizeStoredStringList(listOf(" one ", "ONE", "", oversized, "two"))
        )
    }

    @Test
    fun normalizationCapsEntryCount() {
        val normalized = normalizeStoredStringList(
            List(APP_STATE_STRING_LIST_MAX_ENTRIES + 50) { index -> "entry-$index" }
        )

        assertEquals(APP_STATE_STRING_LIST_MAX_ENTRIES, normalized.size)
        assertTrue(normalized.last().startsWith("entry-"))
    }
}
