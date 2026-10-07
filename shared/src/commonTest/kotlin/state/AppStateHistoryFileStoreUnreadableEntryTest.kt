package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.util.FileSystem
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppStateHistoryFileStoreUnreadableEntryTest {
    private val json = Json { ignoreUnknownKeys = true }

    /** An entry file that exists but cannot be read for a while (a provider hiccup). */
    private class FlakyReadFileSystem(
        val delegate: InMemoryFileSystem = InMemoryFileSystem()
    ) : FileSystem by delegate {
        var unreadablePath: String? = null

        override suspend fun readString(path: String): Result<String> =
            if (path == unreadablePath) Result.failure(IllegalStateException("provider unavailable"))
            else delegate.readString(path)
    }

    private fun entry(threadId: String) = ThreadHistoryEntry(
        threadId = threadId,
        boardId = "b",
        title = "title-$threadId",
        titleImageUrl = "",
        boardName = "board",
        boardUrl = "https://may.2chan.net/b/futaba.php",
        lastVisitedEpochMillis = 100L,
        replyCount = 1
    )

    private suspend fun pathOfEntry(files: FlakyReadFileSystem, threadId: String): String {
        val dir = "private/history_store/entries"
        return files.delegate.listFiles(dir)
            .map { "$dir/$it" }
            .first { files.delegate.readString(it).getOrThrow().contains("\"title-$threadId\"") }
    }

    @Test
    fun anEntryThatCouldNotBeReadIsKeptOnDiskAndInTheManifestAcrossLaterWrites() = runBlocking {
        val files = FlakyReadFileSystem()
        val first = AppStateHistoryFileStore(files, json, "first")
        val a = entry("a")
        val b = entry("b")
        val c = entry("c")
        first.persistHistorySnapshot(listOf(a, b, c))
        val bPath = pathOfEntry(files, "b")

        files.unreadablePath = bPath
        val second = AppStateHistoryFileStore(files, json, "second")
        assertEquals(listOf(a, c), second.readHistorySnapshot { null })

        // Further writes (here a new entry and a reordering) must not delete b's file.
        val d = entry("d")
        second.persistHistorySnapshot(listOf(d, a, c))
        second.persistHistorySnapshot(listOf(a, c))
        assertTrue(files.delegate.exists(bPath))

        // Once readable again, the entry is back after a restart.
        files.unreadablePath = null
        val third = AppStateHistoryFileStore(files, json, "third")
        assertEquals(setOf("a", "c", "b"), third.readHistorySnapshot { null }.map { it.threadId }.toSet())
    }

    @Test
    fun aMissingEntryFileIsStillDroppedAndAClearStillDeletesEverything() = runBlocking {
        val files = FlakyReadFileSystem()
        val first = AppStateHistoryFileStore(files, json, "first")
        first.persistHistorySnapshot(listOf(entry("a"), entry("b")))
        val bPath = pathOfEntry(files, "b")
        files.delegate.delete(bPath).getOrThrow()

        val second = AppStateHistoryFileStore(files, json, "second")
        assertEquals(listOf("a"), second.readHistorySnapshot { null }.map { it.threadId })
        second.persistHistorySnapshot(listOf(entry("a")))
        val third = AppStateHistoryFileStore(files, json, "third")
        assertEquals(listOf("a"), third.readHistorySnapshot { null }.map { it.threadId })

        second.persistHistorySnapshot(emptyList())
        assertTrue(files.delegate.listFiles("private/history_store/entries").isEmpty())
    }
}
