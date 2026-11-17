@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.ui.compat.CompatScrollIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.*
import org.junit.Assert.*

class CompatScrollbarRailInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val state = LazyListState()
    private lateinit var scope: CoroutineScope
    @Before fun start() {
        com.valoser.futacha.shared.ui.initializeFutachaPointerDispatch()
        rule.setContent {
            scope = rememberCoroutineScope()
            MaterialTheme { Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize().testTag("rail-body"), state = state) {
                    items((0 until 200).toList()) { Text("row-$it", Modifier.fillMaxWidth().height(60.dp)) }
                }
                CompatScrollIndicator(true, state)
            } }
        }
        rule.waitForIdle()
    }
    @After fun cleanup() { rule.runOnUiThread { rule.activity.setContent {} } }

    @Test fun touchAnywhereOnTheRailJumpsAndCanContinueDragging() {
        rule.onNodeWithTag("compat-scroll-indicator-drag").performTouchInput {
            down(Offset(center.x, height * .75f))
            advanceEventTime(40)
            moveTo(Offset(center.x, height * .25f))
            advanceEventTime(40)
            up()
        }
        rule.waitForIdle()
        assertTrue("rail drag should end near a quarter: ${state.firstVisibleItemIndex}", state.firstVisibleItemIndex in 45..55)
        rule.onNodeWithTag("compat-scroll-indicator-drag").performTouchInput { click(Offset(center.x, height * .70f)) }
        rule.waitForIdle()
        assertTrue(state.firstVisibleItemIndex in 135..145)
    }

    @Test fun aNormalDragNearTheRightEdgeDoesNotBecomeAnAbsoluteJump() {
        rule.runOnUiThread { scope.launch { state.scrollToItem(40) } }
        rule.waitForIdle()
        val railWidth = rule.onNodeWithTag("compat-scroll-indicator-drag").fetchSemanticsNode().boundsInRoot.width
        rule.onNodeWithTag("rail-body").performTouchInput {
            val x = width - railWidth * 1.7f
            swipe(Offset(x, height * .65f), Offset(x, height * .55f), durationMillis = 500)
        }
        rule.waitForIdle()
        assertTrue("a short normal drag jumped to ${state.firstVisibleItemIndex}", state.firstVisibleItemIndex in 40..55)
    }
}
