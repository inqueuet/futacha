package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.HistoryArchiveEntry
import com.valoser.futacha.shared.model.HistoryArchiveManifest
import com.valoser.futacha.shared.model.HistoryArchivePayloadStatus
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadStatus
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

    // L89: an archive is a copy from some earlier time (or another device). Importing it must not take back a status
    // this device learned since: a thread that fell, or was isolated or deleted, does not come back.
    @Test
    fun resolveHistoryArchiveImportMerge_neverTakesBackAKnownThreadStatus() {
        val fallen = historyEntry("200", visited = 5_000L).copy(
            threadStatus = ThreadStatus(isDead = true, isIsolated = true),
            isAutoRefreshDisabled = true,
            lastConfirmedAliveEpochMillis = 4_000L
        )
        val aliveInArchive = historyEntry("200", visited = 9_000L).copy(
            threadStatus = ThreadStatus(),
            lastConfirmedAliveEpochMillis = 1_000L
        )

        val merged = resolveHistoryArchiveImportMergeEntries(listOf(fallen), listOf(aliveInArchive))
            .updatedHistory.single()

        assertEquals(ThreadStatus(isDead = true, isIsolated = true), merged.threadStatus)
        assertTrue(merged.isAutoRefreshDisabled)
        assertEquals(4_000L, merged.lastConfirmedAliveEpochMillis)
        // What the archive legitimately brings still comes in.
        assertEquals(9_000L, merged.lastVisitedEpochMillis)
    }

    @Test
    fun resolveHistoryArchiveImportMerge_keepsWhatTheArchiveKnowsBeyondTheDevice() {
        val aliveHere = historyEntry("200", visited = 9_000L).copy(threadStatus = ThreadStatus())
        val deletedInArchive = historyEntry("200", visited = 5_000L).copy(
            threadStatus = ThreadStatus(isDead = true, isAdminDeleted = true),
            lastConfirmedAliveEpochMillis = 3_000L
        )
        val newToDevice = historyEntry("300", visited = 6_000L).copy(
            threadStatus = ThreadStatus(isDeleted = true),
            lastConfirmedAliveEpochMillis = 2_000L
        )

        val result = resolveHistoryArchiveImportMergeEntries(listOf(aliveHere), listOf(deletedInArchive, newToDevice))
        val byId = result.updatedHistory.associateBy { it.threadId }

        assertEquals(ThreadStatus(isDead = true, isAdminDeleted = true), byId.getValue("200").threadStatus)
        assertEquals(3_000L, byId.getValue("200").lastConfirmedAliveEpochMillis)
        assertEquals(newToDevice, byId.getValue("300"))
        // A status the archive does not have leaves the device's one as it is.
        val unknownInArchive = historyEntry("200", visited = 1_000L)
        assertEquals(
            ThreadStatus(isDead = true, isAdminDeleted = true),
            resolveHistoryArchiveImportMergeEntries(result.updatedHistory, listOf(unknownInArchive))
                .updatedHistory.first { it.threadId == "200" }.threadStatus
        )
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
