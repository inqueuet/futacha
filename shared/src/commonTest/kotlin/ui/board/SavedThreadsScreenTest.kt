package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.SavedThreadIndex
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.buildThreadStorageId
import com.valoser.futacha.shared.util.FileSystem
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SavedThreadsScreenTest {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Test
    fun formatSize_formatsByteRanges() {
        assertEquals("999 B", formatSize(999))
        assertEquals("2 KB", formatSize(2 * 1024L))
        assertEquals("3 MB", formatSize(3 * 1024L * 1024L))
        assertEquals("1.50 GB", formatSize((1.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun formatDecimal_padsFractionDigits() {
        assertEquals("1.50", formatDecimal(1.5, 2))
        assertEquals("2.00", formatDecimal(2.0, 2))
    }

    @Test
    fun savedThreadStatusLabel_returnsExpectedLabels() {
        assertEquals("ダウンロード中", savedThreadStatusLabel(SaveStatus.DOWNLOADING))
        assertEquals("完了", savedThreadStatusLabel(SaveStatus.COMPLETED))
        assertEquals("失敗", savedThreadStatusLabel(SaveStatus.FAILED))
        assertEquals("一部", savedThreadStatusLabel(SaveStatus.PARTIAL))
    }

    @Test
    fun formatDate_returnsPaddedTimestamp() {
        val value = formatDate(0L)

        assertTrue(value.matches(Regex("""\d{4}/\d{2}/\d{2} \d{2}:\d{2}""")))
    }

    @Test
    fun resolveSavedThreadsContentState_prioritizesLoadingThenErrorThenEmptyThenData() {
        assertIs<SavedThreadsContentState.Loading>(
            resolveSavedThreadsContentState(
                isLoading = true,
                loadError = "err",
                threads = listOf(savedThread())
            )
        )
        assertIs<SavedThreadsContentState.Error>(
            resolveSavedThreadsContentState(
                isLoading = false,
                loadError = "err",
                threads = listOf(savedThread())
            )
        )
        assertIs<SavedThreadsContentState.Empty>(
            resolveSavedThreadsContentState(
                isLoading = false,
                loadError = null,
                threads = emptyList()
            )
        )
        assertIs<SavedThreadsContentState.Data>(
            resolveSavedThreadsContentState(
                isLoading = false,
                loadError = null,
                threads = listOf(savedThread())
            )
        )
    }

    @Test
    fun buildSavedThreadsSummaryText_hidesWhileLoading() {
        assertEquals(null, buildSavedThreadsSummaryText(threadCount = 1, totalSize = 10L, isLoading = true))
        assertEquals("2 件 / 2 KB", buildSavedThreadsSummaryText(threadCount = 2, totalSize = 2048L, isLoading = false))
    }

    @Test
    fun buildSavedThreadsLoadErrorMessage_mapsTimeoutSeparately() {
        val timeoutError = runCatching {
            runBlocking {
                withTimeout(1) {
                    delay(10)
                }
            }
        }.exceptionOrNull() ?: error("timeout expected")

        assertEquals(
            "読み込みがタイムアウトしました",
            buildSavedThreadsLoadErrorMessage(timeoutError)
        )
        assertEquals(
            "読み込みエラー: boom",
            buildSavedThreadsLoadErrorMessage(IllegalStateException("boom"))
        )
    }

    @Test
    fun buildSavedThreadsDeleteMessage_mapsSuccessAndFailure() {
        assertEquals("削除しました", buildSavedThreadsDeleteMessage(Result.success(Unit)))
        assertEquals(
            "削除に失敗しました: boom",
            buildSavedThreadsDeleteMessage(Result.failure(IllegalStateException("boom")))
        )
    }

    @Test
    fun resolveSavedThreadsDeleteUiOutcome_mapsSnapshotAndMessage() {
        val snapshot = SavedThreadsSnapshot(
            threads = listOf(savedThread(threadId = "1")),
            totalSize = 10L
        )
        assertEquals(
            SavedThreadsDeleteUiOutcome(
                updatedSnapshot = snapshot,
                message = "削除しました"
            ),
            resolveSavedThreadsDeleteUiOutcome(Result.success(snapshot))
        )
        assertEquals(
            SavedThreadsDeleteUiOutcome(
                updatedSnapshot = null,
                message = "削除に失敗しました: boom"
            ),
            resolveSavedThreadsDeleteUiOutcome(Result.failure(IllegalStateException("boom")))
        )
    }

    @Test
    fun loadSavedThreadsSnapshot_readsThreadsAndTotalSizeFromRepository() = runBlocking {
        val repository = SavedThreadRepository(InMemoryFileSystem(), baseDirectory = "saved_threads")
        val first = savedThread(threadId = "1", totalSize = 10L, savedAt = 100L)
        val second = savedThread(threadId = "2", totalSize = 20L, savedAt = 200L)
        repository.addThreadToIndex(first).getOrThrow()
        repository.addThreadToIndex(second).getOrThrow()

        val snapshot = loadSavedThreadsSnapshot(repository).getOrThrow()

        assertEquals(listOf(second, first), snapshot.threads)
        assertEquals(30L, snapshot.totalSize)
    }

    @Test
    fun loadSavedThreadsSnapshot_propagatesRepositoryLoadFailure() {
        runBlocking {
        val fileSystem = InMemoryFileSystem()
        val repository = SavedThreadRepository(fileSystem, baseDirectory = "saved_threads")
        fileSystem.writeString("saved_threads/index.json", "{invalid").getOrThrow()

        val result = loadSavedThreadsSnapshot(repository)

        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
        }
    }

    @Test
    fun loadSavedThreadsSnapshot_recoversFromBackupIndex() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val repository = SavedThreadRepository(fileSystem, baseDirectory = "saved_threads")
        val thread = savedThread(
            threadId = "42",
            boardId = "img",
            storageId = buildThreadStorageId("img", "42"),
            totalSize = 64L,
            savedAt = 420L
        )
        fileSystem.writeString("saved_threads/index.json", "{broken").getOrThrow()
        fileSystem.writeString(
            "saved_threads/index.json.backup",
            json.encodeToString(
                SavedThreadIndex(
                    threads = listOf(thread),
                    totalSize = 64L,
                    lastUpdated = 1L
                )
            )
        ).getOrThrow()

        val snapshot = loadSavedThreadsSnapshot(repository).getOrThrow()

        assertEquals(listOf(thread), snapshot.threads)
        assertEquals(64L, snapshot.totalSize)
    }

    @Test
    fun deleteSavedThreadAndReload_returnsUpdatedSnapshot() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val repository = SavedThreadRepository(fileSystem, baseDirectory = "saved_threads")
        val target = savedThread(
            threadId = "1",
            totalSize = 10L,
            savedAt = 100L,
            storageId = buildThreadStorageId("b", "1")
        )
        val other = savedThread(
            threadId = "2",
            totalSize = 20L,
            savedAt = 200L,
            storageId = buildThreadStorageId("b", "2")
        )
        repository.addThreadToIndex(target).getOrThrow()
        repository.addThreadToIndex(other).getOrThrow()
        fileSystem.writeString("saved_threads/${target.storageId}/metadata.json", "{}").getOrThrow()
        fileSystem.writeString("saved_threads/${other.storageId}/metadata.json", "{}").getOrThrow()

        val snapshot = deleteSavedThreadAndReload(repository, target).getOrThrow()

        assertEquals(listOf(other), snapshot.threads)
        assertEquals(20L, snapshot.totalSize)
        assertFalse(repository.threadExists(target.threadId, target.boardId))
        assertFalse(fileSystem.exists("saved_threads/${target.storageId}"))
        assertTrue(fileSystem.exists("saved_threads/${other.storageId}"))
    }

    @Test
    fun deleteSavedThreadAndReload_deletesOnlyMatchingBoardWhenThreadIdsOverlap() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val repository = SavedThreadRepository(fileSystem, baseDirectory = "saved_threads")
        val target = savedThread(
            threadId = "100",
            boardId = "img",
            totalSize = 10L,
            savedAt = 100L,
            storageId = buildThreadStorageId("img", "100")
        )
        val otherBoard = savedThread(
            threadId = "100",
            boardId = "dat",
            totalSize = 20L,
            savedAt = 200L,
            storageId = buildThreadStorageId("dat", "100")
        )
        repository.addThreadToIndex(target).getOrThrow()
        repository.addThreadToIndex(otherBoard).getOrThrow()
        fileSystem.writeString("saved_threads/${target.storageId}/metadata.json", "{}").getOrThrow()
        fileSystem.writeString("saved_threads/${otherBoard.storageId}/metadata.json", "{}").getOrThrow()

        val snapshot = deleteSavedThreadAndReload(repository, target).getOrThrow()

        assertEquals(listOf(otherBoard), snapshot.threads)
        assertEquals(20L, snapshot.totalSize)
        assertFalse(fileSystem.exists("saved_threads/${target.storageId}"))
        assertTrue(fileSystem.exists("saved_threads/${otherBoard.storageId}"))
    }

    @Test
    fun deleteSavedThreadAndReload_returnsFailureWhenDeleteFails() = runBlocking {
        val fileSystem = DeleteFailingFileSystem(InMemoryFileSystem())
        val repository = SavedThreadRepository(fileSystem, baseDirectory = "saved_threads")
        val target = savedThread(threadId = "1", totalSize = 10L, savedAt = 100L)
        repository.addThreadToIndex(target).getOrThrow()

        val result = deleteSavedThreadAndReload(repository, target)

        assertTrue(result.isFailure)
        assertTrue(
            buildSavedThreadsDeleteMessage(Result.failure<Unit>(result.exceptionOrNull() ?: error("expected failure")))
                .contains("cannot delete")
        )
    }

    private fun savedThread(
        threadId: String = "1",
        boardId: String = "b",
        totalSize: Long = 1L,
        savedAt: Long = 0L,
        storageId: String? = null
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
}

private class DeleteFailingFileSystem(
    private val delegate: InMemoryFileSystem
) : FileSystem by delegate {
    override suspend fun deleteRecursively(path: String): Result<Unit> {
        return Result.failure(IllegalStateException("cannot delete"))
    }
}
