@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

/** Actual catalog effects and sort controls, with no network or audio. */
class FtutterM30CatalogRefreshInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val a = BoardSummary("m30-a", "板A", "検証", "https://may.2chan.net/b/", "")
    private val b = a.copy(id = "m30-b", name = "板B", url = "https://img.2chan.net/b/")
    private var board by mutableStateOf(a)
    private var shown by mutableStateOf(true)
    private val holder = FtutterCatalogHolder()
    private val calls = CopyOnWriteArrayList<Pair<String, CatalogMode>>()
    private lateinit var loader: ImageLoader
    @Volatile private var holdSecondFetch = false
    private val repository = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getCatalogPage(board: String, mode: CatalogMode): CatalogPageContent {
            calls += board to mode
            if (holdSecondFetch && calls.size == 2) kotlinx.coroutines.awaitCancellation()
            return CatalogPageContent(listOf(CatalogItem(
                id = "123", title = "M30-${calls.size}", threadUrl = "${board}res/123.htm",
                thumbnailUrl = null, fullImageUrl = null, replyCount = calls.size
            )))
        }
    }

    private fun start() {
        loader = ImageLoader.Builder(rule.activity).build()
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                MaterialTheme { FtutterTheme(null, FtutterBrightness.LIGHT) {
                    if (shown) FtutterCatalogScreen(
                        board, listOf(a, b), repository, emptyList(), emptyMap(), holder,
                        { _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, columnsOverride = 3
                    )
                } }
            }
        }
        awaitCalls(1)
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        rule.waitForIdle()
        if (::loader.isInitialized) loader.shutdown()
    }

    private fun awaitCalls(expected: Int) {
        rule.waitUntil(10_000) { calls.size >= expected && holder.load?.loading == false }
        assertEquals(expected, calls.size)
    }
    private fun selectOrder(order: FtutterOrder) {
        rule.onNodeWithTag("ftutter-catalog-order").performClick()
        rule.onNodeWithTag("ftutter-order-${order.name}").performClick()
    }

    @Test fun boardAndSortChangesRefetchEvenIfTheirCatalogWasAlreadyCached() {
        start()
        rule.runOnUiThread { board = b }; awaitCalls(2)
        rule.runOnUiThread { board = a }; awaitCalls(3)
        selectOrder(FtutterOrder.NEW); awaitCalls(4)
        selectOrder(FtutterOrder.CATALOG); awaitCalls(5)
        assertEquals(listOf(a.url, b.url, a.url, a.url, a.url), calls.map { it.first })
        assertEquals(listOf(CatalogMode.Catalog, CatalogMode.Catalog, CatalogMode.Catalog, CatalogMode.New, CatalogMode.Catalog), calls.map { it.second })
    }

    @Test fun returningFromAThreadRetainsTheCatalogAndManualRefreshStillWorks() {
        start()
        // A thread/settings child returning to the same catalog retains its snapshot and position.
        rule.runOnUiThread { shown = false }; rule.waitForIdle()
        rule.runOnUiThread { shown = true }; rule.waitForIdle()
        assertEquals(1, calls.size)
        rule.runOnUiThread { holder.refreshTick++ }; awaitCalls(2)
        assertEquals(listOf(a.url, a.url), calls.map { it.first })
    }

    /** L144: leaving while a fetch runs must not leave the catalog 「取得中」 when returning. */
    @Test fun leavingDuringAFetchFetchesAgainOnReturnInsteadOfStayingLoading() {
        holdSecondFetch = true
        start()
        rule.runOnUiThread { holder.refreshTick++ }
        rule.waitUntil(10_000) { calls.size == 2 && holder.load?.loading == true }
        rule.runOnUiThread { shown = false }; rule.waitForIdle()
        rule.runOnUiThread { shown = true }
        awaitCalls(3)
        assertEquals(false, holder.load?.loading)
    }
}
