package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatibilityCatalogNgManagementTest {
    @Test
    fun referenceManagersIncludeRulesWrittenByOldAndCurrentFutachaSchemas() {
        assertEquals(
            setOf(CompatNgKind.CATALOG_REFUSE, CompatNgKind.CATALOG_THREAD),
            compatCatalogManagementKinds(CompatNgKind.CATALOG_REFUSE)
        )
        assertEquals(
            setOf(CompatNgKind.CATALOG_IGNORE, CompatNgKind.CATALOG_WORD),
            compatCatalogManagementKinds(CompatNgKind.CATALOG_IGNORE)
        )
    }

    @Test
    fun referenceManagersFilterByBoardAndUseTheReferenceOrdering() {
        val rules = listOf(
            rule("ref-old", CompatNgKind.CATALOG_REFUSE, "board-a", "https://example/res/1.htm", 1, "古い"),
            rule("ref-new", CompatNgKind.CATALOG_THREAD, "board-a", "2", 9, "新しい"),
            rule("ref-other", CompatNgKind.CATALOG_REFUSE, "board-b", "https://example/res/3.htm", 20),
            rule("word-z", CompatNgKind.CATALOG_IGNORE, "board-a", "zebra", 5, "Ｚｅｂｒａ"),
            rule("word-a", CompatNgKind.CATALOG_WORD, "*", "apple", 2, "Apple"),
            rule("word-other", CompatNgKind.CATALOG_IGNORE, "board-b", "banana", 8)
        )

        assertEquals(
            listOf("ref-new", "ref-old"),
            compatCatalogManagementRules(rules, "board-a", CompatNgKind.CATALOG_REFUSE).map(CompatNgRule::id)
        )
        assertEquals(
            listOf("word-a", "word-z"),
            compatCatalogManagementRules(rules, "board-a", CompatNgKind.CATALOG_IGNORE).map(CompatNgRule::id)
        )
    }

    @Test
    fun referenceRowsPreserveTypedWordsAndShowRefuseTitleWithUrl() {
        val word = rule("word", CompatNgKind.CATALOG_IGNORE, "*", "test", 1, "Ｔｅｓｔ")
        val refuse = rule(
            "refuse",
            CompatNgKind.CATALOG_REFUSE,
            "board-a",
            "https://may.2chan.net/b/res/123.htm",
            2,
            "題名"
        )

        assertEquals("Ｔｅｓｔ", compatCatalogManagementDisplayValue(word))
        assertEquals("題名\nhttps://may.2chan.net/b/res/123.htm", compatCatalogRefuseDisplayText(refuse))
    }

    @Test
    fun duplicateDetectionMatchesReferenceCaseAndWidthFolding() {
        val rules = listOf(rule("word", CompatNgKind.CATALOG_IGNORE, "*", "test", 1, "ＴＥＳＴ"))

        assertTrue(hasCompatCatalogManagementDuplicate(rules, "Ｔｅｓｔ"))
        assertFalse(hasCompatCatalogManagementDuplicate(rules, "Ｔｅｓｔ", excludingRuleId = "word"))
        assertFalse(hasCompatCatalogManagementDuplicate(rules, "different"))
    }

    private fun rule(
        id: String,
        kind: CompatNgKind,
        scope: String,
        value: String,
        createdAt: Long,
        memo: String = ""
    ) = CompatNgRule(
        id = id,
        kind = kind,
        scopeKey = scope,
        normalizedValue = value,
        createdAtEpochMillis = createdAt,
        memo = memo
    )
}
