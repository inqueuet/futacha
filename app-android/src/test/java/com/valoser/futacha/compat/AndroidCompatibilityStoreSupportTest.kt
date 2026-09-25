package com.valoser.futacha.compat

import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.ScrollAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidCompatibilityStoreSupportTest {
    @Test
    fun isTransientPreferenceKey_matchesOnlyPerThreadAndPerBoardMarkers() {
        assertTrue(isTransientPreferenceKey("compat.ownpost.tab-1.123"))
        assertTrue(isTransientPreferenceKey("compat.catalog.lastFetchThreadCount.board.CATALOG"))
        assertFalse(isTransientPreferenceKey("compat.catalog.catalogThreadSize"))
        assertFalse(isTransientPreferenceKey("compat.thread.threadExtractSoudaneNum"))
        assertFalse(isTransientPreferenceKey("compat.ownpostSomething"))
    }

    @Test
    fun shouldApplyClosedBatchObservation_rejectsStaleExpiryObservation() {
        // Expiry run read the DB first (1), a new close was observed next (2)
        // and scheduled; the expiry's late "nothing pending" must be ignored.
        assertTrue(shouldApplyClosedBatchObservation(observation = 2L, lastAppliedObservation = 0L))
        assertFalse(shouldApplyClosedBatchObservation(observation = 1L, lastAppliedObservation = 2L))
        assertFalse(shouldApplyClosedBatchObservation(observation = 2L, lastAppliedObservation = 2L))
        assertTrue(shouldApplyClosedBatchObservation(observation = 3L, lastAppliedObservation = 2L))
    }

    @Test
    fun withCompatTabScrollAnchor_patchesOnlyTheScrolledTab() {
        val first = tab("a")
        val second = tab("b")
        val tabs = listOf(first, second)
        val anchor = ScrollAnchor(postNo = "123", offsetPx = 40, fallbackIndex = 7)

        val patched = tabs.withCompatTabScrollAnchor("b", anchor)

        assertEquals(anchor, patched[1].scrollAnchor)
        assertEquals(second.copy(scrollAnchor = anchor), patched[1])
        assertSame(first, patched[0])
        assertEquals(ScrollAnchor(), second.scrollAnchor)
    }

    @Test
    fun withCompatTabScrollAnchor_keepsListIdentityWhenNothingChanges() {
        val anchor = ScrollAnchor(postNo = "5")
        val tabs = listOf(tab("a"), tab("b").copy(scrollAnchor = anchor))

        assertSame(tabs, tabs.withCompatTabScrollAnchor("missing", anchor))
        assertSame(tabs, tabs.withCompatTabScrollAnchor("b", anchor))
    }

    @Test
    fun withCompatHistoryScrollAnchor_patchesOnlyTheMatchingEntry() {
        val first = history("a")
        val second = history("b")
        val entries = listOf(first, second)
        val anchor = ScrollAnchor(postNo = "9", offsetPx = 3)

        val patched = entries.withCompatHistoryScrollAnchor(second.canonicalUrl, anchor)

        assertEquals(second.copy(scrollAnchor = anchor), patched[1])
        assertSame(first, patched[0])
        assertSame(patched, patched.withCompatHistoryScrollAnchor(second.canonicalUrl, anchor))
        assertSame(entries, entries.withCompatHistoryScrollAnchor("https://may.2chan.net/b/res/zz.htm", anchor))
    }

    private fun history(key: String) = CompatHistoryEntry(
        canonicalUrl = "https://may.2chan.net/b/res/$key.htm",
        originalUrl = "https://may.2chan.net/b/res/$key.htm",
        boardKey = "may-b",
        boardName = "may/b",
        threadNo = key,
        title = "title $key",
        contentUpdatedAtEpochMillis = 1L,
        lastVisitedEpochMillis = 1L
    )

    private fun tab(key: String) = CompatTab(
        key = key,
        canonicalUrl = "https://may.2chan.net/b/res/$key.htm",
        originalUrl = "https://may.2chan.net/b/res/$key.htm",
        boardKey = "may-b",
        boardName = "may/b",
        threadNo = key,
        title = "title $key",
        insertedAtEpochMillis = 1L,
        contentUpdatedAtEpochMillis = 1L
    )
}
