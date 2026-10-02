package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.HistoryArchiveEntry
import com.valoser.futacha.shared.model.HistoryArchiveManifest
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.ui.isHistoryCoveredByArchive
import com.valoser.futacha.shared.util.isStalePartialFile
import com.valoser.futacha.shared.util.partialFileName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistoryArchiveCleanupSupportTest {
    private val hour = 60L * 60L * 1000L

    @Test
    fun onlyAppNamedArchivesWithoutManifestOlderThanAnHourAreAbandoned() {
        val now = 10_000_000_000_000L
        assertEquals(now - 2 * hour, historyArchiveFolderEpochMillis("history_${now - 2 * hour}"))
        assertNull(historyArchiveFolderEpochMillis("my_backup"))
        assertTrue(isAbandonedHistoryArchive("history_${now - 2 * hour}", hasManifest = false, nowEpochMillis = now))
        assertFalse(isAbandonedHistoryArchive("history_${now - 2 * hour}", hasManifest = true, nowEpochMillis = now))
        assertFalse(isAbandonedHistoryArchive("history_${now - 60_000L}", hasManifest = false, nowEpochMillis = now))
        assertFalse(isAbandonedHistoryArchive("my_backup", hasManifest = false, nowEpochMillis = now))
    }

    @Test
    fun stalePartialFilesAreRecognisedWithoutTouchingLiveWrites() {
        val now = 10_000_000_000_000L
        assertTrue(isStalePartialFile(partialFileName("a.zip", now - 2 * hour, "ff"), "a.zip", now))
        assertFalse(isStalePartialFile(partialFileName("a.zip", now - 1_000L, "ff"), "a.zip", now))
        // Format written by earlier builds (no timestamp).
        assertTrue(isStalePartialFile(".a.zip.partial-1f2e3d", "a.zip", now))
        assertFalse(isStalePartialFile(".b.zip.partial-1f2e3d", "a.zip", now))
        assertFalse(isStalePartialFile("a.zip", "a.zip", now))
    }

    @Test
    fun historyIsCoveredOnlyWhenEveryEntryWasArchivedWithTheSameVisit() {
        fun entry(id: String, visited: Long) = ThreadHistoryEntry(
            threadId = id, boardId = "b", title = id, titleImageUrl = "", boardName = "B",
            boardUrl = "https://may.2chan.net/b/", lastVisitedEpochMillis = visited, replyCount = 0
        )
        val manifest = HistoryArchiveManifest(
            archiveId = "history_1", exportedAtEpochMillis = 1L, entryCount = 2,
            entries = listOf(HistoryArchiveEntry("s1", entry("1", 10)), HistoryArchiveEntry("s2", entry("2", 20)))
        )
        assertTrue(isHistoryCoveredByArchive(listOf(entry("1", 10), entry("2", 20)), manifest))
        assertTrue(isHistoryCoveredByArchive(listOf(entry("1", 10)), manifest))
        assertFalse(isHistoryCoveredByArchive(listOf(entry("1", 10), entry("3", 30)), manifest))
        assertFalse(isHistoryCoveredByArchive(listOf(entry("1", 11)), manifest))
    }
}
