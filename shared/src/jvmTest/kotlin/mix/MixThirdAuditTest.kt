package com.valoser.futacha.shared.mix

import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.*
import com.valoser.futacha.shared.ui.board.fetchMixCatalogPageForMode
import com.valoser.futacha.shared.ui.board.MixModePolicy
import com.valoser.futacha.shared.ui.board.mixEditCandidates
import com.valoser.futacha.shared.ui.compat.buildCompatMixTab
import com.valoser.futacha.shared.ui.compat.compatPreferenceStorageKey
import com.valoser.futacha.shared.ui.futaber.futaberFetchCatalogPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy
import kotlin.test.*

/** Regression checks for the third MIX audit. */
class MixThirdAuditTest {
    private val url = "https://may.2chan.net/b/"
    private val board = BoardSummary("may", "may", "", url, "")
    private fun store(prefs: Map<String, String>): CompatibilityStore = Proxy.newProxyInstance(
        CompatibilityStore::class.java.classLoader, arrayOf(CompatibilityStore::class.java)
    ) { _, method, _ ->
        when (method.name) {
            "getPreferences" -> MutableStateFlow(prefs)
            else -> error("Unexpected call: ${method.name}")
        }
    } as CompatibilityStore

    @Test fun futaberNormalAndMixRequestTheSameLayout() = runBlocking {
        val calls = mutableListOf<String>()
        val source = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getCatalogFetchSettings() = CatalogFetchSettings(titleLines = 8)
            override suspend fun getCatalogPage(board: String, mode: CatalogMode): CatalogPageContent {
                calls += "plain"
                return CatalogPageContent(emptyList())
            }
            override suspend fun getCatalogPageWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings): CatalogPageContent {
                calls += "title=${settings.titleLines}"
                return CatalogPageContent(emptyList())
            }
        }
        futaberFetchCatalogPage(source, url, CatalogMode.Catalog, emptyList())
        fetchMixCatalogPageForMode(source, store(emptyMap()), url, CatalogMode.Catalog, MixModePolicy.FUTABER)
        assertEquals(listOf("title=256", "title=256"), calls)
    }

    @Test fun ftutterMixKeepsPlainFetchDespiteOtherModesSettings() = runBlocking {
        val calls = mutableListOf<String>()
        val requestedUrls = mutableListOf<String>()
        val source = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getCatalogPage(board: String, mode: CatalogMode): CatalogPageContent {
                calls += "plain"
                requestedUrls += board
                return CatalogPageContent(emptyList())
            }
            override suspend fun getCatalogPageWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings): CatalogPageContent {
                calls += "settings"
                return CatalogPageContent(emptyList())
            }
        }
        source.getCatalogPage(url, CatalogMode.Catalog)
        fetchMixCatalogPageForMode(source, store(mapOf(compatPreferenceStorageKey("catalog", "catalogThreadSize") to "200")), url.replace("https://", "http://"), CatalogMode.Catalog, MixModePolicy.FTUTTER)
        assertEquals(listOf("plain", "plain"), calls)
        assertEquals(listOf(url, url), requestedUrls)
    }

    @Test fun interpolationDoesNotExtrapolateOutsideKnownThreadNumbers() = runBlocking {
        val base = 1_730_000_000_000L
        val textBoard = board.copy(url = "https://img.2chan.net/9/")
        fun item(id: String, time: Long?) = CatalogItem(id, "${textBoard.url}res/$id.htm", "text", time?.let {
            "https://img.2chan.net/9/thumb/${it}s.jpg"
        }, null, replyCount = 1)
        val page = CatalogPageContent(listOf(item("100", base), item("1", null), item("200", base + 2_000)))
        val fetcher = MixCatalogFetcher(fetchPage = { _, _ -> page }, loadPause = {}, nowMillis = { base + 10_000 })
        val definition = MixDefinition("mix", "mix", listOf(textBoard.url), createdAt = 1)
        val rows = fetcher.fetch(ResolvedMix(definition, listOf(textBoard), emptyMap()), 1).entries
        val oldestNumber = rows.single { it.item.id == "1" }
        assertNull(oldestNumber.createdAtMillis)
        assertFalse(oldestNumber.createdAtEstimated)
        val weighted = mixCreatedTimesByThreadNumber(listOf(item("200", base + 2_000), item("110", null), item("100", base)), base + 10_000)
        assertEquals(base + 200 to true, weighted[1])
    }

    @Test fun toshiakiSoOpenPreservesCountsAndReadBaseline() {
        val so = MixEntry(board, CatalogItem("1", "${url}res/1.htm", "t", null, null, replyCount = 4242),
            MixEntryKey(url, "1"), null, false, 0, 1, MixCountKind.SOUDANE)
        val compatBoard = CompatBoard("may", "may", url, url, 0)
        val fresh = assertNotNull(buildCompatMixTab(so, compatBoard, null, 1))
        assertEquals(0, fresh.replyCount)
        assertEquals(0, fresh.checkedReplyCount)
        val existing = fresh.copy(replyCount = 12, checkedReplyCount = 7)
        val reopened = assertNotNull(buildCompatMixTab(so, compatBoard, existing, 2))
        assertEquals(12, reopened.replyCount)
        assertEquals(7, reopened.checkedReplyCount)
        val historyOnly = assertNotNull(buildCompatMixTab(so, compatBoard, null, 3,
            existing.toVisitedHistoryEntry(2).copy(replyCount = 42)))
        assertEquals(42, historyOnly.replyCount)
        assertEquals(0, historyOnly.checkedReplyCount)
        assertEquals(42, buildCompatMixTab(so, compatBoard, existing, 3,
            existing.toVisitedHistoryEntry(2).copy(replyCount = 42))?.replyCount)
        val replies = so.copy(countKind = MixCountKind.REPLIES, item = so.item.copy(replyCount = 20))
        assertEquals(20, buildCompatMixTab(replies, compatBoard, existing, 4)?.checkedReplyCount)
    }

    @Test fun staleMixIdClearsOnlyAfterDefinitionsLoad() {
        val nav = selectFutachaMix(FutachaNavigationState(), "removed")
        assertEquals(nav, reconcileFutachaMixSelection(nav, null, mixEnabled = true))
        val reconciled = reconcileFutachaMixSelection(nav, emptyList())
        assertNull(reconciled.selectedMixId)
        assertIs<FutachaDestination.BoardManagement>(resolveFutachaDestination(reconciled, listOf(board), emptyList()))
        val inThread = nav.copy(selectedBoardId = board.id, selectedThreadId = "1")
        assertEquals("1", reconcileFutachaMixSelection(inThread, emptyList()).selectedThreadId)
    }

    @Test fun missingMembersRemainEditableAtTheTenBoardLimit() {
        val missing = (1..10).map { "https://may.2chan.net/board$it/" }
        val definition = MixDefinition("mix", "edit", missing, createdAt = 1)
        val registered = board.copy(id = "img", name = "replacement", url = "https://img.2chan.net/b/")
        val candidates = mixEditCandidates(definition, listOf(registered))
        assertEquals(11, candidates.size)
        assertEquals(missing, candidates.take(10).map { it.first })
        assertTrue(candidates.first().second.name.contains("未登録"))
        assertEquals(registered, candidates.last().second)
    }
    @Test fun soPersistenceKeepsHistoryCountWhenTheUiHasNotLoadedHistoryYet() = runBlocking {
        val root = java.nio.file.Files.createTempDirectory("mix-so-visit").toFile()
        val store = DesktopCompatibilityStore(com.valoser.futacha.shared.util.JvmFileSystem(root))
        try {
            store.initialize()
            val b = CompatBoard("may", "may", url, url, 0)
            store.upsertBoard(b)
            val threadUrl = "${url}res/1.htm"
            store.upsertHistory(CompatHistoryEntry(threadUrl, threadUrl, b.key, b.name, "1", "known", replyCount = 42,
                contentUpdatedAtEpochMillis = 1, lastVisitedEpochMillis = 1))
            val so = MixEntry(board, CatalogItem("1", threadUrl, "t", null, null, replyCount = 4242),
                MixEntryKey(url, "1"), null, false, 0, 1, MixCountKind.SOUDANE)
            val opened = assertNotNull(com.valoser.futacha.shared.ui.compat.persistCompatMixTab(store, so, b, 100))
            assertEquals(42, opened.replyCount)
            assertEquals(0, opened.checkedReplyCount)
            val history = store.history.first().single()
            assertEquals(42, history.replyCount)
            assertEquals(100, history.lastVisitedEpochMillis)
        } finally { store.close(); root.deleteRecursively() }
    }
}
