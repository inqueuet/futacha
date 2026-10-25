@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.*
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.ui.futaber.*
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Production catalog refresh, dialogs and persisted scope; no request reaches a real server. */
class ThreadFeedbackOpqrInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var loader: ImageLoader
    private val board = BoardSummary("audit", "検証板", "検証", "https://may.2chan.net/b/", "")
    private val db = "feedback_opqr.db"
    private val items = (1..3).map { n -> CatalogItem(id = "$n", title = "THREAD-$n", thumbnailUrl = null, fullImageUrl = null, threadUrl = "${board.url}res/$n.htm", replyCount = 2) }
    private val entry = ThreadHistoryEntry("3", board.id, "THREAD-3", "", board.name, board.url, 10, 2)
    private var fail = false
    private var fetches = 0
    private var creates = 0
    private val repo = object : BoardRepository by FakeBoardRepository() {
        override suspend fun createThread(board: String, name: String, email: String, subject: String, comment: String,
            password: String, imageFile: ByteArray?, imageFileName: String?, textOnly: Boolean, handwriting: Boolean): String? {
            creates++; return "4"
        }
        override suspend fun getCatalogPage(board: String, mode: CatalogMode): CatalogPageContent {
            fetches++; if (fail) error("模擬通信失敗")
            return CatalogPageContent(items)
        }
        override suspend fun getCatalogWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings): List<CatalogItem> {
            fetches++; if (fail) error("模擬通信失敗")
            return items
        }
    }
    @Before fun prepare() {
        rule.activity.deleteDatabase(db)
        store = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking { store.initialize(); store.importModernBoards(listOf(board)) }
        loader = ImageLoader.Builder(rule.activity).build()
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        loader.shutdown(); runBlocking { store.closeForTest() }; rule.activity.deleteDatabase(db)
    }
    @Composable private fun Images(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) { MaterialTheme { content() } }
    }
    private fun capture(name: String) {
        rule.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val out = File(rule.activity.getExternalFilesDir(null), "feedback-opqr").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun titleBounds(title: String): androidx.compose.ui.geometry.Rect? =
        rule.onAllNodesWithText(title, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
            .firstOrNull { it.left >= 0 && it.right <= rule.activity.resources.displayMetrics.widthPixels && it.width > 0 && it.height > 0 }
    private fun waitTitle() {
        try { rule.waitUntil(15_000) { titleBounds("THREAD-1") != null && titleBounds("THREAD-3") != null } }
        catch (error: AssertionError) { capture("catalog-wait-failure"); println(rule.onRoot(useUnmergedTree = true).printToString()); throw error }
        catch (error: androidx.compose.ui.test.ComposeTimeoutException) { capture("catalog-wait-failure"); println(rule.onRoot(useUnmergedTree = true).printToString()); throw error }
    }
    private fun precedes(a: androidx.compose.ui.geometry.Rect, b: androidx.compose.ui.geometry.Rect): Boolean =
        if (kotlin.math.abs(a.top - b.top) <= 8f) a.left < b.left else a.top < b.top
    private fun assertOrder(first: String, second: String) {
        val a = requireNotNull(titleBounds(first))
        val b = requireNotNull(titleBounds(second))
        assertTrue("$first should precede $second: $a $b", precedes(a, b))
    }
    @Test fun futaberPinsOnlyAfterSuccessfulManualRefreshAndKeepsSearch() {
        val view = FutaberCatalogViewState()
        val search = FutaberCatalogSearchState()
        rule.setContent { Images { FutaberTheme(FutaberThemeMode.Light) {
            FutaberCatalogScreen(board, repo, FutaberCatalogDisplayStyle.Row1, CatalogMode.default,
                {}, {}, {}, {}, {}, {}, 0, onOpenThread = {}, view = view, search = search,
                viewedFirstEnabled = true, lastViewedThreadId = "3")
        } } }
        waitTitle(); assertOrder("THREAD-1", "THREAD-3")
        rule.onNodeWithTag("futaber-refresh").performClick()
        rule.waitUntil(15_000) { view.viewedFirstId == "3" && !view.load.isLoading }
        assertOrder("THREAD-3", "THREAD-1")
        rule.onNodeWithTag("futaber-search").performClick()
        rule.onNodeWithTag("futaber-search-field").performTextInput("THREAD-1")
        rule.waitUntil(10_000) { rule.onAllNodesWithText("THREAD-3", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        rule.onAllNodesWithText("THREAD-1", useUnmergedTree = true).assertCountEquals(2)
        capture("futaber-search-does-not-resurrect-pinned")
    }
    @Test fun ftutterFailedRefreshDoesNotPinButSuccessfulManualRefreshDoes() {
        val holder = FtutterCatalogHolder()
        val prefs = mapOf(viewedThreadFirstKey(board.url) to "ON")
        rule.setContent { Images { FtutterTheme(null, FtutterBrightness.LIGHT) {
            FtutterCatalogScreen(board, listOf(board), repo, listOf(entry), prefs, holder,
                { _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, columnsOverride = 3)
        } } }
        waitTitle(); assertOrder("THREAD-1", "THREAD-3")
        fail = true
        rule.runOnUiThread { holder.manualRefreshKey = holder.load?.key; holder.refreshTick++ }
        rule.waitUntil(15_000) { holder.load?.error != null }
        assertOrder("THREAD-1", "THREAD-3")
        fail = false
        rule.runOnUiThread { holder.manualRefreshKey = holder.load?.key; holder.refreshTick++ }
        rule.waitUntil(15_000) { holder.load?.viewedFirstId == "3" && holder.load?.loading == false }
        assertOrder("THREAD-3", "THREAD-1")
        capture("ftutter-manual-refresh-pins-viewed")
    }
    @Test fun reportCheckboxStartsUncheckedCancelDoesNotSendAndNgStaysThreadLocal() {
        var sends = 0
        var open by mutableStateOf(true)
        rule.setContent { MaterialTheme { if (open) ReportPostConfirmation("456", true, { open = false }) { alsoNg ->
            runBlocking { sendReportAndOptionalNg(alsoNg, { sends++ }, { registerReportedThreadPostNg(store, board, "123", "456") }) }
            open = false
        } } }
        rule.onNode(isToggleable()).assertIsOff()
        capture("report-ng-unchecked")
        rule.onNodeWithText("キャンセル").performClick()
        assertEquals(0, sends); assertTrue(runBlocking { store.ngRules.first().isEmpty() })
        rule.runOnUiThread { open = true }
        rule.onNode(isToggleable()).performClick()
        rule.onNodeWithTag("report-post-confirm").performClick()
        assertEquals(1, sends)
        val rules = runBlocking { store.ngRules.first() }
        runBlocking { store.closeForTest() }
        store = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking { store.initialize() }
        assertEquals(rules, runBlocking { store.ngRules.first() })
        assertEquals(listOf("https://may.2chan.net/b/res/123.htm"), rules.map { it.scopeKey })
        assertTrue(runBlocking { store.tabs.first().isEmpty() })
        val chosen = buildCompatThreadNgRuleIndex(rules, compatTabKey(rules.single().scopeKey), compatBoardKey(board.url))
        assertEquals(setOf("456"), chosen.postNos.toSet())
        assertTrue(buildCompatThreadNgRuleIndex(rules, compatTabKey("${board.url}res/124.htm"), compatBoardKey(board.url)).postNos.isEmpty())
        runBlocking { store.deleteBoard(compatBoardKey(board.url)) }
        assertTrue(runBlocking { store.ngRules.first().isEmpty() })
    }
    @Test fun summaryWaitsForLivePageAndDoesNotRepeatOnRecomposition() {
        val own = Post(id = "1", author = null, subject = null, timestamp = "", messageHtml = "本文", imageUrl = null, thumbnailUrl = null)
        val reply = own.copy(id = "2", quoteReferences = listOf(QuoteReference(">No.1", listOf("1"))))
        var live by mutableStateOf(false)
        var tick by mutableIntStateOf(0)
        val notices = mutableListOf<String>()
        rule.setContent { MaterialTheme { Column {
            Text("$tick")
            ThreadOpeningSummaryEffect("scope", 1, listOf(own, reply), setOf("1"), enabled = true, live = live,
                onNotice = { notices += it })
        } } }
        rule.waitForIdle(); assertTrue(notices.isEmpty())
        rule.runOnUiThread { live = true }; rule.waitForIdle()
        assertEquals(listOf("新着1件・自分への返信1件"), notices)
        rule.runOnUiThread { tick++; live = false }; rule.waitForIdle()
        rule.runOnUiThread { live = true }; rule.waitForIdle()
        assertEquals(1, notices.size)
    }
    @Test fun tabListShowsUnambiguousStatusesWithUnreadCount() {
        val cb = runBlocking { store.boards.first().single() }
        val tab = CompatTab("q", "${board.url}res/123.htm", "${board.url}res/123.htm", cb.key, board.name, "123", "状態の検証タイトル",
            replyCount = 12, checkedReplyCount = 10, isIsolated = true, isDead = true, insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1)
        rule.setContent { CompatibilityProfileTheme("mono") {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                CompatTabSelector(listOf(tab), tab.key, true, {}, {})
                CompatThreadMetadataRow(tab, {}, {})
            }
        } }
        rule.onNodeWithTag("compat-tab-status-q", useUnmergedTree = true).assertTextEquals("隔離")
        val badge = rule.onNodeWithTag("compat-tab-status-q", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val title = rule.onNodeWithTag("compat-tab-title-q", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Status must stay above the title", badge.bottom <= title.top)
        rule.onNodeWithTag("compat-tab-title-q", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("+2", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("compat-drawer-tab-status-q", useUnmergedTree = true).assertTextEquals("隔離・落ち")
        rule.onNodeWithTag("compat-drawer-tab-title-q", useUnmergedTree = true).assertIsDisplayed()
        capture("tab-status-and-title")
    }
    @Test fun ftutterOpeningSummaryUsesRecordedPostsAndConfirmedStatus() {
        val state = com.valoser.futacha.shared.state.AppStateStore(com.valoser.futacha.shared.state.BaseInMemoryPlatformStateStorage())
        runBlocking { state.updateHistory { listOf(entry.copy(lastConfirmedAliveEpochMillis = 1)) } }
        val notices = mutableListOf<String>()
        val own = Post(id = "1", author = null, subject = null, timestamp = "", messageHtml = "本文", imageUrl = null, thumbnailUrl = null)
        val page = ThreadPage("3", null, null, "隔離", listOf(own, own.copy(id = "2"), own.copy(id = "4",
            quoteReferences = listOf(QuoteReference(">No.1", listOf("1"))))))
        val threadRepo = object : BoardRepository by repo {
            override suspend fun getThreadContent(board: String, threadId: String) = ThreadPageContent(page)
        }
        rule.setContent { Images { FtutterTheme(null, FtutterBrightness.LIGHT) {
            FtutterThreadScreen(board, FtutterThreadRef(board.id, "3", "スレッド"), threadRepo, null,
                emptyMap(), parseFtutterDesign(emptyMap()), entry, setOf("1"), 2,
                { _, _ -> }, {}, {}, {}, { _, _, _, _ -> }, {}, {}, {}, { notices += it },
                extensions = FtutterExtensions(setOf(FtutterExtension.OPEN_SUMMARY)), historyStateStore = state)
        } } }
        rule.waitUntil(15_000) { notices.contains("新着1件・自分への返信1件") }
        assertEquals(1, notices.count { it == "新着1件・自分への返信1件" })
        rule.waitUntil(10_000) { runBlocking { state.history.first().single().threadStatus?.isIsolated == true } }
        capture("ftutter-opening-summary")
    }
    @Test fun futaberOpeningSummaryUsesRecordedPostsAfterNgFiltering() {
        val own = Post(id = "1", author = null, subject = null, timestamp = "", messageHtml = "本文", imageUrl = null, thumbnailUrl = null)
        val page = ThreadPage("3", null, null, null, listOf(own, own.copy(id = "2"), own.copy(id = "4",
            quoteReferences = listOf(QuoteReference(">No.1", listOf("1"))))))
        val threadRepo = object : BoardRepository by repo {
            override suspend fun getThreadContent(board: String, threadId: String) = ThreadPageContent(page)
        }
        rule.setContent { Images { FutaberTheme(FutaberThemeMode.Light) {
            CompositionLocalProvider(LocalFutaberDisplaySettings provides FutaberDisplaySettings(extOpenSummary = true)) {
                FutaberThreadScreen(board, FutaberThreadRef(board.id, "3", "スレッド", "", 2), threadRepo,
                    isTabbed = true, onToggleTab = {}, onOpenManage = {}, onOpenPost = {}, refreshSignal = 0,
                    quoteMode = false, onQuoteLine = {}, onOpenDrawer = {}, drawerGestureEnabled = false,
                    onPageLoaded = {}, onScrollSettled = { _, _, _, _ -> }, ngWords = emptyList(), ngHeaders = emptyList(),
                    ngRules = emptyList(), onChangeNgWords = {}, onChangeNgHeaders = {}, onBack = {},
                    selfPostIds = setOf("1"), initialKnownPostCount = 2)
            }
        } } }
        rule.waitUntil(15_000) { rule.onAllNodesWithText("新着1件・自分への返信1件").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("新着1件・自分への返信1件").assertIsDisplayed()
        capture("futaber-opening-summary")
    }

    @Test fun futachaCatalogPinsOnManualRefreshWithSharedBoardSetting() {
        val state = com.valoser.futacha.shared.state.AppStateStore(com.valoser.futacha.shared.state.BaseInMemoryPlatformStateStorage())
        runBlocking {
            state.updateBoards { listOf(board) }; state.updateHistory { listOf(entry) }
            store.savePreference(viewedThreadFirstKey(board.url), "ON")
        }
        rule.setContent { Images {
            ProvideFutachaSharedFeatures(store, null, repo, null, null, "test", appStateStore = state) {
                CatalogScreen(board, listOf(entry), {}, {}, repository = repo, stateStore = state,
                    preferencesState = ScreenPreferencesState("test"))
            }
        } }
        waitTitle(); assertOrder("THREAD-1", "THREAD-3")
        rule.onNodeWithContentDescription("カタログ更新", useUnmergedTree = true).performClick()
        rule.waitUntil(15_000) {
            val first = titleBounds("THREAD-3")
            val other = titleBounds("THREAD-1")
            first != null && other != null && precedes(first, other)
        }
        assertOrder("THREAD-3", "THREAD-1")
        capture("futacha-manual-refresh-pins-viewed")
    }
    /** The ふたちゃ catalog with the viewed thread (No.3) put first by a manual refresh. */
    private fun pinnedFutachaCatalog() {
        val state = com.valoser.futacha.shared.state.AppStateStore(com.valoser.futacha.shared.state.BaseInMemoryPlatformStateStorage())
        runBlocking {
            state.updateBoards { listOf(board) }; state.updateHistory { listOf(entry) }
            state.setHasShownPostingNotice(true)
            store.savePreference(viewedThreadFirstKey(board.url), "ON")
        }
        rule.setContent { Images {
            ProvideFutachaSharedFeatures(store, null, repo, null, null, "test", appStateStore = state) {
                CatalogScreen(board, listOf(entry), {}, {}, repository = repo, stateStore = state,
                    preferencesState = ScreenPreferencesState("test"))
            }
        } }
        waitTitle(); assertOrder("THREAD-1", "THREAD-3")
        rule.onNodeWithContentDescription("カタログ更新", useUnmergedTree = true).performClick()
        rule.waitUntil(15_000) { pinned("THREAD-3", "THREAD-1") }
    }
    private fun pinned(first: String, second: String): Boolean {
        val a = titleBounds(first)
        val b = titleBounds(second)
        return a != null && b != null && precedes(a, b)
    }
    private fun clickNav(label: String) {
        val node = hasClickAction() and (hasContentDescription(label) or hasText(label))
        rule.waitUntil(5_000) { rule.onAllNodes(node).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(node).onFirst().performClick()
    }

    /** L37: another order drops the placement, and coming back to the first order does not bring it back. */
    @Test fun futachaCatalogDropsTheViewedPlacementForAnotherOrder() {
        pinnedFutachaCatalog()
        val before = fetches
        clickNav("モード")
        rule.onNodeWithText("新順").performClick()
        rule.waitUntil(15_000) { fetches > before && pinned("THREAD-1", "THREAD-3") }
        val again = fetches
        clickNav("モード")
        rule.onNodeWithText("多い順").performClick()
        rule.waitUntil(15_000) { fetches > again && titleBounds("THREAD-1") != null }
        rule.waitForIdle()
        assertOrder("THREAD-1", "THREAD-3")
    }

    /** L37: the refresh after creating a thread does not put the last viewed thread above the new one. */
    @Test fun futachaCatalogDoesNotPinTheViewedThreadAfterCreatingOne() {
        pinnedFutachaCatalog()
        val before = fetches
        clickNav("スレッド作成")
        rule.onNode(hasSetTextAction() and hasText("コメント")).performTextInput("L37")
        rule.onNode(hasSetTextAction() and hasText("削除キー")).performTextReplacement("l37key")
        rule.onAllNodesWithContentDescription("送信する").onFirst().performClick()
        rule.waitUntil(10_000) { creates == 1 || rule.onAllNodesWithText("投稿の確認").fetchSemanticsNodes().isNotEmpty() }
        if (creates == 0) rule.onAllNodesWithText("送信する").onLast().performClick()
        rule.waitUntil(15_000) { creates == 1 && fetches > before && pinned("THREAD-1", "THREAD-3") }
    }

    /** L37: ふたばー風's reload after a created thread does not pin the last viewed thread again. */
    @Test fun futaberDoesNotPinTheViewedThreadAfterCreatingOne() {
        val view = FutaberCatalogViewState()
        val search = FutaberCatalogSearchState()
        var signal by mutableStateOf(0)
        rule.setContent { Images { FutaberTheme(FutaberThemeMode.Light) {
            FutaberCatalogScreen(board, repo, FutaberCatalogDisplayStyle.Row1, CatalogMode.default,
                {}, {}, {}, {}, {}, {}, signal, onOpenThread = {}, view = view, search = search,
                viewedFirstEnabled = true, lastViewedThreadId = "3")
        } } }
        waitTitle()
        rule.onNodeWithTag("futaber-refresh").performClick()
        rule.waitUntil(15_000) { view.viewedFirstId == "3" && !view.load.isLoading }
        val before = fetches
        // As FutaberApp does when a thread was created on this board.
        rule.runOnUiThread { view.afterThreadCreated(); signal++ }
        rule.waitUntil(15_000) { fetches > before && !view.load.isLoading }
        rule.waitForIdle()
        assertEquals(null, view.viewedFirstId)
        assertOrder("THREAD-1", "THREAD-3")
    }

    /** L37: ふたったー風's reload after a created thread does not keep the last viewed thread first. */
    @Test fun ftutterDoesNotKeepTheViewedThreadFirstAfterCreatingOne() {
        val holder = FtutterCatalogHolder()
        val prefs = mapOf(viewedThreadFirstKey(board.url) to "ON")
        rule.setContent { Images { FtutterTheme(null, FtutterBrightness.LIGHT) {
            FtutterCatalogScreen(board, listOf(board), repo, listOf(entry), prefs, holder,
                { _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, columnsOverride = 3)
        } } }
        waitTitle()
        rule.runOnUiThread { holder.manualRefreshKey = holder.load?.key; holder.refreshTick++ }
        rule.waitUntil(15_000) { holder.load?.viewedFirstId == "3" && holder.load?.loading == false }
        val before = fetches
        // As FtutterApp does when a thread was created, followed by its reload.
        rule.runOnUiThread { holder.afterThreadCreated(); holder.refreshTick++ }
        rule.waitUntil(15_000) { fetches > before && holder.load?.loading == false }
        rule.waitForIdle()
        assertEquals(null, holder.load?.viewedFirstId)
        assertOrder("THREAD-1", "THREAD-3")
    }

    @Test fun toshiakiCatalogPinsOnManualRefreshWithSharedBoardSetting() {
        runBlocking {
            store.savePreference(viewedThreadFirstKey(board.url), "ON")
            store.upsertHistory(CompatHistoryEntry("${board.url}res/3.htm", "${board.url}res/3.htm",
                compatBoardKey(board.url), board.name, "3", "THREAD-3", contentUpdatedAtEpochMillis = 10))
        }
        rule.setContent { Images { CompatibilityApp(store = store, repository = repo, onExitApplication = {}) } }
        rule.onNodeWithText(board.url).performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("compat-catalog-grid").fetchSemanticsNodes().isNotEmpty() }
        fun bounds(id: String) = rule.onNodeWithTag("compat-catalog-item-$id", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("compat-catalog-item-3").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(precedes(bounds("1"), bounds("3")))
        rule.onNodeWithContentDescription("リロード").performClick()
        rule.waitUntil(15_000) { precedes(bounds("3"), bounds("1")) }
        assertTrue(precedes(bounds("3"), bounds("1")))
        capture("toshiaki-manual-refresh-pins-viewed")
    }

    private var toshiakiAiCommand by mutableStateOf<com.valoser.futacha.shared.ai.FutachaAiCommand?>(null)
    private fun toshiakiBounds(id: String) =
        rule.onNodeWithTag("compat-catalog-item-$id", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    private fun toshiakiPinned(first: String, second: String): Boolean =
        rule.onAllNodesWithTag("compat-catalog-item-$first", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
            rule.onAllNodesWithTag("compat-catalog-item-$second", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
            precedes(toshiakiBounds(first), toshiakiBounds(second))
    /** としあき(仮)'s catalog, optionally with the viewed thread (No.3) put first by the toolbar's reload. */
    private fun toshiakiCatalog(pin: Boolean = true, aiEnabled: Boolean = false) {
        // "AIアプリ操作" (shared by every mode) is kept in the app state; it is off unless turned on.
        val state = if (!aiEnabled) null else com.valoser.futacha.shared.state.AppStateStore(
            com.valoser.futacha.shared.state.BaseInMemoryPlatformStateStorage())
        runBlocking {
            store.savePreference(viewedThreadFirstKey(board.url), "ON")
            store.upsertHistory(CompatHistoryEntry("${board.url}res/3.htm", "${board.url}res/3.htm",
                compatBoardKey(board.url), board.name, "3", "THREAD-3", contentUpdatedAtEpochMillis = 10))
            state?.updateBoards { listOf(board) }
            state?.setAiCommandEnabled(true)
        }
        rule.setContent { Images { CompatibilityApp(store = store, repository = repo, stateStore = state, onExitApplication = {},
            platformAiCommand = toshiakiAiCommand, onPlatformAiCommandConsumed = { toshiakiAiCommand = null }) } }
        rule.onNodeWithText(board.url).performClick()
        rule.waitUntil(15_000) { toshiakiPinned("1", "3") }
        if (pin) {
            rule.onNodeWithContentDescription("リロード").performClick()
            rule.waitUntil(15_000) { toshiakiPinned("3", "1") }
        }
    }

    /** L37: another order drops the placement, and coming back to the first order does not bring it back. */
    @Test fun toshiakiCatalogDropsTheViewedPlacementForAnotherOrder() {
        toshiakiCatalog()
        val before = fetches
        rule.onNodeWithContentDescription("表示順").performClick()
        rule.onAllNodesWithText("新しい順").onLast().performClick()
        rule.waitUntil(15_000) { fetches > before && toshiakiPinned("1", "3") }
        rule.onNodeWithContentDescription("表示順").performClick()
        rule.onAllNodesWithText("カタログ").onLast().performClick()
        // The first order's catalog may come from its stored copy, without a new request.
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("compat-catalog-item-1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        assertTrue(toshiakiPinned("1", "3"))
    }

    /** L37: after a thread is created, the catalog no longer keeps the last viewed thread first. */
    @Test fun toshiakiCatalogDropsTheViewedPlacementAfterCreatingAThread() {
        toshiakiCatalog()
        rule.onNodeWithContentDescription("スレ立て").performClick()
        rule.onNode(hasSetTextAction() and hasText("コメント")).performTextInput("L37")
        rule.onNode(hasSetTextAction() and hasText("削除キー")).performTextReplacement("l37key")
        rule.onAllNodesWithContentDescription("送信する").onFirst().performClick()
        rule.waitUntil(10_000) { creates == 1 || rule.onAllNodesWithText("送信する").fetchSemanticsNodes().isNotEmpty() }
        if (creates == 0) rule.onAllNodesWithText("送信する").onLast().performClick()
        rule.waitUntil(15_000) { creates == 1 }
        // The new thread opens; back on the catalog the placement is gone.
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("compat-catalog-grid").fetchSemanticsNodes().isEmpty() }
        androidx.test.espresso.Espresso.pressBack()
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("compat-catalog-grid").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(15_000) { toshiakiPinned("1", "3") }
    }

    /** L37: the AI "カタログ更新" is a manual refresh, so it puts the viewed thread first like the toolbar's reload. */
    @Test fun toshiakiAiCatalogRefreshPinsTheViewedThread() {
        toshiakiCatalog(pin = false, aiEnabled = true)
        val before = fetches
        rule.runOnUiThread { toshiakiAiCommand = com.valoser.futacha.shared.ai.parseFutachaAiDeepLink("futacha://ai?action=refresh_catalog") }
        rule.waitUntil(15_000) { fetches > before && toshiakiPinned("3", "1") }
    }

    @Test fun futaberReportDialogKeepsNgOptionalAndForwardsOnlyTheConfirmedChoice() {
        val choices = mutableListOf<Boolean>()
        var open by mutableStateOf(true)
        rule.setContent { FutaberTheme(FutaberThemeMode.Light) {
            if (open) FutaberRemoteActionDialog(FutaberRemoteAction.Report, "456",
                onConfirm = { choices += it; open = false }, onDismiss = { open = false }, offerReportNg = true)
        } }
        rule.onNode(isToggleable()).assertIsOff()
        rule.onNodeWithTag("futaber-remote-confirm").performClick()
        rule.waitForIdle()
        assertEquals(listOf(false), choices)
        rule.runOnUiThread { open = true }
        rule.onNode(isToggleable()).assertIsOff().performClick()
        rule.onNodeWithTag("futaber-remote-confirm").performClick()
        rule.waitForIdle()
        assertEquals(listOf(false, true), choices)
        rule.runOnUiThread { open = true }
        rule.onNode(isToggleable()).assertIsOff()
        rule.onNodeWithText("キャンセル").performClick()
        assertEquals(2, choices.size)
    }

    @Test fun ftutterCompactStatusTitleAndUnreadRemainSeparateAtLargerFont() {
        val url = "${board.url}res/3.htm"
        val tab = FtutterTabView(url, entry.copy(title = "題名", threadStatus = ThreadStatus(isIsolated = true, isDead = true)),
            FtutterTabNumbers(12, 2), false, true)
        rule.setContent { Images { FtutterTheme(null, FtutterBrightness.LIGHT) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 1.3f)) {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    FtutterTabStrip(listOf(tab), url, FtutterTabSettings(showTab = true), parseFtutterDesign(emptyMap()), {}, {}, showStatus = true)
                }
            }
        } } }
        val badge = rule.onNodeWithTag("ftutter-tab-status", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val title = rule.onNodeWithText("題名", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val unread = rule.onNodeWithText("2", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        capture("ftutter-tab-status-larger-font")
        assertFalse("Badge overlaps title: $badge $title", badge.overlaps(title))
        assertFalse("Title overlaps unread: $title $unread", title.overlaps(unread))
    }

    /** L18: an earlier fall record (isAutoRefreshDisabled only, e.g. .ftt sureOti) still shows 「落ち」 with the extension ON. */
    @Test fun ftutterStatusExtensionKeepsAnEarlierFallRecord() {
        val url = "${board.url}res/4.htm"
        val tab = FtutterTabView(url, entry.copy(title = "以前の落ち", isAutoRefreshDisabled = true, threadStatus = null),
            FtutterTabNumbers(12, null), false, false)
        rule.setContent { Images { FtutterTheme(null, FtutterBrightness.LIGHT) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                FtutterTabStrip(listOf(tab), url, FtutterTabSettings(showTab = true), parseFtutterDesign(emptyMap()), {}, {}, showStatus = true)
            }
        } } }
        rule.onNodeWithTag("ftutter-tab-status", useUnmergedTree = true).assertTextEquals("落ち")
        rule.onNodeWithContentDescription("以前の落ち、落ち").assertExists()
    }

}
