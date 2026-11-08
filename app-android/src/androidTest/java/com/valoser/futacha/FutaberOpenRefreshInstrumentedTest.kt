@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "DEPRECATION")

package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.ThreadPageContent
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.BaseInMemoryPlatformStateStorage
import com.valoser.futacha.shared.ui.futaber.FutaberApp
import com.valoser.futacha.shared.ui.futaber.FutaberMediaServices
import com.valoser.futacha.shared.ui.futaber.FutaberTheme
import com.valoser.futacha.shared.ui.futaber.FutaberThemeMode
import com.valoser.futacha.shared.ui.futaber.FutaberThreadRef
import com.valoser.futacha.shared.ui.futaber.FutaberThreadScreen
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.util.createFileSystem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.io.IOException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * L-B7「開く・更新・タップ」 (look doc §1.2 S22・G21・F03・G20, §1.3 S26) on the real FutaberApp: a catalog row loads its
 * thread under "Loading..." before it opens (one fetch; back and another row cancel it; a failure still opens the
 * thread for its fallbacks), the pull at the end refreshes once (not a fling), 「新着 N」 follows a refresh and a tab's
 * opening, a tap on the lower third pages down without any setting, reopening comes back to where reading stopped, and
 * the search bubble closes on a refresh. The board has may's address but every page comes from a local fake repository
 * (and MockEngine answers 404 to anything else), so nothing reaches a server.
 */
class FutaberOpenRefreshInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var appState: AppStateStore
    private lateinit var loader: ImageLoader
    private val database = "futaber_open_refresh_ui_test.db"
    private val may = BoardSummary("may", "may/b", "", "https://may.2chan.net/b/futaba.php", "")
    private val client = HttpClient(MockEngine { respond("", HttpStatusCode.NotFound) })

    /** Every fetch of a thread, in order; the gate holds them while closed; a thread in [failing] fails once. */
    private val fetches: MutableList<String> = Collections.synchronizedList(mutableListOf())
    @Volatile private var gate = CompletableDeferred<Unit>().apply { complete(Unit) }
    @Volatile private var failNext = false
    private val sizes = ConcurrentHashMap<String, Int>()

    private fun page(threadId: String, size: Int) = ThreadPage(threadId, null, "12:34頃消えます", null, (0 until size).map { index ->
        Post("${threadId.toLong() * 100 + index}", author = null, subject = null, timestamp = "",
            messageHtml = "thread-$threadId row-$index<br>local line two<br>local line three",
            imageUrl = null, thumbnailUrl = null)
    })

    /** Three local threads (the bundled tutorial catalog has one). */
    private val catalog = listOf("4100", "4200", "4300").map { id ->
        com.valoser.futacha.shared.model.CatalogItem(id, "https://may.2chan.net/b/res/$id.htm", "スレ$id", null, null, replyCount = 19)
    }
    private val repository: BoardRepository = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getCatalogPageWithSettings(
            board: String, mode: com.valoser.futacha.shared.model.CatalogMode,
            settings: com.valoser.futacha.shared.model.CatalogFetchSettings
        ) = com.valoser.futacha.shared.model.CatalogPageContent(catalog)
        override suspend fun getThreadContent(board: String, threadId: String): ThreadPageContent {
            fetches += threadId
            gate.await()
            if (failNext) { failNext = false; throw IOException("timeout (test)") }
            // Each fetch after the first brings two more posts (「新着 2」).
            val size = sizes.compute(threadId) { _, old -> if (old == null) 20 else old + 2 }!!
            return ThreadPageContent(page(threadId, size))
        }
    }

    @Before fun prepare() {
        rule.activity.deleteDatabase(database)
        store = AndroidCompatibilityStore(rule.activity, databaseName = database)
        appState = AppStateStore(BaseInMemoryPlatformStateStorage())
        runBlocking {
            store.initialize()
            appState.setBoards(listOf(may))
            appState.setHistory(emptyList())
        }
        loader = ImageLoader(rule.activity)
    }

    @After fun cleanup() {
        gate.complete(Unit)
        rule.runOnUiThread { rule.activity.setContent {} }
        rule.waitForIdle()
        loader.shutdown()
        client.close()
        runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase(database)
    }

    private fun host(preferences: Map<String, String> = emptyMap(), theme: String = "light") {
        runBlocking {
            store.savePreferences(preferences + ("compat.futaber.lastBoardId" to "may") + ("compat.futaber.theme" to theme) +
                ("compat.futaber.catalog.displayStyle" to "row1"))
        }
        val services = FutaberMediaServices(store, client, createFileSystem(rule.activity), null)
        rule.setContent {
            val boards by appState.boards.collectAsState(emptyList())
            val history by appState.history.collectAsState(emptyList())
            val prefs by store.preferences.collectAsState(emptyMap())
            val ngRules by store.ngRules.collectAsState(emptyList())
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                if (boards.isNotEmpty() && prefs["compat.futaber.lastBoardId"] != null) FutaberApp(
                    boards = boards, history = history, repository = repository, stateStore = appState,
                    preferences = prefs, ngRules = ngRules,
                    savePreference = { key, value -> runBlocking { store.savePreference(key, value) } },
                    mediaServices = services, appVersion = "test"
                )
            }
        }
        waitForTag("futaber-catalog-item")
        rule.waitForIdle()
    }

    private fun present(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun waitForTag(tag: String, timeoutMillis: Long = 20_000) = rule.waitUntil(timeoutMillis) { present(tag) }
    private fun waitForGone(tag: String, timeoutMillis: Long = 20_000) = rule.waitUntil(timeoutMillis) { !present(tag) }
    private fun textShown(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun row(index: Int) = rule.onAllNodesWithTag("futaber-catalog-item")[index]
    /** A post's text in the thread's list (the title row above it shows the first post's line too). */
    private fun postTexts(text: String) =
        rule.onAllNodes(hasText(text, substring = true) and hasAnyAncestor(hasTestTag("futaber-thread-list"))).fetchSemanticsNodes()
    private fun postShown(text: String) = postTexts(text).isNotEmpty()

    private val density get() = rule.density.density

    private fun shot(name: String) {
        rule.waitForIdle()
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/futaber-open-refresh-$name.png")
        java.io.FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
        pfd.close()
    }

    private fun openRowAndWait(index: Int) {
        row(index).performClick()
        waitForTag("futaber-post")
        rule.waitForIdle()
    }

    private fun listScroll(): Float {
        rule.waitForIdle()
        return rule.onNodeWithTag("futaber-thread-list").fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.value?.invoke() ?: 0f
    }

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    private fun backToCatalog() {
        rule.onNodeWithTag("futaber-thread-title").performCustomAccessibilityActionWithLabel("カタログへ戻る")
        waitForTag("futaber-catalog-item")
        rule.waitForIdle()
    }

    /** S22: the pressed row is blue (white text) under "Loading..." while the thread loads; it opens with that one fetch. */
    @Test fun aRowIsBlueUnderLoadingThenItsThreadOpensWithOneFetch() {
        host()
        gate = CompletableDeferred()
        row(0).performClick()
        waitForTag("futaber-hud")
        rule.waitUntil(5_000) { fetches.size == 1 }
        val opened = fetches.single()
        assertFalse("the thread waits for its page", present("futaber-thread-title"))
        rule.onAllNodesWithTag("futaber-catalog-item")[0].assert(isSelected())
        val bounds = rule.onAllNodesWithTag("futaber-catalog-item").fetchSemanticsNodes()[0].boundsInRoot
        val count = rule.onAllNodesWithTag("futaber-catalog-item-count", useUnmergedTree = true).fetchSemanticsNodes()[0].boundsInRoot
        // The press's ripple fades first.
        fun groundOf(image: androidx.compose.ui.graphics.PixelMap) =
            image[(count.left - 4f * density).toInt(), (bounds.top + 3f * density).toInt()].let {
                Triple((it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt())
            }
        fun isBlue(rgb: Triple<Int, Int, Int>) = abs(rgb.first - 0x2F) <= 6 && abs(rgb.second - 0x70) <= 6 && abs(rgb.third - 0xE1) <= 6
        var image = rule.onRoot().captureToImage().toPixelMap()
        val until = android.os.SystemClock.uptimeMillis() + 5_000
        while (!isBlue(groundOf(image)) && android.os.SystemClock.uptimeMillis() < until) {
            Thread.sleep(100); image = rule.onRoot().captureToImage().toPixelMap()
        }
        assertTrue("the row is futaber's blue #2F70E1: ${groundOf(image)}", isBlue(groundOf(image)))
        // The count is white on it (not red).
        var whitest = 0f
        for (y in count.top.toInt() until count.bottom.toInt()) for (x in count.left.toInt() until count.right.toInt()) {
            val p = image[x, y]
            whitest = maxOf(whitest, minOf(p.red, p.green, p.blue))
        }
        assertTrue("the count is white on the blue row: $whitest", whitest > 0.9f)
        shot("pressed-row-hud-light")
        gate.complete(Unit)
        waitForTag("futaber-post")
        waitForGone("futaber-hud")
        rule.waitForIdle()
        assertTrue(postShown("thread-$opened row-0"))
        // The page loaded before opening stood for the thread's first fetch: no second one.
        Thread.sleep(500)
        rule.waitForIdle()
        assertEquals(listOf(opened), fetches.toList())
        assertFalse("opened from the catalog: no 「新着 N」", present("futaber-toast"))
    }

    /** S22: back cancels the load (the row no longer blue, nothing opens); another row replaces the first. */
    @Test fun backCancelsTheLoadAndAnotherRowReplacesIt() {
        host()
        gate = CompletableDeferred()
        row(0).performClick()
        waitForTag("futaber-hud")
        androidx.test.espresso.Espresso.pressBack()
        waitForGone("futaber-hud")
        rule.onAllNodesWithTag("futaber-catalog-item")[0].assert(androidx.compose.ui.test.isNotSelected())
        gate.complete(Unit)
        Thread.sleep(500)
        rule.waitForIdle()
        assertFalse("a cancelled load opens nothing", present("futaber-thread-title"))
        assertTrue(present("futaber-catalog-item"))
        // Another row while one loads: the second one opens.
        gate = CompletableDeferred()
        fetches.clear()
        row(0).performClick()
        waitForTag("futaber-hud")
        row(1).performClick()
        rule.waitUntil(5_000) { fetches.size == 2 }
        val (first, second) = fetches.toList()
        rule.onAllNodesWithTag("futaber-catalog-item")[1].assert(isSelected())
        rule.onAllNodesWithTag("futaber-catalog-item")[0].assert(androidx.compose.ui.test.isNotSelected())
        gate.complete(Unit)
        waitForTag("futaber-post")
        rule.waitForIdle()
        assertTrue("the second row's thread", postShown("thread-$second row-0"))
        assertFalse(postShown("thread-$first row-0"))
    }

    /** S22: a failed load (a timeout here) still opens the thread, which handles it as today, without fetching again. */
    @Test fun aFailedLoadStillOpensTheThreadForItsFallbacks() {
        host()
        failNext = true
        row(0).performClick()
        waitForTag("futaber-thread-title")
        rule.waitUntil(10_000) { textShown("再読み込み") }
        assertEquals("the failure was handed to the thread, not fetched twice", 1, fetches.size)
        rule.onNodeWithText("再読み込み").performClick()
        waitForTag("futaber-post")
        assertEquals(2, fetches.size)
    }

    /** G21・F03・F22: the end row is always there; pulling up past it refreshes once and 「新着 2」 shows; a fling does not. */
    @Test fun pullingUpAtTheEndRefreshesOnceAndAFlingDoesNot() {
        host()
        openRowAndWait(0)
        val list = rule.onNodeWithTag("futaber-thread-list")
        // A fling from the middle that runs into the end: no refresh.
        list.performScrollToIndex(4)
        rule.waitForIdle()
        list.performTouchInput { swipe(Offset(centerX, height * 0.7f), Offset(centerX, height * 0.35f), durationMillis = 40) }
        rule.waitForIdle()
        Thread.sleep(800)
        rule.waitForIdle()
        list.performScrollToNode(hasTestTag("futaber-thread-pull"))
        rule.waitForIdle()
        Thread.sleep(600)
        assertEquals("a fling stopping at the end does not refresh", 1, fetches.size)
        // The end: the expiry and 「引っ張って...」 on the grey footer.
        rule.onNodeWithTag("futaber-thread-expiry", useUnmergedTree = true).assertExists()
        val pull = rule.onNodeWithTag("futaber-thread-pull", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals("引っ張って...", pull.config.getOrNull(SemanticsProperties.Text)?.joinToString(""))
        val end = rule.onNodeWithTag("futaber-thread-end").fetchSemanticsNode().boundsInRoot
        assertTrue("the end row holds the 50 high pull row", end.height >= 50f * density)
        shot("thread-end-light")
        // A short pull is not enough.
        list.performTouchInput { swipe(Offset(centerX, height * 0.6f), Offset(centerX, height * 0.6f - 25f * density), durationMillis = 400) }
        rule.waitForIdle()
        Thread.sleep(500)
        assertEquals(1, fetches.size)
        // Pulling up well past the end and letting go: one refresh.
        list.performTouchInput { swipe(Offset(centerX, height * 0.8f), Offset(centerX, height * 0.2f), durationMillis = 700) }
        rule.waitUntil(10_000) { fetches.size == 2 }
        waitForTag("futaber-toast")
        assertTrue(textShown("新着 2"))
        shot("new-count-light")
        Thread.sleep(800)
        assertEquals("once per pull", 2, fetches.size)
        // The box goes away by itself.
        waitForGone("futaber-toast", 8_000)
    }

    /** F03: the refresh button says 「新着 2」; opening a thread from the tab strip says what came since it was read. */
    @Test fun theRefreshButtonAndATabsOpeningTellTheNewPosts() {
        host()
        openRowAndWait(0)
        val a = fetches.single()
        rule.onNodeWithTag("futaber-thread-refresh").performClick()
        rule.waitUntil(10_000) { fetches.size == 2 }
        waitForTag("futaber-toast")
        assertTrue(textShown("新着 2"))
        // The toast sits at the right, above the tab strip and the bar.
        val toast = rule.onNodeWithTag("futaber-toast").fetchSemanticsNode().boundsInRoot
        val bar = rule.onNodeWithTag("futaber-toolbar").fetchSemanticsNode().boundsInRoot
        assertTrue("right side", toast.center.x > bar.center.x)
        assertTrue("above the bar", toast.bottom <= bar.top)
        waitForGone("futaber-toast", 8_000)
        // Read to its end (22 posts seen), keep it as a tab, open another thread, then the tab.
        rule.onNodeWithTag("futaber-thread-bottom").performClick()
        rule.waitForIdle()
        Thread.sleep(1_200) // the seen count is written once the reading position settles (500 ms)
        rule.onNodeWithTag("futaber-manage").performTouchInput { longClick() }
        waitForTag("futaber-tab")
        backToCatalog()
        openRowAndWait(1)
        assertFalse(present("futaber-toast"))
        val tab = rule.onAllNodesWithTag("futaber-tab").fetchSemanticsNodes()
        assertEquals(1, tab.size)
        rule.onAllNodesWithTag("futaber-tab")[0].performClick()
        rule.waitUntil(10_000) { fetches.count { it == a } == 3 }
        waitForTag("futaber-toast")
        assertTrue("24 fetched, 22 seen", textShown("新着 2"))
    }

    /** G20: with nothing set, a tap on the lower third pages down; an explicit OFF leaves the list still. */
    @Test fun aLowerThirdTapPagesDownWithNothingSet() {
        host()
        openRowAndWait(0)
        val list = rule.onNodeWithTag("futaber-thread-list")
        list.performScrollToIndex(0)
        val start = listScroll()
        list.performTouchInput { click(Offset(width - 4f, height * 0.9f)) }
        rule.waitUntil(5_000) { listScroll() > start + height(list) * 0.5f }
        val down = listScroll()
        list.performTouchInput { click(Offset(width - 4f, height * 0.5f)) }
        rule.waitForIdle()
        assertEquals("the middle does nothing", down, listScroll(), 1f)
        runBlocking { store.savePreference("compat.futaber.tapScroll", "OFF") }
        rule.waitForIdle()
        Thread.sleep(300)
        list.performTouchInput { click(Offset(width - 4f, height * 0.9f)) }
        rule.waitForIdle()
        Thread.sleep(500)
        assertEquals("turned off: nothing", down, listScroll(), 1f)
    }

    private fun height(node: androidx.compose.ui.test.SemanticsNodeInteraction) = node.fetchSemanticsNode().boundsInRoot.height

    /** S26: reopening a thread from the catalog comes back to where reading stopped (not near the end, futaber's slip). */
    @Test fun reopeningAThreadComesBackToWhereReadingStopped() {
        host()
        openRowAndWait(0)
        val a = fetches.single()
        rule.onNodeWithTag("futaber-thread-list").performScrollToIndex(12)
        rule.waitForIdle()
        rule.waitUntil(10_000) {
            runBlocking { appState.history.first() }.firstOrNull { it.threadId == a }?.lastReadItemIndex == 12
        }
        backToCatalog()
        row(0).performClick()
        waitForTag("futaber-post")
        rule.waitUntil(10_000) { postShown("thread-$a row-12") }
        rule.waitForIdle()
        assertEquals("one fetch per opening", 2, fetches.size)
        val firstShown = postTexts("thread-$a row-")
            .filter { it.boundsInRoot.bottom > rule.onNodeWithTag("futaber-thread-list").fetchSemanticsNode().boundsInRoot.top + 1f }
            .minByOrNull { it.boundsInRoot.top }?.config?.getOrNull(SemanticsProperties.Text)?.joinToString("").orEmpty()
        assertTrue("the first post on screen is row-12: $firstShown", firstShown.contains("row-12"))
    }

    /** Pictures only (build/l-b7): the pressed row under Loading..., the end row and 「新着 N」 in the dark theme. */
    @Test fun captureTheOpenAndRefreshInTheDarkForReview() {
        host(theme = "dark")
        gate = CompletableDeferred()
        row(0).performClick()
        waitForTag("futaber-hud")
        Thread.sleep(1_200) // the press's ripple
        shot("pressed-row-hud-dark")
        gate.complete(Unit)
        waitForTag("futaber-post")
        rule.onNodeWithTag("futaber-thread-list").performScrollToNode(hasTestTag("futaber-thread-pull"))
        rule.waitForIdle()
        Thread.sleep(800)
        shot("thread-end-dark")
        rule.onNodeWithTag("futaber-thread-refresh").performClick()
        waitForTag("futaber-toast")
        shot("new-count-dark")
    }

    /** L §8.5: the search word's bubble (L-B6) closes when the thread is refreshed, as the other bubbles do. */
    @Test fun theSearchBubbleClosesOnARefresh() {
        var signal by androidx.compose.runtime.mutableIntStateOf(0)
        val board = BoardSummary("bubble", "bubble", "", "https://may.2chan.net/b/futaba.php", "")
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                FutaberTheme(FutaberThemeMode.Light) {
                    FutaberThreadScreen(board, FutaberThreadRef(board.id, "7", "bubble", "", 20), repository,
                        isTabbed = false, onToggleTab = {}, onOpenManage = {}, onOpenPost = {}, refreshSignal = signal,
                        quoteMode = false, onQuoteLine = {}, onOpenDrawer = {}, drawerGestureEnabled = false,
                        ngWords = emptyList(), ngHeaders = emptyList(), ngRules = emptyList(), onChangeNgWords = {},
                        onChangeNgHeaders = {}, onPageLoaded = {}, onScrollSettled = { _, _, _, _ -> }, onBack = {})
                }
            }
        }
        waitForTag("futaber-post")
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        waitForTag("futaber-operation-menu")
        rule.onNodeWithTag("futaber-menu-search").performClick()
        rule.onNodeWithTag("futaber-menu-search").performTextInput("row-3")
        rule.onNodeWithTag("futaber-menu-search").performImeAction()
        waitForTag("futaber-quote-card")
        rule.runOnUiThread { signal += 1 }
        waitForGone("futaber-quote-card")
        rule.waitUntil(10_000) { fetches.size == 2 }
    }
}
