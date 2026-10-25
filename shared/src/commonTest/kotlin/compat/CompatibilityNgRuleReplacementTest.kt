package com.valoser.futacha.shared.compat

import kotlinx.coroutines.flow.first
import kotlin.test.*

class CompatibilityNgRuleReplacementTest {
    private fun rule(id: String) = CompatNgRule(id, CompatNgKind.THREAD_POST_NO, "*", "101", 1)
    @Test fun replacementWorksAtTheRegistrationLimitWithoutDroppingAnotherRule() {
        val current = (0 until MAX_COMPAT_NG_RULES).map { rule("$it") }
        val replacement = rule("updated").copy(normalizedValue = "102")
        val next = planCompatNgRuleReplacement(current, "0", replacement, emptySet(), emptySet())!!
        assertEquals(MAX_COMPAT_NG_RULES, next.size)
        assertEquals(replacement, next.first())
        assertEquals(current.drop(1), next.drop(1))
    }
    @Test fun missingDeletedAndCollidingRulesAreNotRecreatedOrOverwritten() {
        val current = listOf(rule("a"), rule("b"))
        assertNull(planCompatNgRuleReplacement(current, "missing", rule("new"), emptySet(), emptySet()))
        assertNull(planCompatNgRuleReplacement(current, "a", rule("b"), emptySet(), emptySet()))
        assertEquals(listOf("a", "b"), current.map { it.id })
    }
    @Test fun invalidScopesAndValuesRetainTheOriginal() {
        val old = rule("a")
        assertNull(planCompatNgRuleReplacement(listOf(old), "a", rule("new").copy(scopeKey = "closed"), emptySet(), emptySet()))
        assertNull(planCompatNgRuleReplacement(listOf(old), "a", rule("new").copy(normalizedValue = ""), emptySet(), emptySet()))
    }
    @Test fun aSameIdMemoEditIsAnUpdate() {
        val old = rule("a")
        assertEquals(listOf(old.copy(memo = "memo")), planCompatNgRuleReplacement(listOf(old), "a", old.copy(memo = "memo"), emptySet(), emptySet()))
    }
    @Test fun postNumberLabelsReturnTheExactNumericMatcherValue() {
        assertEquals("101", compatTypedThreadReferenceEditValue(CompatNgKind.THREAD_POST_NO, "No.101"))
        assertEquals("101", compatTypedThreadReferenceEditValue(CompatNgKind.THREAD_POST_NO, "　Ｎｏ．１０１　"))
        assertNull(compatTypedThreadReferenceEditValue(CompatNgKind.THREAD_POST_NO, "No.words"))
    }
    @Test fun identitiesKeepTheirCaseAndFullIpv6Length() {
        assertEquals("ID:AbCd", compatTypedThreadReferenceEditValue(CompatNgKind.THREAD_POSTER_ID, "ID:AbCd"))
        val ipv6 = "IP:2001:db8:1234:5678::1"
        assertEquals(ipv6, compatTypedThreadReferenceEditValue(CompatNgKind.THREAD_POSTER_ID, ipv6))
    }
    @Test fun anIdentityMemoCannotBecomeTheEditedValue() {
        assertEquals("No.101", compatThreadReferenceDisplayValue(rule("a").copy(memo = "memo")))
        assertEquals("ID:AbCd", compatThreadReferenceDisplayValue(rule("a").copy(kind = CompatNgKind.THREAD_POSTER_ID,
            normalizedValue = "ID:AbCd", memo = "memo")))
    }
}

internal suspend fun verifyCompatNgReplacementPersistence(store: CompatibilityStore): List<CompatNgRule> {
    val old = CompatNgRule("old", CompatNgKind.THREAD_POST_NO, "*", "101", 1, memo = "memo")
    val new = old.copy(id = "new", normalizedValue = "102")
    assertTrue(store.upsertNgRule(old))
    assertTrue(store.replaceNgRule("old", new))
    assertEquals(listOf(new), store.ngRules.first())
    assertFalse(store.replaceNgRule("old", old))
    assertFalse(store.replaceNgRule("new", new.copy(scopeKey = "closed")))
    val other = new.copy(id = "other", normalizedValue = "103")
    assertTrue(store.upsertNgRule(other))
    assertFalse(store.replaceNgRule("new", other))
    val memo = new.copy(memo = "changed")
    assertTrue(store.replaceNgRule("new", memo))
    return store.ngRules.first().also { assertEquals(setOf(memo, other), it.toSet()) }
}
