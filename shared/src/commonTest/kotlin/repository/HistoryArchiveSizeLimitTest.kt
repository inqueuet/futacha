package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.HistoryArchiveManifest
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryArchiveSizeLimitTest {
    private fun entry(index: Int, titleLength: Int) = ThreadHistoryEntry(
        threadId = index.toString(),
        boardId = "b",
        title = "t".repeat(titleLength),
        titleImageUrl = "",
        boardName = "board",
        boardUrl = "https://example.invalid/b/",
        replyCount = 0,
        lastVisitedEpochMillis = index.toLong()
    )

    @Test
    fun exportLeavesOutEntriesThatWouldPushTheManifestPastItsLimitInsteadOfFailing() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val entries = List(60) { entry(it, titleLength = 100_000) }

        val result = exportHistoryArchive(
            fileSystem = fileSystem,
            sourceRepositories = emptyList(),
            request = HistoryArchiveExportRequest(
                archiveId = "big-manifest",
                historyEntries = entries,
                exportedAtEpochMillis = 1L
            )
        ).getOrThrow()

        assertTrue(result.omittedEntryCount > 0)
        assertEquals(entries.size, result.manifest.entryCount + result.omittedEntryCount)
        // The entries that fit are the leading ones, in order.
        assertEquals(
            entries.take(result.manifest.entryCount).map { it.threadId },
            result.manifest.entries.map { it.historyEntry.threadId }
        )
        val written = fileSystem.readString("history_archives/big-manifest/manifest.json").getOrThrow()
        assertTrue(written.encodeToByteArray().size.toLong() <= MAX_HISTORY_ARCHIVE_MANIFEST_BYTES)
        // The archive it produced can be read back.
        val imported = importHistoryArchive(
            fileSystem = fileSystem,
            destinationRepository = SavedThreadRepository(fileSystem, IMPORTED_HISTORY_DIRECTORY),
            request = HistoryArchiveImportRequest("history_archives/big-manifest")
        ).getOrThrow()
        assertEquals(result.manifest.entryCount, imported.importedHistoryEntries.size)
    }

    @Test
    fun smallExportsAreUnchangedAndWriteTheFormatVersion() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val result = exportHistoryArchive(
            fileSystem = fileSystem,
            sourceRepositories = emptyList(),
            request = HistoryArchiveExportRequest(
                archiveId = "small",
                historyEntries = listOf(entry(1, 5), entry(2, 5)),
                exportedAtEpochMillis = 1L
            )
        ).getOrThrow()
        assertEquals(0, result.omittedEntryCount)
        assertEquals(2, result.manifest.entryCount)
        val written = fileSystem.readString("history_archives/small/manifest.json").getOrThrow()
        assertTrue("\"archiveVersion\"" in written)
        assertFalse('\n' in written)
    }

    @Test
    fun importRejectsAManifestFromANewerFormatVersion() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val manifest = HistoryArchiveManifest(
            archiveVersion = 99,
            archiveId = "future",
            exportedAtEpochMillis = 1L,
            entryCount = 0,
            entries = emptyList()
        )
        fileSystem.writeString(
            "history_archives/future/manifest.json",
            Json.encodeToString(HistoryArchiveManifest.serializer(), manifest)
        ).getOrThrow()

        val result = importHistoryArchive(
            fileSystem = fileSystem,
            destinationRepository = SavedThreadRepository(fileSystem, IMPORTED_HISTORY_DIRECTORY),
            request = HistoryArchiveImportRequest("history_archives/future")
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("not supported") == true)
    }
}
