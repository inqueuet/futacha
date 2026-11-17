@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.Espresso
import coil3.ImageLoader
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.BaseInMemoryPlatformStateStorage
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** Calls the real mode entry API used by browser, single watch-alert and watch-to-phone routing. No network. */
class FtutterExternalNavigationInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val a = BoardSummary("m62-a", "外部A", "test", "https://may.2chan.net/b/", "")
    private val b = a.copy(id = "m62-b", name = "外部B", url = "https://img.2chan.net/b/")
    private val existingUrl = "${a.url}res/99.htm"
    private val stateStore = AppStateStore(BaseInMemoryPlatformStateStorage())
    private var boards by mutableStateOf(listOf(a, b))
    private var threadLink by mutableStateOf<String?>(null)
    private var boardLink by mutableStateOf<String?>(null)
    private var preferences by mutableStateOf(mapOf(FtutterKeys.DELETE_KEY to "auditm62", FtutterKeys.TABS to encodeFtutterList(listOf(existingUrl))))
    private val consumedThreads = CopyOnWriteArrayList<String>()
    private val consumedBoards = CopyOnWriteArrayList<String>()
    private val calls = CopyOnWriteArrayList<Pair<String, String>>()
    private lateinit var loader: ImageLoader
    private val repository = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getThreadContent(board: String, threadId: String): ThreadPageContent {
            calls += board to threadId
            return ThreadPageContent(ThreadPage(threadId, null, null, null, listOf(Post(threadId,
                author = null, subject = null, timestamp = "", messageHtml = "m62-body-$threadId", imageUrl = null, thumbnailUrl = null))))
        }
        override suspend fun getCatalogPage(board: String, mode: CatalogMode): CatalogPageContent = CatalogPageContent(emptyList())
    }
    @Before fun prepare() {
        loader = ImageLoader.Builder(rule.activity).build()
        runBlocking {
            stateStore.setBoards(boards)
            stateStore.setHistory(listOf(ThreadHistoryEntry("99", a.id, "existing", "", a.name, a.url, 1, 1)))
        }
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle()
        if (::loader.isInitialized) loader.shutdown()
        stateStore.close()
    }
    private fun open(initial: String? = null) {
        threadLink = initial
        rule.setContent {
            // Match RuntimeHost: do not mount FtutterApp with a fabricated empty history.
            // Its initial tab pruning intentionally removes tabs absent from a loaded history.
            val loadedHistory by stateStore.observedHistory.collectAsState<List<ThreadHistoryEntry>, List<ThreadHistoryEntry>?>(null)
            val history = loadedHistory ?: return@setContent
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                FtutterApp(boards = boards, history = history, repository = repository, stateStore = stateStore,
                    preferences = preferences, ngRules = emptyList(), mediaServices = null, autoSavedThreadRepository = null,
                    appVersion = "audit", initialThreadDeepLink = threadLink,
                    onThreadDeepLinkConsumed = { value ->
                        consumedThreads += value; android.util.Log.i("AuditM62", "thread consumed=$value calls=$calls")
                        if (threadLink == value) threadLink = null
                    }, initialBoardDeepLink = boardLink,
                    onBoardDeepLinkConsumed = { value -> consumedBoards += value; if (boardLink == value) boardLink = null },
                    onWatchAlertSettingChangeRequested = null, externalNotice = null, onExternalNoticeShown = {},
                    onArchiveReportEnqueued = null, onArchiveReportEnabledChanged = {}, aiInbox = null,
                    mediaHost = { content -> content() }, onRefreshHistory = {},
                    savePreference = { key, value -> preferences = preferences + (key to value) },
                    editPreference = { key, edit -> edit(preferences[key])?.let { preferences = preferences + (key to it) } })
            }
        }
    }
    private fun bodyIsDisplayed(id: String): Boolean {
        val nodes = rule.onAllNodes(hasText("m62-body-$id") and hasAnyAncestor(hasTestTag("ftutter-thread-list")), useUnmergedTree = true)
        return nodes.fetchSemanticsNodes().indices.any { nodes[it].isDisplayed() }
    }
    private fun awaitBody(id: String) = rule.waitUntil(10_000) { bodyIsDisplayed(id) }
    private fun assertBodyDisplayed(id: String) = assertTrue("Body $id must remain visible in the thread list", bodyIsDisplayed(id))
    private fun awaitThreadConsumed(count: Int) = rule.waitUntil(5_000) { consumedThreads.size == count }
    private fun assertExistingDataKept() {
        assertTrue("Existing tab missing: ${parseFtutterTabs(preferences).urls}", parseFtutterTabs(preferences).urls.contains(existingUrl))
        assertTrue(runBlocking { stateStore.history.first() }.any { it.threadId == "99" && it.boardId == a.id })
    }
    @Test fun coldBrowserLinkOpensTheThreadAndRecordsItsTabAndHistory() {
        val raw = "http://MAY.2chan.net/b/res/101.htm?from=browser#r2"
        open(raw); awaitThreadConsumed(1); awaitBody("101")
        assertEquals(listOf(a.url to "101"), calls.toList())
        assertTrue(parseFtutterTabs(preferences).urls.contains("${a.url}res/101.htm"))
        rule.waitUntil(5_000) { runBlocking { stateStore.history.first() }.any { it.threadId == "101" && it.boardId == a.id } }
        assertExistingDataKept()
        Espresso.pressBack()
        rule.onNodeWithTag("ftutter-catalog").assertIsDisplayed()
        assertEquals(a.id, preferences[FtutterKeys.LAST_BOARD])
    }
    @Test fun warmNotificationAndWatchThreadLinksOpenTheirTargetsOnceWithoutReplayingOnRecomposition() {
        open()
        rule.runOnUiThread { threadLink = "${a.url}res/101.htm" }; awaitThreadConsumed(1); awaitBody("101")
        rule.runOnUiThread { threadLink = "${b.url}res/202.htm" }; awaitThreadConsumed(2); awaitBody("202")
        val opened = preferences[FtutterKeys.STATS_OPENED]
        rule.runOnUiThread { preferences = preferences + (FtutterKeys.MEM_SIZE to "201") }
        rule.waitForIdle()
        assertEquals(2, consumedThreads.size)
        assertEquals(opened, preferences[FtutterKeys.STATS_OPENED])
        assertEquals(listOf(a.url to "101", b.url to "202"), calls.toList())
        // The same URL is a new request after its previous request was consumed, without a duplicate tab.
        rule.runOnUiThread { threadLink = "${b.url}res/202.htm" }; awaitThreadConsumed(3); awaitBody("202")
        assertEquals(1, parseFtutterTabs(preferences).urls.count { it == "${b.url}res/202.htm" })
        assertExistingDataKept()
    }
    @Test fun watchBoardSelectionOpensTheCatalogAndKeepsThePreviousThreadTab() {
        open("${a.url}res/101.htm"); awaitBody("101")
        val raw = "http://IMG.2chan.net/b/futaba.php?mode=cat"
        rule.runOnUiThread { boardLink = raw }
        rule.waitUntil(5_000) { consumedBoards.size == 1 }
        rule.onNodeWithTag("ftutter-catalog").assertIsDisplayed()
        assertEquals(b.id, preferences[FtutterKeys.LAST_BOARD])
        assertTrue(parseFtutterTabs(preferences).urls.contains("${a.url}res/101.htm"))
        assertExistingDataKept()
    }
    @Test fun unregisteredAndInvalidLinksExplainTheFailureAndKeepTheOpenThread() {
        open("${a.url}res/101.htm"); awaitThreadConsumed(1); awaitBody("101")
        rule.runOnUiThread { threadLink = "https://may.2chan.net/27/res/303.htm" }
        awaitThreadConsumed(2)
        rule.onNodeWithText("登録していない板です").assertIsDisplayed()
        assertBodyDisplayed("101")
        rule.runOnUiThread { threadLink = "https://evil.example/b/res/404.htm" }
        awaitThreadConsumed(3)
        rule.onNodeWithText("スレッドURLを解釈できませんでした").assertIsDisplayed()
        assertBodyDisplayed("101")
        rule.runOnUiThread { boardLink = "https://evil.example/b/" }
        rule.waitUntil(5_000) { consumedBoards.size == 1 }
        rule.onNodeWithText("板URLを解釈できませんでした").assertIsDisplayed()
        assertBodyDisplayed("101")
        assertEquals(listOf(a.url to "101"), calls.toList())
        assertEquals(listOf(a, b), runBlocking { stateStore.boards.first() })
        assertExistingDataKept()
    }
    @Test fun aLoadedEmptyBoardListReportsTheUnregisteredBoardInsteadOfDiscardingOrWaiting() {
        boards = emptyList(); runBlocking { stateStore.setBoards(emptyList()) }
        open("${a.url}res/101.htm"); awaitThreadConsumed(1)
        rule.onNodeWithText("登録していない板です").assertIsDisplayed()
        assertTrue(calls.isEmpty())
    }
}
