package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedPost
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedThreadIndex
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.service.buildLegacyThreadStorageId
import com.valoser.futacha.shared.service.buildThreadStorageId
import com.valoser.futacha.shared.util.FileSystem
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SavedThreadRepositoryTest {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Test
    fun loadIndex_repairsDuplicateStorageEntriesAndTotalSize() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val repository = SavedThreadRepository(fileSystem, baseDirectory = "saved_threads")
        val storageId = buildThreadStorageId("b", "123")
        val newerThread = savedThread(
            threadId = "123",
            boardId = "b",
            storageId = storageId,
            savedAt = 200L,
            totalSize = 20L
        )
        val olderThread = savedThread(
            threadId = "123",
            boardId = "b",
            storageId = storageId,
            savedAt = 100L,
            totalSize = 10L
        )
        val otherThread = savedThread(
            threadId = "456",
            boardId = "b",
            storageId = buildThreadStorageId("b", "456"),
            savedAt = 150L,
            totalSize = 40L
        )

        fileSystem.writeString(
            "saved_threads/index.json",
            json.encodeToString(
                SavedThreadIndex(
                    threads = listOf(olderThread, newerThread, otherThread),
                    totalSize = 999L,
                    lastUpdated = 1L
                )
            )
        ).getOrThrow()

        val repaired = repository.loadIndex()

        assertEquals(listOf(newerThread, otherThread), repaired.threads)
        assertEquals(60L, repaired.totalSize)
    }

    @Test
    fun savedThreadRepositorySupport_resolvesMetadataCandidatesAndSanitizesIndex() {
        val newer = savedThread(
            threadId = "123",
            boardId = "b",
            storageId = buildThreadStorageId("b", "123"),
            savedAt = 200L,
            totalSize = 20L
        )
        val older = savedThread(
            threadId = "123",
            boardId = "b",
            storageId = buildThreadStorageId("b", "123"),
            savedAt = 100L,
            totalSize = 10L
        )
        val sanitized = sanitizeSavedThreadIndex(
            index = SavedThreadIndex(
                threads = listOf(older, newer),
                totalSize = 999L,
                lastUpdated = 1L
            ),
            nowMillis = 300L
        )

        assertEquals(listOf(newer), sanitized.index.threads)
        assertEquals(20L, sanitized.index.totalSize)
        assertEquals(1, sanitized.droppedDuplicateCount)
        assertEquals(
            listOf(
                "${buildThreadStorageId("b", "123")}/metadata.json",
                "${buildThreadStorageId("b", "123")}/metadata.json",
                "${buildLegacyThreadStorageId("b", "123")}/metadata.json",
                "123/metadata.json"
            ),
            resolveSavedThreadMetadataCandidates(listOf(older, newer), "123", "b")
        )
    }

    @Test
    fun loadThreadMetadata_fallsBackToLegacyStoragePath() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val repository = SavedThreadRepository(fileSystem, baseDirectory = "saved_threads")
        val legacyStorageId = buildLegacyThreadStorageId("b", "123")
        val metadata = savedMetadata(threadId = "123", boardId = "b", storageId = legacyStorageId)

        fileSystem.writeString(
            "saved_threads/$legacyStorageId/metadata.json",
            json.encodeToString(metadata)
        ).getOrThrow()

        val loaded = repository.loadThreadMetadata("123", "b").getOrThrow()

        assertEquals(metadata, loaded)
    }

    @Test
    fun loadIndex_usesBackupWhenPrimaryIsCorrupted() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val repository = SavedThreadRepository(fileSystem, baseDirectory = "saved_threads")
        val thread = savedThread(
            threadId = "123",
            boardId = "b",
            storageId = buildThreadStorageId("b", "123"),
            savedAt = 100L,
            totalSize = 10L
        )

        fileSystem.writeString("saved_threads/index.json", "{invalid").getOrThrow()
        fileSystem.writeString(
            "saved_threads/index.json.backup",
            json.encodeToString(
                SavedThreadIndex(
                    threads = listOf(thread),
                    totalSize = 10L,
                    lastUpdated = 1L
                )
            )
        ).getOrThrow()

        val loaded = repository.loadIndex()

        assertEquals(listOf(thread), loaded.threads)
        assertEquals(10L, loaded.totalSize)
    }

    @Test
    fun loadIndex_throwsWhenPrimaryAndBackupAreBothCorrupted() {
        runBlocking {
        val fileSystem = InMemoryFileSystem()
        val repository = SavedThreadRepository(fileSystem, baseDirectory = "saved_threads")

        fileSystem.writeString("saved_threads/index.json", "{invalid").getOrThrow()
        fileSystem.writeString("saved_threads/index.json.backup", "{invalid").getOrThrow()

        assertFailsWith<IllegalStateException> {
            repository.loadIndex()
        }
        }
    }

    @Test
    fun threadExists_checksFallbackStoragePathsWhenIndexIsMissing() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val repository = SavedThreadRepository(fileSystem, baseDirectory = "saved_threads")
        val currentStorageId = buildThreadStorageId("b", "123")

        fileSystem.createDirectory("saved_threads/$currentStorageId").getOrThrow()

        assertTrue(repository.threadExists("123", "b"))
    }

    @Test
    fun getThreadHtmlPath_returnsResolvedAbsolutePathForPathStorage() {
        val fileSystem = InMemoryFileSystem()
        val repository = SavedThreadRepository(fileSystem, baseDirectory = "saved_threads")
        val storageId = buildThreadStorageId("b", "123")

        val path = repository.getThreadHtmlPath("123", "b")

        assertEquals("/virtual/saved_threads/$storageId/123.htm", path)
    }

    @Test
    fun getThreadHtmlPath_returnsRelativePathForTreeUriStorage() {
        val fileSystem = InMemoryFileSystem()
        val repository = SavedThreadRepository(
            fileSystem = fileSystem,
            baseDirectory = "ignored",
            baseSaveLocation = SaveLocation.TreeUri("content://tree/root")
        )
        val storageId = buildThreadStorageId("b", "123")

        val path = repository.getThreadHtmlPath("123", "b")

        assertEquals("$storageId/123.htm", path)
    }

    @Test
    fun updateThread_replacesExistingEntryAndRecalculatesStats() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val repository = SavedThreadRepository(fileSystem, baseDirectory = "saved_threads")
        val original = savedThread(
            threadId = "123",
            boardId = "b",
            storageId = buildThreadStorageId("b", "123"),
            savedAt = 100L,
            totalSize = 10L
        )
        val other = savedThread(
            threadId = "456",
            boardId = "b",
            storageId = buildThreadStorageId("b", "456"),
            savedAt = 90L,
            totalSize = 20L
        )
        repository.addThreadToIndex(original).getOrThrow()
        repository.addThreadToIndex(other).getOrThrow()

        val updated = original.copy(
            title = "updated-title",
            totalSize = 99L,
            imageCount = 3
        )

        repository.updateThread(updated).getOrThrow()

        val threads = repository.getAllThreads()
        assertEquals(listOf(updated, other), threads)
        assertEquals(119L, repository.getTotalSize())
    }

    @Test
    fun removeThreadFromIndex_removesMatchingEntryAndRecalculatesStats() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val repository = SavedThreadRepository(fileSystem, baseDirectory = "saved_threads")
        val target = savedThread(
            threadId = "123",
            boardId = "b",
            storageId = buildThreadStorageId("b", "123"),
            savedAt = 100L,
            totalSize = 10L
        )
        val other = savedThread(
            threadId = "456",
            boardId = "b",
            storageId = buildThreadStorageId("b", "456"),
            savedAt = 90L,
            totalSize = 20L
        )

        repository.addThreadToIndex(target).getOrThrow()
        repository.addThreadToIndex(other).getOrThrow()

        repository.removeThreadFromIndex("123", "b").getOrThrow()

        assertEquals(listOf(other), repository.getAllThreads())
        assertEquals(20L, repository.getTotalSize())
    }

    @Test
    fun deleteThread_removesStoredDirectoryAndIndexEntry() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val repository = SavedThreadRepository(fileSystem, baseDirectory = "saved_threads")
        val target = savedThread(
            threadId = "123",
            boardId = "b",
            storageId = buildThreadStorageId("b", "123"),
            savedAt = 100L,
            totalSize = 10L
        )
        val other = savedThread(
            threadId = "456",
            boardId = "b",
            storageId = buildThreadStorageId("b", "456"),
            savedAt = 90L,
            totalSize = 20L
        )
        repository.addThreadToIndex(target).getOrThrow()
        repository.addThreadToIndex(other).getOrThrow()
        fileSystem.writeString("saved_threads/${target.storageId}/metadata.json", "{}").getOrThrow()
        fileSystem.writeString("saved_threads/${other.storageId}/metadata.json", "{}").getOrThrow()

        repository.deleteThread("123", "b").getOrThrow()

        assertTrue(!fileSystem.exists("saved_threads/${target.storageId}"))
        assertTrue(fileSystem.exists("saved_threads/${other.storageId}"))
        assertEquals(listOf(other), repository.getAllThreads())
        assertEquals(20L, repository.getTotalSize())
    }

    @Test
    fun deleteAllThreads_clearsDirectoriesAndIndex() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val repository = SavedThreadRepository(fileSystem, baseDirectory = "saved_threads")
        val first = savedThread(
            threadId = "123",
            boardId = "b",
            storageId = buildThreadStorageId("b", "123"),
            savedAt = 100L,
            totalSize = 10L
        )
        val second = savedThread(
            threadId = "456",
            boardId = "img",
            storageId = buildThreadStorageId("img", "456"),
            savedAt = 90L,
            totalSize = 20L
        )
        repository.addThreadToIndex(first).getOrThrow()
        repository.addThreadToIndex(second).getOrThrow()
        fileSystem.writeString("saved_threads/${first.storageId}/metadata.json", "{}").getOrThrow()
        fileSystem.writeString("saved_threads/${second.storageId}/metadata.json", "{}").getOrThrow()

        repository.deleteAllThreads().getOrThrow()

        assertTrue(!fileSystem.exists("saved_threads/${first.storageId}"))
        assertTrue(!fileSystem.exists("saved_threads/${second.storageId}"))
        assertEquals(emptyList(), repository.getAllThreads())
        assertEquals(0L, repository.getTotalSize())
    }

    @Test
    fun deleteThread_cleansUpStaleOperationBackups() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val repository = SavedThreadRepository(fileSystem, baseDirectory = "saved_threads")
        val target = savedThread(
            threadId = "123",
            boardId = "b",
            storageId = buildThreadStorageId("b", "123"),
            savedAt = 100L,
            totalSize = 10L
        )
        repository.addThreadToIndex(target).getOrThrow()
        fileSystem.writeString("saved_threads/${target.storageId}/metadata.json", "{}").getOrThrow()
        fileSystem.writeString("saved_threads/index.json.0.thread_delete.backup", "old").getOrThrow()
        fileSystem.writeString("saved_threads/index.json.1.all_delete.backup", "old").getOrThrow()

        repository.deleteThread("123", "b").getOrThrow()

        assertTrue(!fileSystem.exists("saved_threads/index.json.0.thread_delete.backup"))
        assertTrue(!fileSystem.exists("saved_threads/index.json.1.all_delete.backup"))
    }

    @Test
    fun threadExists_and_deleteThread_workWithTreeUriStorage() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val base = SaveLocation.TreeUri("content://tree/root")
        val repository = SavedThreadRepository(
            fileSystem = fileSystem,
            baseDirectory = "ignored",
            baseSaveLocation = base
        )
        val target = savedThread(
            threadId = "123",
            boardId = "b",
            storageId = buildThreadStorageId("b", "123"),
            savedAt = 100L,
            totalSize = 10L
        )
        val other = savedThread(
            threadId = "456",
            boardId = "b",
            storageId = buildThreadStorageId("b", "456"),
            savedAt = 90L,
            totalSize = 20L
        )
        repository.addThreadToIndex(target).getOrThrow()
        repository.addThreadToIndex(other).getOrThrow()
        fileSystem.createDirectory(base, requireNotNull(target.storageId)).getOrThrow()
        fileSystem.createDirectory(base, requireNotNull(other.storageId)).getOrThrow()

        assertTrue(repository.threadExists("123", "b"))

        repository.deleteThread("123", "b").getOrThrow()

        assertTrue(!fileSystem.exists(base, requireNotNull(target.storageId)))
        assertTrue(fileSystem.exists(base, requireNotNull(other.storageId)))
        assertEquals(listOf(other), repository.getAllThreads())
    }

    private fun savedThread(
        threadId: String,
        boardId: String,
        storageId: String,
        savedAt: Long,
        totalSize: Long
    ): SavedThread {
        return SavedThread(
            threadId = threadId,
            boardId = boardId,
            boardName = "board",
            title = "title-$threadId",
            storageId = storageId,
            thumbnailPath = null,
            savedAt = savedAt,
            postCount = 1,
            imageCount = 0,
            videoCount = 0,
            totalSize = totalSize,
            status = SaveStatus.COMPLETED
        )
    }

    private fun savedMetadata(
        threadId: String,
        boardId: String,
        storageId: String
    ): SavedThreadMetadata {
        return SavedThreadMetadata(
            threadId = threadId,
            boardId = boardId,
            boardName = "board",
            boardUrl = "https://may.2chan.net/$boardId/futaba.php",
            title = "title-$threadId",
            storageId = storageId,
            savedAt = 100L,
            expiresAtLabel = null,
            posts = listOf(
                SavedPost(
                    id = "1",
                    order = 1,
                    author = null,
                    subject = null,
                    timestamp = "24/01/01(月)00:00:00",
                    messageHtml = "body",
                    originalImageUrl = null,
                    localImagePath = null,
                    originalVideoUrl = null,
                    localVideoPath = null,
                    originalThumbnailUrl = null,
                    localThumbnailPath = null
                )
            ),
            totalSize = 10L
        )
    }
}

