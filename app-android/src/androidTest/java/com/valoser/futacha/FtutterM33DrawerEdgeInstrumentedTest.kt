@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.ui.compat.CompatFastScrollbar
import com.valoser.futacha.shared.ui.ftutter.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

/** Actual drawer/back button/scrollbar, existing production APIs only; no network or sound. */
class FtutterM33DrawerEdgeInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val drawer = DrawerState(DrawerValue.Closed)
    private val list = LazyListState()
    private var side by mutableStateOf(FtutterDrawerSide.LEFT)
    private lateinit var scope: CoroutineScope
    private var backs = 0
    private var actions = 0
    private var cells = 0
    private var leftTools = 0
    private var rightTools = 0

    private fun start(initialSide: FtutterDrawerSide, listContent: Boolean = false, drag: Boolean = true) {
        side = initialSide
        rule.setContent {
            scope = rememberCoroutineScope()
            MaterialTheme { FtutterTheme(null, FtutterBrightness.LIGHT) {
                FtutterDrawerHost(drawer, FtutterTabSettings(drawerSide = side, drawerOpenByDrag = drag), true,
                    drawer = { Text("M33 drawer") }) {
                    Column(Modifier.fillMaxSize()) {
                        FtutterTopBar("M33", onBack = { backs++ }) {
                            TextButton({ actions++ }, Modifier.testTag("m33-action")) { Text("action") }
                        }
                        Box(Modifier.weight(1f).fillMaxWidth().testTag("m33-content")) {
                            if (listContent) {
                                LazyColumn(Modifier.fillMaxSize(), state = list) {
                                    items(200) { Text("ROW $it", Modifier.fillMaxWidth().height(48.dp)) }
                                }
                                CompatFastScrollbar(true, 200, list.firstVisibleItemIndex,
                                    list.layoutInfo.visibleItemsInfo.size, list.isScrollInProgress, list::scrollToItem,
                                    alwaysVisible = true, onLeft = side == FtutterDrawerSide.LEFT)
                            } else {
                                Box(Modifier.fillMaxSize().clickable { cells++ }.testTag("m33-cell"))
                            }
                        }
                        Row(Modifier.fillMaxWidth().height(56.dp)) {
                            Box(Modifier.weight(1f).fillMaxHeight().clickable { leftTools++ }.testTag("m33-tool-left"))
                            Box(Modifier.weight(1f).fillMaxHeight().clickable { rightTools++ }.testTag("m33-tool-right"))
                        }
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

    private fun tapEdge(tag: String, right: Boolean = false) {
        rule.onNodeWithTag(tag).performTouchInput { click(Offset(if (right) width - 5f else 5f, height / 2f)) }
    }
    private fun swipeIn(right: Boolean, atEdge: Boolean = true) {
        rule.onNodeWithTag("m33-content").performTouchInput {
            val x = if (!atEdge) width / 2f else if (right) width - 5f else 5f
            swipe(Offset(x, height / 2f), Offset(x + if (right) -width / 3f else width / 3f, height / 2f), 400)
        }
        rule.waitForIdle()
    }
    private fun assertControls(left: Boolean, right: Boolean) {
        if (left) {
            tapEdge("ftutter-back"); assertEquals(1, backs)
            tapEdge("m33-cell"); assertEquals(1, cells)
            tapEdge("m33-tool-left"); assertEquals(1, leftTools)
        }
        if (right) {
            tapEdge("m33-action", true); assertEquals(1, actions)
            tapEdge("m33-cell", true); assertEquals(if (left) 2 else 1, cells)
            tapEdge("m33-tool-right", true); assertEquals(1, rightTools)
        }
        assertTrue(drawer.isClosed)
    }

    @Test fun leftEdgePassesBackCellAndToolbarTaps() {
        start(FtutterDrawerSide.LEFT); assertControls(left = true, right = false)
    }
    @Test fun rightEdgePassesActionCellAndToolbarTaps() {
        start(FtutterDrawerSide.RIGHT); assertControls(left = false, right = true)
    }
    @Test fun bothEdgesPassTheirControls() {
        start(FtutterDrawerSide.BOTH); assertControls(left = true, right = true)
    }

    @Test fun leftScrollbarAndOrdinaryEdgeReadingSwipeRemainUsable() {
        verifyVerticalGestures(FtutterDrawerSide.LEFT, right = false)
    }
    @Test fun rightScrollbarRemainsUsableWithBothDrawerEdgesEnabled() {
        verifyVerticalGestures(FtutterDrawerSide.BOTH, right = true)
    }
    private fun verifyVerticalGestures(drawerSide: FtutterDrawerSide, right: Boolean) {
        start(drawerSide, listContent = true)
        rule.waitUntil(5_000) { list.layoutInfo.visibleItemsInfo.isNotEmpty() }
        rule.onNodeWithTag("m33-content").performTouchInput {
            val x = if (right) width - 5f else 5f
            swipe(Offset(x, height * 0.8f), Offset(x, height * 0.4f), 400)
        }
        rule.waitUntil(5_000) { list.firstVisibleItemIndex > 0 }
        assertTrue(drawer.isClosed)
        rule.runOnUiThread { scope.launch { list.scrollToItem(0) } }
        rule.waitUntil(5_000) { list.firstVisibleItemIndex == 0 && !list.isScrollInProgress }
        rule.onNodeWithTag("compat-fast-scrollbar").performTouchInput {
            val x = if (right) width - 5f else 5f
            swipe(Offset(x, 5f), Offset(x, height * 0.5f), 400)
        }
        rule.waitUntil(5_000) { list.firstVisibleItemIndex > 50 }
        assertTrue(drawer.isClosed)
    }

    @Test fun configuredEdgesOpenByInwardSwipeAndCloseByOutwardSwipe() {
        start(FtutterDrawerSide.LEFT)
        for ((setting, right) in listOf(FtutterDrawerSide.LEFT to false, FtutterDrawerSide.RIGHT to true,
            FtutterDrawerSide.BOTH to true, FtutterDrawerSide.BOTH to false)) {
            rule.runOnUiThread { side = setting }; rule.waitForIdle()
            swipeIn(right, atEdge = false); assertTrue(drawer.isClosed)
            swipeIn(right); rule.waitUntil(5_000) { drawer.isOpen }
            rule.onNodeWithTag("ftutter-drawer").performTouchInput {
                swipe(Offset(width * if (right) 0.2f else 0.8f, height / 2f),
                    Offset(width * if (right) 0.95f else 0.05f, height / 2f), 400)
            }
            rule.waitUntil(5_000) { drawer.isClosed }
        }
    }

    @Test fun turningDragOffOrDisablingTheDrawerLeavesEdgeControlsUsable() {
        start(FtutterDrawerSide.BOTH, drag = false)
        assertControls(left = true, right = true)
        swipeIn(false); swipeIn(true); assertTrue(drawer.isClosed)
        rule.runOnUiThread { side = FtutterDrawerSide.NONE }; rule.waitForIdle()
        tapEdge("ftutter-back"); tapEdge("m33-action", true)
        assertEquals(2, backs); assertEquals(2, actions)
        swipeIn(false); swipeIn(true); assertTrue(drawer.isClosed)
    }
}
