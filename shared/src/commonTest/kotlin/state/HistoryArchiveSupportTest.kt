package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.HistoryArchiveEntry
import com.valoser.futacha.shared.model.HistoryArchiveManifest
import com.valoser.futacha.shared.model.HistoryArchivePayloadStatus
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistoryArchiveSupportTest {
    @Test
    fun evaluateHistoryGrowthWarning_returnsNullBelowThresholds() {
        assertNull(
            evaluateHistoryGrowthWarning(
                entryCount = 499,
                jsonByteSize = 1_999_999L
            )
        )
    }

    @Test
    fun evaluateHistoryGrowthWarning_reportsEntryAndJsonTriggersSeparately() {
        val entryWarning = assertNotNull(
            evaluateHistoryGrowthWarning(
                entryCount = 500,
                jsonByteSize = 10L
            )
        )
        assertTrue(entryWarning.isEntryCountWarning)
        assertFalse(entryWarning.isJsonSizeWarning)

        val jsonWarning = assertNotNull(
            evaluateHistoryGrowthWarning(
                entryCount = 1,
                jsonByteSize = 2_000_000L
            )
        )
        assertFalse(jsonWarning.isEntryCountWarning)
        assertTrue(jsonWarning.isJsonSizeWarning)
    }

    @Test
    fun resolveHistoryArchiveImportMerge_appendsSelectedPartialArchiveEntriesAfterCurrentHistory() {
        val current = listOf(historyEntry(threadId = "100", visited = 10_000L))
        val imported = listOf(
            archiveEntry("archive-0526", "200", visited = 8_000L),
            archiveEntry("archive-0526", "300", visited = 9_000L)
        )

        val result = resolveHistoryArchiveImportMerge(current, imported)

        assertEquals(2, result.addedCount)
        assertEquals(0, result.updatedCount)
        assertEquals(
            listOf("100", "300", "200"),
            result.updatedHistory.map { it.threadId }
        )
    }

    @Test
    fun resolveHistoryArchiveImportMerge_isIdempotentWhenSameArchiveIsImportedAgain() {
        val imported = listOf(
            archiveEntry("archive-0526", "200", visited = 8_000L),
            archiveEntry("archive-0526", "300", visited = 9_000L)
        )
        val firstImport = resolveHistoryArchiveImportMerge(emptyList(), imported)

        val secondImport = resolveHistoryArchiveImportMerge(firstImport.updatedHistory, imported)

        assertEquals(0, secondImport.addedCount)
        assertEquals(0, secondImport.updatedCount)
        assertEquals(2, secondImport.unchangedCount)
        assertEquals(firstImport.updatedHistory, secondImport.updatedHistory)
    }

    @Test
    fun resolveHistoryArchiveImportMerge_keepsOneHistoryEntryForOverlappingThreadSnapshots() {
        val may26 = archiveEntry(
            archiveId = "archive-0526",
            threadId = "200",
            visited = 10_000L,
            readIndex = 2,
            replyCount = 20
        )
        val may27 = archiveEntry(
            archiveId = "archive-0527",
            threadId = "200",
            visited = 20_000L,
            readIndex = 7,
            replyCount = 25
        )

        val firstImport = resolveHistoryArchiveImportMerge(emptyList(), listOf(may26))
        val secondImport = resolveHistoryArchiveImportMerge(firstImport.updatedHistory, listOf(may27))

        assertEquals(1, secondImport.updatedHistory.size)
        val merged = secondImport.updatedHistory.single()
        assertEquals("200", merged.threadId)
        assertEquals(20_000L, merged.lastVisitedEpochMillis)
        assertEquals(7, merged.lastReadItemIndex)
        assertEquals(25, merged.replyCount)
    }

    @Test
    fun historyArchiveManifest_roundTripsVersionedEntries() {
        val json = Json { ignoreUnknownKeys = true }
        val manifest = HistoryArchiveManifest(
            archiveId = "archive-0526",
            exportedAtEpochMillis = 1_779_753_600_000L,
            appVersion = "1.2.3",
            entryCount = 1,
            totalPayloadBytes = 1234L,
            entries = listOf(
                archiveEntry(
                    archiveId = "archive-0526",
                    threadId = "200",
                    visited = 10_000L
                )
            )
        )

        val decoded = json.decodeFromString(
            HistoryArchiveManifest.serializer(),
            json.encodeToString(HistoryArchiveManifest.serializer(), manifest)
        )

        assertEquals(manifest, decoded)
        assertEquals(HistoryArchivePayloadStatus.FULL, decoded.entries.single().payloadStatus)
    }

    private fun archiveEntry(
        archiveId: String,
        threadId: String,
        visited: Long,
        readIndex: Int = 0,
        replyCount: Int = 10
    ): HistoryArchiveEntry {
        return HistoryArchiveEntry(
            snapshotId = "$archiveId-$threadId",
            historyEntry = historyEntry(
                threadId = threadId,
                visited = visited,
                readIndex = readIndex,
                replyCount = replyCount
            ),
            metadataPath = "threads/$archiveId/$threadId/metadata.json",
            htmlPath = "threads/$archiveId/$threadId/$threadId.htm",
            payloadStatus = HistoryArchivePayloadStatus.FULL
        )
    }

    private fun historyEntry(
        threadId: String,
        visited: Long,
        readIndex: Int = 0,
        replyCount: Int = 10
    ): ThreadHistoryEntry {
        return ThreadHistoryEntry(
            threadId = threadId,
            boardId = "img",
            title = "thread $threadId",
            titleImageUrl = "",
            boardName = "虹裏 img",
            boardUrl = "https://may.2chan.net/b/res/$threadId.htm",
            lastVisitedEpochMillis = visited,
            replyCount = replyCount,
            lastReadItemIndex = readIndex,
            lastReadItemOffset = readIndex * 10,
            hasAutoSave = true
        )
    }
}