internal class InMemoryFileSystem : FileSystem {
    private val files = linkedMapOf<String, ByteArray>()
    private val directories = linkedSetOf<String>("/", "/virtual")

    override suspend fun createDirectory(path: String): Result<Unit> = runCatching {
        ensureDirectory(normalize(path))
    }

    override suspend fun writeBytes(path: String, bytes: ByteArray): Result<Unit> = runCatching {
        val normalized = normalize(path)
        ensureParentDirectory(normalized)
        files[normalized] = bytes.copyOf()
    }

    override suspend fun appendBytes(path: String, bytes: ByteArray): Result<Unit> = runCatching {
        val normalized = normalize(path)
        ensureParentDirectory(normalized)
        val current = files[normalized] ?: ByteArray(0)
        files[normalized] = current + bytes
    }

    override suspend fun writeString(path: String, content: String): Result<Unit> {
        return writeBytes(path, content.encodeToByteArray())
    }

    override suspend fun readBytes(path: String): Result<ByteArray> = runCatching {
        files[normalize(path)]?.copyOf() ?: error("File not found: ${normalize(path)}")
    }

    override suspend fun readString(path: String): Result<String> = runCatching {
        readBytes(path).getOrThrow().decodeToString()
    }

    override suspend fun delete(path: String): Result<Unit> = runCatching {
        val normalized = normalize(path)
        files.remove(normalized)
        directories.remove(normalized)
    }

