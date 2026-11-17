@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import org.junit.*
import org.junit.Assert.*

/** L140: the ftutter gallery selection save can be cancelled and explains a save that stopped early. No real I/O. */
class FtutterGallerySaveResultInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var loader: ImageLoader
    private val urls = listOf("file:///nonexistent/l140-1.jpg", "file:///nonexistent/l140-2.jpg")
    @Before fun prepare() { loader = ImageLoader.Builder(rule.activity).build() }
    @After fun cleanup() { rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle(); loader.shutdown() }

    private fun show(tools: FtutterMediaSaveTools) {
        val posts = urls.mapIndexed { index, url -> CompatPostSnapshot(index + 1, "${140 + index}", timestamp = "", messageHtml = "", imageUrl = url) }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                FtutterTheme(null, FtutterBrightness.LIGHT) {
                    FtutterGalleryScreen(media = posts, columns = 2, privacyAlpha = 1f, tools = tools,
                        onColumns = {}, onOpenViewer = {}, onBackToPost = {}, onClose = {})
                }
            }
        }
        rule.onAllNodesWithTag("ftutter-gallery-cell")[0].performTouchInput { longClick(center) }
        rule.onNodeWithTag("ftutter-gallery-select-all").performClick()
        rule.onNodeWithTag("ftutter-gallery-save").performClick()
        rule.onNodeWithTag("ftutter-gallery-save-ok").performClick()
    }

    @Test fun aRunningSelectionSaveCanBeCancelled() {
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        show(FtutterMediaSaveTools("test", {}, {}, { _, progress ->
            progress(1); started.complete(Unit)
            try { awaitCancellation() } finally { cancelled = true }
        }, false))
        rule.waitUntil(5_000) { started.isCompleted }
        rule.onNodeWithTag("ftutter-gallery-save-cancel").assertIsDisplayed().performClick()
        rule.waitUntil(5_000) { cancelled }
        rule.onNodeWithText("保存を中止しました", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("ftutter-gallery-save-ok").assertDoesNotExist()
    }

    @Test fun aStoppedSaveShowsTheReasonAndKeepsTheSelectionForARetry() {
        var calls = 0
        show(FtutterMediaSaveTools("test", {}, {}, { selected, _ ->
            calls++
            if (calls == 1) FtutterBatchSaveResult(0, selected.size, "保存先に書き込めません。設定の「保存先」でフォルダを選び直してください。")
            else FtutterBatchSaveResult(selected.size, 0)
        }, false))
        rule.onNodeWithText("選び直してください", substring = true).assertIsDisplayed()
        // The selection is still there: saving again needs no reselection.
        rule.onNodeWithTag("ftutter-gallery-save").performClick()
        rule.onNodeWithText("2個のファイルを保存しますか？").assertIsDisplayed()
        rule.onNodeWithTag("ftutter-gallery-save-ok").performClick()
        rule.onNodeWithText("2個のファイルを保存しました").assertIsDisplayed()
        assertEquals(2, calls)
    }
}
