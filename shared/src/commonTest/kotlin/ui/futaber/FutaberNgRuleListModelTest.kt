package com.valoser.futacha.shared.ui.futaber

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The 2026-11-08 audit's B1: 「すべて削除」 of a rule list is one store write, not one per rule. */
class FutaberNgRuleListModelTest {
    @Test fun removingAllAsksTheStoreOnceWithEveryRule() {
        val single = mutableListOf<String>()
        val bulk = mutableListOf<Collection<String>>()
        val rows = (1..5_000).map { FutaberNgRow("rule-$it", "画像 $it") }
        val model = futaberNgRuleListModel(rows, "hint", { single += it }, { bulk += it })
        model.onRemoveAll()
        assertTrue(single.isEmpty(), "No rule-by-rule deletion")
        assertEquals(1, bulk.size)
        assertEquals(rows.map { it.id }, bulk.single().toList())
    }

    @Test fun oneRowIsStillTakenBackAlone() {
        val single = mutableListOf<String>()
        val bulk = mutableListOf<Collection<String>>()
        val model = futaberNgRuleListModel(listOf(FutaberNgRow("a", "A"), FutaberNgRow("b", "B")), "hint", { single += it }, { bulk += it })
        model.onRemove(model.rows[1])
        assertEquals(listOf("b"), single)
        assertTrue(bulk.isEmpty())
    }

    @Test fun anEmptyListAsksNothing() {
        val bulk = mutableListOf<Collection<String>>()
        futaberNgRuleListModel(emptyList(), "hint", {}, { bulk += it }).onRemoveAll()
        assertTrue(bulk.isEmpty())
    }
}
