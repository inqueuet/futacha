package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.util.FileSystem
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * L135: opening a thread already in the history only reorders it; the backup of the history's manifest is then
 * left as it is (it was written in full on every reorder), and is written again when the threads in it change.
 */
class AppStateHistoryBackupWriteTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val backup = "private/history_store/manifest.json.backup"

    private class Counting(val delegate: InMemoryFileSystem = InMemoryFileSystem()) : FileSystem by delegate {
        val writes = mutableMapOf<String, Int>()
        override suspend fun writeString(path: String, content: String): Result<Unit> {
            writes[path] = (writes[path] ?: 0) + 1
            return delegate.writeString(path, content)
        }
    }

    private fun entry(id: String) = ThreadHistoryEntry(id, "b", "title-$id", "", "board",
        "https://may.2chan.net/b/res/$id.htm", lastVisitedEpochMillis = id.toLong(), replyCount = 1)

    @Test fun aReorderLeavesTheBackupAndANewThreadWritesIt(): Unit = runBlocking {
        val files = Counting()
        val store = AppStateHistoryFileStore(files, json, "backup")
        store.persistHistorySnapshot(listOf(entry("1"), entry("2")))
        val afterFirst = files.writes[backup] ?: 0

        store.persistHistorySnapshot(listOf(entry("2"), entry("1")))
        assertEquals(afterFirst, files.writes[backup] ?: 0, "A reorder writes no backup")

        store.persistHistorySnapshot(listOf(entry("3"), entry("2"), entry("1")))
        assertEquals(afterFirst + 1, files.writes[backup] ?: 0, "A new thread writes the backup")

        // A later start still reads the last order (from the manifest itself).
        assertEquals(listOf("3", "2", "1"),
            AppStateHistoryFileStore(files, json, "restart").readHistorySnapshot { "[]" }.map { it.threadId })
    }
}
