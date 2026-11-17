package com.valoser.futacha

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.state.createAppStateStore
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.createFileSystem
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit item 29: a successful background refresh stamps
 * lastConfirmedAliveEpochMillis, so each refreshed history row is rewritten
 * (with fsync) even when nothing visible changed. This measures the writes and
 * their time for one Worker-sized run (20 threads) over a 500-entry history.
 */
class HistoryRefreshWriteCostInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private class CountingFileSystem(private val real: FileSystem) : FileSystem by real {
        val writes = AtomicInteger()
        val nanos = AtomicLong()
        override suspend fun writeString(path: String, content: String): Result<Unit> {
            val started = SystemClock.elapsedRealtimeNanos()
            return real.writeString(path, content).also {
                if ("history_store" in path) { writes.incrementAndGet(); nanos.addAndGet(SystemClock.elapsedRealtimeNanos() - started) }
            }
        }
        fun reset() { writes.set(0); nanos.set(0) }
    }

    @Test
    fun workerSizedRefreshOfUnchangedThreadsWritesOnlyTheRefreshedRows(): Unit = runBlocking {
        val fs = CountingFileSystem(createFileSystem(context))
        val store = createAppStateStore(context, fs)
        val saved = store.history.first()
        val board = BoardSummary("b", "二次元裏", "", "https://may.2chan.net/b/", "")
        val history = (1..500).map { index ->
            ThreadHistoryEntry(threadId = "${900_000 + index}", boardId = board.id, title = "スレ$index",
                titleImageUrl = "", boardName = board.name, boardUrl = board.url,
                lastVisitedEpochMillis = 1_000_000L + index, replyCount = 10)
        }
        val repository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getThread(board: String, threadId: String) = ThreadPage(threadId, "二次元裏", null, null,
                (0 until 10).map { Post(id = "$threadId$it", author = null, subject = if (it == 0) "スレ" else null,
                    timestamp = "09/24 12:00", messageHtml = "本文$it", imageUrl = null, thumbnailUrl = null) })
        }
        try {
            store.setHistory(history)
            val refresher = HistoryRefresher(store, repository, Dispatchers.IO)
            val runs = (1..3).map {
                fs.reset()
                val started = SystemClock.elapsedRealtime()
                refresher.refresh(boardsSnapshot = listOf(board), historySnapshot = store.history.first(), maxThreadsPerRun = 20)
                Triple(fs.writes.get(), fs.nanos.get() / 1_000_000, SystemClock.elapsedRealtime() - started)
            }
            runs.forEachIndexed { index, (writes, writeMillis, totalMillis) ->
                Log.i("HistoryWriteCost", "run=${index + 1} entries=500 refreshed=20 historyWrites=$writes writeMillis=$writeMillis refreshMillis=$totalMillis")
            }
            // The rows written are bounded by the refreshed rows, never the whole history.
            runs.forEach { (writes) -> assertTrue("wrote $writes history files", writes in 1..21) }
            assertEquals(500, store.history.first().size)
        } finally {
            store.setHistory(saved)
        }
    }
}
