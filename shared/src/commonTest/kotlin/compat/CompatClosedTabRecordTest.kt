package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The pure rules of the shared closed-tab record and the trim order by marks (plan B6). */
class CompatClosedTabRecordTest {
    private fun tab(no: Int, favorite: Boolean = false) = CompatTab(
        key = "tab-$no", canonicalUrl = "u$no", originalUrl = "u$no", boardKey = "b", boardName = "板",
        threadNo = no.toString(), title = "スレ", favorite = favorite,
        insertedAtEpochMillis = no.toLong(), contentUpdatedAtEpochMillis = no.toLong()
    )
    private fun view(no: Int, profile: String, ordinal: Int) = CompatSharedTabView("tab-$no", profile, ordinal, 1L)
    private val futaber = ExperienceProfile.FUTABER.persistedValue
    private val ftutter = ExperienceProfile.FTUTTER.persistedValue

    @Test
    fun aRecordWrittenBeforeB6UsesItsExpiryAsTheNoticeDeadline() {
        val legacy = ClosedTabBatch("old", listOf(ClosedCompatTab(tab(1), 0)), null, expiresAtEpochMillis = 7_000)
        assertEquals(7_000, legacy.noticeDeadlineEpochMillis)
        assertEquals("old", compatPendingClosedBatch(listOf(legacy), 6_999)?.id)
        assertNull(compatPendingClosedBatch(listOf(legacy), 7_000))
        val fresh = newCompatClosedTabBatch(listOf(ClosedCompatTab(tab(2), 0)), null, 1_000, futaber)
        assertEquals(1_000 + COMPAT_UNDO_NOTICE_MILLIS, fresh.noticeDeadlineEpochMillis)
        assertEquals(1_000 + COMPAT_CLOSED_BATCH_KEEP_MILLIS, fresh.expiresAtEpochMillis)
    }

    @Test
    fun keptRecordsAreTheTwentyNewestUnexpiredOnes() {
        val batches = (1..30).map { newCompatClosedTabBatch(listOf(ClosedCompatTab(tab(it), 0)), null, it * 10L, null) }
        val kept = compatKeptClosedBatches(batches + batches.first(), 100)
        assertEquals(20, kept.size)
        assertEquals("tab-30", kept.first().tabs.single().tab.key)
        assertTrue(compatKeptClosedBatches(batches, 30 * 10L + COMPAT_CLOSED_BATCH_KEEP_MILLIS).isEmpty())
    }

    @Test
    fun aRestoreAddsOnlyTheStarToATabOpenAgain() {
        val closed = ClosedCompatTab(tab(1, favorite = true), 0)
        assertEquals(tab(1, favorite = true), compatRestoredTab(closed, null, null))
        assertEquals(false, compatRestoredTab(closed, null, ftutter)?.favorite)
        assertEquals(true, compatRestoredTab(closed, tab(1).copy(title = "新"), null)?.favorite)
        assertEquals("新", compatRestoredTab(closed, tab(1).copy(title = "新"), null)?.title)
        assertNull(compatRestoredTab(closed, tab(1, favorite = true), null))
        assertNull(compatRestoredTab(closed, tab(1), ftutter))
    }

    @Test
    fun viewsComeBackAtTheirOldPlacesPerMode() {
        val batch = ClosedTabBatch(
            "c", listOf(ClosedCompatTab(tab(2), 1, views = listOf(view(2, futaber, 1), view(2, ftutter, 0)))), null, 1L
        )
        val current = listOf(view(1, futaber, 0), view(3, futaber, 1), view(4, ftutter, 0))
        val restored = compatRestoredViews(current, batch, setOf("tab-2"))
        assertEquals(listOf("tab-1", "tab-2", "tab-3"), restored.filter { it.profile == futaber }.sortedBy { it.ordinal }.map { it.tabKey })
        assertEquals(listOf("tab-2", "tab-4"), restored.filter { it.profile == ftutter }.sortedBy { it.ordinal }.map { it.tabKey })
        assertEquals(current, compatRestoredViews(current, batch, emptySet()))
    }

    @Test
    fun aViewIsAddedFirstOrLastAndAnExistingOneStays() {
        val views = listOf(view(1, futaber, 0), view(2, futaber, 1))
        assertEquals(listOf("tab-3", "tab-1", "tab-2"), compatViewsWith(views, futaber, "tab-3", CompatTabPlacement.FIRST, 5).map { it.tabKey })
        assertEquals(listOf("tab-1", "tab-2", "tab-3"), compatViewsWith(views, futaber, "tab-3", CompatTabPlacement.LAST, 5).map { it.tabKey })
        assertEquals(listOf("tab-1", "tab-2"), compatViewsWith(views, futaber, "tab-1", CompatTabPlacement.LAST, 5).map { it.tabKey })
    }

    @Test
    fun theTrimTakesOnlyFtutterTabsFirstThenTheOthersOldestFirst() {
        val tabs = (1..30).map { tab(it) }
        // Tabs 1..10 are shown by another mode, 11..20 only by ふたったー風, 21..30 carry no mark (ふたちゃ / としあき(仮)).
        val views = (1..10).map { view(it, futaber, it) } + (11..20).map { view(it, ftutter, it) }
        val removed = planSharedTabTrim(tabs, emptySet(), limit = 20, tabViews = views)
        // budget 20, keep 18: 12 go — the 10 only ふたったー風 shows, then the 2 oldest others (unmarked and marked alike).
        assertEquals(((11..20) + (1..2)).map { "tab-$it" }.toSet(), removed)
    }
}
