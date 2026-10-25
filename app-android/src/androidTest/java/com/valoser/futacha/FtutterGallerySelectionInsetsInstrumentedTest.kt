@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil3.ImageLoader
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import org.junit.*
import org.junit.Assert.*

/** Run on the owned AVD once in three-button mode and once in gesture mode. No network or actual saving. */
class FtutterGallerySelectionInsetsInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var loader: ImageLoader
    @Before fun prepare() {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        loader = ImageLoader.Builder(rule.activity).build()
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle(); loader.shutdown()
    }
    @Test fun selectionAndSaveButtonsClearCurrentNavigationInsetAndAcceptRealTouches() {
        val urls = listOf("file:///nonexistent/audit-m36-1.jpg", "file:///nonexistent/audit-m36-2.jpg")
        val posts = urls.mapIndexed { index, url ->
            CompatPostSnapshot(index + 1, "${100 + index}", timestamp = "", messageHtml = "", imageUrl = url)
        }
        var saved: List<String>? = null
        val tools = FtutterMediaSaveTools("test folder", {}, {}, { selected, progress ->
            saved = selected; progress(selected.size); FtutterBatchSaveResult(selected.size, 0)
        }, false)
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                FtutterTheme(null, FtutterBrightness.LIGHT) {
                    FtutterGalleryScreen(media = posts, columns = 2, privacyAlpha = 1f, tools = tools,
                        onColumns = {}, onOpenViewer = {}, onBackToPost = {}, onClose = {})
                }
            }
        }
        rule.onAllNodesWithTag("ftutter-gallery-cell")[0].performTouchInput { longClick(center) }
        rule.waitForIdle()
        val insets = checkNotNull(ViewCompat.getRootWindowInsets(rule.activity.window.decorView))
            .getInsets(WindowInsetsCompat.Type.navigationBars())
        assertTrue("This test requires a real system navigation inset", insets.bottom > 0)
        val safeBottom = rule.activity.window.decorView.height - insets.bottom
        val minimumVisualHeight = with(rule.density) { 40.dp.toPx() }
        val minimumTouchHeight = with(rule.density) { 48.dp.toPx() }
        for (tag in listOf("ftutter-gallery-select-all", "ftutter-gallery-save")) {
            val node = rule.onNodeWithTag(tag).fetchSemanticsNode()
            val bounds = node.boundsInWindow
            assertTrue("$tag must retain its 40dp visual height: $bounds", bounds.height >= minimumVisualHeight - 1f)
            val touchBounds = node.touchBoundsInRoot
            assertTrue("$tag must retain its 48dp touch target: $touchBounds", touchBounds.height >= minimumTouchHeight - 1f)
            val touchBottomInWindow = touchBounds.bottom + node.positionInWindow.y - node.positionInRoot.y
            assertTrue("$tag touch target must stay above navigation: $touchBounds / $safeBottom", touchBottomInWindow <= safeBottom + 1f)
            assertTrue("$tag must stay above the navigation area: $bounds / $safeBottom", bounds.bottom <= safeBottom + 1f)
        }
        rule.onNodeWithTag("ftutter-gallery-select-all").performTouchInput { click(center) }
        rule.onNodeWithTag("ftutter-gallery-save").performTouchInput { click(center) }
        rule.onNodeWithText("2個のファイルを保存しますか？").assertIsDisplayed()
        rule.onNodeWithTag("ftutter-gallery-save-ok").performTouchInput { click(center) }
        rule.waitUntil(5_000) { saved != null }
        assertEquals(urls, saved)
        rule.onNodeWithText("2個のファイルを保存しました").assertIsDisplayed()
        println("M36 navigationBottom=${insets.bottom} safeBottom=$safeBottom selected=${saved?.size}")
    }
}
