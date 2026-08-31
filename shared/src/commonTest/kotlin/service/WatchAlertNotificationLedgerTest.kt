package com.valoser.futacha.shared.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WatchAlertNotificationLedgerTest {
    @Test
    fun markMatchesPreservesAllCurrentMatchesEvenWhenTheyExceedCap() {
        val matches = (1..5).map { index -> watchAlertMatch(index.toString()) }

        val serialized = WatchAlertNotificationLedger.markMatches(
            serializedEntries = null,
            matches = matches,
            nowMillis = 100L,
            maxEntries = 2
        )

        val secondPass = WatchAlertNotificationLedger.filterNewMatches(
            serializedEntries = serialized,
            matches = matches
        )

        assertEquals(emptyList(), secondPass)
        assertTrue(WatchAlertNotificationLedger.readEntries(serialized).size >= matches.size)
    }

    @Test
    fun filterNewMatchesReadsLegacyKeys() {
        val matches = listOf(watchAlertMatch("1"), watchAlertMatch("2"))

        val filtered = WatchAlertNotificationLedger.filterNewMatches(
            serializedEntries = null,
            legacyKeys = setOf("b::1"),
            matches = matches
        )

        assertEquals(listOf("2"), filtered.map { it.threadId })
    }

    @Test
    fun readEntriesCapsMalformedOversizedLedger() {
        val serialized = (1..25_000).joinToString("\n") { index -> "$index\tb::$index" }

        val entries = WatchAlertNotificationLedger.readEntries(serialized)

        assertEquals(20_000, entries.size)
    }

    @Test
    fun serializationNeverExceedsPersistenceBudget() {
        val matches = (1..5_000).map { index ->
            watchAlertMatch("$index-${"x".repeat(1_000)}")
        }

        val serialized = WatchAlertNotificationLedger.markMatches(
            serializedEntries = null,
            matches = matches,
            nowMillis = 100L
        )

        assertTrue(serialized.length <= 2 * 1024 * 1024)
    }
}

private fun watchAlertMatch(threadId: String): CatalogWatchAlertMatch {
    return CatalogWatchAlertMatch(
        threadId = threadId,
        boardId = "b",
        boardName = "二次元裏",
        boardUrl = "https://may.2chan.net/b/futaba.php",
        title = "watch $threadId",
        titleImageUrl = "",
        replyCount = 0,
        detectedAtEpochMillis = 1L
    )
}
