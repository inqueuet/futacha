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
}
