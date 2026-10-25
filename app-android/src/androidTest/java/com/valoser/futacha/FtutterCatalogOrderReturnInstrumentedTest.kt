@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.*
import org.junit.Assert.*

/** Real catalog callbacks dispose/recreate the screen as thread/settings children do; all data is local. */
class FtutterCatalogOrderReturnInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val a = BoardSummary("m69-a", "M69 board A", "test", "https://may.2chan.net/b/", "")
    private val b = a.copy(id = "m69-b", name = "M69 board B", url = "https://img.2chan.net/b/")
    private var board by mutableStateOf(a)
    private var holder by mutableStateOf(FtutterCatalogHolder())
    private var child by mutableStateOf<String?>(null)
    private var preferences by mutableStateOf(mapOf(FtutterKeys.DEFAULT_ORDER_MODE to "0", FtutterKeys.PREVIOUS_ORDER_MODE to "2"))
    private val calls = CopyOnWriteArrayList<Pair<String, CatalogMode>>()
    private lateinit var loader: ImageLoader
    private val repository = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getCatalogPage(board: String, mode: CatalogMode): CatalogPageContent {
            calls += board to mode
            return CatalogPageContent((1..100).map { n -> CatalogItem(id = "$n", threadUrl = "${board}res/$n.htm",
                title = "M69 item $n", thumbnailUrl = null, fullImageUrl = null, replyCount = n) })
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
                    if (child == null) FtutterCatalogScreen(board, listOf(a, b), repository, emptyList(), preferences, holder,
                        savePreference = { k, v -> preferences = preferences + (k to v) },
                        editPreference = { k, edit -> edit(preferences[k])?.let { preferences = preferences + (k to it) } },
                        onBack = {}, onSwitchBoard = { board = it }, onOpenSettings = { child = "settings" },
                        onOpenThread = { child = "thread" }, onOpenHiddenAndNg = {}, onOpenUrl = {}, onDeleteHistory = {}, onNotice = {},
                        columnsOverride = 3)
                    else Column {
                        Text("M69 $child")
                        if (child == "settings") Button(onClick = { preferences = preferences + (FtutterKeys.DEFAULT_ORDER_MODE to "2") }) { Text("M69 set default OLD") }
                        Button(onClick = { child = null }) { Text("M69 return") }
                    }
                }
            }
        }
        awaitLoaded(1)
    }
    private fun awaitLoaded(count: Int) {
        rule.waitUntil(10_000) { calls.size >= count && holder.load?.loading == false && child == null }
        assertEquals(count, calls.size)
    }
    private fun choose(order: FtutterOrder, expectedCalls: Int) {
        rule.onNodeWithTag("ftutter-catalog-order").performScrollTo().performClick()
        rule.onNodeWithTag("ftutter-order-${order.name}").performClick()
        awaitLoaded(expectedCalls)
    }
    private fun returnFromChild() {
        rule.onNodeWithText("M69 return").performClick()
        rule.waitForIdle()
    }
    @Test fun aFixedDefaultDoesNotReplaceTheChosenOrderWhenReturningFromAThread() {
        start(); choose(FtutterOrder.NEW, 2)
        rule.onNodeWithTag("ftutter-catalog-grid").performScrollToIndex(30)
        rule.waitForIdle()
        val position = holder.gridState.firstVisibleItemIndex to holder.gridState.firstVisibleItemScrollOffset
        rule.onNodeWithText("M69 item 31").performTouchInput { click(center) }
        rule.onNodeWithText("M69 thread").assertExists()
        returnFromChild()
        rule.onNodeWithTag("ftutter-catalog-order").assertTextContains("新順")
        assertTrue(holder.load!!.key.endsWith("|NEW"))
        assertEquals(2, calls.size)
        assertEquals(position, holder.gridState.firstVisibleItemIndex to holder.gridState.firstVisibleItemScrollOffset)
    }
    @Test fun aChangedDefaultWaitsForANewSessionWhileSettingsAndBoardMovesRetainTheCurrentOrder() {
        start(); choose(FtutterOrder.NEW, 2)
        rule.onNodeWithTag("ftutter-board-select").performClick()
        rule.onNodeWithText(b.name).performClick(); awaitLoaded(3)
        assertEquals(b.url to CatalogMode.New, calls.last())
        rule.onNodeWithContentDescription("設定").performClick()
        rule.onNodeWithText("M69 set default OLD").performClick()
        returnFromChild()
        rule.onNodeWithTag("ftutter-catalog-order").assertTextContains("新順")
        assertEquals(3, calls.size)
        // A new app/holder session applies the new startup default, while the persisted previous choice stays NEW.
        rule.runOnUiThread { child = "restart" }; rule.waitForIdle()
        rule.runOnUiThread { holder = FtutterCatalogHolder(); child = null }; awaitLoaded(4)
        rule.onNodeWithTag("ftutter-catalog-order").assertTextContains("古順")
        assertEquals(b.url to CatalogMode.Old, calls.last())
        assertEquals("1", preferences[FtutterKeys.PREVIOUS_ORDER_MODE])
    }
    @Test fun previousOrderSettingStillStartsFromTheSavedChoiceAndRetainsLaterSelections() {
        preferences = preferences + (FtutterKeys.DEFAULT_ORDER_MODE to FtutterOrder.PREVIOUS.toString())
        start()
        rule.onNodeWithTag("ftutter-catalog-order").assertTextContains("古順")
        choose(FtutterOrder.MANY, 2)
        rule.onNodeWithContentDescription("設定").performClick()
        returnFromChild()
        rule.onNodeWithTag("ftutter-catalog-order").assertTextContains("多順")
        assertEquals(2, calls.size)
        assertEquals("3", preferences[FtutterKeys.PREVIOUS_ORDER_MODE])
    }
}
