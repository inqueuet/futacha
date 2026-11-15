package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.mix.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.util.CatalogTitleCompletionPolicy
import kotlin.test.*

class MixHeadMetadataPlanTest {
    private fun entry(boardUrl: String, id: String) = MixEntry(
        BoardSummary(boardUrl, boardUrl, "c", boardUrl, ""), CatalogItem(id, "https://x/$id", "123", null, null, replyCount = 123),
        MixEntryKey(boardUrl, id), null, false, 0, 1)

    @Test fun capsTwelveOverTheWholeMixAndKeepsSameNumbersApart() {
        val may = (1..10).map { entry("https://may.2chan.net/b/", "$it") }
        val img = (1..10).map { entry("https://img.2chan.net/b/", "$it") }
        val all = (may + img).associateBy { it.key.asString() }
        val want = CatalogTitleCompletionPolicy(enabled = true, allowFallbackHeadScan = true)
        val plan = planMixHeadMetadata(all, all.keys.toList(), emptyMap()) { want }
        assertEquals(12, plan.size)
        val done = mapOf(plan[0].key.asString() to buildCatalogHeadMetadataCacheKey(plan[0].board.url, plan[0].item))
        assertFalse(plan[0] in planMixHeadMetadata(all, all.keys.toList(), done) { want })
        assertNotEquals(may[0].key.asString(), img[0].key.asString())
    }
}