    override suspend fun deleteRecursively(path: String): Result<Unit> = runCatching {
        val normalized = normalize(path)
        files.keys.filter { it == normalized || it.startsWith("$normalized/") }
            .toList()
            .forEach(files::remove)
        directories.filter { it == normalized || it.startsWith("$normalized/") }
            .toList()
            .forEach(directories::remove)
    }

    override suspend fun exists(path: String): Boolean {
        val normalized = normalize(path)
        return normalized in files || normalized in directories
    }

    override suspend fun getFileSize(path: String): Long {
        return files[normalize(path)]?.size?.toLong() ?: 0L
    }

    override suspend fun listFiles(directory: String): List<String> {
        val normalized = normalize(directory).trimEnd('/')
        val prefix = if (normalized == "/") "/" else "$normalized/"
        val childNames = linkedSetOf<String>()
        files.keys.forEach { path ->
            if (path.startsWith(prefix)) {
                val remainder = path.removePrefix(prefix)
                remainder.substringBefore('/').takeIf { it.isNotBlank() }?.let(childNames::add)
            }
        }
        directories.forEach { path ->
            if (path != normalized && path.startsWith(prefix)) {
                val remainder = path.removePrefix(prefix)
                remainder.substringBefore('/').takeIf { it.isNotBlank() }?.let(childNames::add)
            }
        }
        return childNames.toList()
    }

