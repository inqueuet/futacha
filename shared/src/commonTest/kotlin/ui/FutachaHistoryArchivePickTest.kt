package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.model.HistoryArchiveEntry
import com.valoser.futacha.shared.model.HistoryArchiveManifest
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.HISTORY_ARCHIVE_DIRECTORY
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Re-audit M35: the archive offered for import is picked one manifest at a time (they were all held in memory together).
 * The pick is the same as before: the most rows not in the history yet, then the newest.
 */
class FutachaHistoryArchivePickTest {
    private fun row(threadId: String) = ThreadHistoryEntry(
        threadId = threadId, boardId = "img", title = "スレ$threadId", titleImageUrl = "", boardName = "img",
        boardUrl = "https://may.2chan.net/img/res/$threadId.htm", lastVisitedEpochMillis = 1L, replyCount = 3
    )

    private suspend fun InMemoryFileSystem.archive(name: String, exportedAt: Long, threads: List<String>) {
        val manifest = HistoryArchiveManifest(
            archiveId = name, exportedAtEpochMillis = exportedAt, entryCount = threads.size,
            entries = threads.map { HistoryArchiveEntry(snapshotId = "$name-$it", historyEntry = row(it)) }
        )
        writeString("$HISTORY_ARCHIVE_DIRECTORY/$name/manifest.json", Json.encodeToString(HistoryArchiveManifest.serializer(), manifest))
            .getOrThrow()
    }

    @Test
    fun theArchiveWithTheMostNewRowsIsPickedAndTheNewerOnATie() = runBlocking<Unit> {
        val fileSystem = InMemoryFileSystem()
        val store = AppStateStore(FakePlatformStateStorage())
        store.setHistory(listOf(row("1"), row("2")))
        fileSystem.archive("a", exportedAt = 10L, threads = listOf("1", "2", "3"))      // 1 new
        fileSystem.archive("b", exportedAt = 20L, threads = listOf("3", "4"))           // 2 new
        fileSystem.archive("c", exportedAt = 30L, threads = listOf("1", "5", "6"))      // 2 new, newer
        assertEquals("$HISTORY_ARCHIVE_DIRECTORY/c", findNextFutachaHistoryArchiveDirectory(store, fileSystem))
        fileSystem.archive("d", exportedAt = 5L, threads = listOf("7", "8", "9"))       // 3 new
        assertEquals("$HISTORY_ARCHIVE_DIRECTORY/d", findNextFutachaHistoryArchiveDirectory(store, fileSystem))
    }
}
