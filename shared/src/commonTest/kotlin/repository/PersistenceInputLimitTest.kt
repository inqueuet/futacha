package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.SavedPost
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.model.HistoryArchiveEntry
import com.valoser.futacha.shared.model.HistoryArchiveFile
import com.valoser.futacha.shared.model.HistoryArchiveFileKind
import com.valoser.futacha.shared.model.HistoryArchiveManifest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class PersistenceInputLimitTest {
    @Test
    fun savedThreadMetadata_rejectsExcessivePostCount() {
        val post = SavedPost(
            id = "1",
            order = 0,
            author = null,
            subject = null,
            timestamp = "",
            messageHtml = "",
            originalImageUrl = null,
            localImagePath = null,
            originalVideoUrl = null,
            localVideoPath = null,
            originalThumbnailUrl = null,
            localThumbnailPath = null
        )
        val metadata = SavedThreadMetadata(
            threadId = "1",
            boardId = "b",
            boardName = "board",
            boardUrl = "https://example.invalid/b/",
            title = "thread",
            savedAt = 1L,
            expiresAtLabel = null,
            posts = List(MAX_SAVED_THREAD_METADATA_POSTS + 1) { post },
            totalSize = 0L
        )

        assertFailsWith<IllegalArgumentException> {
            requireSavedThreadMetadataWithinLimits(metadata)
        }
    }

    @Test
    fun savedThreadIndex_rejectsOversizedPayloadBeforeDecode() {
        runBlocking {
            val fileSystem = InMemoryFileSystem()
            val baseDirectory = "saved_threads_limit"
            fileSystem.writeString(
                "$baseDirectory/index.json",
                "x".repeat(4 * 1024 * 1024 + 1)
            ).getOrThrow()
            val repository = SavedThreadRepository(fileSystem, baseDirectory = baseDirectory)

            assertFailsWith<IllegalStateException> {
                repository.loadIndex()
            }
        }
    }

    @Test
    fun historyArchive_rejectsOversizedManifestBeforeDecode() {
        runBlocking {
            val fileSystem = InMemoryFileSystem()
            val archiveDirectory = "history_archives/oversized"
            fileSystem.writeString(
                "$archiveDirectory/manifest.json",
                "x".repeat(4 * 1024 * 1024 + 1)
            ).getOrThrow()

            val result = importHistoryArchive(
                fileSystem = fileSystem,
                destinationRepository = SavedThreadRepository(
                    fileSystem,
                    baseDirectory = IMPORTED_HISTORY_DIRECTORY
                ),
                request = HistoryArchiveImportRequest(archiveDirectory)
            )

            check(result.isFailure)
            check(result.exceptionOrNull()?.message?.contains("manifest is too large") == true)
        }
    }

    @Test
    fun historyArchiveExport_rejectsTooManyEntriesBeforeCreatingOutput() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val result = exportHistoryArchive(
            fileSystem = fileSystem,
            sourceRepositories = emptyList(),
            request = HistoryArchiveExportRequest(
                archiveId = "too-many",
                historyEntries = List(MAX_HISTORY_ARCHIVE_ENTRIES + 1) { index ->
                    com.valoser.futacha.shared.model.ThreadHistoryEntry(
                        threadId = index.toString(),
                        boardId = "b",
                        title = "thread",
                        titleImageUrl = "",
                        boardName = "board",
                        boardUrl = "https://example.invalid/b/",
                        replyCount = 0,
                        lastVisitedEpochMillis = index.toLong()
                    )
                },
                exportedAtEpochMillis = 1L
            )
        )

        check(result.isFailure)
        assertFalse(fileSystem.exists("history_archives/too-many"))
    }

    @Test
    fun historyArchiveSizeAddition_rejectsLongOverflow() {
        assertFailsWith<IllegalArgumentException> {
            safeHistoryArchiveExportSizeAdd(Long.MAX_VALUE, 1L)
        }
    }

    @Test
    fun historyArchiveImport_rejectsInconsistentDeclaredPayloadTotal() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val archiveDirectory = "history_archives/inconsistent"
        val manifest = HistoryArchiveManifest(
            archiveId = "inconsistent",
            exportedAtEpochMillis = 1L,
            entryCount = 1,
            totalPayloadBytes = 1L,
            entries = listOf(
                HistoryArchiveEntry(
                    snapshotId = "snapshot",
                    historyEntry = com.valoser.futacha.shared.model.ThreadHistoryEntry(
                        threadId = "1",
                        boardId = "b",
                        title = "thread",
                        titleImageUrl = "",
                        boardName = "board",
                        boardUrl = "https://example.invalid/b/",
                        replyCount = 0,
                        lastVisitedEpochMillis = 1L
                    ),
                    payloadFiles = listOf(
                        HistoryArchiveFile(
                            relativePath = "entries/snapshot/file.bin",
                            sizeBytes = 2L,
                            kind = HistoryArchiveFileKind.OTHER
                        )
                    )
                )
            )
        )
        fileSystem.writeString(
            "$archiveDirectory/manifest.json",
            kotlinx.serialization.json.Json.encodeToString(HistoryArchiveManifest.serializer(), manifest)
        ).getOrThrow()

        val result = importHistoryArchive(
            fileSystem = fileSystem,
            destinationRepository = SavedThreadRepository(fileSystem, IMPORTED_HISTORY_DIRECTORY),
            request = HistoryArchiveImportRequest(archiveDirectory)
        )

        check(result.isFailure)
        check(result.exceptionOrNull()?.message?.contains("size is inconsistent") == true)
    }
}
