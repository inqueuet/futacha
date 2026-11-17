package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.compat.toCompatHistoryEntry
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * 2026-10-20の監査L44：としあき(仮)で履歴を1件消す時、AppStateの履歴（最大2万件）を1行ずつ共有の履歴へ変換しながら、
 * 呼び出し側（画面のスレッド）で探していた。探すのは呼び出し側の外で、最初に一致した行を返すことは変わらない。
 */
class L44FindModernHistoryEntryDispatcherTest {
    private fun entry(n: Int, title: String = "スレ$n") = ThreadHistoryEntry(
        threadId = "$n", boardId = "b", title = title, titleImageUrl = "", boardName = "虹裏",
        boardUrl = "https://may.2chan.net/b/res/$n.htm", lastVisitedEpochMillis = n.toLong(), replyCount = 3
    )

    /** The history, whose rows may only be read away from [caller]. */
    private class CallerForbiddenHistory(
        private val rows: List<ThreadHistoryEntry>,
        private val caller: Thread
    ) : AbstractList<ThreadHistoryEntry>() {
        override val size: Int get() = rows.size
        override fun get(index: Int): ThreadHistoryEntry {
            assertNotSame(caller, Thread.currentThread(), "The history must not be searched on the caller UI thread")
            return rows[index]
        }
    }

    @Test
    fun theHistoryIsSearchedOffTheCallerThreadAndTheFirstMatchStillWins() = runBlocking {
        // 20,001 rows: the row looked for near the end, and a second row of the same thread after it.
        val rows = ArrayList<ThreadHistoryEntry>(20_001)
        for (n in 1..19_999) rows += entry(1_000_000 + n)
        val first = entry(42, title = "先の行")
        val duplicate = entry(42, title = "後の行")
        rows += first
        rows += duplicate
        val target = requireNotNull(first.toCompatHistoryEntry()).canonicalUrl
        assertEquals(target, requireNotNull(duplicate.toCompatHistoryEntry()).canonicalUrl)
        Executors.newSingleThreadExecutor { task -> Thread(task, "L44-ui") }.asCoroutineDispatcher().use { ui ->
            withContext(ui) {
                val caller = Thread.currentThread()
                val history = CallerForbiddenHistory(rows, caller)
                val found = findModernHistoryEntryFor(history, target)
                assertSame(caller, Thread.currentThread(), "The caller must resume on its UI dispatcher")
                assertSame(first, found, "The first row of the thread is the one deleted, as before")
                assertNull(findModernHistoryEntryFor(history, "https://may.2chan.net/b/res/7.htm"))
                assertSame(caller, Thread.currentThread())
            }
        }
    }
}
