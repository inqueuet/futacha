package com.valoser.futacha

import android.view.KeyEvent
import android.view.textclassifier.TextClassification
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextClassifier
import android.view.textclassifier.TextSelection
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Compare state-based Material3 with the legacy reproducer on the same device/dependencies.
 * The recorder delegates normal requests to the actual device classifier. No feature flag changes.
 */
class TextSelectionStateApiInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val state = TextFieldState("alpha beta gamma")
    private val requests = CopyOnWriteArrayList<TextSelection.Request>()
    private lateinit var manager: TextClassificationManager
    private lateinit var originalClassifier: TextClassifier

    @Before fun recordRequests() {
        manager = rule.activity.getSystemService(TextClassificationManager::class.java)
        originalClassifier = manager.textClassifier
        manager.setTextClassifier(object : TextClassifier {
            override fun suggestSelection(request: TextSelection.Request): TextSelection {
                requests.add(request)
                return originalClassifier.suggestSelection(request)
            }
            override fun classifyText(request: TextClassification.Request): TextClassification =
                originalClassifier.classifyText(request)
        })
    }

    @After fun restoreClassifier() {
        if (::manager.isInitialized && ::originalClassifier.isInitialized) {
            manager.setTextClassifier(originalClassifier)
        }
    }

    private fun showAndHold() {
        rule.setContent {
            MaterialTheme {
                TextField(state = state,
                    modifier = Modifier.padding(32.dp).fillMaxWidth().testTag("input"))
            }
        }
        val node = rule.onNodeWithTag("input")
        node.performClick()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val point = layouts.single().getBoundingBox(13).center
        val inset = with(rule.density) { 16.dp.toPx() }
        node.performTouchInput {
            down(point + Offset(inset, inset))
            advanceEventTime(800)
            move()
        }
        rule.waitForIdle()
        val selection = node.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
        assertFalse(selection.collapsed)
        assertEquals("gamma", state.text.substring(selection.min, selection.max))
    }

    private fun backspace() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DEL)
        rule.waitForIdle()
        assertEquals("alpha beta ", state.text.toString())
    }

    private fun release(cancel: Boolean = false) {
        rule.onNodeWithTag("input").performTouchInput {
            if (cancel) cancel() else up()
        }
        rule.waitForIdle()
    }

    @Test fun smartSelectionStillCallsDeviceClassifier() {
        showAndHold()
        release()
        rule.waitUntil(5_000) { requests.isNotEmpty() }
        assertEquals("alpha beta gamma", requests.first().text.toString())
        assertEquals(11, requests.first().startIndex)
        assertEquals(16, requests.first().endIndex)
    }

    @Test fun keyboardDeletionWhileHeld() {
        showAndHold()
        backspace()
        release()
        assertEquals("alpha beta ", state.text.toString())
    }

    @Test fun keyboardDeletionThenPointerCancel() {
        showAndHold()
        backspace()
        release(cancel = true)
        assertEquals("alpha beta ", state.text.toString())
    }

    @Test fun shorterNonemptyReplacementWhileHeld() {
        showAndHold()
        // Deliberate state update: isolates an async restore/reset, not a full product flow.
        rule.runOnIdle { state.setTextAndPlaceCursorAtEnd("prefix") }
        release()
        assertEquals("prefix", state.text.toString())
    }

    @Test fun emptyReplacementWhileHeld() {
        showAndHold()
        rule.runOnIdle { state.setTextAndPlaceCursorAtEnd("") }
        release()
        assertEquals("", state.text.toString())
    }
}
