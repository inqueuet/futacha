package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlin.test.Test
import kotlin.test.assertEquals

class AppStateHistoryPersistenceSupportTest {
    @Test
    fun persistAppStateHistory_writesLatestRevisionOnceAndSkipsAbsorbedRevision() = runBlocking {
        val historyPersistMutex = Mutex()
        val originalHistory = listOf(historyEntry("111"))
        val latestHistory = listOf(historyEntry("222"))
        val writes = mutableListOf<Pair<Long, List<ThreadHistoryEntry>>>()
        var persistedRevision = 0L
        var latestContinuation: AppStatePersistedHistoryContinuation? =
            AppStatePersistedHistoryContinuation(
                revision = 2L,
                history = latestHistory
            )

        suspend fun persist(revision: Long, history: List<ThreadHistoryEntry>) {
            persistAppStateHistory(
                historyPersistMutex = historyPersistMutex,
                revision = revision,
                history = history,
                writeHistoryJson = { targetRevision, targetHistory ->
                    writes += targetRevision to targetHistory
                },
                readLatestHistoryContinuation = { targetRevision ->
                    latestContinuation?.takeIf { it.revision > targetRevision }
                },
                shouldSkipRevision = { targetRevision ->
                    targetRevision <= persistedRevision
                },
                markPersistedRevision = { targetRevision ->
                    persistedRevision = maxOf(persistedRevision, targetRevision)
                }
            )
        }

        persist(revision = 1L, history = originalHistory)
        latestContinuation = null
        persist(revision = 2L, history = latestHistory)

        assertEquals(listOf(2L), writes.map { it.first })
        assertEquals(listOf("222"), writes.single().second.map { it.threadId })
        assertEquals(2L, persistedRevision)
    }

    @Test
    fun persistAppStateHistory_writesRequestedRevisionWhenNoNewerContinuationExists() = runBlocking {
        val historyPersistMutex = Mutex()
        val history = listOf(historyEntry("111"))
        val writes = mutableListOf<Long>()
        var persistedRevision = 0L

        persistAppStateHistory(
            historyPersistMutex = historyPersistMutex,
            revision = 1L,
            history = history,
            writeHistoryJson = { targetRevision, _ ->
                writes += targetRevision
            },
            readLatestHistoryContinuation = { null },
            shouldSkipRevision = { targetRevision ->
                targetRevision <= persistedRevision
            },
            markPersistedRevision = { targetRevision ->
                persistedRevision = maxOf(persistedRevision, targetRevision)
            }
        )

        assertEquals(listOf(1L), writes)
        assertEquals(1L, persistedRevision)
    }
}

private fun historyEntry(threadId: String): ThreadHistoryEntry {
    return ThreadHistoryEntry(
        threadId = threadId,
        boardId = "b",
        title = "title-$threadId",
        titleImageUrl = "thumb-$threadId",
        boardName = "board",
        boardUrl = "https://may.2chan.net/b/futaba.php",
        lastVisitedEpochMillis = 100L,
        replyCount = 1
    )
}
