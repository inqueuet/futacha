@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import coil3.ImageLoader
import coil3.network.NetworkFetcher
import coil3.network.ktor3.asNetworkClient
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogFetchSettings
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.model.CatalogPageContent
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.futaber.FutaberCatalogDisplayStyle
import com.valoser.futacha.shared.ui.futaber.FutaberCatalogScreen
import com.valoser.futacha.shared.ui.futaber.FutaberCatalogSearchState
import com.valoser.futacha.shared.ui.futaber.FutaberCatalogViewState
import com.valoser.futacha.shared.ui.futaber.FutaberTheme
import com.valoser.futacha.shared.ui.futaber.FutaberThemeMode
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import java.io.ByteArrayOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * L-B10 (S25): futaber's search goes back to the top with every thread on 「キャンセル」. The tutorial board has one
 * thread, so this draws the catalog with sixty of its own (local pages, no network).
 */
class FutaberCatalogSearchCancelInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val board = BoardSummary("search", "検索", "test", "https://may.2chan.net/27/futaba.php", "")
    private val items = (1..60).map { CatalogItem("${2000 + it}", "https://may.2chan.net/27/res/${2000 + it}.htm", "ねこのスレ$it", null, null, replyCount = it) }
    private lateinit var loader: ImageLoader
    private lateinit var client: HttpClient
    private val repository: BoardRepository = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getCatalogPage(board: String, mode: CatalogMode) = CatalogPageContent(items)
        override suspend fun getCatalogPageWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings) = CatalogPageContent(items)
    }
    private val view = FutaberCatalogViewState()
    private val search = FutaberCatalogSearchState()

    @Before fun prepare() {
        val bytes = ByteArrayOutputStream().also { out -> Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.GRAY); compress(Bitmap.CompressFormat.PNG, 100, out); recycle()
        } }.toByteArray()
        client = HttpClient(MockEngine { respond(bytes, headers = headersOf(HttpHeaders.ContentType, "image/png")) })
        loader = ImageLoader.Builder(rule.activity).diskCache(null).components {
            add(NetworkFetcher.Factory(networkClient = { client.asNetworkClient() }))
        }.build()
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                FutaberTheme(FutaberThemeMode.Light) {
                    FutaberCatalogScreen(board, repository, FutaberCatalogDisplayStyle.Row1, CatalogMode.Catalog, {}, {}, {}, {}, {}, {}, 0,
                        onOpenThread = {}, view = view, search = search)
                }
            }
        }
    }

    @After fun cleanup() {
        loader.shutdown(); client.close()
    }

    @Test fun cancelGoesBackToTheTopWithEveryThread() {
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("futaber-catalog-item").fetchSemanticsNodes().isNotEmpty() }
        repeat(3) { rule.onNodeWithTag("futaber-catalog-list").performTouchInput { swipeUp() } }
        rule.waitForIdle()
        assertTrue("the list is scrolled down (${view.listState.firstVisibleItemIndex})", view.listState.firstVisibleItemIndex > 5)
        rule.onNodeWithTag("futaber-search").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-catalog-search-blank").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("futaber-search-field").performTextInput("ねこ")
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-catalog-item").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("futaber-search-cancel").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-search-field").fetchSemanticsNodes().isEmpty() }
        rule.waitUntil(10_000) { view.listState.firstVisibleItemIndex == 0 && view.listState.firstVisibleItemScrollOffset == 0 }
        assertEquals(0, view.listState.firstVisibleItemIndex)
        assertEquals("", search.query)
    }
}
