@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.board.viewedThreadFirstKey
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import org.junit.*
import org.junit.Assert.*

/** Real catalog, toolbar/search/scroll and callback; repository success/failure is entirely local. */
class FtutterCatalogFailureInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val board = BoardSummary("m68", "M68 catalog", "test", "https://may.2chan.net/b/", "")
    private val holder = FtutterCatalogHolder()
    private val items = (1..180).map { n -> CatalogItem(id = "$n", title = "keep-M68-$n", threadUrl = "${board.url}res/$n.htm",
        thumbnailUrl = null, fullImageUrl = null, replyCount = n) }
    private val calls = CopyOnWriteArrayList<CatalogMode>()
    private val notices = CopyOnWriteArrayList<String>()
    @Volatile private var failure: Throwable? = null
    @Volatile private var gate: CompletableDeferred<Unit>? = null
    private var notice by mutableStateOf("")
    private var preferences by mutableStateOf(mapOf(viewedThreadFirstKey(board.url) to "ON"))
    private lateinit var loader: ImageLoader
    private val repository = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getCatalogPage(board: String, mode: CatalogMode): CatalogPageContent {
            calls += mode
            gate?.await()
            failure?.let { throw it }
            return CatalogPageContent(items)
        }
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle()
        if (::loader.isInitialized) loader.shutdown()
    }
    private fun start() {
        loader = ImageLoader.Builder(rule.activity).build()
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                FtutterTheme(null, FtutterBrightness.LIGHT) {
                    Column(Modifier.fillMaxSize()) {
                        Text(notice, Modifier.fillMaxWidth().height(40.dp).testTag("m68-notice"), maxLines = 1)
                        Box(Modifier.weight(1f)) {
                            FtutterCatalogScreen(board = board, boards = listOf(board), repository = repository,
                                history = emptyList(), preferences = preferences, holder = holder,
                                savePreference = { k, v -> preferences = preferences + (k to v) },
                                editPreference = { k, edit -> edit(preferences[k])?.let { preferences = preferences + (k to it) } },
                                onBack = {}, onSwitchBoard = {}, onOpenSettings = {}, onOpenThread = {},
                                onOpenHiddenAndNg = {}, onOpenUrl = {}, onDeleteHistory = {},
                                onNotice = { notices += it; notice = it }, columnsOverride = 3)
                        }
                    }
                }
            }
        }
    }
    private fun awaitLoaded(count: Int) {
        rule.waitUntil(10_000) { calls.size >= count && holder.load?.loading == false }
        assertEquals(count, calls.size)
    }
    private fun refresh() = rule.onNodeWithTag("ftutter-catalog-tool-refresh").performScrollTo().performClick()
    @Test fun refreshingAPopulatedCatalogReportsTheFailureAndKeepsItsSearchSortItemsAndPosition() {
        start(); awaitLoaded(1)
        rule.onNodeWithTag("ftutter-catalog-order").performClick()
        rule.onNodeWithTag("ftutter-order-NEW").performClick(); awaitLoaded(2)
        rule.onNodeWithTag("ftutter-catalog-tool-search").performScrollTo().performClick()
        rule.onNode(hasSetTextAction()).performTextInput("keep")
        rule.waitUntil(5_000) { holder.search(board.id).query == "keep" }
        rule.onNodeWithTag("ftutter-catalog-grid").performScrollToIndex(60)
        rule.waitForIdle()
        val before = holder.gridState.firstVisibleItemIndex to holder.gridState.firstVisibleItemScrollOffset
        val heldItems = holder.load!!.items
        failure = NetworkException("HTTP error 503 at private fixture endpoint", statusCode = 503)
        refresh(); awaitLoaded(3)
        assertEquals(listOf("サーバーが応答していません。しばらくしてからもう一度お試しください（HTTP 503）"), notices.toList())
        rule.onNodeWithTag("m68-notice").assertTextContains("HTTP 503", substring = true)
        assertSame(heldItems, holder.load!!.items)
        assertEquals(before, holder.gridState.firstVisibleItemIndex to holder.gridState.firstVisibleItemScrollOffset)
        assertEquals("keep", holder.search(board.id).query)
        assertTrue(holder.load!!.key.endsWith("|NEW"))
        assertEquals(CatalogMode.New, calls.last())
        rule.mainClock.advanceTimeBy(30_000); rule.waitForIdle(); assertEquals(3, calls.size)
        failure = null; refresh(); awaitLoaded(4)
        assertNull(holder.load!!.error)
        assertEquals(before, holder.gridState.firstVisibleItemIndex to holder.gridState.firstVisibleItemScrollOffset)
    }
    @Test fun anInitialEnglishFailureIsReadableAndOnlyAnExplicitRetryRecoversTheCatalog() {
        failure = IllegalStateException("fixture exploded while reading catalog")
        start(); awaitLoaded(1)
        val inlineError = hasText("カタログを読み込めませんでした") and !hasTestTag("m68-notice")
        rule.onNode(inlineError).assertExists()
        assertEquals("カタログを読み込めませんでした", holder.load!!.error)
        assertTrue(notices.none { it.contains("fixture exploded") })
        rule.mainClock.advanceTimeBy(30_000); rule.waitForIdle(); assertEquals(1, calls.size)
        failure = null; refresh(); awaitLoaded(2)
        assertEquals(items, holder.load!!.items)
        assertNull(holder.load!!.error)
        rule.onNode(inlineError).assertDoesNotExist()
    }
    @Test fun loadingAndFailureKeepTheExistingViewedFirstOrderInsteadOfMovingRows() {
        start(); awaitLoaded(1)
        rule.runOnUiThread { holder.load = holder.load!!.copy(viewedFirstId = "139") }
        gate = CompletableDeferred()
        failure = IllegalStateException("fixture failed")
        refresh()
        rule.waitUntil(5_000) { calls.size == 2 }
        assertEquals("139", holder.load!!.viewedFirstId)
        assertEquals(items, holder.load!!.items)
        gate!!.complete(Unit); awaitLoaded(2)
        assertEquals("139", holder.load!!.viewedFirstId)
        assertEquals(items, holder.load!!.items)
    }
    @Test fun cancellationIsNotPublishedAsALoadErrorAndTheNextExplicitRefreshStillWorks() {
        start(); awaitLoaded(1)
        failure = CancellationException("fixture cancelled")
        refresh()
        rule.waitUntil(5_000) { calls.size == 2 && holder.manualRefreshKey == null }
        assertTrue(notices.isEmpty())
        assertNull(holder.load!!.error)
        assertEquals(items, holder.load!!.items)
        rule.mainClock.advanceTimeBy(30_000); rule.waitForIdle(); assertEquals(2, calls.size)
        failure = null; refresh(); awaitLoaded(3)
        assertNull(holder.load!!.error)
        assertEquals(items, holder.load!!.items)
    }
}
