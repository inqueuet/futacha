@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.Espresso
import coil3.ColorImage
import coil3.ImageLoader
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.ui.futaber.*
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** M61: real media Dialog/gear/Back with two inline images belonging to the same post. Old product APIs, no IO services. */
class FutaberViewerSettingsReturnInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val database = "futaber_viewer_settings_return.db"
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var loader: ImageLoader

    @Before fun prepare() {
        rule.activity.deleteDatabase(database)
        store = AndroidCompatibilityStore(rule.activity, databaseName = database)
        runBlocking { store.initialize() }
        loader = ImageLoader.Builder(rule.activity).memoryCache(null).diskCache(null).components {
            // Every URL, including synthetic inline あぷ小 media, terminates here without a network fetcher.
            add(Interceptor { chain -> SuccessResult(ColorImage(android.graphics.Color.BLUE, 128, 128), chain.request) })
        }.build()
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        if (::loader.isInitialized) loader.shutdown()
        if (::store.isInitialized) runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase(database)
    }

    private fun host(initialPost: String? = null) {
        val board = BoardSummary("m61", "模擬板", "", "https://may.2chan.net/b/futaba.php", "")
        val first = "https://may.2chan.net/b/src/100.jpg"
        val page = ThreadPage("100", "模擬板", null, null, listOf(
            Post("100", author = null, subject = null, timestamp = "", messageHtml =
                "<a href='https://dec.2chan.net/up2/src/fu7099123.jpg'>fu7099123.jpg</a><br>" +
                "<a href='https://dec.2chan.net/up2/src/fu7099124.jpg'>fu7099124.jpg</a>",
                imageUrl = first, thumbnailUrl = first),
            Post("101", author = null, subject = null, timestamp = "", messageHtml = "other post",
                imageUrl = "https://may.2chan.net/b/src/101.jpg", thumbnailUrl = "https://may.2chan.net/b/src/101.jpg")
        ))
        val services = FutaberMediaServices(store, null, createFileSystem(rule.activity), null)
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                FutaberTheme(FutaberThemeMode.Light) {
                    FutaberMediaOverlay(board, FutaberThreadRef("m61", "100", "M61", first, 2), page, services,
                        initialPostNo = initialPost, onShowPost = {}, onClose = {})
                }
            }
        }
        if (initialPost == null) waitTag("futaber-gallery-item") else waitCounter("4 of 4")
    }

    private fun waitTag(tag: String) = rule.waitUntil(5_000) {
        rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
    private fun waitCounter(text: String) = rule.waitUntil(5_000) {
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    private fun swipeLeftTo(counter: String) {
        rule.onNodeWithTag("compat-viewer-image-page").performTouchInput { swipeLeft(durationMillis = 400) }
        waitCounter(counter)
    }
    private fun settingsAndBack(expected: String) {
        rule.onNodeWithTag("futaber-viewer-gear").performClick()
        waitTag("compat-settings-list-viewer")
        Espresso.pressBack()
        waitCounter(expected)
        rule.onNodeWithTag("futaber-viewer-counter").assertTextEquals(expected)
    }

    @Test fun currentInlineImageSurvivesOpeningAndReturningFromViewerSettings() {
        host()
        rule.onAllNodesWithTag("futaber-gallery-item")[0].performClick()
        waitCounter("1 of 4")
        swipeLeftTo("2 of 4")
        swipeLeftTo("3 of 4")
        // The first three pages have the same postNo; restoring only that post must not pick its primary image.
        settingsAndBack("3 of 4")
        settingsAndBack("3 of 4")
    }

    @Test fun aNonFirstInitialSelectionDoesNotReplaceThePageReachedBeforeSettings() {
        host(initialPost = "101")
        rule.onNodeWithTag("compat-viewer-image-page").performTouchInput { swipeRight(durationMillis = 400) }
        waitCounter("3 of 4")
        settingsAndBack("3 of 4")
    }

    @Test fun returningToGalleryAndSelectingAnotherImageStartsAFreshViewerVisit() {
        host()
        rule.onAllNodesWithTag("futaber-gallery-item")[0].performClick()
        waitCounter("1 of 4")
        swipeLeftTo("2 of 4")
        swipeLeftTo("3 of 4")
        Espresso.pressBack()
        waitTag("futaber-gallery-item")
        rule.onAllNodesWithTag("futaber-gallery-item")[3].performClick()
        waitCounter("4 of 4")
        settingsAndBack("4 of 4")
        Espresso.pressBack()
        waitTag("futaber-gallery-item")
        rule.onAllNodesWithTag("futaber-gallery-item")[0].performClick()
        waitCounter("1 of 4")
    }
}
