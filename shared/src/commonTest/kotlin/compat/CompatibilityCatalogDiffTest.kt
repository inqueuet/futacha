package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.CatalogItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatibilityCatalogDiffTest {
    @Test
    fun replyDeltasCompareTheCurrentCatalogWithItsPreviousGeneration() {
        val previous = listOf(item("10", replies = 5), item("20", replies = 12))
        val current = listOf(
            item("10", replies = 15),
            item("20", replies = 9),
            item("30", replies = 7)
        )

        val deltas = buildCompatCatalogReplyDeltas(current, previous)

        assertEquals(10, deltas[current[0].compatCatalogReplyDeltaKey()])
        assertEquals(0, deltas[current[1].compatCatalogReplyDeltaKey()])
        assertEquals(0, deltas[current[2].compatCatalogReplyDeltaKey()])
    }

    @Test
    fun replyDeltasNeedAPreviousGeneration() {
        assertTrue(buildCompatCatalogReplyDeltas(listOf(item("10", replies = 15)), emptyList()).isEmpty())
    }

    @Test
    fun visitedUnreadIsRedAndOtherwisePreviousCatalogDifferenceIsGray() {
        assertEquals(
            CompatCatalogReplyIndicator(7, CompatCatalogReplyIndicatorKind.UNREAD),
            resolveCompatCatalogReplyIndicator(20, checkedReplyCount = 13, previousCatalogDelta = 4)
        )
        assertEquals(
            CompatCatalogReplyIndicator(4, CompatCatalogReplyIndicatorKind.PREVIOUS_CATALOG),
            resolveCompatCatalogReplyIndicator(20, checkedReplyCount = null, previousCatalogDelta = 4)
        )
        // Once visited, zero unread suppresses the gray generation-only count.
        assertEquals(
            null,
            resolveCompatCatalogReplyIndicator(20, checkedReplyCount = 20, previousCatalogDelta = 4)
        )
    }

    @Test
    fun apkNinetyPercentBoundarySeparatesWithinFromBottom() {
        val current = (1..100).map { item((1_000 - it * 10).toString()) }
        val previous = listOf(item("995"), item("95"), item("-5"), item("900"))

        val diff = diffCompatCatalogGenerations(current, previous, requestedThreadCount = 100, enabled = true)

        assertEquals(listOf("995"), diff.vanishedWithin.map { it.id })
        assertEquals(listOf("95"), diff.vanishedBottom.map { it.id })
        // A full requested page cannot prove that entries below the fetched bottom vanished.
        assertFalse(diff.vanishedBottom.any { it.id == "-5" })
        assertFalse(diff.vanishedWithin.any { it.id == "900" })
    }

    @Test
    fun shortPageProvesItemsBelowCurrentBottomHaveDied() {
        val current = listOf(item("300"), item("200"), item("100"))
        val diff = diffCompatCatalogGenerations(
            current,
            previous = listOf(item("50")),
            requestedThreadCount = 10,
            enabled = true
        )
        assertEquals(listOf("50"), diff.vanishedBottom.map { it.id })
        assertTrue(diff.vanishedWithin.isEmpty())
    }

    @Test
    fun disabledOrEmptyGenerationNeverCreatesFalseDroppedRows() {
        val current = listOf(item("10"))
        val previous = listOf(item("11"))
        assertTrue(diffCompatCatalogGenerations(current, previous, 10, false).vanishedWithin.isEmpty())
        assertTrue(diffCompatCatalogGenerations(emptyList(), previous, 10, true).vanishedBottom.isEmpty())
    }

    @Test
    fun largeGenerationKeepsTheNinetyPercentBoundary() {
        val current = (0L until 3_000L).map { item((10_000L - it * 2L).toString()) }
        val previous = listOf(item("9999"), item("4601"), item("4001"))

        val diff = diffCompatCatalogGenerations(
            current = current,
            previous = previous,
            requestedThreadCount = 3_000,
            enabled = true
        )

        assertEquals(listOf("9999"), diff.vanishedWithin.map { it.id })
        assertEquals(listOf("4601"), diff.vanishedBottom.map { it.id })
    }

    @Test
    fun enabledDroppedProjectionAppendsAfterLiveCatalogAndSupportsUndoSuppression() {
        val live = listOf(item("300"), item("200"))
        val dropped = listOf(
            CompatDroppedCatalogItem(
                boardKey = "may-b",
                item = item("100"),
                droppedAtEpochMillis = 30L,
                lastSeenAtEpochMillis = 20L,
                classification = CompatCatalogDroppedClass.DIE
            ),
            CompatDroppedCatalogItem(
                boardKey = "may-b",
                item = item("250"),
                droppedAtEpochMillis = 40L,
                lastSeenAtEpochMillis = 30L,
                classification = CompatCatalogDroppedClass.ISOLATED
            ),
            // A stale dropped record that has returned to the live catalog
            // must never produce a duplicate lazy-layout key.
            CompatDroppedCatalogItem("may-b", item("200"), 50L, 40L)
        )

        assertEquals(
            listOf("300", "200", "250", "100"),
            appendCompatDroppedCatalogItems(live, dropped, enabled = true).map(CatalogItem::id)
        )
        assertEquals(
            listOf("300", "200", "100"),
            appendCompatDroppedCatalogItems(
                live,
                dropped,
                enabled = true,
                suppressedThreadIds = setOf("250")
            ).map(CatalogItem::id)
        )
        assertEquals(live, appendCompatDroppedCatalogItems(live, dropped, enabled = false))
        assertEquals(
            live,
            appendCompatDroppedCatalogItems(
                live,
                dropped,
                enabled = true,
                contentReady = false
            )
        )
    }

    @Test
    fun imageTimestampAndApkOldestTenPercentRuleArePreserved() {
        val items = listOf(
            item("1", "https://may.2chan.net/b/thumb/1000000s.jpg"),
            item("2", "https://may.2chan.net/b/thumb/1500000s.jpg"),
            item("3", "https://may.2chan.net/b/thumb/2000000s.jpg")
        )
        val states = buildCompatCatalogItemStates(
            items,
            previousStates = emptyMap(),
            fetchedAtEpochMillis = 9_000_000L,
            sort = CompatCatalogSort.CATALOG,
            requestedThreadCount = 300
        )
        assertTrue(states.getValue("1").isOld)
        assertFalse(states.getValue("2").isOld)
        assertEquals(1_000L, states.getValue("1").createdAtEpochSeconds)

        val newSort = buildCompatCatalogItemStates(
            items,
            previousStates = states,
            fetchedAtEpochMillis = 10_000_000L,
            sort = CompatCatalogSort.NEW,
            requestedThreadCount = 300
        )
        assertTrue(newSort.values.none { it.isOld })
        assertEquals(1_000L, newSort.getValue("1").createdAtEpochSeconds)
    }

    private fun item(id: String, thumbnail: String? = null, replies: Int = 0): CatalogItem = CatalogItem(
        id = id,
        threadUrl = "https://may.2chan.net/b/res/$id.htm",
        title = id,
        thumbnailUrl = thumbnail,
        fullImageUrl = null,
        replyCount = replies
    )
}
