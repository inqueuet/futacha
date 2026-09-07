@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.ui.board.rememberStableTextInputState
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** Device diagnostic for the v10.3 TextSelection.Request.Builder crash. */
@OptIn(ExperimentalFoundationApi::class)
class TextSelectionCrashInstrumentedTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val text = mutableStateOf("hello world selection")
    private var previousSmartSelection = true

    @Before
    fun configureSmartSelection() {
        previousSmartSelection = ComposeFoundationFlags.isSmartSelectionEnabled
        ComposeFoundationFlags.isSmartSelectionEnabled =
            InstrumentationRegistry.getArguments().getString("smartSelection", "true").toBoolean()
    }

    @After
    fun restoreSmartSelection() {
        ComposeFoundationFlags.isSmartSelectionEnabled = previousSmartSelection
    }

    private fun showField(useAppInputState: Boolean = false) {
        rule.setContent {
            MaterialTheme {
                if (useAppInputState) {
                    val input = rememberStableTextInputState(text.value, { text.value = it })
                    TextField(
                        value = input.value,
                        onValueChange = input.onValueChange,
                        modifier = Modifier.padding(32.dp).fillMaxWidth().testTag("selection-field")
                    )
                } else {
                    TextField(
                        value = text.value,
                        onValueChange = { text.value = it },
                        modifier = Modifier.padding(32.dp).fillMaxWidth().testTag("selection-field")
                    )
                }
            }
        }
    }

    private fun holdWord() {
        val node = rule.onNodeWithTag("selection-field")
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        // Text layout coordinates exclude TextField's 16dp horizontal padding.
        val glyph = layouts.single().getBoundingBox(2)
        val inset = with(rule.density) { 16.dp.toPx() }
        node.performTouchInput {
            down(glyph.center + androidx.compose.ui.geometry.Offset(inset, inset))
            advanceEventTime(800)
            move()
        }
        rule.waitForIdle()
        assertFalse(
            "The real long press must establish a nonempty selection",
            node.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange].collapsed
        )
    }

    @Test
    fun longPressWithUnchangedText() {
        showField()
        holdWord()
        rule.onNodeWithTag("selection-field").performTouchInput { up() }
        rule.waitForIdle()
    }

    @Test
    fun textShortensWhileLongPressIsHeld() {
        showField()
        holdWord()
        rule.runOnIdle { text.value = "a" }
        rule.waitForIdle()
        rule.onNodeWithTag("selection-field").performTouchInput { up() }
        rule.waitForIdle()
    }

    @Test
    fun appInputStateTextShortensWhileLongPressIsHeld() {
        showField(useAppInputState = true)
        holdWord()
        rule.runOnIdle { text.value = "a" }
        rule.waitForIdle()
        rule.onNodeWithTag("selection-field").performTouchInput { up() }
        rule.waitForIdle()
    }

    @Test
    fun doubleTapWithUnchangedText() {
        showField()
        val node = rule.onNodeWithTag("selection-field")
        node.performTouchInput { doubleClick(center) }
        rule.waitForIdle()
        assertFalse(node.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange].collapsed)
    }
}
