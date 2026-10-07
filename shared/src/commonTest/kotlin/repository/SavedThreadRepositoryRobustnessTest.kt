package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.service.buildThreadStorageId
import com.valoser.futacha.shared.util.FileSystem
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SavedThreadRepositoryRobustnessTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val tree = SaveLocation.TreeUri("content://tree/root")

    private fun savedThread(threadId: String, boardId: String = "b") = SavedThread(
        threadId = threadId,
        boardId = boardId,
        boardName = "board",
        title = "title-$threadId",
        storageId = buildThreadStorageId(boardId, threadId),
        thumbnailPath = null,
        savedAt = 100L,
        postCount = 1,
        imageCount = 0,
        videoCount = 0,
        totalSize = 10L,
        status = SaveStatus.COMPLETED
    )

    private fun metadata(threadId: String, boardId: String = "b") = SavedThreadMetadata(
        threadId = threadId,
        boardId = boardId,
        boardName = "board",
        boardUrl = "https://may.2chan.net/$boardId/futaba.php",
        title = "title-$threadId",
        storageId = buildThreadStorageId(boardId, threadId),
        savedAt = 100L,
        expiresAtLabel = null,
        posts = emptyList(),
        totalSize = 10L
    )

    /** A document tree whose exists() reports a provider failure as "absent", as Android's does. */
    private class FlakyTreeFileSystem(private val delegate: InMemoryFileSystem) : FileSystem by delegate {
        var hideIndexFromExists = false
        var failListing = false

        override suspend fun exists(base: SaveLocation, relativePath: String): Boolean =
            if (hideIndexFromExists && relativePath.startsWith("index.json")) false
            else delegate.exists(base, relativePath)

        override suspend fun listFilesOrThrow(base: SaveLocation, directory: String): List<String> {
            if (failListing) throw IllegalStateException("provider unavailable")
            return delegate.listFilesOrThrow(base, directory)
        }
    }

    @Test
    fun indexIsNotReplacedByAnEmptyOneWhenTheDocumentTreeCannotAnswerRightNow() = runBlocking {
        val files = FlakyTreeFileSystem(InMemoryFileSystem())
        val writer = SavedThreadRepository(files, baseDirectory = "ignored", baseSaveLocation = tree)
        writer.addThreadToIndex(savedThread("1")).getOrThrow()
        writer.addThreadToIndex(savedThread("2")).getOrThrow()
        val before = files.readString(tree, "index.json").getOrThrow()

        // A fresh instance has no cached index; the provider reports index.json as absent.
        files.hideIndexFromExists = true
        val reader = SavedThreadRepository(files, baseDirectory = "ignored", baseSaveLocation = tree)
        assertFailsWith<IllegalStateException> { reader.loadIndex() }
        assertTrue(reader.addThreadToIndex(savedThread("3")).isFailure)

        files.hideIndexFromExists = false
        assertEquals(before, files.readString(tree, "index.json").getOrThrow())
        assertEquals(setOf("1", "2"), reader.getAllThreads().map { it.threadId }.toSet())
    }

    @Test
    fun aFailedListingIsRetriedInsteadOfStartingFromAnEmptyIndex() = runBlocking {
        val files = FlakyTreeFileSystem(InMemoryFileSystem())
        files.failListing = true
        val repository = SavedThreadRepository(files, baseDirectory = "ignored", baseSaveLocation = tree)
        assertFailsWith<IllegalStateException> { repository.loadIndex() }
        assertTrue(repository.addThreadToIndex(savedThread("1")).isFailure)

        files.failListing = false
        repository.addThreadToIndex(savedThread("1")).getOrThrow()
        assertEquals(listOf("1"), repository.getAllThreads().map { it.threadId })
    }

    @Test
    fun aGenuinelyNewOrIndexlessLocationStillStartsEmpty() = runBlocking {
        val files = FlakyTreeFileSystem(InMemoryFileSystem())
        val fresh = SavedThreadRepository(files, baseDirectory = "ignored", baseSaveLocation = tree)
        assertTrue(fresh.loadIndex().threads.isEmpty())

        // Saved thread folders but no index file: not transient, and recoverable by scanning.
        val other = SaveLocation.TreeUri("content://tree/other")
        files.createDirectory(other, buildThreadStorageId("b", "9")).getOrThrow()
        files.writeString(other, "unrelated.txt", "x").getOrThrow()
        val withFolders = SavedThreadRepository(files, baseDirectory = "ignored", baseSaveLocation = other)
        assertTrue(withFolders.loadIndex().threads.isEmpty())
        withFolders.addThreadToIndex(savedThread("1")).getOrThrow()
        assertEquals(listOf("1"), withFolders.getAllThreads().map { it.threadId })
    }

    private class MetadataReadCountingFileSystem(
        val delegate: InMemoryFileSystem
    ) : FileSystem by delegate {
        var metadataReads = 0

        override suspend fun readString(path: String): Result<String> {
            if (path.endsWith("metadata.json")) metadataReads += 1
            return delegate.readString(path)
        }
    }

    @Test
    fun deletingHistoryEntriesReadsEachFoldersMetadataOnlyOnce() = runBlocking {
        val files = MetadataReadCountingFileSystem(InMemoryFileSystem())
        val ids = listOf("1", "2", "3", "4")
        ids.forEach { id ->
            val storageId = buildThreadStorageId("b", id)
            files.delegate.writeString(
                "saved_threads/$storageId/metadata.json",
                json.encodeToString(metadata(id))
            ).getOrThrow()
        }
        val repository = SavedThreadRepository(files, baseDirectory = "saved_threads")

        repository.purgeThreadStorage("1", "b").getOrThrow()
        val readsAfterFirst = files.metadataReads
        assertTrue(readsAfterFirst >= ids.size)

        repository.purgeThreadStorage("2", "b").getOrThrow()
        repository.purgeThreadStorage("3", "b").getOrThrow()
        assertEquals(readsAfterFirst, files.metadataReads)

        assertTrue(!files.delegate.exists("saved_threads/${buildThreadStorageId("b", "2")}"))
        assertTrue(!files.delegate.exists("saved_threads/${buildThreadStorageId("b", "3")}"))
        assertTrue(files.delegate.exists("saved_threads/${buildThreadStorageId("b", "4")}"))
    }

    @Test
    fun orphanFolderCreatedAfterTheFirstScanIsStillFound() = runBlocking {
        val files = MetadataReadCountingFileSystem(InMemoryFileSystem())
        val repository = SavedThreadRepository(files, baseDirectory = "saved_threads")
        val keep = buildThreadStorageId("b", "9")
        files.delegate.writeString("saved_threads/$keep/metadata.json", json.encodeToString(metadata("9"))).getOrThrow()
        repository.purgeThreadStorage("1", "b").getOrThrow()

        // An older generation of thread 2 left behind under a different folder name.
        files.delegate.writeString(
            "saved_threads/old_generation_of_2/metadata.json",
            json.encodeToString(metadata("2"))
        ).getOrThrow()
        repository.purgeThreadStorage("2", "b").getOrThrow()

        assertTrue(!files.delegate.exists("saved_threads/old_generation_of_2"))
        assertTrue(files.delegate.exists("saved_threads/$keep"))
    }

    @Test
    fun purgingSeveralIndexedThreadsTogetherRemovesOnlyThose() = runBlocking {
        val files = InMemoryFileSystem()
        val repository = SavedThreadRepository(files, baseDirectory = "saved_threads")
        listOf("1", "2", "3").forEach { id ->
            repository.addThreadToIndex(savedThread(id)).getOrThrow()
            files.writeString(
                "saved_threads/${buildThreadStorageId("b", id)}/metadata.json",
                json.encodeToString(metadata(id))
            ).getOrThrow()
        }

        repository.purgeIndexedThreadsStorage(listOf("1" to "b", "2" to "B")).getOrThrow()

        assertEquals(listOf("3"), repository.getAllThreads().map { it.threadId })
        assertTrue(!files.exists("saved_threads/${buildThreadStorageId("b", "1")}"))
        assertTrue(!files.exists("saved_threads/${buildThreadStorageId("b", "2")}"))
        assertTrue(files.exists("saved_threads/${buildThreadStorageId("b", "3")}"))
        assertTrue(repository.purgeIndexedThreadsStorage(emptyList()).isSuccess)
    }
}
