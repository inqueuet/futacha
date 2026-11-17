package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.util.FileSystem
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppStateHistoryFileStoreWriteFailureTest {
    private val json = Json { ignoreUnknownKeys = true }

    /** Simulates a non-atomic provider: the failing write leaves a truncated file behind. */
    private class TornWriteFileSystem(
        private val delegate: InMemoryFileSystem = InMemoryFileSystem()
    ) : FileSystem by delegate {
        var tearNextEntryWrite = false

        override suspend fun writeString(path: String, content: String): Result<Unit> {
            val tear = when {
                path.startsWith("private/history_store/entries/") && tearNextEntryWrite -> {
                    tearNextEntryWrite = false
                    true
                }
                else -> false
            }
            if (tear) {
                delegate.writeString(path, content.take(content.length / 2)).getOrThrow()
                return Result.failure(IllegalStateException("write interrupted"))
            }
            return delegate.writeString(path, content)
        }
    }

    private fun entry(threadId: String, replyCount: Int = 1) = ThreadHistoryEntry(
        threadId = threadId,
        boardId = "b",
        title = "title-$threadId",
        titleImageUrl = "",
        boardName = "board",
        boardUrl = "https://may.2chan.net/b/futaba.php",
        lastVisitedEpochMillis = 100L,
        replyCount = replyCount
    )

    @Test
    fun failedEntryWriteIsRepairedWhenTheSameEntryIsPersistedAgain() = runBlocking {
        val files = TornWriteFileSystem()
        val store = AppStateHistoryFileStore(files, json, "test")
        val original = entry("111")
        store.persistHistorySnapshot(listOf(original))

        files.tearNextEntryWrite = true
        val failure = runCatching { store.persistHistorySnapshot(listOf(original.copy(replyCount = 9))) }
        assertTrue(failure.isFailure)

        // Rolling back to the previously persisted value must rewrite the torn file.
        store.persistHistorySnapshot(listOf(original))
        val restarted = AppStateHistoryFileStore(files, json, "restart")
        assertEquals(listOf(original), restarted.readHistorySnapshot { null })
    }
}
