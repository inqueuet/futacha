@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import android.content.pm.ActivityInfo
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
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** L-B11: the picture list and the viewer as futaber has them (V18・V19・G26・G27・G37・C27〜C29), on the real Dialog windows. */
class FutaberViewerInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val database = "futaber_viewer_l_b11.db"
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var loader: ImageLoader
    private var shownPost: String? = null

    @Before fun prepare() {
        rule.activity.deleteDatabase(database)
        store = AndroidCompatibilityStore(rule.activity, databaseName = database)
        runBlocking {
            store.initialize()
            // A save place is set, so a share starts at once instead of opening the system's folder picker over the test.
            store.savePreference("compat.storage.dummyDownloadDir", rule.activity.getExternalFilesDir(null)!!.absolutePath)
        }
        loader = ImageLoader.Builder(rule.activity).memoryCache(null).diskCache(null).components {
            add(Interceptor { chain -> SuccessResult(ColorImage(android.graphics.Color.BLUE, 128, 128), chain.request) })
        }.build()
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        if (::loader.isInitialized) loader.shutdown()
        if (::store.isInitialized) runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase(database)
    }

    private fun host() {
        val board = BoardSummary("lb11", "模擬板", "", "https://may.2chan.net/b/futaba.php", "")
        val a = "https://may.2chan.net/b/src/100.jpg"
        val page = ThreadPage("100", "模擬板", null, null, listOf(
            Post("100", author = null, subject = null, timestamp = "", messageHtml = "一行目<br>二行目<br>三行目<br>四行目", imageUrl = a, thumbnailUrl = a),
            Post("101", author = null, subject = null, timestamp = "", messageHtml = "other", imageUrl = "https://may.2chan.net/b/src/101.jpg", thumbnailUrl = "https://may.2chan.net/b/src/101.jpg")
        ))
        val services = FutaberMediaServices(store, null, createFileSystem(rule.activity), null)
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                FutaberTheme(FutaberThemeMode.Light) {
                    FutaberMediaOverlay(board, FutaberThreadRef("lb11", "100", "LB11", a, 2), page, services,
                        onShowPost = { shownPost = it }, onClose = {})
                }
            }
        }
        waitTag("futaber-gallery-item")
    }

    private fun waitTag(tag: String) = rule.waitUntil(5_000) {
        rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
    private fun gone(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
    private fun openViewer() {
        host()
        rule.onAllNodesWithTag("futaber-gallery-item")[0].performClick()
        waitTag("futaber-viewer-counter")
    }

    @Test fun theGalleryTitleCountsPhotos() {
        host()
        rule.onNodeWithTag("futaber-gallery-count").assertTextEquals("2 photos")
    }

    @Test fun aDoubleTapZoomsAndHidesTheBarsAndAnotherOneBringsThemBack() {
        openViewer()
        rule.onNodeWithTag("compat-viewer-image-page").performTouchInput { doubleClick(center) }
        rule.waitUntil(3_000) { gone("futaber-viewer-counter") }
        rule.onNodeWithTag("compat-viewer-image-page").performTouchInput { doubleClick(center) }
        rule.waitForIdle()
        waitTag("futaber-viewer-counter")
    }

    @Test fun tappingTheBarsAndTheCaptionLeavesThemOnScreen() {
        openViewer()
        rule.onNodeWithTag("futaber-viewer-counter").performClick()
        rule.onNodeWithTag("futaber-viewer-info").performClick()
        rule.waitForIdle()
        assertTrue(!gone("futaber-viewer-counter"))
        assertTrue(!gone("futaber-viewer-info"))
    }

    @Test fun theCaptionIsSmallAndKeepsTheBodyToOneLine() {
        openViewer()
        val density = rule.activity.resources.displayMetrics.density
        val bounds = rule.onNodeWithTag("futaber-viewer-info").getUnclippedBoundsInRoot()
        assertTrue("caption ${bounds.bottom.value - bounds.top.value} must be a few small lines", (bounds.bottom.value - bounds.top.value) <= 13f * 3 + 10f + 6f)
        val body = rule.onNodeWithTag("futaber-viewer-body").getUnclippedBoundsInRoot()
        assertTrue("one line of text: ${body.bottom.value - body.top.value}", (body.bottom.value - body.top.value) <= 13f * 1.2f + 1f)
        assertTrue(density > 0f)
    }

    @Test fun aLongPressWhereTheFingerStaysStartsTheShare() {
        openViewer()
        rule.onNodeWithTag("compat-viewer-image-page").performTouchInput { longClick(center) }
        rule.waitUntil(5_000) { !gone("futaber-viewer-message") || !gone("futaber-viewer-save-progress") }
    }

    @Test fun aSlowSwipeDownIsNotALongPress() {
        openViewer()
        rule.onNodeWithTag("compat-viewer-image-page").performTouchInput {
            down(center)
            repeat(14) { moveBy(androidx.compose.ui.geometry.Offset(0f, 30f), delayMillis = 100) }
            up()
        }
        rule.waitForIdle()
        assertTrue("a swipe must not start the share", gone("futaber-viewer-message") && gone("futaber-viewer-save-progress"))
    }

    @Test fun theViewersSettingsPageHasTheStorageChoicesAndTheTwoSwitches() {
        openViewer()
        rule.onNodeWithTag("futaber-viewer-gear").performClick()
        waitTag("futaber-viewer-settings-page")
        listOf("none", "save", "action", "longpress").forEach { rule.onNodeWithTag("futaber-viewer-storage-$it").assertExists() }
        rule.onNodeWithTag("futaber-viewer-storage-note").assertExists()
        rule.onNodeWithTag("futaber-viewer-portrait-lock").assertExists()
        rule.onNodeWithTag("futaber-viewer-autoplay").assertExists()
        rule.onNodeWithTag("futaber-viewer-shared-row").assertExists()
        rule.onNodeWithTag("futaber-viewer-storage-longpress").performClick()
        rule.waitUntil(3_000) { readPref(FutaberSettingKeys.VIEWER_STORAGE_BUTTON) == "longpress" }
        Espresso.pressBack()
        waitTag("futaber-viewer-counter")
    }

    @Test fun thePortraitLockHoldsTheActivityUprightWhileTheViewerIsUp() {
        runBlocking { store.savePreference(FutaberSettingKeys.VIEWER_PORTRAIT_LOCK, "ON") }
        openViewer()
        rule.waitUntil(3_000) { rule.activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, rule.activity.requestedOrientation)
    }

    private fun readPref(key: String): String? = runBlocking {
        kotlinx.coroutines.withTimeoutOrNull(500) { store.preferences.first()[key] }
    }
}
