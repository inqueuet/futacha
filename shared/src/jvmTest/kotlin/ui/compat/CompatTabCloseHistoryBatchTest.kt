package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.compat.toCompatHistoryEntry
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Audit 2026-11-11 P2-M1: closing many tabs with the link on deletes their history in one pass and one write. */
class CompatTabCloseHistoryBatchTest {
    private fun entry(n: Int) = ThreadHistoryEntry(
        threadId = "$n", boardId = "b", title = "スレ$n", titleImageUrl = "", boardName = "b",
        boardUrl = "https://may.2chan.net/b/res/$n.htm", lastVisitedEpochMillis = 1L, replyCount = 3
    )

    @Test
    fun manyClosedTabsAreDeletedInOneWriteAndToTheEnd() = runBlocking {
        val root = Files.createTempDirectory("tab-close-history").toFile()
        try {
            val desktop = DesktopCompatibilityStore(JvmFileSystem(root))
            desktop.initialize()
            var writes = 0
            val compat = object : CompatibilityStore by desktop {
                override suspend fun deleteHistory(canonicalUrl: String) { writes++; desktop.deleteHistory(canonicalUrl) }
                override suspend fun deleteHistories(canonicalUrls: Collection<String>) { writes++; desktop.deleteHistories(canonicalUrls) }
            }
            val store = AppStateStore(FakePlatformStateStorage())
            val rows = (1..60).map(::entry)
            store.setHistory(rows)
            rows.forEach { compat.recordHistoryVisit(it.toCompatHistoryEntry()!!) }
            val closing = rows.take(50).map { it.toCompatHistoryEntry()!! }
            deleteCompatHistoryRowsAtOnce(store, compat, null, null, closing)
            assertEquals((51..60).map { "$it" }, store.history.first().map { it.threadId }.sortedBy { it.toInt() })
            assertTrue(writes <= 1, "one write for all the closed tabs, was $writes")
        } finally {
            root.deleteRecursively()
        }
    }
}
