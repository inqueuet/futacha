@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.ui.compat.CompatPostDrawingScreen
import com.valoser.futacha.shared.util.ImageData
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicReference

/** Resize the real product canvas without replacing its composition; no posting, storage I/O or audio. */
class CompatDrawingResizeInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var width by mutableStateOf(360)
    private val saved = AtomicReference<ImageData?>()
    private val ink = 0xFF800000.toInt()
    private val paper = 0xFFF0E0D6.toInt()
    @After fun cleanup() { rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle() }
    private fun open() {
        rule.setContent { MaterialTheme {
            Box(Modifier.width(width.dp).fillMaxHeight()) {
                CompatPostDrawingScreen(onSaved = { saved.set(it) }, onBack = {}, forceLandscape = false)
            }
        } }
        rule.waitForIdle()
    }
    private fun resize(next: Int) { rule.runOnUiThread { width = next }; rule.waitForIdle() }
    private fun point(x: Int, y: Int) {
        rule.onNodeWithTag("compat-drawing-canvas").performTouchInput {
            click(Offset(x * this.width / 344f, y * this.height / 135f))
        }
    }
    private fun preview(vararg points: Pair<Int, Int>) {
        val pixels = rule.onNodeWithTag("compat-drawing-canvas").captureToImage().toPixelMap()
        points.forEach { (x, y) ->
            assertEquals("Ink at logical $x,$y after host width=$width", ink,
                pixels[(x * pixels.width / 344f).toInt(), (y * pixels.height / 135f).toInt()].toArgb())
        }
    }
    private fun png(): IntArray {
        saved.set(null)
        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("保存する").performClick()
        rule.onNodeWithText("保存する").performClick()
        rule.waitUntil(10_000) { saved.get() != null }
        val image = checkNotNull(saved.get())
        val bitmap = checkNotNull(BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size))
        try {
            assertEquals(344, bitmap.width); assertEquals(135, bitmap.height)
            return IntArray(344 * 135).also { bitmap.getPixels(it, 0, 344, 0, 0, 344, 135) }
        } finally { bitmap.recycle() }
    }
    @Test fun finishedAndRedoStrokesKeepTheirPreviewAndEveryExportPixelAcrossResizes() {
        open(); point(280, 30); point(280, 100)
        val both = png()
        rule.onNodeWithContentDescription("元に戻す").performClick()
        val first = png()
        assertEquals(paper, first[100 * 344 + 280])
        resize(240); preview(280 to 30)
        assertArrayEquals("Finished stroke export must not change when the canvas shrinks", first, png())
        rule.onNodeWithContentDescription("やり直す").performClick(); preview(280 to 30, 280 to 100)
        assertArrayEquals("Redo must restore the same logical stroke on the smaller canvas", both, png())
        point(80, 70); preview(80 to 70)
        val withNewStroke = png()
        assertEquals(ink, withNewStroke[70 * 344 + 80])
        resize(360); preview(280 to 30, 280 to 100, 80 to 70)
        assertArrayEquals("Old and new strokes must export identically after growing again", withNewStroke, png())
    }
    @Test fun anActiveStrokeKeepsItsEarlierPointsWhileNewPointsUseTheResizedCanvas() {
        open()
        val downTime = SystemClock.uptimeMillis()
        fun dispatch(action: Int, x: Float, y: Float) {
            val bounds = rule.onNodeWithTag("compat-drawing-canvas").fetchSemanticsNode().boundsInWindow
            val px = bounds.left + x * bounds.width / 344f
            val py = bounds.top + y * bounds.height / 135f
            rule.runOnUiThread {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, px, py, 0)
                try { rule.activity.dispatchTouchEvent(event) } finally { event.recycle() }
            }
        }
        dispatch(MotionEvent.ACTION_DOWN, 40f, 30f); dispatch(MotionEvent.ACTION_MOVE, 100f, 30f)
        preview(40 to 30, 100 to 30)
        resize(240); preview(40 to 30, 100 to 30)
        dispatch(MotionEvent.ACTION_MOVE, 180f, 80f); dispatch(MotionEvent.ACTION_UP, 300f, 110f)
        preview(40 to 30, 100 to 30, 180 to 80, 300 to 110)
        val resized = png()
        for ((x, y) in listOf(40 to 30, 100 to 30, 180 to 80, 300 to 110)) assertEquals(ink, resized[y * 344 + x])
        resize(360); preview(40 to 30, 100 to 30, 180 to 80, 300 to 110)
        assertArrayEquals(resized, png())
    }
}
