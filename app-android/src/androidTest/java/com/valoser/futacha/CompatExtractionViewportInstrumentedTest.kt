package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.compat.CompatibilityApp
import com.valoser.futacha.shared.ui.image.*
import kotlinx.coroutines.runBlocking
import org.junit.*

/** Real header long presses build two extraction frames; Back must recover the parent viewport. */
class CompatExtractionViewportInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var loader: ImageLoader
    private lateinit var store: AndroidCompatibilityStore
    private val db = "extraction-viewport-${System.nanoTime()}.db"
    private val board = "https://may.2chan.net/b/"
    private fun post(id: String, body: String, refs: List<QuoteReference> = emptyList(), count: Int = 0) =
        Post(id, author = null, subject = null, timestamp = "", messageHtml = body,
            imageUrl = null, thumbnailUrl = null, quoteReferences = refs, referencedCount = count)
    @Before fun prepare() {
        loader = ImageLoader.Builder(rule.activity).build()
        store = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking {
            store.initialize(); store.savePreference("compat.commonUsedVersion", "1.0")
            store.savePreference("compat.control.controlPostTapBehavior", "legacy")
            store.upsertBoard(CompatBoard(compatBoardKey(board), "fixture", board, board, 0))
        }
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle()
        loader.shutdown(); runBlocking { store.closeForTest() }; rule.activity.deleteDatabase(db)
    }
    @Test fun nestedExtractionStartsAtTheTopAndBackRetainsItsParentsPosition() {
        val parent = (0..17).map { i -> post("${5000+i}", "&gt;No.100<br>extraction-parent-$i<br>extra line",
            listOf(QuoteReference(">No.100", listOf("100"))), if (i == 15) 8 else 0) }
        val child = (0..7).map { i -> post("${10000+i}", "&gt;No.5015<br>extraction-child-$i<br>extra line",
            listOf(QuoteReference(">No.5015", listOf("5015")))) }
        val page = ThreadPage("100", "fixture", null, null, listOf(post("100", "OP", count = 18)) + parent + child)
        val repo = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getThreadByUrl(threadUrl: String) = page
        }
        rule.setContent { MaterialTheme {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                CompatibilityApp(store = store, repository = repo, imageLoader = loader,
                    initialThreadDeepLink = "${board}res/100.htm", onExitApplication = {})
            }
        } }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-thread-post-100").fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasText("No.100", substring = true) and hasAnyAncestor(hasTestTag("compat-thread-post-100")))
            .performTouchInput { longClick() }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("compat-extraction-popup-list").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("compat-extraction-popup-list").performScrollToIndex(15)
        rule.onNodeWithText("extraction-parent-15", substring = true).assertIsDisplayed()
        rule.onNode(hasText("No.5015", substring = true) and hasAnyAncestor(hasTestTag("compat-extraction-popup")))
            .performTouchInput { longClick() }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("返信レス No.5015").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("extraction-child-0", substring = true).assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("返信レス No.100").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("extraction-parent-15", substring = true).assertIsDisplayed()
    }
}
