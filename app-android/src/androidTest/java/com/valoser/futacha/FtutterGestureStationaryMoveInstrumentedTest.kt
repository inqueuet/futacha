@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.shared.ui.initializeFutachaPointerDispatch
import com.valoser.futacha.shared.ui.ftutter.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Android MotionEvents through the real product GestureLayer; no network or sound. */
class FtutterGestureStationaryMoveInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val stationaryMoves = AtomicInteger()
    private val actions = mutableListOf<FtutterGestureAction>()

    @Before fun enableProductPointerDispatch() {
        initializeFutachaPointerDispatch()
    }

    private fun show(map: Map<String, FtutterGestureAction> = FTUTTER_GESTURE_DEFAULTS) {
        rule.setContent {
            FtutterTheme(null, FtutterBrightness.LIGHT) {
                Box(Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.pressed && it.previousPressed && it.position == it.previousPosition }) {
                                stationaryMoves.incrementAndGet()
                            }
                        }
                    }
                }) {
                    FtutterGestureLayer(FtutterGestureSettings(map = map), true, { actions.add(it) },
                        modifier = Modifier.fillMaxSize()) {}
                }
            }
        }
    }

    @Test fun stationaryMovesInsideTheUpStrokeStillSelectAndRunOpenDrawer() {
        show()
        stroke(listOf(Offset(0f, -3f) to true)) {
            rule.onNodeWithText("ドロワーを開く").assertIsDisplayed()
        }
        rule.runOnIdle {
            assertTrue("Stationary MOVE events must actually reach Compose", stationaryMoves.get() >= 8)
            assertEquals(listOf(FtutterGestureAction.OPEN_DRAWER), actions)
        }
        rule.onNodeWithTag("ftutter-gesture-hint").assertDoesNotExist()
    }

    @Test fun stationaryMovesInsideTheLastRightStrokeDoNotRunCloseTabInsteadOfRefresh() {
        show(FTUTTER_GESTURE_DEFAULTS + ("131" to FtutterGestureAction.REFRESH))
        stroke(listOf(Offset(0f, 3f) to false, Offset(3f, 0f) to true)) {
            rule.onNodeWithText("更新").assertIsDisplayed()
        }
        rule.runOnIdle {
            assertTrue("Stationary MOVE events must actually reach Compose", stationaryMoves.get() >= 8)
            assertEquals(listOf(FtutterGestureAction.REFRESH), actions)
        }
    }

    /** Each pair is a per-sample dp movement and whether a stationary MOVE follows it. */
    private fun stroke(segments: List<Pair<Offset, Boolean>>, beforeRelease: () -> Unit) {
        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        var x = 0f
        var y = 0f
        fun dispatch(action: Int) {
            val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { rule.activity.dispatchTouchEvent(event) } finally { event.recycle() }
            eventTime += 16
        }
        rule.runOnUiThread {
            val root = rule.activity.window.decorView
            val location = IntArray(2)
            root.getLocationOnScreen(location)
            val visible = Rect()
            root.getWindowVisibleDisplayFrame(visible)
            x = root.width * 0.25f
            y = visible.top - location[1] + visible.height() * 0.5f
            val density = rule.activity.resources.displayMetrics.density
            dispatch(MotionEvent.ACTION_DOWN)
            // Clear the original pan slop and start the mandatory first horizontal segment.
            repeat(10) { x += 8f * density; dispatch(MotionEvent.ACTION_MOVE) }
            segments.forEach { (deltaDp, pause) ->
                repeat(8) {
                    x += deltaDp.x * density
                    y += deltaDp.y * density
                    dispatch(MotionEvent.ACTION_MOVE)
                    if (pause) dispatch(MotionEvent.ACTION_MOVE)
                }
            }
        }
        rule.waitForIdle()
        try { beforeRelease() } finally {
            rule.runOnUiThread { dispatch(MotionEvent.ACTION_UP) }
        }
        rule.waitForIdle()
    }
}
