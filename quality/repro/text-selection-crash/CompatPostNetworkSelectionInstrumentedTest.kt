@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatToolbarItem
import com.valoser.futacha.shared.compat.CompatToolbarSurface
import com.valoser.futacha.shared.compat.compatToolbarMaster
import com.valoser.futacha.shared.ui.compat.CompatPostScreen
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real production screen. Only network response timing and the isolated DB are controlled. */
class CompatPostNetworkSelectionInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val databaseName = "selection_network_diagnostic_${System.nanoTime()}.db"
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var loader: ImageLoader
    private lateinit var client: HttpClient
    private val requestStarted = CompletableDeferred<Unit>()
    private val responseReady = CompletableDeferred<Unit>()
    private val body = "prefix " + "alpha beta ".repeat(8) + "gamma"

    @Before fun prepare() {
        store = AndroidCompatibilityStore(context, databaseName = databaseName)
        runBlocking {
            store.initialize()
            store.saveToolbar(CompatToolbarSurface.POST, compatToolbarMaster(CompatToolbarSurface.POST).mapIndexed { index, item ->
                CompatToolbarItem(item.key, index, item.key == "network_info")
            })
        }
        loader = ImageLoader.Builder(context).build()
        client = HttpClient(MockEngine {
            check(it.url.host == "ipinfo.io")
            requestStarted.complete(Unit)
            responseReady.await()
            respond("example.test", headers = headersOf("Content-Type", "text/plain"))
        })
    }

    @After fun cleanup() {
        responseReady.complete(Unit)
        if (::client.isInitialized) client.close()
        if (::loader.isInitialized) loader.shutdown()
        if (::store.isInitialized) runBlocking { store.closeForTest() }
        context.deleteDatabase(databaseName)
    }

    private fun showAndRequestNetworkInfo() {
        val boardUrl = "https://img.2chan.net/b/"
        val board = CompatBoard("diagnostic-board", "diagnostic", boardUrl, boardUrl, 0)
        val url = "${boardUrl}res/123.htm"
        val tab = CompatTab("diagnostic-tab", url, url, board.key, board.name, "123", "diagnostic", insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1)
        runBlocking {
            store.upsertBoard(board)
            store.openTab(tab)
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
                MaterialTheme {
                    CompatPostScreen(
                        tab = tab, board = board, repository = null, httpClient = client,
                        store = store, preferences = emptyMap(), appVersion = "10.5",
                        fileSystem = null, onToolbarEdit = {}, onBack = {}
                    )
                }
            }
        }
        val field = rule.onNodeWithTag("compat-post-comment-field")
        field.performClick()
        instrumentation.sendStringSync("prefix ")
        rule.waitForIdle()
        assertEquals("prefix ", currentText())
        rule.onNodeWithContentDescription("回線情報").performClick()
        rule.waitUntil(5_000) { requestStarted.isCompleted }
        field.performClick()
        // Restore cursor to the end with a real keyboard key, then type normally.
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_MOVE_END)
        instrumentation.sendStringSync("alpha beta ".repeat(8) + "gamma")
        rule.waitForIdle()
        assertEquals(body, currentText())
    }

    private fun currentText(): String = rule.onNodeWithTag("compat-post-comment-field")
        .fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    @Test fun networkReplyOverwritesNewKeyboardInput() {
        showAndRequestNetworkInfo()
        responseReady.complete(Unit)
        rule.waitUntil(5_000) { currentText().contains("example.test") }
        assertTrue(currentText().startsWith("prefix"))
        assertFalse("Diagnostic confirms the app loses input typed during the request", currentText().contains("gamma"))
    }

    @Test fun networkReplyDuringLongPressOfNewKeyboardInput() {
        showAndRequestNetworkInfo()
        val node = rule.onNodeWithTag("compat-post-comment-field")
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val point = layouts.single().getBoundingBox(body.length - 2).center
        // A labeled Material3 TextField positions its editable text below the label.
        val xInset = with(rule.density) { 16.dp.toPx() }
        val yInset = with(rule.density) { 24.dp.toPx() }
        node.performTouchInput {
            down(point + Offset(xInset, yInset))
            advanceEventTime(800)
            move()
        }
        rule.waitForIdle()
        val selection = node.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
        assertFalse(selection.collapsed)
        assertEquals("gamma", body.substring(selection.min, selection.max))
        responseReady.complete(Unit)
        rule.waitUntil(5_000) { currentText().contains("example.test") }
        assertTrue(currentText().length < selection.max)
        node.performTouchInput { up() }
        rule.waitForIdle()
    }
}
