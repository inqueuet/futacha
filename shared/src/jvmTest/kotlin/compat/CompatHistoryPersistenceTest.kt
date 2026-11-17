package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.JvmFileSystem
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class CompatHistoryPersistenceTest {
    @Test fun visitsSurviveMetadataRefreshQuickRevisitAndReopen() = runBlocking {
        val directory = Files.createTempDirectory("history-order").toFile()
        val fs = JvmFileSystem(directory)
        var store = DesktopCompatibilityStore(fs)
        try {
            store.initialize()
            store.upsertBoard(CompatBoard("may-b", "虹裏", "https://may.2chan.net/b/", "https://may.2chan.net/b/", 0))
            fun entry(id: String, time: Long) = CompatHistoryEntry(
                "https://may.2chan.net/b/res/$id.htm", "https://may.2chan.net/b/res/$id.htm",
                "may-b", "虹裏", id, id, contentUpdatedAtEpochMillis = time)
            val a = entry("1", 100)
            val b = entry("2", 200)
            store.recordHistoryVisit(a)
            store.recordHistoryVisit(b)
            store.upsertHistory(a.copy(contentUpdatedAtEpochMillis = 300, replyCount = 42))
            assertEquals(listOf("2", "1"), store.history.first().map { it.threadNo })
            store.recordHistoryVisit(a.copy(lastVisitedEpochMillis = 201))
            store.upsertHistory(b.copy(contentUpdatedAtEpochMillis = 400))
            store.close()
            store = DesktopCompatibilityStore(fs)
            store.initialize()
            assertEquals(listOf("1", "2"), store.history.first().map { it.threadNo })
            assertEquals(listOf(201L, 200L), store.history.first().map { it.lastVisitedEpochMillis })
            store.deleteHistory(a.canonicalUrl)
            store.upsertHistory(a.copy(contentUpdatedAtEpochMillis = 500))
            assertEquals(listOf("2"), store.history.first().map { it.threadNo })
        } finally { store.close(); directory.deleteRecursively() }
    }

    @Test fun modernImportKeepsUpdateTimeAndRepeatsWithoutWriting() = runBlocking {
        val directory = Files.createTempDirectory("history-import").toFile()
        val fs = JvmFileSystem(directory)
        val store = DesktopCompatibilityStore(fs)
        try {
            store.initialize()
            val key = compatBoardKey("https://may.2chan.net/b/")
            store.upsertBoard(CompatBoard(key, "虹裏", "https://may.2chan.net/b/", "https://may.2chan.net/b/", 0))
            fun modern(id: Int, visited: Long) = com.valoser.futacha.shared.model.ThreadHistoryEntry(
                threadId = id.toString(), boardId = "may", title = "スレ$id", titleImageUrl = "",
                boardName = "虹裏", boardUrl = "https://may.2chan.net/b/", lastVisitedEpochMillis = visited, replyCount = 1)
            val url = "https://may.2chan.net/b/res/1.htm"
            store.recordHistoryVisit(CompatHistoryEntry(url, url, key, "虹裏", "1", "スレ1",
                contentUpdatedAtEpochMillis = 150, lastVisitedEpochMillis = 100))
            assertEquals(1, store.importModernHistory(listOf(modern(1, 500))))
            val imported = store.history.first().single()
            assertEquals(500L, imported.lastVisitedEpochMillis)
            assertEquals(150L, imported.contentUpdatedAtEpochMillis)

            // Entries the 200-item limit trims are not counted (or written) again.
            val large = (0 until 1_000).map { index -> modern(10_000 + index, 1_000L + index) }
            assertTrue(store.importModernHistory(large) > 0)
            assertEquals(0, store.importModernHistory(large))
            assertTrue(store.history.first().size <= 200)
        } finally { store.close(); directory.deleteRecursively() }
    }
}
