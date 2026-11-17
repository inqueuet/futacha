package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.HistoryArchiveFileKind
import com.valoser.futacha.shared.model.HistoryArchivePayloadStatus
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedPost
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SavedThreadArchivePayloadSupportTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun planHistoryArchivePayload_buildsSourceAndArchivePathsForSavedThread() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val repository = SavedThreadRepository(fileSystem, baseDirectory = "autosaved_threads")
        val savedThread = savedThread()
        val metadata = savedThreadMetadata(storageId = savedThread.storageId!!)

        repository.saveIndex(
            com.valoser.futacha.shared.model.SavedThreadIndex(
                threads = listOf(savedThread),
                totalSize = 38L,
                lastUpdated = 1_000L
            )
        ).getOrThrow()
        fileSystem.writeString(
            "autosaved_threads/${savedThread.storageId}/metadata.json",
            json.encodeToString(metadata)
        ).getOrThrow()
        fileSystem.writeString("autosaved_threads/${savedThread.storageId}/123.htm", "<html></html>").getOrThrow()
        fileSystem.writeBytes("autosaved_threads/${savedThread.storageId}/thumb/1s.jpg", byteArrayOf(1, 2)).getOrThrow()
        fileSystem.writeBytes("autosaved_threads/${savedThread.storageId}/src/1.jpg", byteArrayOf(1, 2, 3)).getOrThrow()
        fileSystem.writeBytes("autosaved_threads/${savedThread.storageId}/videos/1.webm", byteArrayOf(1, 2, 3, 4)).getOrThrow()

        val result = repository.planHistoryArchivePayload(
            historyEntry = historyEntry(),
            archiveId = "archive-0526",
            snapshotId = "snapshot-123"
        ).getOrThrow()

        assertEquals(HistoryArchivePayloadStatus.FULL, result.archiveEntry.payloadStatus)
        assertEquals("threads/archive-0526/snapshot-123/metadata.json", result.archiveEntry.metadataPath)
        assertEquals("threads/archive-0526/snapshot-123/123.htm", result.archiveEntry.htmlPath)
        assertTrue(result.archiveEntry.historyEntry.hasAutoSave)
        assertEquals(
            listOf(
                "img__123_payload/metadata.json",
                "img__123_payload/123.htm",
                "img__123_payload/thumb/1s.jpg",
                "img__123_payload/src/1.jpg",
                "img__123_payload/videos/1.webm"
            ),
            result.sourceFiles.map { it.sourceRelativePath }
        )
        assertEquals(
            listOf(
                HistoryArchiveFileKind.METADATA,
                HistoryArchiveFileKind.HTML,
                HistoryArchiveFileKind.THUMBNAIL,
                HistoryArchiveFileKind.IMAGE,
                HistoryArchiveFileKind.VIDEO
            ),
            result.sourceFiles.map { it.kind }
        )
        assertEquals(listOf(2L, 3L, 4L), result.sourceFiles.drop(2).map { it.sizeBytes })
    }

    @Test
    fun planHistoryArchivePayload_returnsHistoryOnlyWhenMetadataIsMissing() = runBlocking {
        val repository = SavedThreadRepository(InMemoryFileSystem(), baseDirectory = "autosaved_threads")

        val result = repository.planHistoryArchivePayload(
            historyEntry = historyEntry(),
            archiveId = "archive-0526",
            snapshotId = "snapshot-123"
        ).getOrThrow()

        assertEquals(HistoryArchivePayloadStatus.HISTORY_ONLY, result.archiveEntry.payloadStatus)
        assertEquals(emptyList(), result.sourceFiles)
        assertEquals("123", result.archiveEntry.historyEntry.threadId)
    }

    private fun savedThread(): SavedThread {
        return SavedThread(
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
    }

    private fun savedThreadMetadata(storageId: String): SavedThreadMetadata {
        return SavedThreadMetadata(
            threadId = "123",
            boardId = "img",
            boardName = "虹裏 img",
            boardUrl = "https://may.2chan.net/b/",
            title = "thread",
            storageId = storageId,
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
    }

    private fun historyEntry(): ThreadHistoryEntry {
        return ThreadHistoryEntry(
            threadId = "123",
            boardId = "img",
            title = "thread",
            titleImageUrl = "",
            boardName = "虹裏 img",
            boardUrl = "https://may.2chan.net/b/res/123.htm",
            lastVisitedEpochMillis = 10_000L,
            replyCount = 1
        )
    }
}
