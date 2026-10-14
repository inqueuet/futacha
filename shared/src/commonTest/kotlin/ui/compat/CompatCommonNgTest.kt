package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.CompatOtherMenuRoute
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.buildCompatCatalogRuleIndex
import com.valoser.futacha.shared.compat.buildCompatThreadNgRuleIndex
import com.valoser.futacha.shared.compat.compatCatalogOtherMenu
import com.valoser.futacha.shared.compat.compatThreadOtherMenu
import com.valoser.futacha.shared.compat.matchesCompatThreadNg
import com.valoser.futacha.shared.model.CatalogItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatCommonNgTest {
    @Test
    fun theCommonListsBecomeUnsavedRulesForEveryScope() {
        val rules = compatCommonNgFilterRules(
            ngHeaders = listOf("ID:abc", " ", "ID:abc"),
            ngWords = listOf("荒らし"),
            catalogNgWords = listOf(" 晒し ")
        )
        assertEquals(
            listOf(
                CompatNgKind.THREAD_REFUSE to "ID:abc",
                CompatNgKind.THREAD_POSTER_ID to "ID:abc",
                CompatNgKind.THREAD_IGNORE to "荒らし",
                CompatNgKind.CATALOG_IGNORE to "晒し"
            ),
            rules.map { it.kind to it.normalizedValue }
        )
        assertTrue(rules.all { it.scopeKey == "*" && isCompatCommonNgRule(it) })
        assertEquals(emptyList(), compatCommonNgFilterRules(emptyList(), emptyList(), emptyList()))
    }

    @Test
    fun theManagementSideNeverSeesThem() {
        val own = CompatNgRule("rule-1", CompatNgKind.THREAD_WORD, "*", "own", 0L)
        val common = compatCommonNgFilterRules(listOf("h"), listOf("w"), listOf("c"))
        assertFalse(isCompatCommonNgRule(own))
        assertEquals(listOf(own), (listOf(own) + common).withoutCompatCommonNgRules())
    }

    @Test
    fun theRulesHideThroughThisModesOwnFilters() {
        val rules = compatCommonNgFilterRules(listOf("ID:abc", "としあき"), listOf("荒らし"), listOf("晒し"))
        val index = buildCompatThreadNgRuleIndex(rules, scopeKey = "compat_tab_x", boardKey = "compat_board_x")
        fun post(no: String, body: String, id: String? = null) =
            CompatPostSnapshot(position = 0, postNo = no, timestamp = "", posterId = id, messageHtml = body)
        assertTrue(post("1", "これは荒らしです").matchesCompatThreadNg(index))
        assertTrue(post("2", "普通", id = "ID:abc").matchesCompatThreadNg(index))
        assertTrue(CompatPostSnapshot(position = 0, postNo = "4", author = "としあき", timestamp = "", messageHtml = "普通").matchesCompatThreadNg(index))
        assertFalse(post("3", "普通").matchesCompatThreadNg(index))

        val catalog = buildCompatCatalogRuleIndex(rules)
        assertTrue(catalog.hides(CatalogItem("1", "u1", "晒しスレ", null, null, replyCount = 1)))
        assertFalse(catalog.hides(CatalogItem("2", "u2", "普通のスレ", null, null, replyCount = 1)))
    }

    @Test
    fun theSwitchIsOffByDefault() {
        assertFalse(compatCommonNgApplyEnabled(emptyMap()))
        assertTrue(compatCommonNgApplyEnabled(mapOf("compat.control.controlCommonNgApply" to "ON")))
    }

    @Test
    fun addingSkipsBlanksAndDuplicatesLikeFutacha() {
        assertEquals(listOf("a", "b"), compatCommonNgAdd(listOf("a"), " b "))
        assertEquals(listOf("A"), compatCommonNgAdd(listOf("A"), "a"))
        assertEquals(listOf("A"), compatCommonNgAdd(listOf("A"), "  "))
    }

    @Test
    fun theNgMenusGetTheEntryOnlyWhenAsked() {
        val catalogDefault = compatCatalogOtherMenu(CompatOtherMenuRoute.CATALOG_NG, true, false, false, false)
        val catalogWith = compatCatalogOtherMenu(CompatOtherMenuRoute.CATALOG_NG, true, false, false, false, commonNgEnabled = true)
        assertEquals(catalogDefault, catalogWith.dropLast(1))
        assertEquals(COMPAT_COMMON_NG_MENU_KEY to "共通NG（ふたちゃ・ふたばー風と共有）", catalogWith.last().key to catalogWith.last().label)
        val threadDefault = compatThreadOtherMenu(CompatOtherMenuRoute.THREAD_NG, true, false, 0)
        val threadWith = compatThreadOtherMenu(CompatOtherMenuRoute.THREAD_NG, true, false, 0, commonNgEnabled = true)
        assertEquals(threadDefault, threadWith.dropLast(1))
        assertEquals(COMPAT_COMMON_NG_MENU_KEY, threadWith.last().key)
        // Other routes are untouched.
        assertEquals(
            compatThreadOtherMenu(CompatOtherMenuRoute.THREAD_ROOT, true, false, 0),
            compatThreadOtherMenu(CompatOtherMenuRoute.THREAD_ROOT, true, false, 0, commonNgEnabled = true)
        )
    }
}
