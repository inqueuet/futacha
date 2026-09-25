package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.state.AppStateHistoryFileStore
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoSaveStorageMaintenanceTest {
    private fun thread(threadId: String, savedAt: Long, totalSize: Long = 10L) = SavedThread(
        threadId = threadId,
        boardId = "b",
        boardName = "board",
        title = "title-$threadId",
        storageId = buildThreadStorageId("b", threadId),
        thumbnailPath = null,
        savedAt = savedAt,
        postCount = 1,
        imageCount = 0,
        videoCount = 0,
        totalSize = totalSize,
        status = SaveStatus.COMPLETED
    )

    private fun historyEntry(threadId: String, visitedAt: Long) = ThreadHistoryEntry(
        threadId = threadId,
        boardId = "b",
        title = "title-$threadId",
        titleImageUrl = "",
        boardName = "board",
        boardUrl = "https://may.2chan.net/b/futaba.php",
        lastVisitedEpochMillis = visitedAt,
        replyCount = 1,
        hasAutoSave = true
    )

    private suspend fun SavedThreadRepository.addWithFolder(files: InMemoryFileSystem, base: String, saved: SavedThread) {
        files.writeString("$base/${saved.storageId}/metadata.json", "{}").getOrThrow()
        addThreadToIndex(saved).getOrThrow()
    }

    @Test
    fun autoSaveOverItsSizeCapEvictsTheLeastRecentlySavedThreads() = runBlocking {
        val files = InMemoryFileSystem()
        val repository = SavedThreadRepository(files, baseDirectory = AUTO_SAVE_DIRECTORY)
        repository.autoSaveTotalByteLimit = 25L
        val oldest = thread("1", savedAt = 100L)
        repository.addWithFolder(files, AUTO_SAVE_DIRECTORY, oldest)
        repository.addWithFolder(files, AUTO_SAVE_DIRECTORY, thread("2", savedAt = 200L))

        repository.addWithFolder(files, AUTO_SAVE_DIRECTORY, thread("3", savedAt = 300L))

        assertEquals(listOf("3", "2"), repository.getAllThreads().map { it.threadId })
        assertEquals(20L, repository.getTotalSize())
        assertFalse(files.exists("$AUTO_SAVE_DIRECTORY/${oldest.storageId}"))
    }

    @Test
    fun sizeCapNeverEvictsTheSavedThreadOrOneBeingSaved() = runBlocking {
        val files = InMemoryFileSystem()
        val repository = SavedThreadRepository(files, baseDirectory = AUTO_SAVE_DIRECTORY)
        repository.addWithFolder(files, AUTO_SAVE_DIRECTORY, thread("1", savedAt = 100L))
        repository.addWithFolder(files, AUTO_SAVE_DIRECTORY, thread("2", savedAt = 200L))
        repository.autoSaveTotalByteLimit = 15L

        // Thread 1 is being saved again (e.g. the thread on screen): evict 2 instead.
        AutoSaveRetentionRegistry.retain("1", "b") {
            repository.addWithFolder(files, AUTO_SAVE_DIRECTORY, thread("3", savedAt = 300L, totalSize = 50L))
        }

        // The new save alone exceeds the cap; it is kept anyway.
        assertEquals(listOf("3", "1"), repository.getAllThreads().map { it.threadId })
    }

    @Test
    fun manualSavesAreNotEvictedForSize() = runBlocking {
        val files = InMemoryFileSystem()
        val repository = SavedThreadRepository(files, baseDirectory = MANUAL_SAVE_DIRECTORY)
        repository.autoSaveTotalByteLimit = 5L
        repository.addWithFolder(files, MANUAL_SAVE_DIRECTORY, thread("1", savedAt = 100L))
        repository.addWithFolder(files, MANUAL_SAVE_DIRECTORY, thread("2", savedAt = 200L))

        assertEquals(2, repository.getThreadCount())
    }

    @Test
    fun historyTrimmedByItsLimitReportsTheDroppedEntriesAfterTheWrite() = runBlocking {
        val files = InMemoryFileSystem()
        val trimmed = CompletableDeferred<List<ThreadHistoryEntry>>()
        val json = Json { ignoreUnknownKeys = true }
        val store = AppStateStore(
            storage = FakePlatformStateStorage(),
            historyFileStore = AppStateHistoryFileStore(files, json, "test", maxEntries = 2),
            json = json,
            onHistoryEntriesTrimmed = { trimmed.complete(it) }
        )
        store.setHistory(listOf(historyEntry("1", 100L), historyEntry("2", 200L)))

        store.prependOrReplaceHistoryEntry(historyEntry("3", 300L))

        assertEquals(listOf("1"), withTimeout(5_000L) { trimmed.await() }.map { it.threadId })
        store.close()
    }

    @Test
    fun trimmedHistoryAutoSaveIsDeleted() = runBlocking {
        val files = InMemoryFileSystem()
        val repository = SavedThreadRepository(files, baseDirectory = AUTO_SAVE_DIRECTORY)
        val dropped = thread("1", savedAt = 100L)
        repository.addWithFolder(files, AUTO_SAVE_DIRECTORY, dropped)
        repository.addWithFolder(files, AUTO_SAVE_DIRECTORY, thread("2", savedAt = 200L))

        purgeAutoSavesOfTrimmedHistory(repository, listOf(historyEntry("1", 100L)))

        assertEquals(listOf("2"), repository.getAllThreads().map { it.threadId })
        assertFalse(files.exists("$AUTO_SAVE_DIRECTORY/${dropped.storageId}"))
        assertTrue(files.exists("$AUTO_SAVE_DIRECTORY/${buildThreadStorageId("b", "2")}"))
    }
}
