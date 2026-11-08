@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
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
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/**
 * L-B6「操作メニュー」(docs/futaber-evolution-look-2026-10-31.md §1.2 2・3, §1.3 M10・M11・M15〜M18・M54・V33・V34) on
 * Android with a local page: the original's eleven slots, the card on the bar's top, the veil over the bar, the search
 * word's list and bubble, 「返信が多いレスを表示」's bubble, the small pictures, the MHT alert over the menu, the NG
 * toggle, 新着へスクロール and the two switches of the old behaviour. Nothing reaches a server: the repository is a
 * fake, the pictures come from a MockEngine, and the MHT alert is only cancelled.
 */
class FutaberOperationMenuInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val board = BoardSummary("menu-test", "操作メニュー", "test", "https://may.2chan.net/27/futaba.php", "")
    private val db = "lb6_operation_menu.db"
    private val posts = listOf(
        Post("2001", author = "名無し", subject = "無題", timestamp = "26/10/16(金)21:19:42",
            messageHtml = "最初の本文 ねこ", imageUrl = "https://may.2chan.net/27/src/2001.jpg",
            thumbnailUrl = "https://may.2chan.net/27/thumb/2001s.jpg", thumbnailWidth = 250, thumbnailHeight = 188)
    ) + (2002..2004).map {
        Post("$it", author = "名無し", subject = "無題", timestamp = "26/10/16(金)22:00:00",
            messageHtml = "&gt;最初の本文 ねこ<br>返信$it", imageUrl = null, thumbnailUrl = null,
            quoteReferences = listOf(QuoteReference(">最初の本文 ねこ", listOf("2001"))))
    } + listOf(
        Post("2005", author = "名無し", subject = "無題", timestamp = "26/10/16(金)23:00:00",
            messageHtml = "ネコの話", imageUrl = null, thumbnailUrl = null)
    ) + (2006..2040).map {
        Post("$it", author = "名無し", subject = "無題", timestamp = "26/10/18(日)00:00:00",
            messageHtml = "body-$it", imageUrl = null, thumbnailUrl = null)
    }
    private val page = ThreadPage("2001", null, "28年5月頃消えます", null, posts)
    private val loads = AtomicInteger()
    private val repository = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getThreadContent(board: String, threadId: String): ThreadPageContent {
            loads.incrementAndGet()
            return ThreadPageContent(page)
        }
    }
    private var display by mutableStateOf<FutaberDisplaySettings>(FutaberDisplaySettings().extensionsOff())
    private var themeMode by mutableStateOf(FutaberThemeMode.Light)
    private var ngSettingsOpened = 0
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var loader: ImageLoader
    private lateinit var client: HttpClient
    private val density get() = rule.density.density

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

    private fun present(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    private fun label(tag: String): String =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("").orEmpty()

    private fun open(seenCount: Int = posts.size, withNgSettings: Boolean = false) {
        val services = FutaberMediaServices(store, client, com.valoser.futacha.shared.util.createFileSystem(rule.activity), null)
        rule.setContent {
            CompositionLocalProvider(
                LocalFutaberDisplaySettings provides display,
                LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader
            ) {
                FutaberTheme(themeMode) {
                    Box(Modifier.fillMaxSize()) {
                        FutaberThreadScreen(board, FutaberThreadRef(board.id, "2001", "操作メニュー", "", posts.size, seenCount = seenCount),
                            repository, isTabbed = false, onToggleTab = {}, onOpenManage = {}, onOpenPost = {}, refreshSignal = 0,
                            quoteMode = false, onQuoteLine = {}, onOpenDrawer = {}, drawerGestureEnabled = false,
                            onPageLoaded = {}, onScrollSettled = { _, _, _, _ -> }, ngWords = emptyList(), ngHeaders = emptyList(),
                            ngRules = emptyList(), onChangeNgWords = {}, onChangeNgHeaders = {}, onBack = {},
                            mediaServices = services,
                            onOpenNgSettings = if (withNgSettings) ({ ngSettingsOpened += 1 }) else null)
                    }
                }
            }
        }
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("futaber-post").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openMenu() {
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        rule.waitUntil(5_000) { present("futaber-operation-menu") }
    }

    private fun waitMenuClosed() = rule.waitUntil(5_000) { !present("futaber-operation-menu") }

    private fun capture(name: String) {
        rule.waitForIdle()
        Thread.sleep(800) // the pictures and the veil's fade
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/futaber-lb6-$name.png")
        java.io.FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
        pfd.close()
    }

    /** M20: the original's eleven items in its cells (4 columns, 100 high), the 11th empty, this app's items after them. */
    @Test fun theOriginalsElevenItemsSitInItsCellsAndTheRestFollowInTheGrid() {
        open()
        openMenu()
        val grid = bounds("futaber-menu-grid")
        val cellWidth = grid.width / 4f
        val cellHeight = FUTABER_MENU_CELL_HEIGHT_DP * density
        assertEquals("the grid shows three rows", 3f * cellHeight, grid.height, 1.5f * density)
        FUTABER_MENU_SLOT_IDS.forEachIndexed { index, id ->
            val cell = bounds("futaber-menu-$id")
            assertEquals("$id column", grid.left + (index % 4) * cellWidth, cell.left, 2f)
            assertEquals("$id row", grid.top + (index / 4) * cellHeight, cell.top, 2f)
            rule.onNodeWithTag("futaber-menu-$id").assertIsDisplayed()
        }
        // F-B2: the 12th cell (row 3, column 4) is 「その他の機能」; this app's items are its second step.
        val first = bounds("futaber-menu-more")
        assertEquals(grid.left + 3 * cellWidth, first.left, 2f)
        assertEquals(grid.top + 2 * cellHeight, first.top, 2f)
        rule.onNodeWithTag("futaber-menu-save").assertDoesNotExist()
        // V34: each label is the label itself, broken by its 50 width ("画像を小さ／くする").
        val labels = rule.onAllNodesWithTag("futaber-menu-label", useUnmergedTree = true).fetchSemanticsNodes()
        val small = labels.first { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") == "画像を小さくする" }
        val layout = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        small.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(layout)
        assertEquals(50f * density, layout.single().size.width.toFloat(), 1.5f * density)
        assertEquals(2, layout.single().lineCount)
        assertEquals("画像を小さ", "画像を小さくする".substring(0, layout.single().getLineEnd(0)))
        rule.onNodeWithTag("futaber-menu-more").performClick()
        rule.onNodeWithTag("futaber-menu-live-read-aloud").assertIsDisplayed()
        rule.onNodeWithTag("futaber-menu-save").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("futaber-menu-write").performScrollTo().assertIsDisplayed()
        capture("menu-light")
    }

    /** V33・M54: the card stands on the bar's top, 8 from the sides; the veil covers the bar, which does not react. */
    @Test fun theCardStandsOnTheBarsTopAndTheVeilCoversTheBarWhichDoesNotReact() {
        open()
        rule.waitForIdle()
        val loadsBefore = loads.get()
        openMenu()
        val card = bounds("futaber-operation-menu")
        val bar = bounds("futaber-toolbar")
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals("the card's bottom is the bar's top", bar.top, card.bottom, 1.5f)
        assertEquals(8f * density, card.left - root.left, 1.5f)
        assertEquals(8f * density, root.right - card.right, 1.5f)
        // 30 + the 30 field + 20 + the 300 grid + 60 (the original's 440).
        assertEquals(440f * density, card.height, 2f * density)
        val scrim = bounds("futaber-menu-scrim")
        assertTrue("the veil covers the bar", scrim.top <= bar.top && scrim.bottom >= bar.bottom)
        // A tap where the bar's 「更新」 is lands on the veil: the menu closes and nothing is reloaded.
        val refresh = bounds("futaber-thread-refresh")
        rule.onRoot().performTouchInput { click(refresh.center) }
        waitMenuClosed()
        rule.waitForIdle()
        assertEquals("the bar did not reload the thread", loadsBefore, loads.get())
        // Android's system back closes the menu too.
        openMenu()
        Espresso.pressBack()
        waitMenuClosed()
    }

    /** M08: the field puts the grid away for the found posts' lines; the search key shows them as a bubble. */
    @Test fun theSearchWordListsTheFoundPostsAndShowsThemAsABubble() {
        open()
        openMenu()
        rule.onNodeWithTag("futaber-menu-search").performClick()
        rule.waitUntil(5_000) { present("futaber-menu-search-cancel") && !present("futaber-menu-grid") }
        rule.onNodeWithTag("futaber-menu-search").performTextInput("ねこ")
        rule.waitUntil(5_000) { present("futaber-menu-result-count") }
        rule.onNodeWithTag("futaber-menu-result-count").assertTextEquals("5レスが見つかりました")
        // The first post, the three replies quoting it and 「ネコの話」 (kana folded alike).
        assertEquals(5, rule.onAllNodesWithTag("futaber-menu-search-result").fetchSemanticsNodes().size)
        // The card reaches up to the title row while searching.
        assertTrue(bounds("futaber-operation-menu").top < bounds("futaber-thread-list").top + 4f * density)
        capture("menu-search-typed")
        rule.onNodeWithTag("futaber-menu-search").performImeAction()
        waitMenuClosed()
        rule.waitUntil(5_000) { present("futaber-quote-card") }
        rule.onNodeWithTag("futaber-quote-card").assert(hasContentDescription("検索結果、5件"))
        assertFalse("the thread is not narrowed", present("futaber-filter-chip"))
        rule.onNodeWithTag("futaber-quote-close-one").assertIsDisplayed()
        rule.onNodeWithTag("futaber-quote-close-all").performClick()
        rule.waitUntil(5_000) { !present("futaber-quote-card") }
        // Opened again, the word is empty and the grid is back.
        openMenu()
        assertEquals("", rule.onNodeWithTag("futaber-menu-search").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertTrue(present("futaber-menu-grid"))
        // A found line shows that one post; キャンセル goes back to the grid.
        rule.onNodeWithTag("futaber-menu-search").performClick()
        rule.onNodeWithTag("futaber-menu-search").performTextInput("ネコの話")
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("futaber-menu-search-result").fetchSemanticsNodes().size == 1 }
        rule.onNodeWithTag("futaber-menu-search-result").performClick()
        waitMenuClosed()
        rule.waitUntil(5_000) { present("futaber-quote-card") }
        rule.onNodeWithTag("futaber-quote-card").assert(hasContentDescription("検索結果、1件"))
        rule.onNodeWithTag("futaber-quote-close-all").performClick()
        rule.waitUntil(5_000) { !present("futaber-quote-card") }
        openMenu()
        rule.onNodeWithTag("futaber-menu-search").performClick()
        rule.onNodeWithTag("futaber-menu-search").performTextInput("ない語")
        rule.onNodeWithTag("futaber-menu-search-cancel").performClick()
        rule.waitUntil(5_000) { present("futaber-menu-grid") && !present("futaber-menu-search-cancel") }
        // Nothing found: the one bubble says so.
        rule.onNodeWithTag("futaber-menu-search").performClick()
        rule.onNodeWithTag("futaber-menu-search").performTextInput("ない語")
        rule.onNodeWithTag("futaber-menu-search").performImeAction()
        waitMenuClosed()
        rule.waitUntil(5_000) { present("futaber-quote-nothing-found") }
        rule.onNodeWithTag("futaber-quote-nothing-found").assertTextEquals("レスが見つかりませんでした")
    }

    /** M11: the posts with many replies as a bubble; none, and the bubble says so; a tap outside closes it. */
    @Test fun manyRepliesOpensABubbleAndSaysWhenThereIsNone() {
        open()
        openMenu()
        rule.onNodeWithTag("futaber-menu-many-replies").performClick()
        waitMenuClosed()
        rule.waitUntil(5_000) { present("futaber-quote-card") }
        rule.onNodeWithTag("futaber-quote-card").assert(hasContentDescription("返信が多いレス、1件"))
        // Under the title, pointing up (futaber's 43-01).
        assertTrue(bounds("futaber-quote-card").top < bounds("futaber-thread-list").top + 30f * density)
        assertFalse(present("futaber-filter-chip"))
        capture("menu-many-replies")
        rule.onNodeWithTag("futaber-quote-scrim").performTouchInput { click(Offset(centerX, bottom - 4f)) }
        rule.waitUntil(5_000) { !present("futaber-quote-card") }
        display = FutaberDisplaySettings(manyRepliesThreshold = 30).extensionsOff()
        rule.waitForIdle()
        openMenu()
        rule.onNodeWithTag("futaber-menu-many-replies").performClick()
        rule.waitUntil(5_000) { present("futaber-quote-nothing-found") }
        rule.onNodeWithTag("futaber-quote-nothing-found").assertTextEquals("レスが見つかりませんでした")
        capture("menu-many-replies-none")
    }

    /** M10: the pictures at 0.3 of their size; the item turns to 「画像を大きくする」 and back. */
    @Test fun theImageItemDrawsThePicturesAtThreeTenthsAndTurnsItsLabel() {
        open()
        assertEquals(175f * density, bounds("futaber-post-image").width, 1.5f * density)
        openMenu()
        assertEquals("画像を小さくする", label("futaber-menu-small-images"))
        rule.onNodeWithTag("futaber-menu-small-images").performClick()
        waitMenuClosed()
        rule.waitUntil(5_000) { kotlin.math.abs(bounds("futaber-post-image").width - 52.5f * density) <= 1.5f * density }
        assertEquals(39.5f * density, bounds("futaber-post-image").height, 1.5f * density)
        openMenu()
        assertEquals("画像を大きくする", label("futaber-menu-small-images"))
        capture("menu-image-toggled")
        rule.onNodeWithTag("futaber-menu-small-images").performClick()
        waitMenuClosed()
        rule.waitUntil(5_000) { kotlin.math.abs(bounds("futaber-post-image").width - 175f * density) <= 1.5f * density }
    }

    /** M15: the MHT format is asked in an alert over the menu; キャンセル leaves the menu as it was. */
    @Test fun theMhtAlertComesOverTheMenuAndCancelKeepsTheMenu() {
        open()
        openMenu()
        rule.onNodeWithTag("futaber-menu-mht-save").performClick()
        rule.waitUntil(5_000) { present("futaber-alert") }
        rule.onNodeWithTag("futaber-alert-title").assertTextEquals("MHT保存")
        rule.onNodeWithTag("futaber-mht-choose-thumbs").assertIsDisplayed()
        rule.onNodeWithTag("futaber-mht-choose-full").assertIsDisplayed()
        assertTrue("the menu stays under the alert", present("futaber-operation-menu"))
        capture("menu-mht-alert")
        rule.onNodeWithTag("futaber-mht-choose-cancel").performClick()
        rule.waitUntil(5_000) { !present("futaber-alert") }
        assertTrue("the menu is still open", present("futaber-operation-menu"))
        rule.onNodeWithTag("futaber-menu-grid").assertIsDisplayed()
        // Back while the alert is up is its キャンセル too.
        rule.onNodeWithTag("futaber-menu-mht-save").performClick()
        rule.waitUntil(5_000) { present("futaber-alert") }
        Espresso.pressBack()
        rule.waitUntil(5_000) { !present("futaber-alert") }
        assertTrue(present("futaber-operation-menu"))
    }

    /** M16・M17・M18: NG編集 opens the NG settings; NGを無効にする and 新着へスクロール can be pressed at any time. */
    @Test fun ngEditNgToggleAndNewPostsArePressableAndDoTheOriginalsThing() {
        open(withNgSettings = true)
        openMenu()
        rule.onNodeWithTag("futaber-menu-ng-edit").performClick()
        waitMenuClosed()
        rule.waitUntil(5_000) { ngSettingsOpened == 1 }
        assertFalse("the old NG dialog does not open", present("futaber-ng-input"))
        // Nothing is hidden, and NGを無効にする can still be pressed; its label turns.
        openMenu()
        rule.onNodeWithTag("futaber-menu-ng-toggle").assertIsEnabled()
        assertEquals("NGを無効にする", label("futaber-menu-ng-toggle"))
        rule.onNodeWithTag("futaber-menu-ng-toggle").performClick()
        waitMenuClosed()
        openMenu()
        assertEquals("NGを有効にする", label("futaber-menu-ng-toggle"))
        rule.onNodeWithTag("futaber-menu-ng-toggle").performClick()
        waitMenuClosed()
        // No new post: 新着へスクロール can be pressed and goes to the end.
        openMenu()
        rule.onNodeWithTag("futaber-menu-new").assertIsEnabled().performClick()
        waitMenuClosed()
        rule.waitUntil(10_000) {
            val range = rule.onNodeWithTag("futaber-thread-list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            range.value() >= range.maxValue() - 1f
        }
        assertFalse(present("futaber-new-marker"))
    }

    /** M18: with new posts it goes to the first one, as before. */
    @Test fun newPostsGoesToTheFirstNewPost() {
        open(seenCount = 10)
        openMenu()
        rule.onNodeWithTag("futaber-menu-new").performClick()
        waitMenuClosed()
        rule.waitUntil(10_000) { present("futaber-new-marker") }
        rule.onNodeWithTag("futaber-new-marker").assertIsDisplayed()
    }

    /** Plan §4.1-3: the old narrowing of the search word and of 「返信が多いレスを表示」 behind their switches. */
    @Test fun theOldBehaviourSwitchesBringBackTheNarrowing() {
        display = FutaberDisplaySettings(legacySearchFilter = true, legacyManyRepliesFilter = true).extensionsOff()
        open()
        openMenu()
        rule.onNodeWithTag("futaber-menu-search").performClick()
        rule.onNodeWithTag("futaber-menu-search").performTextInput("ねこ")
        rule.waitUntil(5_000) { present("futaber-menu-result-count") }
        rule.onNodeWithTag("futaber-menu-result-count").assertTextEquals("5件が一致")
        assertTrue("the grid stays", present("futaber-menu-grid"))
        assertFalse(present("futaber-menu-search-cancel"))
        rule.onNodeWithTag("futaber-menu-search").performImeAction()
        waitMenuClosed()
        rule.waitUntil(5_000) { present("futaber-filter-chip") }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("futaber-post").fetchSemanticsNodes().size == 5 }
        assertFalse(present("futaber-quote-card"))
        rule.onNodeWithTag("futaber-filter-chip").performClick()
        rule.waitUntil(5_000) { !present("futaber-filter-chip") }
        openMenu()
        rule.onNodeWithTag("futaber-menu-many-replies").performClick()
        waitMenuClosed()
        rule.waitUntil(5_000) { present("futaber-filter-chip") }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("futaber-post").fetchSemanticsNodes().size == 1 }
        assertFalse(present("futaber-quote-card"))
    }

    /** Screenshots for the comparison (build/l-b6/): the dark theme's menu and its search. */
    @Test fun captureTheMenuInTheDarkTheme() {
        themeMode = FutaberThemeMode.Dark
        open()
        openMenu()
        capture("menu-dark")
        rule.onNodeWithTag("futaber-menu-search").performClick()
        rule.onNodeWithTag("futaber-menu-search").performTextInput("ねこ")
        rule.waitUntil(5_000) { present("futaber-menu-result-count") }
        capture("menu-search-typed-dark")
        rule.onNodeWithTag("futaber-menu-search").performImeAction()
        rule.waitUntil(5_000) { present("futaber-quote-card") }
        capture("menu-search-bubble-dark")
        rule.onNodeWithTag("futaber-quote-close-all").performClick()
        rule.waitUntil(5_000) { !present("futaber-quote-card") }
        openMenu()
        rule.onNodeWithTag("futaber-menu-mht-save").performClick()
        rule.waitUntil(5_000) { present("futaber-alert") }
        capture("menu-mht-alert-dark")
        rule.onNodeWithTag("futaber-mht-choose-cancel").performClick()
        rule.waitUntil(5_000) { !present("futaber-alert") }
    }
}
