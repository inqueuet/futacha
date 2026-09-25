package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedThreadIndex
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.buildThreadStorageId
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SavedThreadIndexLimitTest {
    private fun thread(threadId: String, savedAt: Long, title: String = "title-$threadId") = SavedThread(
        threadId = threadId,
        boardId = "b",
        boardName = "board",
        title = title,
        storageId = buildThreadStorageId("b", threadId),
        thumbnailPath = null,
        savedAt = savedAt,
        postCount = 1,
        imageCount = 0,
        videoCount = 0,
        totalSize = 10L,
        status = SaveStatus.COMPLETED
    )

    private suspend fun InMemoryFileSystem.saveFolder(base: String, thread: SavedThread) {
        writeString("$base/${thread.storageId}/metadata.json", "{}").getOrThrow()
    }

    @Test
    fun autoSaveIndexDropsItsOldestSavesAtTheEntryCap() = runBlocking {
        val files = InMemoryFileSystem()
        val repository = SavedThreadRepository(files, baseDirectory = AUTO_SAVE_DIRECTORY)
        repository.indexEntryLimit = 3
        val saved = (1..3).map { thread("$it", savedAt = it * 100L) }
        saved.forEach {
            files.saveFolder(AUTO_SAVE_DIRECTORY, it)
            repository.addThreadToIndex(it).getOrThrow()
        }

        val newest = thread("4", savedAt = 400L)
        files.saveFolder(AUTO_SAVE_DIRECTORY, newest)
        repository.addThreadToIndex(newest).getOrThrow()

        assertEquals(listOf("4", "3", "2"), repository.getAllThreads().map { it.threadId })
        assertFalse(files.exists("$AUTO_SAVE_DIRECTORY/${saved.first().storageId}"))
        assertTrue(files.exists("$AUTO_SAVE_DIRECTORY/${newest.storageId}"))
    }

    @Test
    fun autoSaveIndexDropsItsOldestSavesToStayReadable() = runBlocking {
        val files = InMemoryFileSystem()
        val repository = SavedThreadRepository(files, baseDirectory = AUTO_SAVE_DIRECTORY)
        repository.addThreadToIndex(thread("1", savedAt = 100L, title = "a".repeat(300))).getOrThrow()
        repository.addThreadToIndex(thread("2", savedAt = 200L, title = "b".repeat(300))).getOrThrow()
        repository.indexByteLimit = files.getFileSize("$AUTO_SAVE_DIRECTORY/index.json") + 50L

        repository.addThreadToIndex(thread("3", savedAt = 300L, title = "c".repeat(300))).getOrThrow()

        assertEquals(listOf("3", "2"), repository.getAllThreads().map { it.threadId })
        assertTrue(files.getFileSize("$AUTO_SAVE_DIRECTORY/index.json") <= repository.indexByteLimit)
    }

    @Test
    fun manualIndexRefusesToGrowPastItsCapInsteadOfWritingAnUnreadableIndex() = runBlocking {
        val files = InMemoryFileSystem()
        val repository = SavedThreadRepository(files, baseDirectory = MANUAL_SAVE_DIRECTORY)
        repository.indexEntryLimit = 2
        repository.addThreadToIndex(thread("1", savedAt = 100L)).getOrThrow()
        repository.addThreadToIndex(thread("2", savedAt = 200L)).getOrThrow()

        val failure = repository.addThreadToIndex(thread("3", savedAt = 300L)).exceptionOrNull()

        assertIs<SavedThreadIndexLimitException>(failure)
        assertEquals(listOf("2", "1"), repository.getAllThreads().map { it.threadId })
    }

    @Test
    fun indexWrittenWithTooManyEntriesIsReadNotTreatedAsCorrupt() = runBlocking {
        val files = InMemoryFileSystem()
        val threads = (1..5).map { thread("$it", savedAt = it * 100L) }
        files.writeString(
            "$AUTO_SAVE_DIRECTORY/index.json",
            Json.encodeToString(SavedThreadIndex.serializer(), SavedThreadIndex(threads, 50L, 1L))
        ).getOrThrow()
        val repository = SavedThreadRepository(files, baseDirectory = AUTO_SAVE_DIRECTORY)
        repository.indexEntryLimit = 3

        assertEquals(listOf("5", "4", "3"), repository.getAllThreads().map { it.threadId })
        repository.addThreadToIndex(thread("6", savedAt = 600L)).getOrThrow()
        assertEquals(listOf("6", "5", "4"), repository.getAllThreads().map { it.threadId })
    }

    @Test
    fun autoSaveFoldersOfEntriesDroppedFromAnOversizedIndexAreDeleted() = runBlocking {
        val files = InMemoryFileSystem()
        val threads = (1..5).map { thread("$it", savedAt = it * 100L) }
        threads.forEach { files.saveFolder(AUTO_SAVE_DIRECTORY, it) }
        files.writeString(
            "$AUTO_SAVE_DIRECTORY/index.json",
            Json.encodeToString(SavedThreadIndex.serializer(), SavedThreadIndex(threads, 50L, 1L))
        ).getOrThrow()
        val repository = SavedThreadRepository(files, baseDirectory = AUTO_SAVE_DIRECTORY)
        repository.indexEntryLimit = 3

        assertEquals(listOf("5", "4", "3"), repository.getAllThreads().map { it.threadId })

        withTimeout(5_000L) {
            while (files.exists("$AUTO_SAVE_DIRECTORY/${threads[0].storageId}") ||
                files.exists("$AUTO_SAVE_DIRECTORY/${threads[1].storageId}")
            ) {
                delay(10L)
            }
        }
        threads.drop(2).forEach { kept ->
            assertTrue(files.exists("$AUTO_SAVE_DIRECTORY/${kept.storageId}/metadata.json"))
        }
    }

    @Test
    fun manualFoldersOfEntriesDroppedFromAnOversizedIndexAreKept() = runBlocking {
        val files = InMemoryFileSystem()
        val threads = (1..3).map { thread("$it", savedAt = it * 100L) }
        threads.forEach { files.saveFolder(MANUAL_SAVE_DIRECTORY, it) }
        files.writeString(
            "$MANUAL_SAVE_DIRECTORY/index.json",
            Json.encodeToString(SavedThreadIndex.serializer(), SavedThreadIndex(threads, 30L, 1L))
        ).getOrThrow()
        val repository = SavedThreadRepository(files, baseDirectory = MANUAL_SAVE_DIRECTORY)
        repository.indexEntryLimit = 2

        assertEquals(listOf("3", "2"), repository.getAllThreads().map { it.threadId })
        delay(100L)

        // Still recoverable with recoverUnindexedThreads.
        assertTrue(files.exists("$MANUAL_SAVE_DIRECTORY/${threads[0].storageId}/metadata.json"))
    }
}
