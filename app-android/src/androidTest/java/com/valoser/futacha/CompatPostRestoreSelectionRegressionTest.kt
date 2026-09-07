@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.valoser.futacha

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatReplyDraft
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatToolbarItem
import com.valoser.futacha.shared.compat.CompatToolbarSurface
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.compatToolbarMaster
import com.valoser.futacha.shared.ui.compat.CompatPostScreen
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Production screen; input is edited only through Android keyboard/touch events. */
class CompatPostRestoreSelectionRegressionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val databaseName = "selection_other_diagnostic_${System.nanoTime()}.db"
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var loader: ImageLoader
    private val draftRequested = CompletableDeferred<Unit>()
    private val draftReady = CompletableDeferred<Unit>()
    private val draftApplied = CompletableDeferred<Unit>()
    @Volatile private var savedDraft: CompatReplyDraft? = null

    @Before fun prepare() {
        store = AndroidCompatibilityStore(context, databaseName = databaseName)
        runBlocking {
            store.initialize()
            store.saveToolbar(CompatToolbarSurface.POST, compatToolbarMaster(CompatToolbarSurface.POST).mapIndexed { index, item ->
                CompatToolbarItem(item.key, index, item.key == "reset")
            })
        }
        loader = ImageLoader.Builder(context).build()
    }

    @After fun cleanup() {
        draftReady.complete(Unit)
        if (::loader.isInitialized) loader.shutdown()
        if (::store.isInitialized) runBlocking { store.closeForTest() }
        context.deleteDatabase(databaseName)
    }

    private fun showScreen(delayDraft: Boolean = false) {
        val boardUrl = "https://img.2chan.net/b/"
        val board = CompatBoard("diagnostic-board", "diagnostic", boardUrl, boardUrl, 0)
        val url = "${boardUrl}res/123.htm"
        val tab = CompatTab("diagnostic-tab", url, url, board.key, board.name, "123", "diagnostic", insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1)
        runBlocking {
            store.upsertBoard(board)
            store.openTab(tab)
            store.saveDraft(CompatReplyDraft(tabKey = tab.key, comment = "prefix", name = "name",
                email = "mail", subject = "title", deleteKey = "key", updatedAtEpochMillis = 1))
        }
        val screenStore = if (delayDraft) object : CompatibilityStore by store {
            override suspend fun loadDraft(tabKey: String): CompatReplyDraft? {
                val saved = store.loadDraft(tabKey)
                draftRequested.complete(Unit)
                draftReady.await()
                return saved
            }
            override suspend fun saveDraft(draft: CompatReplyDraft) {
                store.saveDraft(draft)
                savedDraft = draft
                draftApplied.complete(Unit)
            }
        } else store
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
                MaterialTheme {
                    CompatPostScreen(
                        tab = tab, board = board, repository = null,
                        store = screenStore, preferences = emptyMap(), appVersion = "10.5",
                        fileSystem = null, onToolbarEdit = {}, onBack = {}
                    )
                }
            }
        }
        if (delayDraft) rule.waitUntil(5_000) { draftRequested.isCompleted }
        else rule.waitUntil(5_000) { currentText() == "prefix" }
    }

    private fun currentText(): String = rule.onNodeWithTag("compat-post-comment-field")
        .fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun typeSuffix() {
        val before = currentText()
        rule.onNodeWithTag("compat-post-comment-field").performClick()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MOVE_END)
        instrumentation.sendStringSync(" alpha beta gamma")
        rule.waitForIdle()
        assertEquals(before + " alpha beta gamma", currentText())
    }

    private fun holdLastWord() {
        val body = currentText()
        val node = rule.onNodeWithTag("compat-post-comment-field")
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val point = layouts.single().getBoundingBox(body.length - 2).center
        val inset = with(rule.density) { Offset(16.dp.toPx(), 24.dp.toPx()) }
        node.performTouchInput {
            down(point + inset)
            advanceEventTime(800)
            move()
        }
        rule.waitForIdle()
        val selection = node.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
        assertFalse(selection.collapsed)
        assertEquals("gamma", body.substring(selection.min, selection.max))
    }

    @Test fun resetAfterReleasingLongPress() {
        showScreen()
        typeSuffix()
        holdLastWord()
        rule.onNodeWithTag("compat-post-comment-field").performTouchInput { up() }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("リセット").performTouchInput { down(center); up() }
        rule.waitForIdle()
        assertEquals("prefix", currentText())
    }

    @Test fun resetWithSecondFingerWhileLongPressIsHeld() {
        showScreen()
        typeSuffix()
        holdLastWord()
        rule.onNodeWithContentDescription("リセット").performTouchInput {
            down(pointerId = 1, position = center)
            up(pointerId = 1)
        }
        rule.waitForIdle()
        assertEquals("prefix", currentText())
        rule.onNodeWithTag("compat-post-comment-field").performTouchInput { up(pointerId = 0) }
        rule.waitForIdle()
    }

    @Test fun delayedDraftRestorationPreservesNewInput() {
        showScreen(delayDraft = true)
        typeSuffix()
        draftReady.complete(Unit)
        rule.waitUntil(5_000) { draftApplied.isCompleted }
        rule.waitForIdle()
        assertEquals(" alpha beta gamma", currentText())
    }

    @Test fun delayedDraftRestorationWhileLongPressIsHeld() {
        showScreen(delayDraft = true)
        typeSuffix()
        holdLastWord()
        draftReady.complete(Unit)
        rule.waitUntil(5_000) { draftApplied.isCompleted }
        rule.waitForIdle()
        assertEquals(" alpha beta gamma", currentText())
        rule.onNodeWithTag("compat-post-comment-field").performTouchInput { up() }
        rule.waitForIdle()
    }

    @Test fun delayedRestorePreservesExplicitClearAndOtherFieldEdits() {
        showScreen(delayDraft = true)
        // UI text-edit commands, not assignments to the production State.
        rule.onNodeWithTag("compat-post-comment-field").performTextReplacement("temporary")
        rule.onNodeWithTag("compat-post-comment-field").performTextReplacement("")
        rule.onNodeWithTag("compat-post-name-field").performTextReplacement("edited")
        draftReady.complete(Unit)
        rule.waitUntil(5_000) { draftApplied.isCompleted }
        assertEquals("", savedDraft?.comment)
        assertEquals("edited", savedDraft?.name)
        assertEquals("mail", savedDraft?.email)
        assertEquals("title", savedDraft?.subject)
        assertEquals("key", savedDraft?.deleteKey)
    }

    @Test fun delayedRestoreStillRestoresUntouchedFields() {
        showScreen(delayDraft = true)
        draftReady.complete(Unit)
        rule.waitUntil(5_000) { draftApplied.isCompleted }
        assertEquals("prefix", currentText())
        assertEquals("prefix", savedDraft?.comment)
        assertEquals("name", savedDraft?.name)
        assertEquals("mail", savedDraft?.email)
        assertEquals("title", savedDraft?.subject)
        assertEquals("key", savedDraft?.deleteKey)
    }
}
