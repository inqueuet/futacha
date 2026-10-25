@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@file:OptIn(coil3.annotation.ExperimentalCoilApi::class)
package com.valoser.futacha

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import coil3.ImageLoader
import coil3.network.NetworkFetcher
import coil3.network.ktor3.asNetworkClient
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.*
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.futaber.*
import com.valoser.futacha.shared.ui.image.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import org.junit.*
import org.junit.Assert.*

/** Only local page fixtures and a MockEngine image loader; no real server, saving or playback. */
class FutaberThreadImageTapInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val board = BoardSummary("image-tap", "Image tap board", "test", "https://may.2chan.net/b/", "")
    private val db = "audit_m41_image_tap.db"
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var loader: ImageLoader
    private lateinit var client: HttpClient
    private fun post(id: Int, image: Boolean = false) = Post("$id", author = null, subject = null, timestamp = "",
        messageHtml = "body-$id", imageUrl = if (image) "https://may.2chan.net/b/src/$id.jpg" else null,
        thumbnailUrl = if (image) "https://may.2chan.net/b/thumb/${id}s.jpg" else null)
    private val posts = listOf(post(100, true), post(101).copy(messageHtml = "&gt;No.100<br>body-101",
        quoteReferences = listOf(QuoteReference(">No.100", listOf("100")))), post(102, true)) + (103..135).map { post(it) }
    private val page = ThreadPage("100", null, null, null, posts)
    private val repo = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getThreadContent(board: String, threadId: String) = ThreadPageContent(page)
    }
    @Before fun prepare() {
        rule.activity.deleteDatabase(db)
        store = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking { store.initialize() }
        val bytes = ByteArrayOutputStream().also { out -> Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.BLUE); compress(Bitmap.CompressFormat.JPEG, 90, out); recycle()
        } }.toByteArray()
        client = HttpClient(MockEngine { respond(bytes, headers = headersOf(HttpHeaders.ContentType, "image/jpeg")) })
        loader = ImageLoader.Builder(rule.activity).diskCache(null).components {
            add(NetworkFetcher.Factory(networkClient = { client.asNetworkClient() }))
        }.build()
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle()
        loader.shutdown(); client.close(); runBlocking { store.closeForTest() }; rule.activity.deleteDatabase(db)
    }
    private fun open(withServices: Boolean = true) {
        val services = if (withServices) FutaberMediaServices(store, client, null, null) else null
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                FutaberTheme(FutaberThemeMode.Light) {
                    FutaberThreadScreen(board, FutaberThreadRef(board.id, "100", "image-tap", "", posts.size), repo,
                        isTabbed = false, onToggleTab = {}, onOpenManage = {}, onOpenPost = {}, refreshSignal = 0,
                        quoteMode = false, onQuoteLine = {}, onOpenDrawer = {}, drawerGestureEnabled = false,
                        onPageLoaded = {}, onScrollSettled = { _, _, _, _ -> }, ngWords = emptyList(), ngHeaders = emptyList(),
                        ngRules = emptyList(), onChangeNgWords = {}, onChangeNgHeaders = {}, onBack = {}, mediaServices = services)
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-post-image").fetchSemanticsNodes().size >= 2 }
    }
    @Test fun tappingTheSecondThumbnailOpensItsOwnViewerPage() {
        open()
        rule.onAllNodesWithTag("futaber-post-image")[1].performTouchInput { click(center) }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-viewer").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("futaber-viewer-counter").assertTextEquals("2 of 2")
        rule.onNodeWithTag("futaber-viewer-info").assert(hasAnyDescendant(hasText("body-102")))
    }
    @Test fun aThumbnailLongPressKeepsTheImageMenuAndDoesNotOpenTheViewer() {
        open()
        rule.onAllNodesWithTag("futaber-post-image")[0].performTouchInput { longClick(center) }
        rule.onNodeWithText("レス:0の画像").assertIsDisplayed()
        rule.onNodeWithText("画像検索").assertIsDisplayed()
        rule.onNodeWithTag("futaber-viewer").assertDoesNotExist()
    }
    @Test fun draggingFromAThumbnailScrollsWithoutOpeningTheViewer() {
        open()
        fun scrollPosition() = rule.onNodeWithTag("futaber-thread-list").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        val before = scrollPosition()
        rule.onAllNodesWithTag("futaber-post-image")[0].performTouchInput { swipeUp(durationMillis = 400) }
        rule.waitForIdle()
        rule.onNodeWithTag("futaber-viewer").assertDoesNotExist()
        assertTrue("The scroll must move the list instead of treating the drag as a tap", scrollPosition() > before)
    }
    @Test fun aThumbnailTapWithoutMediaServicesRemainsSafeAndKeepsTheImageMenu() {
        open(withServices = false)
        rule.onAllNodesWithTag("futaber-post-image")[0].performTouchInput { click(center) }
        rule.onNodeWithTag("futaber-viewer").assertDoesNotExist()
        rule.onAllNodesWithTag("futaber-post-image")[0].performTouchInput { longClick(center) }
        rule.onNodeWithText("レス:0の画像").assertIsDisplayed()
    }
    @Test fun quoteTapsAndTheBodyLongPressKeepTheirExistingActions() {
        open()
        rule.onNodeWithTag("futaber-quote-line").performTouchInput { click(center) }
        rule.onNodeWithTag("futaber-quote-close-one").assertIsDisplayed().performClick()
        rule.onNodeWithText("body-100").performTouchInput { longClick(center) }
        rule.onNodeWithText("レス:0").assertIsDisplayed()
        rule.onNodeWithTag("futaber-viewer").assertDoesNotExist()
    }

    /** L145: a picture inside a quote bubble opens the viewer on tap and the picture menu (over the bubble) on long press. */
    @Test fun aPictureInsideAQuoteBubbleOpensTheViewerAndItsMenu() {
        open()
        rule.onNodeWithTag("futaber-quote-line").performTouchInput { click(center) }
        val inBubble = hasTestTag("futaber-post-image") and hasAnyAncestor(hasTestTag("futaber-quote-card"))
        rule.waitUntil(5_000) { rule.onAllNodes(inBubble).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(inBubble)[0].performTouchInput { longClick(center) }
        rule.onNodeWithText("レス:0の画像").assertIsDisplayed()
        rule.onNodeWithText("キャンセル").performClick()
        rule.onAllNodes(inBubble)[0].performTouchInput { click(center) }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-viewer").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("futaber-viewer-counter").assertTextEquals("1 of 2")
        rule.onNodeWithTag("futaber-viewer-info").assert(hasAnyDescendant(hasText("body-100")))
    }
}
