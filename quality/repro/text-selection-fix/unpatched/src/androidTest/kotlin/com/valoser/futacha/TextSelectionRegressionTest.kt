@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.valoser.futacha

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.ui.board.rememberStableTextInputState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** No direct text updates after composition: edits arrive as Android keyboard events. */
class TextSelectionRegressionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val fieldValue = mutableStateOf(TextFieldValue("alpha beta gamma"))
    private val appText = mutableStateOf("alpha beta gamma")

    private fun showField(appState: Boolean) {
        rule.setContent {
            MaterialTheme {
                val modifier = Modifier.padding(32.dp).fillMaxWidth().testTag("input")
                if (appState) {
                    val input = rememberStableTextInputState(appText.value, { appText.value = it })
                    TextField(input.value, input.onValueChange, modifier)
                } else {
                    TextField(fieldValue.value, { fieldValue.value = it }, modifier)
                }
            }
        }
        rule.onNodeWithTag("input").performClick()
    }

    private fun holdLastWord() {
        val node = rule.onNodeWithTag("input")
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
        assertFalse(node.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange].collapsed)
    }

    private fun backspace() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DEL)
        rule.waitForIdle()
        assertEquals(
            "alpha beta ",
            rule.onNodeWithTag("input").fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        )
    }

    @Test fun plainComposeBackspaceAfterRelease() {
        showField(false)
        holdLastWord()
        rule.onNodeWithTag("input").performTouchInput { up() }
        rule.waitForIdle()
        backspace()
    }

    @Test fun plainComposeBackspaceWhileHeld() {
        showField(false)
        holdLastWord()
        backspace()
        rule.onNodeWithTag("input").performTouchInput { up() }
        rule.waitForIdle()
    }

    @Test fun appStateBackspaceWhileHeld() {
        showField(true)
        holdLastWord()
        backspace()
        rule.onNodeWithTag("input").performTouchInput { up() }
        rule.waitForIdle()
    }

    private fun verifyJapaneseImeComposition(appState: Boolean) {
        showField(appState)
        rule.runOnIdle {
            val connection = checkNotNull(rule.activity.currentFocus?.onCreateInputConnection(
                android.view.inputmethod.EditorInfo()
            ))
            connection.setSelection(0, 16)
            connection.setComposingText("にほん", 1)
            // Reuse this connection for the whole IME transaction. Creating another
            // connection resets its composing buffer; the installed keyboard can also
            // finish composing independently once control returns to the event loop.
            if (!appState) assertEquals(androidx.compose.ui.text.TextRange(0, 3), fieldValue.value.composition)
            connection.commitText("日本", 1)
            connection.finishComposingText()
        }
        rule.waitForIdle()
        assertEquals("日本", rule.onNodeWithTag("input").fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text)
        if (!appState) assertEquals(null, fieldValue.value.composition)
    }

    @Test fun plainComposeJapaneseImeComposition() = verifyJapaneseImeComposition(false)

    @Test fun appStateJapaneseImeComposition() = verifyJapaneseImeComposition(true)
}
