package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.HistoryArchiveManifest
import com.valoser.futacha.shared.model.HistoryArchivePayloadStatus
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedPost
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedThreadIndex
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryArchiveExportSupportTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun exportHistoryArchive_writesManifestAndCopiedPayloadFiles() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val sourceRepository = seedSavedThread(fileSystem)

        val result = exportHistoryArchive(
            fileSystem = fileSystem,
            sourceRepositories = listOf(sourceRepository),
            request = HistoryArchiveExportRequest(
                archiveId = "archive-0526",
                historyEntries = listOf(historyEntry("123")),
                exportedAtEpochMillis = 1_779_753_600_000L,
                appVersion = "1.2.3"
            )
        ).getOrThrow()

        assertEquals("history_archives/archive-0526", result.archiveDirectory)
        assertEquals(5, result.copiedFileCount)
        assertEquals(0, result.historyOnlyCount)
        assertEquals(0, result.partialPayloadCount)
        val manifestPayload = fileSystem.readString("history_archives/archive-0526/manifest.json").getOrThrow()
        val manifest = json.decodeFromString(HistoryArchiveManifest.serializer(), manifestPayload)
        assertEquals(result.manifest, manifest)
        assertEquals("archive-0526", manifest.archiveId)
        assertEquals("1.2.3", manifest.appVersion)
        assertEquals(1, manifest.entryCount)
        assertEquals(HistoryArchivePayloadStatus.FULL, manifest.entries.single().payloadStatus)
        assertTrue(manifest.entries.single().payloadFiles.any { it.relativePath.endsWith("/metadata.json") })
        assertEquals(
            "<html>thread</html>",
            fileSystem.readString("history_archives/archive-0526/${manifest.entries.single().htmlPath}").getOrThrow()
        )
        assertEquals(
            byteArrayOf(1, 2, 3).toList(),
            fileSystem.readBytes(
                "history_archives/archive-0526/${manifest.entries.single().payloadFiles.first { it.relativePath.endsWith("/src/1.jpg") }.relativePath}"
            ).getOrThrow().toList()
        )
    }

    @Test
    fun exportHistoryArchive_exportsOnlySelectedHistoryEntries() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val sourceRepository = seedSavedThread(fileSystem)

        val result = exportHistoryArchive(
            fileSystem = fileSystem,
            sourceRepositories = listOf(sourceRepository),
            request = HistoryArchiveExportRequest(
                archiveId = "archive-selected",
                historyEntries = listOf(historyEntry("456")),
                exportedAtEpochMillis = 1_779_753_600_000L
            )
        ).getOrThrow()

        assertEquals(1, result.manifest.entryCount)
        assertEquals("456", result.manifest.entries.single().historyEntry.threadId)
        assertEquals(HistoryArchivePayloadStatus.HISTORY_ONLY, result.manifest.entries.single().payloadStatus)
        assertEquals(0, result.copiedFileCount)
    }

    private suspend fun seedSavedThread(fileSystem: InMemoryFileSystem): SavedThreadRepository {
        val repository = SavedThreadRepository(fileSystem, baseDirectory = "autosaved_threads")
        val savedThread = SavedThread(
            threadId = "123",
            boardId = "img",
            boardName = "虹裏 img",
            title = "thread",
            storageId = "img__123_payload",
            thumbnailPath = "thumb/1s.jpg",
            savedAt = 10_000L,
            postCount = 1,
            imageCount = 1,
            videoCount = 1,
            totalSize = 38L,
            status = SaveStatus.COMPLETED
        )
        repository.saveIndex(
            SavedThreadIndex(
                threads = listOf(savedThread),
                totalSize = 38L,
                lastUpdated = 1_000L
            )
        ).getOrThrow()
        val metadata = SavedThreadMetadata(
            threadId = "123",
            boardId = "img",
            boardName = "虹裏 img",
            boardUrl = "https://may.2chan.net/b/",
            title = "thread",
            storageId = savedThread.storageId,
            savedAt = 10_000L,
            expiresAtLabel = null,
            posts = listOf(
                SavedPost(
                    id = "123",
                    order = 1,
                    author = null,
                    subject = null,
                    timestamp = "now",
                    messageHtml = "body",
                    originalImageUrl = "https://example.com/src/1.jpg",
                    localImagePath = "src/1.jpg",
                    originalVideoUrl = "https://example.com/src/1.webm",
                    localVideoPath = "videos/1.webm",
                    originalThumbnailUrl = "https://example.com/thumb/1s.jpg",
                    localThumbnailPath = "thumb/1s.jpg"
                )
            ),
            totalSize = 38L,
            rawHtmlPath = "123.htm"
        )
        fileSystem.writeString(
            "autosaved_threads/${savedThread.storageId}/metadata.json",
            json.encodeToString(metadata)
        ).getOrThrow()
        fileSystem.writeString("autosaved_threads/${savedThread.storageId}/123.htm", "<html>thread</html>").getOrThrow()
        fileSystem.writeBytes("autosaved_threads/${savedThread.storageId}/thumb/1s.jpg", byteArrayOf(1, 2)).getOrThrow()
        fileSystem.writeBytes("autosaved_threads/${savedThread.storageId}/src/1.jpg", byteArrayOf(1, 2, 3)).getOrThrow()
        fileSystem.writeBytes("autosaved_threads/${savedThread.storageId}/videos/1.webm", byteArrayOf(1, 2, 3, 4)).getOrThrow()
        return repository
    }

    private fun historyEntry(threadId: String): ThreadHistoryEntry {
        return ThreadHistoryEntry(
            threadId = threadId,
            boardId = "img",
            title = "thread $threadId",
            titleImageUrl = "",
            boardName = "虹裏 img",
            boardUrl = "https://may.2chan.net/b/res/$threadId.htm",
            lastVisitedEpochMillis = 10_000L,
            replyCount = 1
        )
    }
}
