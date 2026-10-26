@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.shared.ui.ftutter.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * M78: with the polyline gesture off and "swipe to the next tab" on, the swipe that opens the drawer from
 * the edge belongs to the drawer. Its remaining movement must not also move to another tab.
 */
class FtutterM78EdgeSwipeInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val drawer = DrawerState(DrawerValue.Closed)
    private val swipes = mutableListOf<Boolean>()

    private fun start(side: FtutterDrawerSide) {
        rule.setContent {
            MaterialTheme { FtutterTheme(null, FtutterBrightness.LIGHT) {
                FtutterDrawerHost(drawer, FtutterTabSettings(drawerSide = side, drawerOpenByDrag = true), true,
                    drawer = { Text("M78 drawer") }) {
                    FtutterGestureLayer(FtutterGestureSettings(enabled = false, moveTabBySwipe = true), enabled = true,
                        onAction = {}, modifier = Modifier.fillMaxSize(), onSwipe = { swipes += it }) {
                        Box(Modifier.fillMaxSize().testTag("m78-content"))
                    }
                }
            } }
        }
        rule.waitForIdle()
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        rule.waitForIdle()
    }

    private fun swipe(fromX: (Float) -> Float, toX: (Float) -> Float, duration: Long = 500) {
        rule.onNodeWithTag("m78-content").performTouchInput {
            swipe(Offset(fromX(width.toFloat()), height / 2f), Offset(toX(width.toFloat()), height / 2f), duration)
        }
        rule.waitForIdle()
    }

    @Test fun aLongEdgeSwipeOpensTheDrawerWithoutAlsoMovingTheTab() {
        start(FtutterDrawerSide.LEFT)
        swipe({ 5f }, { it * 0.7f })
        assertTrue("the edge swipe must open the drawer", drawer.isOpen)
        assertEquals("the drawer's swipe must not also move the tab", emptyList<Boolean>(), swipes.toList())
    }

    @Test fun aLongRightEdgeSwipeOpensTheRightDrawerWithoutMovingTheTab() {
        start(FtutterDrawerSide.RIGHT)
        swipe({ it - 5f }, { it * 0.3f })
        assertTrue(drawer.isOpen)
        assertEquals(emptyList<Boolean>(), swipes.toList())
    }

    @Test fun aSlowSwipeKeepsTheWholeGestureAfterTheDrawerHasOpened() {
        start(FtutterDrawerSide.LEFT)
        swipe({ 5f }, { it * 0.9f }, 3000)
        assertTrue(drawer.isOpen)
        assertEquals(emptyList<Boolean>(), swipes.toList())
    }

    @Test fun resettingTheHistoryLimitAlsoAsksBeforeItCanCloseTabs() {
        val saved = mutableListOf<String>()
        rule.setContent { MaterialTheme { FtutterTheme(null, FtutterBrightness.LIGHT) {
            FtutterSliderSetting("履歴の保存数", "test-limit", 1.0, 1000.0, 1.0, 200.0,
                mapOf("test-limit" to "300"), save = { _, value -> saved += value },
                confirm = { if (it < 250) "タブ50件が閉じられます" else null })
        } } }
        rule.onNodeWithTag("ftutter-setting-test-limit").performClick()
        rule.onNodeWithText("リセット").performClick()
        rule.onNodeWithTag("ftutter-slider-confirm").assertExists()
        assertTrue(saved.isEmpty())
        rule.onNodeWithTag("ftutter-slider-confirm-cancel").performClick()
        assertTrue(saved.isEmpty())
        rule.onNodeWithTag("ftutter-setting-test-limit").performClick()
        rule.onNodeWithText("リセット").performClick()
        rule.onNodeWithTag("ftutter-slider-confirm").performClick()
        assertEquals(listOf(""), saved)
    }

    @Test fun anOrdinarySwipeFromTheMiddleStillMovesTheTab() {
        start(FtutterDrawerSide.LEFT)
        swipe({ it * 0.8f }, { it * 0.2f })
        assertFalse(drawer.isOpen)
        assertEquals(listOf(true), swipes.toList())
    }
}
