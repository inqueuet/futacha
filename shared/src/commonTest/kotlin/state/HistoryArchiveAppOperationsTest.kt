package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedPost
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedThreadIndex
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.IMPORTED_HISTORY_DIRECTORY
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.repository.SavedThreadRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryArchiveAppOperationsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun exportAppHistoryArchive_usesSelectedEntriesWhenProvided() = runBlocking {
        val store = AppStateStore(FakePlatformStateStorage())
        store.setHistory(listOf(historyEntry("111"), historyEntry("222")))
        val fileSystem = InMemoryFileSystem()

        val result = exportAppHistoryArchive(
            stateStore = store,
            fileSystem = fileSystem,
            sourceRepositories = emptyList(),
            archiveId = "selected",
            exportedAtEpochMillis = 1_000L,
            selectedEntries = listOf(historyEntry("222"))
        ).getOrThrow()

        assertEquals(listOf("222"), result.manifest.entries.map { it.historyEntry.threadId })
        assertEquals(1, result.historyOnlyCount)
    }

    @Test
    fun importAppHistoryArchive_restoresPayloadAndMergesHistoryIntoStore() = runBlocking {
        val store = AppStateStore(FakePlatformStateStorage())
        store.setHistory(listOf(historyEntry("111", visited = 10_000L)))
        val fileSystem = InMemoryFileSystem()
        val sourceRepository = seedSavedThread(fileSystem, "222")
        val export = exportAppHistoryArchive(
            stateStore = store,
            fileSystem = fileSystem,
            sourceRepositories = listOf(sourceRepository),
            archiveId = "archive-0526",
            exportedAtEpochMillis = 1_000L,
            selectedEntries = listOf(historyEntry("222", visited = 20_000L))
        ).getOrThrow()
        val importedRepository = SavedThreadRepository(
            fileSystem = fileSystem,
            baseDirectory = IMPORTED_HISTORY_DIRECTORY
        )

        val result = importAppHistoryArchive(
            stateStore = store,
            fileSystem = fileSystem,
            destinationRepository = importedRepository,
            archiveDirectory = export.archiveDirectory
        ).getOrThrow()

        assertEquals(1, result.archiveImport.restoredPayloadCount)
        assertEquals(1, result.merge.addedCount)
        assertEquals(listOf("111", "222"), store.history.first().map { it.threadId })
        assertTrue(store.history.first().last().hasAutoSave)
        assertEquals("222", importedRepository.getAllThreads().single().threadId)
    }

    private suspend fun seedSavedThread(fileSystem: InMemoryFileSystem, threadId: String): SavedThreadRepository {
        val repository = SavedThreadRepository(fileSystem, baseDirectory = "autosaved_threads")
        val storageId = "img__${threadId}_payload"
        val savedThread = SavedThread(
            threadId = threadId,
            boardId = "b",
            boardName = "board",
            title = "title-$threadId",
            storageId = storageId,
            thumbnailPath = "thumb/1s.jpg",
            savedAt = 20_000L,
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
                lastUpdated = 20_000L
            )
        ).getOrThrow()
        val metadata = SavedThreadMetadata(
            threadId = threadId,
            boardId = "b",
            boardName = "board",
            boardUrl = "https://may.2chan.net/b/",
            title = "title-$threadId",
            storageId = storageId,
            savedAt = 20_000L,
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
            "autosaved_threads/$storageId/metadata.json",
            json.encodeToString(metadata)
        ).getOrThrow()
        fileSystem.writeString("autosaved_threads/$storageId/$threadId.htm", "<html>$threadId</html>").getOrThrow()
        fileSystem.writeBytes("autosaved_threads/$storageId/thumb/1s.jpg", byteArrayOf(1, 2)).getOrThrow()
        fileSystem.writeBytes("autosaved_threads/$storageId/src/1.jpg", byteArrayOf(1, 2, 3)).getOrThrow()
        return repository
    }

    private fun historyEntry(threadId: String, visited: Long = 100L): ThreadHistoryEntry {
        return ThreadHistoryEntry(
            threadId = threadId,
            boardId = "b",
            title = "title-$threadId",
            titleImageUrl = "thumb-$threadId",
            boardName = "board",
            boardUrl = "https://may.2chan.net/b/res/$threadId.htm",
            lastVisitedEpochMillis = visited,
            replyCount = 1
        )
    }
}
