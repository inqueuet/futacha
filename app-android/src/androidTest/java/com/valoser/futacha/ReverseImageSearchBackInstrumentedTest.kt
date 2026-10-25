@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.filters.SdkSuppress
import com.valoser.futacha.shared.ui.compat.CompatImageSearchResult
import com.valoser.futacha.shared.ui.compat.CompatReverseImageSearchScreen
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** L50: the system back on the image-search result goes back a page first, as the screen's own back arrow does. */
@SdkSuppress(minSdkVersion = 29)
class ReverseImageSearchBackInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
    }

    @Test fun theSystemBackGoesBackAPageBeforeClosing() {
        val closes = AtomicInteger(0)
        val html = "<html><body><p>first</p></body></html>"
        rule.setContent {
            var open by remember { mutableStateOf(true) }
            if (open) {
                CompatReverseImageSearchScreen(
                    result = CompatImageSearchResult.InlineHtml("画像検索", html, "https://example.com/first"),
                    onClose = { closes.incrementAndGet(); open = false }, onOpenExternal = {}
                )
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-reverse-search-page", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        // A second page, as a link on a result page would open (without the network).
        var web: WebView? = null
        rule.waitUntil(10_000) { web = resultWebView(); web != null }
        rule.runOnUiThread {
            web!!.loadDataWithBaseURL("https://example.com/second", "<html><body>second</body></html>", "text/html", "UTF-8",
                "https://example.com/second")
        }
        val pageBack = hasContentDescription("履歴を戻る") and isEnabled()
        rule.waitUntil(10_000) { rule.onAllNodes(pageBack).fetchSemanticsNodes().isNotEmpty() }

        Espresso.pressBack()
        rule.waitForIdle()
        assertEquals("The first back goes back a page", 0, closes.get())
        rule.waitUntil(5_000) { rule.onAllNodes(pageBack).fetchSemanticsNodes().isEmpty() }

        Espresso.pressBack()
        rule.waitUntil(5_000) { closes.get() == 1 }
        rule.waitForIdle()
        assertEquals(1, closes.get())
        rule.onAllNodesWithContentDescription("履歴を戻る").assertCountEquals(0)
    }

    /** The result page's WebView, in the Dialog's own window. */
    private fun resultWebView(): WebView? {
        var found: WebView? = null
        rule.runOnUiThread {
            fun search(view: View): WebView? = when (view) {
                is WebView -> view
                is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { search(view.getChildAt(it)) }
                else -> null
            }
            found = WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::search)
        }
        return found
    }
}
