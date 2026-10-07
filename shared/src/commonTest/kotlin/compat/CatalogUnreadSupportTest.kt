package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.CatalogItem
import kotlin.test.*

class CatalogUnreadSupportTest {
    private val url = "https://may.2chan.net/b/res/123.htm"
    private fun item(url: String = this.url, count: Int = 15) = CatalogItem("123", url, "title", null, null, replyCount = count)
    private fun tab() = CompatTab("tab", url, url, "board", "may", "123", "title", replyCount = 10,
        checkedReplyCount = 8, favorite = true, insertedAtEpochMillis = 12, contentUpdatedAtEpochMillis = 100,
        scrollAnchor = ScrollAnchor("122", 31, 7, 4), snapshotRevision = 4)

    @Test fun catalogRefreshUpdatesOnlyTotalAndContentTime() {
        val before = tab()
        val updated = before.withCatalogReplyCount(catalogReplyCountsByUrl(listOf(item())), 200)
        assertEquals(7, updated.unreadCount)
        assertEquals(before.copy(replyCount = 15, contentUpdatedAtEpochMillis = 200), updated)
    }
    @Test fun staleAndDecreasedCountsCannotEraseUnreadOrMoveReadAnchor() {
        val before = tab()
        assertEquals(before, before.withCatalogReplyCount(catalogReplyCountsByUrl(listOf(item(count = 20))), 99))
        assertEquals(before, before.withCatalogReplyCount(catalogReplyCountsByUrl(listOf(item(count = 5))), 200))
        assertEquals(before, before.withCatalogReplyCount(emptyMap(), 200))
    }
    @Test fun canonicalUrlsMatchButOtherBoardsAndUnknownThreadsNeverMatch() {
        assertEquals(15, tab().withCatalogReplyCount(catalogReplyCountsByUrl(listOf(item("http://may.2chan.net/b/res/123.htm#x"))), 200).replyCount)
        assertEquals(tab(), tab().withCatalogReplyCount(catalogReplyCountsByUrl(listOf(item("https://img.2chan.net/b/res/123.htm"))), 200))
        assertTrue(catalogReplyCountsByUrl(listOf(item("https://example.org/res/123.htm"))).isEmpty())
    }
    @Test fun unreadOverridesDeltaAndClearsWhenRead() {
        val current = item()
        val deltas = mapOf(current.compatCatalogReplyDeltaKey() to 3)
        assertEquals(7, buildCatalogReplyIndicators(listOf(current), listOf(tab()), deltas)[url]?.count)
        assertEquals(3, buildCatalogReplyIndicators(listOf(current), emptyList(), deltas)[url]?.count)
        assertTrue(buildCatalogReplyIndicators(listOf(current), listOf(tab().copy(checkedReplyCount = 15)), deltas).isEmpty())
        assertTrue(buildCatalogReplyIndicators(listOf(current), emptyList(), emptyMap()).isEmpty())
    }
}
