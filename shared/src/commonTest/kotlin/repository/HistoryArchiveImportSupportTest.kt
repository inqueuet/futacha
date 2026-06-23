package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.HistoryArchivePayloadStatus
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedPost
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedThreadIndex
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.state.resolveHistoryArchiveImportMergeEntries
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryArchiveImportSupportTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun importHistoryArchive_restoresPayloadIntoImportedRepository() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val sourceRepository = seedSavedThread(fileSystem, threadId = "123")
        val export = exportHistoryArchive(
            fileSystem = fileSystem,
            sourceRepositories = listOf(sourceRepository),
            request = HistoryArchiveExportRequest(
                archiveId = "archive-0526",
                historyEntries = listOf(historyEntry("123", visited = 10_000L)),
                exportedAtEpochMillis = 1_779_753_600_000L
            )
        ).getOrThrow()
        val importedRepository = SavedThreadRepository(fileSystem, baseDirectory = IMPORTED_HISTORY_DIRECTORY)

        val importResult = importHistoryArchive(
            fileSystem = fileSystem,
            destinationRepository = importedRepository,
            request = HistoryArchiveImportRequest(export.archiveDirectory)
        ).getOrThrow()

        assertEquals(1, importResult.restoredPayloadCount)
        assertEquals(0, importResult.historyOnlyCount)
        assertEquals(0, importResult.partialPayloadCount)
        assertTrue(importResult.importedHistoryEntries.single().hasAutoSave)
        val importedThread = importedRepository.getAllThreads().single()
        assertEquals("123", importedThread.threadId)
        assertEquals(SaveStatus.COMPLETED, importedThread.status)
        val metadata = importedRepository.loadThreadMetadata("123", "img").getOrThrow()
        assertEquals(importedThread.storageId, metadata.storageId)
        assertEquals(
            byteArrayOf(1, 2, 3).toList(),
            fileSystem.readBytes(
                "$IMPORTED_HISTORY_DIRECTORY/${importedThread.storageId}/src/1.jpg"
            ).getOrThrow().toList()
        )
    }

    @Test
    fun importHistoryArchive_importsOnlySelectedSnapshots() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val sourceRepository = seedSavedThread(fileSystem, threadId = "123")
        val export = exportHistoryArchive(
            fileSystem = fileSystem,
            sourceRepositories = listOf(sourceRepository),
            request = HistoryArchiveExportRequest(
                archiveId = "archive-selected",
                historyEntries = listOf(
                    historyEntry("123", visited = 10_000L),
                    historyEntry("456", visited = 20_000L)
                ),
                exportedAtEpochMillis = 1_779_753_600_000L
            )
        ).getOrThrow()
        val selectedSnapshot = export.manifest.entries.first { it.historyEntry.threadId == "456" }.snapshotId
        val importedRepository = SavedThreadRepository(fileSystem, baseDirectory = IMPORTED_HISTORY_DIRECTORY)

        val importResult = importHistoryArchive(
            fileSystem = fileSystem,
            destinationRepository = importedRepository,
            request = HistoryArchiveImportRequest(
                archiveDirectory = export.archiveDirectory,
                selectedSnapshotIds = setOf(selectedSnapshot)
            )
        ).getOrThrow()

        assertEquals(listOf("456"), importResult.importedHistoryEntries.map { it.threadId })
        assertEquals(1, importResult.historyOnlyCount)
        assertEquals(1, importResult.skippedEntryCount)
        assertEquals(emptyList(), importedRepository.getAllThreads())
    }

    @Test
    fun importHistoryArchive_preservesOlderSnapshotsWhenOverlappingThreadIsImportedLater() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val firstSource = seedSavedThread(fileSystem, threadId = "123", storageId = "img__123_old", savedAt = 10_000L)
        val firstExport = exportHistoryArchive(
            fileSystem = fileSystem,
            sourceRepositories = listOf(firstSource),
            request = HistoryArchiveExportRequest(
                archiveId = "archive-0526",
                historyEntries = listOf(historyEntry("123", visited = 10_000L, readIndex = 1)),
                exportedAtEpochMillis = 1_779_753_600_000L
            )
        ).getOrThrow()
        val secondSource = seedSavedThread(fileSystem, threadId = "123", storageId = "img__123_new", savedAt = 20_000L)
        val secondExport = exportHistoryArchive(
            fileSystem = fileSystem,
            sourceRepositories = listOf(secondSource),
            request = HistoryArchiveExportRequest(
                archiveId = "archive-0527",
                historyEntries = listOf(historyEntry("123", visited = 20_000L, readIndex = 5)),
                exportedAtEpochMillis = 1_779_840_000_000L
            )
        ).getOrThrow()
        val importedRepository = SavedThreadRepository(fileSystem, baseDirectory = IMPORTED_HISTORY_DIRECTORY)

        val firstImport = importHistoryArchive(
            fileSystem = fileSystem,
            destinationRepository = importedRepository,
            request = HistoryArchiveImportRequest(firstExport.archiveDirectory)
        ).getOrThrow()
        val secondImport = importHistoryArchive(
            fileSystem = fileSystem,
            destinationRepository = importedRepository,
            request = HistoryArchiveImportRequest(secondExport.archiveDirectory)
        ).getOrThrow()
        val merged = resolveHistoryArchiveImportMergeEntries(
            currentHistory = firstImport.importedHistoryEntries,
            importedHistory = secondImport.importedHistoryEntries
        )

        assertEquals(2, importedRepository.getAllThreads().size)
        assertFalse(importedRepository.getAllThreads()[0].storageId == importedRepository.getAllThreads()[1].storageId)
        assertEquals(1, merged.updatedHistory.size)
        assertEquals(20_000L, merged.updatedHistory.single().lastVisitedEpochMillis)
        assertEquals(5, merged.updatedHistory.single().lastReadItemIndex)
    }

    private suspend fun seedSavedThread(
        fileSystem: InMemoryFileSystem,
        threadId: String,
        storageId: String = "img__${threadId}_payload",
        savedAt: Long = 10_000L
    ): SavedThreadRepository {
        val repository = SavedThreadRepository(fileSystem, baseDirectory = "autosaved_threads_$storageId")
        val savedThread = SavedThread(
            threadId = threadId,
            boardId = "img",
            boardName = "虹裏 img",
            title = "thread $threadId",
            storageId = storageId,
            thumbnailPath = "thumb/1s.jpg",
            savedAt = savedAt,
            postCount = 1,
            imageCount = 1,
            videoCount = 0,
            totalSize = 30L,
            status = SaveStatus.COMPLETED
        )
        repository.saveIndex(
            SavedThreadIndex(
                threads = listOf(savedThread),
                totalSize = 30L,
                lastUpdated = savedAt
            )
        ).getOrThrow()
        val metadata = SavedThreadMetadata(
            threadId = threadId,
            boardId = "img",
            boardName = "虹裏 img",
            boardUrl = "https://may.2chan.net/b/",
            title = "thread $threadId",
            storageId = storageId,
            savedAt = savedAt,
            expiresAtLabel = null,
            posts = listOf(
                SavedPost(
                    id = threadId,
                    order = 1,
                    author = null,
                    subject = null,
                    timestamp = "now",
                    messageHtml = "body",
                    originalImageUrl = "https://example.com/src/1.jpg",
                    localImagePath = "src/1.jpg",
                    originalVideoUrl = null,
                    localVideoPath = null,
                    originalThumbnailUrl = "https://example.com/thumb/1s.jpg",
                    localThumbnailPath = "thumb/1s.jpg"
                )
            ),
            totalSize = 30L,
            rawHtmlPath = "$threadId.htm"
        )
        fileSystem.writeString(
            "autosaved_threads_$storageId/$storageId/metadata.json",
            json.encodeToString(metadata)
        ).getOrThrow()
        fileSystem.writeString("autosaved_threads_$storageId/$storageId/$threadId.htm", "<html>$threadId</html>").getOrThrow()
        fileSystem.writeBytes("autosaved_threads_$storageId/$storageId/thumb/1s.jpg", byteArrayOf(1, 2)).getOrThrow()
        fileSystem.writeBytes("autosaved_threads_$storageId/$storageId/src/1.jpg", byteArrayOf(1, 2, 3)).getOrThrow()
        return repository
    }

    private fun historyEntry(threadId: String, visited: Long, readIndex: Int = 0): ThreadHistoryEntry {
        return ThreadHistoryEntry(
            threadId = threadId,
            boardId = "img",
            title = "thread $threadId",
            titleImageUrl = "",
            boardName = "虹裏 img",
            boardUrl = "https://may.2chan.net/b/res/$threadId.htm",
            lastVisitedEpochMillis = visited,
            replyCount = 1,
            lastReadItemIndex = readIndex,
            lastReadItemOffset = readIndex * 10
        )
    }
}
