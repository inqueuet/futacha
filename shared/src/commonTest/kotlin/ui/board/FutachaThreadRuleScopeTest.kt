package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.compat.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** L38: ふたばー風・ふたったー風's "del＋レスNG" (scoped to the thread's address) hides the post in ふたちゃ too. */
class FutachaThreadRuleScopeTest {
    private val boardUrl = requireNotNull(canonicalizeBoardUrl("https://may.2chan.net/b/futaba.php"))
    // ふたちゃ keys its thread as FutachaThreadFeatures does.
    private val tabKey = compatTabKey("${boardUrl}res/123.htm")
    private val boardKey = compatBoardKey(boardUrl)

    private fun postNo(scope: String) = CompatNgRule("r", CompatNgKind.THREAD_POST_NO, scope, "5", 1)

    @Test fun anotherModesDelAndNgAppliesToTheSameThreadOnly() {
        val sameThread = requireNotNull(canonicalizeThreadUrl("https://may.2chan.net/b/res/123.htm")).canonicalUrl
        assertEquals(tabKey, compatTabKey(sameThread))
        assertTrue(futachaThreadRuleApplies(postNo(sameThread), tabKey, boardKey))
        val otherThread = requireNotNull(canonicalizeThreadUrl("https://may.2chan.net/b/res/124.htm")).canonicalUrl
        assertFalse(futachaThreadRuleApplies(postNo(otherThread), tabKey, boardKey))
        // Rules keyed to the tab or to every thread, as before.
        assertTrue(futachaThreadRuleApplies(postNo(tabKey), tabKey, boardKey))
        assertTrue(futachaThreadRuleApplies(postNo("*"), tabKey, boardKey))
        // Only the post-number rule reads an address scope.
        assertFalse(futachaThreadRuleApplies(postNo(sameThread).copy(kind = CompatNgKind.THREAD_WORD), tabKey, boardKey))
    }
}
