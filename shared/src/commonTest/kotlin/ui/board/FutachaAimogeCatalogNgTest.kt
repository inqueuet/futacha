package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.buildCompatCatalogRuleIndex
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatCatalogRulesForBoard
import com.valoser.futacha.shared.compat.compatNgRuleId
import com.valoser.futacha.shared.model.CatalogItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** seq65-66: the ふたちゃ catalog features (long-press menu, NG) work on an あいもげ board and its NG survives a refetch. */
class FutachaAimogeCatalogNgTest {
    private val boardUrl = "https://nijiurachan.net/b/aimg/"
    private val threadUrl = "https://nijiurachan.net/b/aimg/thread/0b8f6c1e-1111-4222-8333-444455556666"
    private fun item(title: String = "もげ") = CatalogItem("0b8f6c1e-1111-4222-8333-444455556666", threadUrl, title, null, null, replyCount = 3)

    @Test
    fun anAimogeBoardGetsTheCatalogFeatures() {
        assertEquals(boardUrl, futachaCatalogFeatureBoardUrl("https://nijiurachan.net/b/aimg"))
        assertNotNull(futachaCatalogFeatureBoardUrl("https://may.2chan.net/b/futaba.php"))
    }

    @Test
    fun anAimogeThreadKeepsItsOwnAddressForCopyAndBrowser() {
        assertEquals(threadUrl, futachaCatalogThreadUrl(boardUrl, item()))
    }

    @Test
    fun aRegisteredAimogeThreadStaysHiddenAfterARefetch() {
        val key = compatBoardKey(futachaCatalogFeatureBoardUrl(boardUrl)!!)
        val rule = CompatNgRule(compatNgRuleId(CompatNgKind.CATALOG_REFUSE, key, threadUrl), CompatNgKind.CATALOG_REFUSE,
            key, threadUrl, 1L)
        val index = buildCompatCatalogRuleIndex(compatCatalogRulesForBoard(listOf(rule), key))
        assertTrue(index.hides(item()))
        assertTrue(index.hides(item(title = "もげ（更新後）").copy(replyCount = 99)))
        assertFalse(index.hides(item().copy(id = "x", threadUrl = "https://nijiurachan.net/b/aimg/thread/x")))
    }
}
