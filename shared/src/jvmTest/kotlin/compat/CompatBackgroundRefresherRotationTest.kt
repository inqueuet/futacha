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
import kotlin.test.*

class CompatBackgroundRefresherRotationTest {
    private fun tab(key: String) = CompatTab(key, "u$key", "u$key", "b", "板", key, "スレ",
        insertedAtEpochMillis = 1L, contentUpdatedAtEpochMillis = 1L)

    @Test fun watchCatalogTimeoutContinuesAtTheNextBoardAndKeepsDiagnostics() = runBlocking {
        val root = Files.createTempDirectory("futacha-watch-rotation").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            val first = "https://may.2chan.net/b/"
            val second = "https://img.2chan.net/b/"
            store.importModernBoards(listOf(BoardSummary("may", "may", "", first, ""), BoardSummary("img", "img", "", second, "")))
            CompatWatcherRepository(store).saveRules(listOf(CompatWatchRule("猫")))
            val ordered = store.boards.first().sortedBy { it.key }
            val calls = mutableListOf<String>()
            var slow = true
            val repository = object : BoardRepository by FakeBoardRepository() {
                override suspend fun getCatalogWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings): List<CatalogItem> {
                    calls += board
                    if (slow) delay(60_000)
                    return listOf(CatalogItem("333", board + "res/333.htm", "猫のスレ", null, null, replyCount = 1))
                }
                override suspend fun probeThreadGone(threadUrl: String) = false
            }
            refreshCompatTabsInBackground(store, repository, checkUpdates = false, checkExistence = false,
                checkWatchWords = true, budgetMillis = 600)
            assertEquals(ordered.first().originalUrl, calls.first())
            assertNotNull(decodeWatchRunDiagnostics(store.preferences.first()[WATCH_RUN_CRAWL_KEY]))
            calls.clear(); slow = false
            val next = refreshCompatTabsInBackground(store, repository, checkUpdates = false, checkExistence = false,
                checkWatchWords = true, budgetMillis = 3000)
            assertEquals(ordered[1].originalUrl, calls.first())
            assertTrue(next.newWatchMatches.isNotEmpty())
            assertTrue(store.history.first().isEmpty())
        } finally { store.close(); root.deleteRecursively() }
    }

    @Test fun rotationContinuesAfterTheCursorAndWraps() {
        val tabs = listOf("c", "a", "e", "b", "d").map(::tab)
        assertEquals(listOf("a", "b"), compatRotatedBackgroundTabs(tabs, null, 2).map(CompatTab::key))
        assertEquals(listOf("c", "d"), compatRotatedBackgroundTabs(tabs, "b", 2).map(CompatTab::key))
        assertEquals(listOf("e", "a"), compatRotatedBackgroundTabs(tabs, "d", 2).map(CompatTab::key))
        // A closed cursor tab, or one after every key, still continues in order.
        assertEquals(listOf("d", "e"), compatRotatedBackgroundTabs(tabs, "cc", 2).map(CompatTab::key))
        assertEquals(listOf("a", "b"), compatRotatedBackgroundTabs(tabs, "z", 2).map(CompatTab::key))
        assertTrue(compatRotatedBackgroundTabs(emptyList(), "a", 2).isEmpty())
    }

    @Test fun successiveRunsProbeEveryTabAndUpdateCountsOfAllTabs() = runBlocking {
        val root = Files.createTempDirectory("futacha-bg-rotation").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            val url = "https://may.2chan.net/b/"
            store.importModernBoards(listOf(BoardSummary("modern-board", "板", "", url, "")))
            val numbers = (100 until 105).map(Int::toString)
            numbers.forEach { no ->
                val threadUrl = "${url}res/$no.htm"
                store.openTab(CompatTab(compatTabKey(threadUrl), threadUrl, threadUrl, compatBoardKey(url), "板",
                    no, "スレ$no", insertedAtEpochMillis = 1L, contentUpdatedAtEpochMillis = no.toLong()))
            }
            val probed = mutableListOf<String>()
            val repository = object : BoardRepository by FakeBoardRepository() {
                override suspend fun getCatalog(board: String, mode: CatalogMode): List<CatalogItem> =
                    numbers.map { no -> CatalogItem(no, "${url}res/$no.htm", "スレ$no", null, null, replyCount = 7) }
                override suspend fun getCatalogWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings): List<CatalogItem> =
                    getCatalog(board, mode)
                override suspend fun probeThreadGone(threadUrl: String): Boolean {
                    probed += threadUrl
                    return false
                }
            }

            val first = refreshCompatTabsInBackground(store, repository, nowEpochMillis = 10_000_000_000L, maxTabs = 2)
            // One catalog covers every tab of the board, not only the probed ones.
            assertEquals(5, first.updatedTabs)
            assertTrue(store.tabs.first().all { it.replyCount == 7 })
            repeat(2) {
                refreshCompatTabsInBackground(store, repository, nowEpochMillis = 10_000_000_000L,
                    maxTabs = 2, checkUpdates = false)
            }
            assertEquals(6, probed.size)
            assertEquals(numbers.map { "${url}res/$it.htm" }.toSet(), probed.toSet())
            assertEquals(5, probed.take(5).toSet().size, "a full cycle probes each tab once")
        } finally { store.close(); root.deleteRecursively() }
    }

    /**
     * Re-audit M16: a slow watch crawl no longer uses up the whole watch phase; the liveness probes of the watched threads
     * still run, so a thread that fell is marked so. The time running out is not a failure.
     */
    @Test fun aSlowWatchCrawlStillLeavesTimeToFindAFallenWatchedThread() = runBlocking<Unit> {
        val root = Files.createTempDirectory("futacha-watch-probe-share").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            val url = "https://may.2chan.net/b/"
            store.importModernBoards(listOf(BoardSummary("may", "may", "", url, "")))
            CompatWatcherRepository(store).saveRules(listOf(CompatWatchRule("猫")))
            var slow = false
            var gone = false
            val repository = object : BoardRepository by FakeBoardRepository() {
                override suspend fun getCatalogWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings): List<CatalogItem> {
                    if (slow) delay(60_000)
                    return listOf(CatalogItem("333", board + "res/333.htm", "猫のスレ", null, null, replyCount = 1))
                }
                override suspend fun probeThreadGone(threadUrl: String) = gone
            }
            val now = 10_000_000_000L
            refreshCompatTabsInBackground(store, repository, nowEpochMillis = now, checkUpdates = false, checkExistence = false,
                checkWatchWords = true, budgetMillis = 3_000)
            assertTrue(CompatWatcherRepository(store).load(now).single().active)
            // The thread fell; the crawl is slow enough to use up any time it is given.
            slow = true; gone = true
            val result = refreshCompatTabsInBackground(store, repository, nowEpochMillis = now + 3_600_000L, checkUpdates = false,
                checkExistence = false, checkWatchWords = true, budgetMillis = 3_000)
            assertFalse(CompatWatcherRepository(store).load(now + 3_600_000L).single().active)
            assertEquals(0, result.failures)
            assertTrue(result.unfinished > 0)
        } finally { store.close(); root.deleteRecursively() }
    }

    @Test fun cappedUpdatePhaseLeavesTheWatchWordsTimeInAShortRun() = runBlocking {
        val root = Files.createTempDirectory("futacha-bg-phase-budget").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            val url = "https://may.2chan.net/b/"
            store.importModernBoards(listOf(BoardSummary("modern-board", "板", "", url, "")))
            val threadUrl = "${url}res/100.htm"
            store.openTab(CompatTab(compatTabKey(threadUrl), threadUrl, threadUrl, compatBoardKey(url), "板",
                "100", "スレ", insertedAtEpochMillis = 1L, contentUpdatedAtEpochMillis = 1L))
            CompatWatcherRepository(store).saveRules(listOf(CompatWatchRule("猫")))
            val repository = object : BoardRepository by FakeBoardRepository() {
                override suspend fun getCatalog(board: String, mode: CatalogMode): List<CatalogItem> {
                    // The update catalog is slow enough to use up the whole run.
                    if (mode == CatalogMode.Catalog) delay(60_000L)
                    return listOf(CatalogItem("200", "${url}res/200.htm", "猫のスレ", null, null, replyCount = 1))
                }
                override suspend fun getCatalogWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings): List<CatalogItem> =
                    getCatalog(board, mode)
            }

            // iOS BGAppRefresh: the update phase is capped so the watch words still run (H4-1).
            val result = refreshCompatTabsInBackground(store, repository, nowEpochMillis = 10_000_000_000L,
                checkExistence = false, checkWatchWords = true, budgetMillis = 5_000L, updateBudgetMillis = 200L)
            assertEquals(listOf("200"), result.newWatchMatches.map { it.history.threadNo })
            // A catalog cut off by the time is left for the next run, not a failure (re-audit M16).
            assertTrue(result.unfinished > 0, "the cut-off catalog is reported")
            assertEquals(0, result.failures)
        } finally { store.close(); root.deleteRecursively() }
    }

    /**
     * Audit M23: a caller that cuts the run off with an outer timeout passes [compatRefreshBudgetWithin], so a slow watch
     * phase (it runs first) leaves time for the tabs' reply counts. Without a budget the watch used up the timeout.
     */
    @Test fun aBudgetWithinTheTimeoutLeavesTimeForTheReplyCountsAfterASlowWatch() = runBlocking<Unit> {
        val root = Files.createTempDirectory("futacha-bg-budget").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            val url = "https://may.2chan.net/b/"
            store.importModernBoards(listOf(BoardSummary("may", "may", "", url, "")))
            CompatWatcherRepository(store).saveRules(listOf(CompatWatchRule("猫")))
            val threadUrl = "${url}res/100.htm"
            val tabKey = compatTabKey(threadUrl)
            store.openTab(CompatTab(tabKey, threadUrl, threadUrl, compatBoardKey(url), "may", "100", "スレ",
                insertedAtEpochMillis = 1L, contentUpdatedAtEpochMillis = 1L))
            val repository = object : BoardRepository by FakeBoardRepository() {
                override suspend fun getCatalogWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings): List<CatalogItem> {
                    // The watch phase fetches the new/old orders; they hang.
                    if (mode == CatalogMode.New || mode == CatalogMode.Old) delay(60_000)
                    return listOf(CatalogItem("100", threadUrl, "スレ", null, null, replyCount = 9))
                }
                override suspend fun probeThreadGone(threadUrl: String) = false
            }
            val timeout = 4_000L
            // Without a budget the watch phase uses up the whole timeout.
            assertNull(kotlinx.coroutines.withTimeoutOrNull(timeout) {
                refreshCompatTabsInBackground(store, repository, checkUpdates = true, checkExistence = false, checkWatchWords = true)
            })
            assertNotEquals(9, store.tabs.first().single().replyCount)
            val result = kotlinx.coroutines.withTimeout(timeout) {
                refreshCompatTabsInBackground(store, repository, checkUpdates = true, checkExistence = false, checkWatchWords = true,
                    budgetMillis = compatRefreshBudgetWithin(timeout))
            }
            assertEquals(9, store.tabs.first().single().replyCount)
            assertTrue(result.updatedTabs >= 1)
            assertEquals(3_600L, compatRefreshBudgetWithin(4_000L))
            assertEquals(330_000L, compatRefreshBudgetWithin(6 * 60_000L))
        } finally { store.close(); root.deleteRecursively() }
    }

    /** Audit M24: after a mode switch (the gate refuses) nothing is written, not even the watch diagnostics. */
    @Test fun aClosedGateWritesNoWatchDiagnostics() = runBlocking<Unit> {
        val root = Files.createTempDirectory("futacha-bg-gate").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            store.importModernBoards(listOf(BoardSummary("may", "may", "", "https://may.2chan.net/b/", "")))
            CompatWatcherRepository(store).saveRules(listOf(CompatWatchRule("猫")))
            val repository = object : BoardRepository by FakeBoardRepository() {
                override suspend fun getCatalogWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings): List<CatalogItem> = emptyList()
                override suspend fun probeThreadGone(threadUrl: String) = false
            }
            refreshCompatTabsInBackground(store, repository, checkUpdates = false, checkExistence = false, checkWatchWords = true,
                commitGate = { false })
            assertNull(store.preferences.first()[WATCH_RUN_CRAWL_KEY])
            assertNull(store.preferences.first()["compat.background.watch_board_cursor"])
            // With the gate open it is written.
            refreshCompatTabsInBackground(store, repository, checkUpdates = false, checkExistence = false, checkWatchWords = true)
            assertNotNull(decodeWatchRunDiagnostics(store.preferences.first()[WATCH_RUN_CRAWL_KEY]))
        } finally { store.close(); root.deleteRecursively() }
    }
}
