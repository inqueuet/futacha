package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.test.*

/** B2 of docs/shared-history-tabs-plan-2026-11-09.md: history deletions survive a restart (audit F3, F8). */
class HistoryTombstonePersistenceTest {
    private val storage = FakePlatformStateStorage()
    private val files = InMemoryFileSystem()
    private val json = Json { ignoreUnknownKeys = true }
    private fun now() = Clock.System.now().toEpochMilliseconds()
    private fun entry(no: Int, visited: Long = now() - 60_000) = ThreadHistoryEntry(
        no.toString(), "b", "題$no", "", "may", "https://may.2chan.net/b/res/$no.htm",
        lastVisitedEpochMillis = visited, replyCount = 3
    )
    private fun store() = AppStateStore(storage, AppStateHistoryFileStore(files, json, "tombstones"))
    private suspend fun <T> withStore(block: suspend (AppStateStore) -> T): T {
        val store = store()
        try { return block(store) } finally { store.close() }
    }
    /** What the start-up / mode-switch merges do: add a copy read from the other store. */
    private suspend fun AppStateStore.mergeCopy(vararg rows: ThreadHistoryEntry) =
        updateHistory { current -> current + rows.filter { row -> current.none { it.threadId == row.threadId } } }

    @Test fun aDeletedRowDoesNotComeBackFromAnOldCopyAfterARestart() = runBlocking {
        val old = entry(1)
        withStore { it.setHistory(listOf(old, entry(2))); it.removeHistoryEntry(old) }
        withStore { restarted ->
            restarted.mergeCopy(old)
            assertEquals(listOf("2"), restarted.readHistory().map { it.threadId })
        }
    }

    @Test fun deletingARowThisStoreDoesNotHoldStillKeepsItsTombstone() = runBlocking {
        val onlyInTheCopy = entry(5)
        withStore { it.removeHistoryEntries(listOf(onlyInTheCopy)) }
        withStore { restarted ->
            restarted.mergeCopy(onlyInTheCopy)
            assertTrue(restarted.readHistory().isEmpty())
        }
    }

    @Test fun aClearIsKeptAcrossARestartAsItsTimeAlone() = runBlocking {
        val rows = (1..3).map { entry(it) }
        withStore { it.setHistory(rows); it.clearHistory() }
        val kept = AppStateHistoryTombstoneFile(files, json, "read").read()
        assertTrue(kept.clearedAtMillis > 0)
        assertTrue(kept.entries.isEmpty(), "a clear is its time, not a row per entry")
        withStore { restarted ->
            restarted.mergeCopy(*rows.toTypedArray())
            assertTrue(restarted.readHistory().isEmpty())
            // A row visited after the clear is a new visit and comes in.
            restarted.mergeCopy(entry(9, visited = now() + 1_000))
            assertEquals(listOf("9"), restarted.readHistory().map { it.threadId })
        }
    }

    @Test fun aLateOpenWithATicketTakenBeforeTheDeletionIsRefused() = runBlocking {
        withStore { store ->
            val row = entry(1)
            store.setHistory(listOf(row))
            val ticket = store.historyDeletionTicket()
            store.removeHistoryEntry(row)
            store.prependOrReplaceHistoryEntry(row.copy(lastVisitedEpochMillis = now()), openedAtTicket = ticket)
            assertTrue(store.readHistory().isEmpty())
            // Opened again after the deletion: it goes in.
            store.prependOrReplaceHistoryEntry(row.copy(lastVisitedEpochMillis = now()), openedAtTicket = store.historyDeletionTicket())
            assertEquals(listOf("1"), store.readHistory().map { it.threadId })
        }
    }

    @Test fun anOpenGoesInEvenWithTheDeletionsTimeOrAClockSetBack() = runBlocking {
        val row = entry(1, visited = now())
        withStore { it.setHistory(listOf(row)); it.removeHistoryEntry(row) }
        withStore { restarted ->
            restarted.prependOrReplaceHistoryEntry(row.copy(lastVisitedEpochMillis = 1L))
            assertEquals(listOf("1"), restarted.readHistory().map { it.threadId })
        }
        // The open lifted the tombstone for good.
        withStore { again -> assertEquals(listOf("1"), again.readHistory().map { it.threadId }) }
    }

    @Test fun importsTheUserChoseGoInAfterARestart() = runBlocking {
        val archived = entry(1)
        val ftt = entry(2)
        withStore { it.setHistory(listOf(archived, ftt)); it.removeHistoryEntries(listOf(archived, ftt)) }
        withStore { restarted ->
            val ticket = restarted.beginHistoryImport()
            restarted.mergeImportedHistory(listOf(archived), ticket)
            restarted.importHistoryEntries(listOf(ftt))
            assertEquals(setOf("1", "2"), restarted.readHistory().map { it.threadId }.toSet())
        }
    }

    @Test fun theKeptTombstonesAreCappedNewestFirstAndTiesKeepTheRecentlyRead() {
        val t = now()
        val map = (1..1_100).associate { no ->
            "k$no" to HistoryTombstone(atMillis = if (no <= 1_050) t else t - 10, sequence = no.toLong(), entryLastVisitedEpochMillis = no.toLong())
        }
        val kept = selectPersistedHistoryTombstones(map, clearedAtMillis = 0, nowMillis = t)
        assertEquals(MAX_PERSISTED_HISTORY_TOMBSTONES, kept.entries.size)
        assertTrue(kept.entries.all { it.atMillis == t })
        assertEquals("k1050", kept.entries.first().key)
        assertFalse(kept.entries.any { it.key == "k1" || it.key == "k26" }, "the least recently read of the tie go")
        val old = selectPersistedHistoryTombstones(
            mapOf("old" to HistoryTombstone(t - PERSISTED_HISTORY_TOMBSTONE_RETENTION_MILLIS - 1, 1)), 0, t
        )
        assertTrue(old.entries.isEmpty())
    }

    @Test fun loadedTombstonesStartAtSequenceZeroAndFutureTimesAreClamped() {
        val t = now()
        val (map, clearedAt) = loadHistoryTombstones(
            PersistedHistoryTombstones(clearedAtMillis = t + 99_999, entries = listOf(PersistedHistoryTombstone("k", t + 99_999, 5))),
            t
        )
        assertEquals(HistoryTombstone(t, 0L, 5), map["k"])
        assertEquals(t, clearedAt)
    }

    @Test fun theTombstonesSurviveTheManifestBeingRebuilt() = runBlocking {
        val row = entry(1)
        withStore { it.setHistory(listOf(row, entry(2))); it.removeHistoryEntry(row) }
        files.deleteRecursively("private/history_store/manifest.json")
        files.deleteRecursively("private/history_store/manifest.json.backup")
        withStore { restarted ->
            restarted.mergeCopy(row)
            assertFalse(restarted.readHistory().any { it.threadId == "1" })
        }
    }

    @Test fun aBackgroundRefreshDoesNotCreateATombstonedRow() = runBlocking {
        val row = entry(1)
        withStore { it.setHistory(listOf(row)); it.removeHistoryEntry(row) }
        withStore { restarted ->
            restarted.upsertHistoryEntry(row.copy(replyCount = 9, lastVisitedEpochMillis = now() + 1_000))
            restarted.mergeHistoryEntries(listOf(row.copy(replyCount = 9)))
            assertTrue(restarted.history.first().isEmpty())
        }
    }
}
