package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogFetchSettings
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompatBackgroundRefresherBatchingTest {
    private class CountingStore(private val inner: CompatibilityStore) : CompatibilityStore by inner {
        var batchCalls = 0
        var batchedUpdates = 0
        override suspend fun applyCatalogReplyCounts(updates: List<CompatCatalogReplyCountUpdate>): Int {
            batchCalls++
            batchedUpdates += updates.size
            return inner.applyCatalogReplyCounts(updates)
        }
    }

    private suspend fun seed(store: CompatibilityStore, url: String, numbers: List<String>) {
        store.importModernBoards(listOf(BoardSummary("modern-board", "板", "", url, "")))
        numbers.forEach { no ->
            val threadUrl = "${url}res/$no.htm"
            store.openTab(CompatTab(compatTabKey(threadUrl), threadUrl, threadUrl, compatBoardKey(url), "板",
                no, "スレ$no", insertedAtEpochMillis = 1L, contentUpdatedAtEpochMillis = no.toLong()))
        }
    }

    @Test fun aBoardsReplyCountsAreAppliedInOneBatch() = runBlocking {
        val root = Files.createTempDirectory("futacha-bg-batch").toFile()
        val desktop = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            desktop.initialize()
            val url = "https://may.2chan.net/b/"
            val numbers = (100 until 105).map(Int::toString)
            seed(desktop, url, numbers)
            val store = CountingStore(desktop)
            val repository = object : BoardRepository by FakeBoardRepository() {
                override suspend fun getCatalog(board: String, mode: CatalogMode): List<CatalogItem> =
                    numbers.map { no -> CatalogItem(no, "${url}res/$no.htm", "スレ$no", null, null, replyCount = 7) }
                override suspend fun getCatalogWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings): List<CatalogItem> =
                    getCatalog(board, mode)
            }
            val result = refreshCompatTabsInBackground(store, repository, nowEpochMillis = 10_000_000_000L, checkExistence = false)
            assertEquals(5, result.updatedTabs)
            assertEquals(1, store.batchCalls)
            assertEquals(5, store.batchedUpdates)
            assertTrue(desktop.tabs.first().all { it.replyCount == 7 })
            // A second run has nothing to change and writes nothing.
            refreshCompatTabsInBackground(store, repository, nowEpochMillis = 10_000_000_000L, checkExistence = false)
            assertEquals(1, store.batchCalls)
        } finally { desktop.close(); root.deleteRecursively() }
    }

    @Test fun existenceProbesRunInGroupsAndTheCursorStillFollowsTabOrder() = runBlocking {
        val root = Files.createTempDirectory("futacha-bg-parallel").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            val url = "https://may.2chan.net/b/"
            val numbers = (100 until 109).map(Int::toString)
            seed(store, url, numbers)
            val inFlight = AtomicInteger()
            var maxInFlight = 0
            val probed = mutableListOf<String>()
            val repository = object : BoardRepository by FakeBoardRepository() {
                override suspend fun probeThreadGone(threadUrl: String): Boolean {
                    val now = inFlight.incrementAndGet()
                    synchronized(probed) { maxInFlight = maxOf(maxInFlight, now); probed += threadUrl }
                    delay(150)
                    inFlight.decrementAndGet()
                    return false
                }
            }
            val started = System.nanoTime()
            val result = refreshCompatTabsInBackground(store, repository, nowEpochMillis = 10_000_000_000L,
                maxTabs = 6, checkUpdates = false)
            val elapsedMillis = (System.nanoTime() - started) / 1_000_000
            assertEquals(6, result.skippedTabs)
            assertTrue(maxInFlight in 2..COMPAT_EXISTENCE_PROBE_PARALLELISM, "in flight: $maxInFlight")
            assertTrue(elapsedMillis < 6 * 150, "probes overlapped: $elapsedMillis ms")
            // The same first six tabs (in key order) as a serial run, and the cursor is the sixth.
            val byKey = store.tabs.first().sortedBy(CompatTab::key).take(6)
            assertEquals(byKey.map { it.originalUrl }.sorted(), probed.sorted())
            assertEquals(byKey.last().key, store.loadPreference(COMPAT_BACKGROUND_EXISTENCE_CURSOR_KEY))
        } finally { store.close(); root.deleteRecursively() }
    }
    private suspend fun seedBoards(store: CompatibilityStore, urls: List<String>) {
        urls.forEachIndexed { index, url ->
            store.upsertBoard(CompatBoard(compatBoardKey(url), "板$index", url, url, index))
            val threadUrl = "${url}res/${100 + index}.htm"
            store.openTab(CompatTab(compatTabKey(threadUrl), threadUrl, threadUrl, compatBoardKey(url), "板$index",
                "${100 + index}", "スレ", insertedAtEpochMillis = 1L, contentUpdatedAtEpochMillis = 1L))
        }
    }

    @Test fun anUnsetCatalogThreadSizeUsesTheCatalogScreenLayout() = runBlocking {
        val root = Files.createTempDirectory("futacha-bg-layout").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            seedBoards(store, listOf("https://may.2chan.net/b/"))
            val requested = mutableListOf<CatalogFetchSettings>()
            val repository = object : BoardRepository by FakeBoardRepository() {
                override suspend fun getCatalog(board: String, mode: CatalogMode): List<CatalogItem> =
                    error("the default repository layout must not be used")
                override suspend fun getCatalogWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings): List<CatalogItem> {
                    requested += settings
                    return emptyList()
                }
            }
            refreshCompatTabsInBackground(store, repository, nowEpochMillis = 10_000_000_000L, checkExistence = false)
            assertEquals(listOf(com.valoser.futacha.shared.ui.compat.compatCatalogFetchSettings(300)), requested)
        } finally { store.close(); root.deleteRecursively() }
    }

    @Test fun updatePhaseContinuesAfterTheLastBoardOfTheShortRun() = runBlocking {
        val root = Files.createTempDirectory("futacha-bg-board-rotation").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            seedBoards(store, listOf("https://may.2chan.net/b/", "https://img.2chan.net/b/", "https://dat.2chan.net/b/"))
            val order = store.boards.first().map { it.originalUrl }
            val requested = mutableListOf<String>()
            val repository = object : BoardRepository by FakeBoardRepository() {
                override suspend fun getCatalogWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings): List<CatalogItem> {
                    requested += board
                    delay(5_000L) // every catalog uses up the whole capped update phase
                    return emptyList()
                }
            }
            repeat(4) {
                refreshCompatTabsInBackground(store, repository, nowEpochMillis = 10_000_000_000L,
                    checkExistence = false, updateBudgetMillis = 300L)
            }
            // One board per run, then around the list again: the last boards are not starved.
            assertEquals(listOf(order[0], order[1], order[2], order[0]), requested)
            assertEquals(compatBoardKey(order[0]), store.loadPreference(COMPAT_BACKGROUND_UPDATE_BOARD_CURSOR_KEY))
        } finally { store.close(); root.deleteRecursively() }
    }

    @Test fun aRunThatReachesEveryBoardLeavesTheTopOrderAndClearsTheCursor() = runBlocking {
        val root = Files.createTempDirectory("futacha-bg-board-full").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            seedBoards(store, listOf("https://may.2chan.net/b/", "https://img.2chan.net/b/", "https://dat.2chan.net/b/"))
            val order = store.boards.first().map { it.originalUrl }
            store.savePreference(COMPAT_BACKGROUND_UPDATE_BOARD_CURSOR_KEY, compatBoardKey(order[0]))
            val requested = mutableListOf<String>()
            val repository = object : BoardRepository by FakeBoardRepository() {
                override suspend fun getCatalogWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings): List<CatalogItem> {
                    requested += board
                    return emptyList()
                }
            }
            refreshCompatTabsInBackground(store, repository, nowEpochMillis = 10_000_000_000L, checkExistence = false)
            assertEquals(listOf(order[1], order[2], order[0]), requested)
            assertTrue(store.loadPreference(COMPAT_BACKGROUND_UPDATE_BOARD_CURSOR_KEY).isNullOrBlank())
            requested.clear()
            refreshCompatTabsInBackground(store, repository, nowEpochMillis = 10_000_000_000L, checkExistence = false)
            assertEquals(order, requested)
        } finally { store.close(); root.deleteRecursively() }
    }

    @Test fun boardRotationContinuesAfterTheCursorAndWraps() {
        fun board(key: String) = CompatBoard(key, key, "https://$key/", "https://$key/", 0)
        val boards = listOf("a", "b", "c", "d").map(::board)
        assertEquals(listOf("a", "b", "c", "d"), compatRotatedBackgroundBoards(boards, null).map(CompatBoard::key))
        assertEquals(listOf("c", "d", "a", "b"), compatRotatedBackgroundBoards(boards, "b").map(CompatBoard::key))
        assertEquals(listOf("a", "b", "c", "d"), compatRotatedBackgroundBoards(boards, "d").map(CompatBoard::key))
        assertEquals(listOf("a", "b", "c", "d"), compatRotatedBackgroundBoards(boards, "gone").map(CompatBoard::key))
        assertTrue(compatRotatedBackgroundBoards(emptyList(), "a").isEmpty())
    }

    @Test fun anAllTabsRunCutShortByTheBudgetSavesWhereItStopped() = runBlocking {
        val root = Files.createTempDirectory("futacha-bg-all-cursor").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            val url = "https://may.2chan.net/b/"
            seed(store, url, (100 until 109).map(Int::toString))
            val probed = mutableListOf<String>()
            var probeDelay = 250L
            val repository = object : BoardRepository by FakeBoardRepository() {
                override suspend fun probeThreadGone(threadUrl: String): Boolean {
                    synchronized(probed) { probed += threadUrl }
                    delay(probeDelay)
                    return false
                }
            }
            val sorted = store.tabs.first().sortedBy(CompatTab::key)
            // Every tab is requested (maxTabs >= tab count), but the budget ends after one group of three.
            refreshCompatTabsInBackground(store, repository, nowEpochMillis = 10_000_000_000L,
                maxTabs = 100, checkUpdates = false, existenceBudgetMillis = 400L)
            assertEquals(sorted[2].key, store.loadPreference(COMPAT_BACKGROUND_EXISTENCE_CURSOR_KEY))

            // The next run continues with the fourth tab, and a run that reaches the end saves nothing more.
            probed.clear()
            probeDelay = 0L
            refreshCompatTabsInBackground(store, repository, nowEpochMillis = 10_000_000_000L,
                maxTabs = 100, checkUpdates = false)
            assertEquals(sorted.subList(3, 6).map { it.originalUrl }.toSet(), probed.take(3).toSet())
            assertEquals(9, probed.size)
            assertEquals(sorted[2].key, store.loadPreference(COMPAT_BACKGROUND_EXISTENCE_CURSOR_KEY))
        } finally { store.close(); root.deleteRecursively() }
    }
}
