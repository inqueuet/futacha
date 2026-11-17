package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompatOwnPostMarkersTest {
    private val openUrl = "https://may.2chan.net/b/res/100.htm"
    private val historyUrl = "https://may.2chan.net/b/res/200.htm"
    private val closedUrl = "https://may.2chan.net/b/res/300.htm"

    private fun tab(url: String) = CompatTab(
        key = compatTabKey(url),
        canonicalUrl = url,
        originalUrl = url,
        boardKey = "compat_board_b",
        boardName = "b",
        threadNo = url.substringAfterLast('/').substringBefore('.'),
        title = "t",
        insertedAtEpochMillis = 0L,
        contentUpdatedAtEpochMillis = 0L
    )

    private fun history(url: String) = CompatHistoryEntry(
        canonicalUrl = url,
        originalUrl = url,
        boardKey = "compat_board_b",
        boardName = "b",
        threadNo = "200",
        title = "t",
        contentUpdatedAtEpochMillis = 0L
    )

    @Test
    fun ownPostMarkerSaveKeepsOnlyTheNewestMarkersPerThread() {
        val tabKey = compatTabKey(openUrl)
        val prefix = compatOwnPostPreferencePrefix(tabKey)
        val preferences = mapOf(
            "${prefix}101" to "1",
            "${prefix}105" to "1",
            "${prefix}103" to "1",
            "${compatOwnPostPreferencePrefix(compatTabKey(historyUrl))}1" to "1",
            "thread.autoScrollPixel" to "5"
        )
        assertEquals(
            mapOf("${prefix}101" to null, "${prefix}103" to null, "${prefix}110" to "1"),
            compatOwnPostMarkerSave(preferences, tabKey, "110", cap = 2)
        )
        // Below the cap only the new marker is written; re-saving is idempotent.
        assertEquals(mapOf("${prefix}110" to "1"), compatOwnPostMarkerSave(preferences, tabKey, "110"))
        assertEquals(mapOf("${prefix}105" to "1"), compatOwnPostMarkerSave(preferences, tabKey, "105", cap = 3))
    }

    @Test
    fun openUndoableAndHistoryThreadsStayReferenced() {
        val pending = ClosedTabBatch(
            id = "close",
            tabs = listOf(ClosedCompatTab(tab(closedUrl), 0)),
            selectedTabKey = null,
            expiresAtEpochMillis = 7_000L
        )
        assertEquals(
            setOf(compatTabKey(openUrl), compatTabKey(historyUrl), compatTabKey(closedUrl)),
            compatReferencedOwnPostTabKeys(listOf(tab(openUrl)), listOf(history(historyUrl)), pending)
        )
    }

    @Test
    fun droppedThreadsAndBoardsLoseOnlyTheirOwnRows() {
        val dropped = compatTabKey(closedUrl)
        val kept = compatTabKey(historyUrl)
        val preferences = mapOf(
            "${compatOwnPostPreferencePrefix(dropped)}301" to "1",
            "${compatOwnPostPreferencePrefix(dropped)}302" to "1",
            "${compatOwnPostPreferencePrefix(kept)}201" to "1",
            compatCatalogLastFetchCountPreferenceKey("compat_board_old", "CATALOG") to "120",
            compatCatalogLastFetchCountPreferenceKey("compat_board_b", "CATALOG") to "80",
            "control.controlPostConfirm" to "ON"
        )
        assertEquals(
            setOf(
                "${compatOwnPostPreferencePrefix(dropped)}301",
                "${compatOwnPostPreferencePrefix(dropped)}302",
                compatCatalogLastFetchCountPreferenceKey("compat_board_old", "CATALOG")
            ),
            compatDroppedReferencePreferenceDeletions(
                preferences,
                droppedTabKeys = setOf(dropped),
                droppedBoardKeys = setOf("compat_board_old")
            ).also { deletions -> assertTrue(deletions.values.all { it == null }) }.keys
        )
        assertTrue(compatDroppedReferencePreferenceDeletions(preferences, emptySet(), emptySet()).isEmpty())
    }
}