    override fun getAppDataDirectory(): String = "/virtual"

    override fun resolveAbsolutePath(relativePath: String): String {
        val normalized = normalize(relativePath)
        return if (normalized.startsWith("/virtual/") || normalized == "/virtual") {
            normalized
        } else {
            "/virtual/${normalized.removePrefix("/")}"
        }
    }

    override suspend fun createDirectory(base: SaveLocation, relativePath: String): Result<Unit> {
        return createDirectory(resolvePath(base, relativePath))
    }

    override suspend fun writeBytes(base: SaveLocation, relativePath: String, bytes: ByteArray): Result<Unit> {
        return writeBytes(resolvePath(base, relativePath), bytes)
    }

    override suspend fun appendBytes(base: SaveLocation, relativePath: String, bytes: ByteArray): Result<Unit> {
        return appendBytes(resolvePath(base, relativePath), bytes)
    }

    override suspend fun writeString(base: SaveLocation, relativePath: String, content: String): Result<Unit> {
        return writeString(resolvePath(base, relativePath), content)
    }

    override suspend fun readString(base: SaveLocation, relativePath: String): Result<String> {
        return readString(resolvePath(base, relativePath))
    }

    override suspend fun exists(base: SaveLocation, relativePath: String): Boolean {
        return exists(resolvePath(base, relativePath))
    }

    override suspend fun delete(base: SaveLocation, relativePath: String): Result<Unit> {
        return delete(resolvePath(base, relativePath))
    }

    private fun resolvePath(base: SaveLocation, relativePath: String): String {
        val root = when (base) {
            is SaveLocation.Path -> base.path
            is SaveLocation.TreeUri -> base.uri
            is SaveLocation.Bookmark -> base.bookmarkData
        }
        return if (relativePath.isBlank()) root else "$root/$relativePath"
    }

    private fun ensureParentDirectory(path: String) {
        ensureDirectory(path.substringBeforeLast('/', "/"))
    }

    private fun ensureDirectory(path: String) {
        val normalized = normalize(path)
        if (normalized == "/") {
            directories.add("/")
            return
        }
        val segments = normalized.removePrefix("/").split('/').filter { it.isNotBlank() }
        var current = ""
        segments.forEach { segment ->
            current += "/$segment"
            directories.add(current)
        }
    }

    private fun normalize(path: String): String {
        val trimmed = path.trim().replace('\\', '/')
        if (trimmed.isBlank()) return "/"
        val parts = trimmed.split('/').filter { it.isNotBlank() }
        return "/" + parts.joinToString("/")
    }
}
