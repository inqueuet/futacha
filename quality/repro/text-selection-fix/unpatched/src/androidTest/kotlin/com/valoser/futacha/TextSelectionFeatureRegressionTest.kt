package com.valoser.futacha

import android.view.KeyEvent
import android.view.textclassifier.TextClassification
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextClassifier
import android.view.textclassifier.TextSelection
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

/** Verify ordinary selection and actual smart features after applying the Android guard.
 * The recorder delegates normal requests to the actual device classifier. No feature flag changes.
 */
class TextSelectionFeatureRegressionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val state = androidx.compose.runtime.mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("alpha beta gamma"))
    private var injectedResponse: ((TextSelection.Request) -> TextSelection)? = null
    private var injectedClassification: TextClassification? = null
    private val requests = CopyOnWriteArrayList<TextSelection.Request>()
    private lateinit var manager: TextClassificationManager
    private lateinit var originalClassifier: TextClassifier
    private var originalAccessibilityFlags = 0

    @Before fun recordRequests() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val info = automation.serviceInfo
        originalAccessibilityFlags = info.flags
        info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
        manager = rule.activity.getSystemService(TextClassificationManager::class.java)
        originalClassifier = manager.textClassifier
        manager.setTextClassifier(object : TextClassifier {
            override fun suggestSelection(request: TextSelection.Request): TextSelection {
                requests.add(request)
                return injectedResponse?.invoke(request) ?: originalClassifier.suggestSelection(request)
            }
            override fun classifyText(request: TextClassification.Request): TextClassification =
                injectedClassification ?: originalClassifier.classifyText(request)
        })
    }

    @After fun restoreClassifier() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val info = automation.serviceInfo
        info.flags = originalAccessibilityFlags
        automation.serviceInfo = info
        if (::manager.isInitialized && ::originalClassifier.isInitialized) {
            manager.setTextClassifier(originalClassifier)
        }
    }

    private fun showAndHold() {
        rule.setContent {
            MaterialTheme {
                TextField(value = state.value, onValueChange = { state.value = it },
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
        assertEquals("gamma", state.value.text.substring(selection.min, selection.max))
    }

    private fun backspace() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DEL)
        rule.waitForIdle()
        assertEquals("alpha beta ", state.value.text.toString())
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

    private fun visibleNativeText(expected: String): Boolean {
        fun contains(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            if (node.text?.toString() == expected || node.contentDescription?.toString() == expected) return true
            return (0 until node.childCount).any { contains(node.getChild(it)) }
        }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        return automation.windows.any { contains(it.root) }
    }

    @Test fun smartExpansionAndMenuActionRemainAvailable() {
        val activity = rule.activity
        val action = android.app.RemoteAction(
            android.graphics.drawable.Icon.createWithResource(activity, android.R.drawable.ic_menu_search),
            "判別", "判別",
            android.app.PendingIntent.getActivity(activity, 9001,
                android.content.Intent(activity, ComponentActivity::class.java),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        )
        // A toolbar can request classification separately from selection expansion.
        // Give both requests the same deterministic action, regardless of their order.
        val classification = TextClassification.Builder().addAction(action).build()
        injectedClassification = classification
        injectedResponse = { TextSelection.Builder(0, 16)
            .setTextClassification(classification).build() }
        showAndHold()
        release()
        rule.waitUntil(5_000) { state.value.selection == androidx.compose.ui.text.TextRange(0, 16) }
        try {
            rule.waitUntil(5_000) { visibleNativeText("判別") }
        } catch (timeout: androidx.compose.ui.test.ComposeTimeoutException) {
            val appLabels = mutableListOf<String>()
            fun collect(node: android.view.accessibility.AccessibilityNodeInfo?) {
                if (node == null) return
                if (node.packageName?.toString() == rule.activity.packageName) {
                    node.text?.let { appLabels.add(it.toString()) }
                    node.contentDescription?.let { appLabels.add(it.toString()) }
                }
                repeat(node.childCount) { collect(node.getChild(it)) }
            }
            InstrumentationRegistry.getInstrumentation().uiAutomation.windows.forEach { collect(it.root) }
            throw AssertionError("Smart menu not found; diagnostic activity labels: $appLabels", timeout)
        }
    }

    @Test fun classifierFailureKeepsOrdinarySelectionAndCopyMenu() {
        injectedResponse = { throw IllegalStateException("diagnostic failure") }
        showAndHold()
        release()
        rule.waitUntil(5_000) { requests.isNotEmpty() }
        rule.waitForIdle()
        assertEquals(androidx.compose.ui.text.TextRange(11, 16), state.value.selection)
        rule.waitUntil(5_000) { visibleNativeText(rule.activity.getString(android.R.string.copy)) }
    }
}
