package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlinx.serialization.json.Json
import kotlin.test.*

class CompatHistorySupportTest {
    private fun entry(id: String, visited: Long) = CompatHistoryEntry(
        "https://may.2chan.net/b/res/$id.htm", "https://may.2chan.net/b/res/$id.htm",
        "may-b", "虹裏", id, "thread $id", contentUpdatedAtEpochMillis = visited,
        lastVisitedEpochMillis = visited
    )

    @Test fun lateNetworkResponseKeepsBrowsingOrderAndReadPosition() {
        val a = entry("1", 100).copy(scrollAnchor = ScrollAnchor(fallbackIndex = 8, offsetPx = 42))
        val b = entry("2", 200)
        val refreshed = mergeCompatHistoryEntry(a.copy(contentUpdatedAtEpochMillis = 300, lastVisitedEpochMillis = 300,
            replyCount = 50, scrollAnchor = ScrollAnchor()), a)
        assertEquals(listOf("2", "1"), listOf(refreshed, b).sortedByDescending { it.lastVisitedEpochMillis }.map { it.threadNo })
        assertEquals(300L, refreshed.contentUpdatedAtEpochMillis)
        assertEquals(50, refreshed.replyCount)
        assertEquals(a.scrollAnchor, refreshed.scrollAnchor)
        val revisited = mergeCompatHistoryEntry(a.copy(lastVisitedEpochMillis = 201), refreshed, recordVisit = true)
        assertEquals(50, revisited.replyCount)
        assertEquals(300L, revisited.contentUpdatedAtEpochMillis)
        assertEquals(listOf("1", "2"), listOf(revisited, b).sortedByDescending { it.lastVisitedEpochMillis }.map { it.threadNo })
        assertEquals(201L, mergeCompatHistoryEntry(a, revisited, recordVisit = true).lastVisitedEpochMillis)
    }

    @Test fun oldJsonKeepsLegacyOrderAndNewJsonSeparatesVisitAndUpdate() {
        val old = """{"canonicalUrl":"https://may.2chan.net/b/res/1.htm","originalUrl":"https://may.2chan.net/b/res/1.htm","boardKey":"may-b","boardName":"虹裏","threadNo":"1","title":"old","contentUpdatedAtEpochMillis":100}"""
        assertEquals(100L, Json.decodeFromString<CompatHistoryEntry>(old).lastVisitedEpochMillis)
        val value = entry("1", 100).copy(contentUpdatedAtEpochMillis = 300)
        assertEquals(value, Json.decodeFromString<CompatHistoryEntry>(Json.encodeToString(value)))
    }

    @Test fun modeBridgeSharesVisitTimeWithoutReplacingLocalScrollPosition() {
        val compat = entry("1", 100).copy(contentUpdatedAtEpochMillis = 900)
        val modern = ThreadHistoryEntry("1", "may-b", "thread", "", "虹裏", "https://may.2chan.net/b/", 200, 3,
            lastReadItemIndex = 9, lastReadItemOffset = 42)
        val updated = mergeCompatibilityHistory(listOf(modern), listOf(compat)).single()
        assertEquals(200L, updated.lastVisitedEpochMillis)
        val revisited = mergeCompatibilityHistory(listOf(updated), listOf(compat.copy(lastVisitedEpochMillis = 300))).single()
        assertEquals(300L, revisited.lastVisitedEpochMillis)
        assertEquals(9, revisited.lastReadItemIndex)
        assertEquals(42, revisited.lastReadItemOffset)
        assertEquals(100L, compat.toModernThreadHistoryEntry()!!.lastVisitedEpochMillis)
        assertEquals(300L, revisited.toCompatHistoryEntry()!!.lastVisitedEpochMillis)
    }
}
