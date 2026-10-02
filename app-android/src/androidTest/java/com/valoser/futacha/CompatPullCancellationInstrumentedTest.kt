@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.shared.ui.compat.CompatBidirectionalPullRefresh
import com.valoser.futacha.shared.ui.theme.FutachaTheme
import org.junit.Rule
import org.junit.Test

class CompatPullCancellationInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun refreshStartingDuringDragClearsTheGestureOwnedHint() {
        val refreshing = mutableStateOf(false)
        rule.setContent {
            FutachaTheme {
                CompatBidirectionalPullRefresh(true, refreshing.value, { false }, { false }, {}, Modifier.fillMaxSize()) {
                    Text("content")
                }
            }
        }
        val surface = rule.onNodeWithTag("compat-pull-refresh")
        surface.performTouchInput { down(center); moveBy(Offset(0f, 120f), 100) }
        rule.onNodeWithText("画面を引っ張って…").assertExists()
        rule.runOnUiThread { refreshing.value = true }
        rule.waitForIdle()
        rule.runOnUiThread { refreshing.value = false }
        surface.performTouchInput { up() }
        rule.waitForIdle()
        rule.onNodeWithText("画面を引っ張って…").assertDoesNotExist()
        rule.onNodeWithText("読み込み中…").assertDoesNotExist()
        surface.performTouchInput { down(center); moveBy(Offset(0f, 120f), 100) }
        rule.onNodeWithText("画面を引っ張って…").assertExists()
        surface.performTouchInput { up() }
    }
}
