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
            assertTrue(result.failures > 0, "the cut-off catalog is reported")
        } finally { store.close(); root.deleteRecursively() }
    }
}
