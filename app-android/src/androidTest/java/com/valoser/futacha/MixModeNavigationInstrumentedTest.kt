@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.espresso.Espresso
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.mix.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.util.createFileSystem
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.BaseInMemoryPlatformStateStorage
import com.valoser.futacha.shared.ui.futaber.*
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import kotlinx.coroutines.runBlocking
import org.junit.*

/** Real mode hosts and navigation, local catalogs and threads only. */
class MixModeNavigationInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val a = BoardSummary("mix-mode-a", "Audit A", "", "https://may.2chan.net/b/", "")
    private val b = a.copy(id = "mix-mode-b", name = "Audit B", url = "https://img.2chan.net/b/")
    private val state = AppStateStore(BaseInMemoryPlatformStateStorage())
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var loader: ImageLoader
    private var threadLink by mutableStateOf<String?>(null)
    private var boardLink by mutableStateOf<String?>(null)
    private var placeholderTitles = false
    private val headCalls = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val repository = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getCatalogPage(board: String, mode: CatalogMode) = CatalogPageContent((501..590).map { id ->
            val prefix = if (board.contains("may.")) "A" else "B"
            CatalogItem("$id", "${board}res/$id.htm", if (placeholderTitles) "No.$id" else "Audit $prefix $id", null, null, replyCount = 1)
        })
        override suspend fun getCatalogPageWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings) = getCatalogPage(board, mode)
        override suspend fun resolveCatalogDisplayTitle(board: String, item: CatalogItem, allowFallbackHeadScan: Boolean): String {
            headCalls += item.id
            return "Completed ${item.id}"
        }
        override suspend fun getThreadContent(board: String, threadId: String) = ThreadPageContent(ThreadPage(threadId, null, null, null,
            listOf(Post(threadId, author = null, subject = null, timestamp = "", messageHtml = "Audit body $threadId", imageUrl = null, thumbnailUrl = null))))
    }
    @Before fun prepare() {
        rule.activity.deleteDatabase("mix_mode_navigation.db")
        store = AndroidCompatibilityStore(rule.activity, databaseName = "mix_mode_navigation.db")
        loader = ImageLoader.Builder(rule.activity).build()
        runBlocking {
            store.initialize()
            state.setBoards(listOf(a, b))
            state.setHistory(emptyList())
            MixStore(store).create("Mode MIX", listOf(a.url, b.url), MixOrderKind.ALTERNATE, 77)
            val id = (MixStore(store).load() as MixLoadResult.Ok).mixes.single().id
            com.valoser.futacha.shared.ui.board.futachaMixSessionCache.remove(id)
            store.savePreference("compat.futaber.lastBoardId", a.id)
        }
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        rule.waitForIdle()
        loader.shutdown()
        state.close()
        runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase("mix_mode_navigation.db")
    }
    private fun start(ftutter: Boolean, preferences: Map<String, String> = emptyMap(), restoration: StateRestorationTester? = null) {
        runBlocking { store.savePreferences(preferences) }
        val services = FutaberMediaServices(store, null, createFileSystem(rule.activity), null)
        val content: @Composable () -> Unit = {
            val boards by state.boards.collectAsState(emptyList())
            val history by state.history.collectAsState(emptyList())
            val prefs by store.preferences.collectAsState(emptyMap())
            val rules by store.ngRules.collectAsState(emptyList())
            val save: (String, String) -> Unit = { k, v -> runBlocking { store.savePreference(k, v) } }
            val density = androidx.compose.ui.platform.LocalDensity.current
            val testDensity = if (preferences[FtutterKeys.USE_TABLET] == "true")
                androidx.compose.ui.unit.Density(density.density * .5f, density.fontScale) else density
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader,
                androidx.compose.ui.platform.LocalDensity provides testDensity) {
                if (boards.isNotEmpty() && prefs[MIX_DEFINITIONS_KEY] != null) {
                    if (!ftutter) FutaberApp(boards, history, repository, state, prefs, rules, save, mediaServices = services,
                        initialThreadDeepLink = threadLink, onThreadDeepLinkConsumed = { threadLink = null },
                        initialBoardDeepLink = boardLink, onBoardDeepLinkConsumed = { boardLink = null })
                    else FtutterApp(boards, history, repository, state, prefs, rules, services, null, "test",
                        threadLink, { threadLink = null }, boardLink, { boardLink = null }, null, null, {}, null, {}, null,
                        { it() }, {}, save, { k, edit -> runBlocking { edit(store.loadPreference(k))?.let { store.savePreference(k, it) } } })
                }
            }
        }
        if (restoration == null) rule.setContent(content) else restoration.setContent(content)
    }
    private fun awaitTag(tag: String) = rule.waitUntil(10_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun openMix(ftutter: Boolean) {
        if (!ftutter && rule.onAllNodesWithText("まとめカタログ").fetchSemanticsNodes().isEmpty()) {
            rule.onRoot().performTouchInput { swipe(Offset(1f, centerY), Offset(width * .8f, centerY), 500) }
        }
        rule.onNodeWithText("まとめカタログ").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("切替").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun futaberExternalBoardAndThreadLeaveTheMix() {
        start(false); awaitTag("futaber-catalog-body"); openMix(false)
        rule.runOnUiThread { boardLink = b.url }
        awaitTag("futaber-catalog-body")
        rule.onNodeWithText("切替").assertDoesNotExist()
        openMix(false)
        rule.runOnUiThread { threadLink = "${a.url}res/501.htm" }
        awaitTag("futaber-thread-body")
        rule.onNodeWithText("切替").assertDoesNotExist()
    }
    @Test fun futaberMixOpenedFromThreadClosesToCatalog() {
        threadLink = "${a.url}res/501.htm"
        start(false); awaitTag("futaber-thread-body"); openMix(false)
        rule.onNodeWithContentDescription("戻る").performClick()
        awaitTag("futaber-catalog-body")
        rule.onNodeWithTag("futaber-thread-body").assertDoesNotExist()
    }
    @Test fun ftutterTwoPaneKeepsMixWhileSelectingDifferentThreads() {
        start(true, mapOf(FtutterKeys.USE_TABLET to "true", FtutterKeys.TABLET_MIN_WIDTH to "0"))
        awaitTag("ftutter-board-list"); openMix(true)
        awaitTag("ftutter-two-pane")
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Audit A 501").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("Audit A 501")[0].performClick()
        awaitTag("ftutter-thread-list")
        rule.onNodeWithText("切替").assertIsDisplayed()
        rule.onAllNodesWithText("Audit A 502")[0].performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Audit body 502", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("切替").assertIsDisplayed()
        Espresso.pressBack()
        rule.onNodeWithText("切替").assertIsDisplayed()
    }
    @Test fun ftutterMixAppliesOwnNgAndHiddenWithoutPruningAndShowsLimit() {
        val hidden = encodeFtutterList(listOf("${a.url}res/502.htm"))
        start(true, mapOf(FtutterKeys.NG_LIST to encodeFtutterNgList(listOf(FtutterNgRule("Audit A 501", FtutterNgType.TITLE.stored))),
            FtutterKeys.HIDDEN_THREADS to hidden))
        awaitTag("ftutter-board-list"); openMix(true)
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Audit B 501").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Audit A 501").assertDoesNotExist()
        rule.onNodeWithText("Audit A 502").assertDoesNotExist()
        rule.onNodeWithText("先頭80件まで", substring = true).assertIsDisplayed()
        Assert.assertEquals(hidden, runBlocking { store.loadPreference(FtutterKeys.HIDDEN_THREADS) })
    }
    @Test fun futaberMixAppliesCommonWordsAndSharedTitlesAndUpdatesLive() {
        runBlocking { state.updateCatalogNgWords { listOf("Audit A 501") } }
        start(false, mapOf(FtutterKeys.NG_LIST to encodeFtutterNgList(listOf(
            FtutterNgRule("Audit A 502", FtutterNgType.TITLE.stored)))))
        awaitTag("futaber-catalog-body"); openMix(false)
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Audit B 501").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Audit A 501").assertDoesNotExist()
        rule.onNodeWithText("Audit A 502").assertDoesNotExist()
        runBlocking {
            state.updateCatalogNgWords { emptyList() }
            store.savePreference(com.valoser.futacha.shared.ui.compat.COMPAT_COMMON_NG_APPLY_STORAGE_KEY, "OFF")
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Audit A 501").fetchSemanticsNodes().isNotEmpty() &&
            rule.onAllNodesWithText("Audit A 502").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun ftutterCorruptMixEntryShowsTheRefusalReason() {
        start(true, mapOf(MIX_DEFINITIONS_KEY to "{broken"))
        awaitTag("ftutter-board-list")
        rule.onNodeWithText("まとめカタログ").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("壊れています", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("切替").assertDoesNotExist()
        Assert.assertEquals("{broken", runBlocking { store.loadPreference(MIX_DEFINITIONS_KEY) })
    }

    @Test fun futaberRestoresTheSelectedMixWithoutItsSessionCache() {
        val restoration = StateRestorationTester(rule)
        start(false, restoration = restoration)
        awaitTag("futaber-catalog-body"); openMix(false)
        val id = runBlocking { (MixStore(store).load() as MixLoadResult.Ok).mixes.single().id }
        com.valoser.futacha.shared.ui.board.futachaMixSessionCache.remove(id)
        restoration.emulateSavedInstanceStateRestore()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("切替").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Audit A 501").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Mode MIX").assertIsDisplayed()
    }

    private fun assertMixDoesNotCompleteTitles(ftutter: Boolean) {
        placeholderTitles = true
        start(ftutter)
        awaitTag(if (ftutter) "ftutter-board-list" else "futaber-catalog-body")
        openMix(ftutter)
        rule.waitUntil(10_000) { rule.onAllNodesWithText("No.501").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        Assert.assertTrue("This mode must not fetch thread heads: $headCalls", headCalls.isEmpty())
    }
    @Test fun futaberMixDoesNotFetchHeadTitles() = assertMixDoesNotCompleteTitles(false)
    @Test fun ftutterMixDoesNotFetchHeadTitles() = assertMixDoesNotCompleteTitles(true)
}
