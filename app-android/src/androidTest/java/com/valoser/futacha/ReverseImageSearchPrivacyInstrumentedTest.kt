@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import android.view.View
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.BaseInMemoryPlatformStateStorage
import com.valoser.futacha.shared.ui.compat.CompatImageSearchResult
import com.valoser.futacha.shared.ui.compat.CompatReverseImageSearchScreen
import com.valoser.futacha.shared.ui.privacy.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** L146: the image-search result window (a WebView in its own Dialog) is darkened by the privacy "画面全体". */
@SdkSuppress(minSdkVersion = 29)
class ReverseImageSearchPrivacyInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val database = "reverse_search_privacy_test.db"
    private lateinit var store: AndroidCompatibilityStore
    private val state = AppStateStore(BaseInMemoryPlatformStateStorage())

    @Before fun prepare() {
        rule.activity.deleteDatabase(database)
        store = AndroidCompatibilityStore(rule.activity, databaseName = database)
        runBlocking {
            store.initialize()
            store.savePreferences(mapOf(PRIVACY_SCOPE_KEY to "SCREEN", PRIVACY_FILTER_KEY to "DIM", PRIVACY_STRENGTH_KEY to "50"))
            state.setPrivacyFilterEnabled(false)
        }
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        if (::store.isInitialized) runBlocking { store.closeForTest() }
        state.close()
        rule.activity.deleteDatabase(database)
    }

    @Test fun theScreenFilterDarkensTheResultPageAndTheImagesScopeLeavesIt() {
        rule.setContent {
            PrivacyModeHost(store, state, compatibilityMode = false) {
                CompatReverseImageSearchScreen(
                    result = CompatImageSearchResult.InlineHtml(
                        "画像検索", "<html><body style=\"margin:0;background:#ffffff\"></body></html>", "https://example.com/"
                    ),
                    onClose = {}, onOpenExternal = {}
                )
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-reverse-search-page", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(10_000) { samples().min() > .9f }

        runBlocking { state.setPrivacyFilterEnabled(true) }
        rule.waitUntil(5_000) { rule.onAllNodes(hasStateDescription("暗くする 50%"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        var pixels = emptyList<Float>()
        rule.waitUntil(5_000) { pixels = samples(); pixels.max() < .6f }
        assertTrue("The result page is darkened: $pixels", pixels.min() > .15f)

        // "画像のみ": the result page is left as it is (the decision for L146).
        runBlocking { store.savePreference(PRIVACY_SCOPE_KEY, "IMAGES") }
        rule.waitUntil(5_000) { rule.onAllNodes(hasStateDescription("暗くする 50%"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        rule.waitUntil(5_000) { samples().min() > .9f }
    }

    /** Samples the displayed result window around the page's centre, with the Dialog's screen offset. */
    private fun samples(): List<Float> {
        val target = rule.onNodeWithTag("compat-reverse-search-page", useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
        val offset = IntArray(2)
        rule.runOnIdle {
            val dialog = WindowInspector.getGlobalWindowViews().filter {
                it !== rule.activity.window.decorView && it.visibility == View.VISIBLE && it.width > 0 && it.height > 0
            }.single()
            val screen = IntArray(2)
            val window = IntArray(2)
            dialog.getLocationOnScreen(screen)
            dialog.getLocationInWindow(window)
            offset[0] = screen[0] - window[0]
            offset[1] = screen[1] - window[1]
        }
        val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            val x = target.center.x.toInt() + offset[0]
            val y = target.center.y.toInt() + offset[1]
            return (-24..24).map { delta -> android.graphics.Color.red(screenshot.getPixel(x + delta, y)) / 255f }
        } finally { screenshot.recycle() }
    }
}
