package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CompatibilityPreferenceValidationTest {
    @Test
    fun acceptsNormalNamespacedPreference() {
        requireValidCompatPreference("compat.catalog.mode", "通常")
    }

    @Test
    fun rejectsUnboundedOrNonNamespacedPreferencePayloads() {
        assertFailsWith<IllegalArgumentException> {
            requireValidCompatPreference("catalog.mode", "通常")
        }
        assertFailsWith<IllegalArgumentException> {
            requireValidCompatPreference(
                "compat.large",
                "x".repeat(MAX_COMPAT_PREFERENCE_VALUE_CHARS + 1)
            )
        }
    }

    @Test
    fun validatesNgRulePersistenceBoundaries() {
        val valid = CompatNgRule(
            id = "rule",
            kind = CompatNgKind.THREAD_WORD,
            scopeKey = "*",
            normalizedValue = "word",
            createdAtEpochMillis = 1L,
            imageUrl = null
        )

        assertTrue(isValidCompatNgRule(valid))
        assertFalse(
            isValidCompatNgRule(
                valid.copy(normalizedValue = "x".repeat(MAX_COMPAT_NG_VALUE_CHARS + 1))
            )
        )
        assertFalse(
            isValidCompatNgRule(
                valid.copy(imageUrl = "x".repeat(MAX_COMPAT_NG_IMAGE_URL_CHARS + 1))
            )
        )
        assertFalse(
            isValidCompatNgRule(
                valid.copy(memo = "x".repeat(MAX_COMPAT_NG_MEMO_CHARS + 1))
            )
        )
    }

    @Test
    fun threadImageNgUsesBoardScopeAndReadsLegacyTabScopedRules() {
        val boardRule = CompatNgRule(
            id = "board-image",
            kind = CompatNgKind.THREAD_IMAGE,
            scopeKey = "board-a",
            normalizedValue = "https://example.invalid/a.jpg",
            createdAtEpochMillis = 1L,
            imageUrl = "https://example.invalid/a.jpg"
        )
        assertTrue(isCompatNgScopeValid(boardRule.kind, boardRule.scopeKey, setOf("board-a"), setOf("tab-a")))
        assertTrue(boardRule.appliesToThreadImage("board-a", "tab-other"))
        assertFalse(boardRule.appliesToThreadImage("board-b", "tab-other"))

        val legacyRule = boardRule.copy(id = "legacy-image", scopeKey = "tab-a")
        assertTrue(isCompatNgScopeValid(legacyRule.kind, legacyRule.scopeKey, setOf("board-a"), setOf("tab-a")))
        assertTrue(legacyRule.appliesToThreadImage("board-a", "tab-a"))
        assertFalse(legacyRule.appliesToThreadImage("board-a", "tab-b"))
        assertEquals("board-a", compatThreadImageNgScopeKey("board-a", localOnly = true))
        assertEquals("*", compatThreadImageNgScopeKey("board-a", localOnly = false))
    }
}
