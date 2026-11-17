package com.valoser.futacha

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.CompatCatalogReplyCountUpdate
import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.applyCatalogReplyCountsOneByOne
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.model.BoardSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The batched `applyCatalogReplyCounts` gives the same rows and count as the one-by-one default. */
@RunWith(AndroidJUnit4::class)
class CompatibilityReplyCountBatchInstrumentedTest {
    private lateinit var context: Context
    private val databaseNames = mutableListOf<String>()
    private val stores = mutableListOf<AndroidCompatibilityStore>()
    private val boardUrl = "https://may.2chan.net/b/"

    @Before fun setUp() { context = InstrumentationRegistry.getInstrumentation().targetContext }

    @After fun tearDown() = runBlocking {
        stores.forEach { it.closeForTest() }
        databaseNames.forEach(context::deleteDatabase)
    }

    private fun threadUrl(no: Int) = "${boardUrl}res/$no.htm"
    private fun tab(no: Int, count: Int) = CompatTab(
        compatTabKey(threadUrl(no)), threadUrl(no), threadUrl(no), compatBoardKey(boardUrl), "板", "$no", "スレ$no",
        replyCount = count, insertedAtEpochMillis = 1L, contentUpdatedAtEpochMillis = 1L
    )
    private fun entry(no: Int, count: Int) = CompatHistoryEntry(
        canonicalUrl = threadUrl(no), originalUrl = threadUrl(no), boardKey = compatBoardKey(boardUrl),
        boardName = "板", threadNo = "$no", title = "スレ$no", replyCount = count, contentUpdatedAtEpochMillis = 1L
    )
    private fun update(no: Int, count: Int) =
        CompatCatalogReplyCountUpdate(compatTabKey(threadUrl(no)), threadUrl(no), count)

    private suspend fun seededStore(): AndroidCompatibilityStore {
        val name = "compat_reply_batch_${System.nanoTime()}.db".also(databaseNames::add)
        val store = AndroidCompatibilityStore(context, databaseName = name)
        stores += store
        store.initialize()
        store.importModernBoards(listOf(BoardSummary("modern-board", "板", "", boardUrl, "")))
        listOf(1 to 5, 2 to 10, 3 to 3, 4 to 8).forEach { (no, count) -> store.openTab(tab(no, count), entry(no, count)) }
        store.upsertHistory(entry(5, 2))
        return store
    }

    @Test
    fun batchMatchesOneByOneAndNeverRollsBackOrCreatesRows() = runBlocking {
        val batched = seededStore()
        val single = seededStore()
        val updates = listOf(update(1, 9), update(2, 4), update(3, 3), update(5, 20), update(6, 7), update(1, 12))

        val batchedChanged = batched.applyCatalogReplyCounts(updates)
        val singleChanged = single.applyCatalogReplyCountsOneByOne(updates)

        assertEquals(2, batchedChanged)
        assertEquals(singleChanged, batchedChanged)
        assertEquals(single.tabs.first().sortedBy { it.key }, batched.tabs.first().sortedBy { it.key })
        assertEquals(single.history.first().sortedBy { it.canonicalUrl }, batched.history.first().sortedBy { it.canonicalUrl })
        assertEquals(
            mapOf(1 to 12, 2 to 10, 3 to 3, 4 to 8),
            batched.tabs.first().associate { it.threadNo.toInt() to it.replyCount }
        )
        assertEquals(
            mapOf(1 to 12, 2 to 10, 3 to 3, 4 to 8, 5 to 20),
            batched.history.first().associate { it.threadNo.toInt() to it.replyCount }
        )
        assertTrue(batched.tabs.first().none { it.threadNo == "6" })
    }

    @Test
    fun deletedHistoryStaysDeletedWhileTheTabGrows() = runBlocking {
        val store = seededStore()
        store.deleteHistory(threadUrl(1))
        assertEquals(1, store.applyCatalogReplyCounts(listOf(update(1, 9))))
        assertEquals(9, store.tabs.first().first { it.threadNo == "1" }.replyCount)
        assertTrue(store.history.first().none { it.canonicalUrl == threadUrl(1) })
    }
}
