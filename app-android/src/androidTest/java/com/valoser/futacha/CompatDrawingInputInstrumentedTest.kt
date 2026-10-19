@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.shared.ui.compat.CompatPostDrawingScreen
import com.valoser.futacha.shared.ui.compat.detectCompatDrawingStrokes
import com.valoser.futacha.shared.util.ImageData
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class CompatDrawingInputInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val strokes = mutableListOf<List<Offset>>()
    private val active = mutableListOf<Offset>()
    private var cancelled = 0

    private fun inputCanvas() {
        rule.setContent {
            Canvas(Modifier.fillMaxSize().testTag("input-canvas").pointerInput(Unit) {
                detectCompatDrawingStrokes(
                    onStart = { active.clear(); active.add(it) },
                    onPoint = { active.add(it) },
                    onEnd = { strokes.add(active.toList()); active.clear() },
                    onCancel = { cancelled++; active.clear() }
                )
            }) {}
        }
    }

    @Test fun contactDrawsImmediatelyAndReleaseKeepsAShortStrokeEndpoint() {
        inputCanvas()
        val start = Offset(80f, 100f)
        val end = start + Offset(2f, 1f)
        rule.onNodeWithTag("input-canvas").performTouchInput { down(start) }
        rule.runOnIdle { assertEquals(listOf(start), active); assertTrue(strokes.isEmpty()) }
        // A release can carry the last movement without a separate MOVE event.
        rule.onNodeWithTag("input-canvas").performTouchInput { updatePointerTo(0, end); up() }
        rule.runOnIdle { assertEquals(listOf(start, end), strokes.single()) }
    }

    @Test fun rapidDotsAndShortStrokesAreSeparateUndoableStrokes() {
        inputCanvas()
        rule.onNodeWithTag("input-canvas").performTouchInput {
            repeat(40) { i ->
                val p = Offset(60f + i * 3, 100f)
                down(p)
                if (i % 2 == 1) moveTo(p + Offset(2f, 2f), delayMillis = 8)
                advanceEventTime(8)
                up()
                advanceEventTime(8)
            }
        }
        rule.runOnIdle {
            assertEquals(40, strokes.size)
            strokes.forEachIndexed { i, points ->
                assertEquals(Offset(60f + i * 3, 100f), points.first())
                assertEquals(points.first() + if (i % 2 == 1) Offset(2f, 2f) else Offset.Zero, points.last())
            }
        }
    }

    @Test fun cancellationDropsOnlyTheUnfinishedStrokeAndNextContactWorks() {
        inputCanvas()
        rule.onNodeWithTag("input-canvas").performTouchInput {
            down(Offset(50f, 50f)); up()
            down(Offset(70f, 70f)); moveTo(Offset(72f, 71f)); cancel()
            down(Offset(90f, 90f)); up()
        }
        rule.runOnIdle {
            assertEquals(1, cancelled)
            assertTrue(active.isEmpty())
            assertEquals(listOf(Offset(50f, 50f), Offset(90f, 90f)), strokes.map { it.first() })
        }
    }

    @Test fun secondFingerNeverJoinsThePenOrBecomesItsEndpoint() {
        inputCanvas()
        rule.onNodeWithTag("input-canvas").performTouchInput {
            down(0, Offset(60f, 60f))
            down(1, Offset(200f, 200f))
            moveTo(0, Offset(62f, 61f))
            up(0)
            moveTo(1, Offset(250f, 220f))
            up(1)
            down(Offset(90f, 90f)); up()
        }
        rule.runOnIdle {
            assertEquals(2, strokes.size)
            assertEquals(Offset(62f, 61f), strokes[0].last())
            assertTrue(strokes[0].all { it.x < 100f })
            assertEquals(Offset(90f, 90f), strokes[1].first())
        }
    }

    @Test fun batchedMotionSamplesKeepTheSmallCurveBetweenFrames() {
        inputCanvas()
        rule.runOnUiThread {
            val downTime = SystemClock.uptimeMillis()
            fun dispatch(event: MotionEvent) { try { rule.activity.dispatchTouchEvent(event) } finally { event.recycle() } }
            val root = rule.activity.window.decorView
            val location = IntArray(2)
            root.getLocationOnScreen(location)
            // Coordinates are in the Activity window, including its top inset.
            val bounds = android.graphics.Rect()
            root.getWindowVisibleDisplayFrame(bounds)
            val y = (bounds.top - location[1] + 100).toFloat()
            dispatch(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 80f, y, 0))
            val move = MotionEvent.obtain(downTime, downTime + 8, MotionEvent.ACTION_MOVE, 81f, y + 2f, 0)
            move.addBatch(downTime + 12, 82f, y - 2f, 1f, 1f, 0)
            move.addBatch(downTime + 16, 83f, y + 1f, 1f, 1f, 0)
            dispatch(move)
            dispatch(MotionEvent.obtain(downTime, downTime + 24, MotionEvent.ACTION_UP, 84f, y, 0))
        }
        rule.runOnIdle {
            val points = strokes.single()
            assertEquals(listOf(80f, 81f, 82f, 83f, 84f), points.map { it.x })
            assertEquals(listOf(0f, 2f, -2f, 1f, 0f), points.map { it.y - points[0].y })
        }
    }

    @Test fun batchedExcursionReturningToPreviousPositionIsNotDropped() {
        inputCanvas()
        rule.runOnUiThread {
            val time = SystemClock.uptimeMillis()
            fun dispatch(event: MotionEvent) { try { rule.activity.dispatchTouchEvent(event) } finally { event.recycle() } }
            val bounds = android.graphics.Rect()
            rule.activity.window.decorView.getWindowVisibleDisplayFrame(bounds)
            val y = bounds.top + 100f
            dispatch(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 80f, y, 0))
            dispatch(MotionEvent.obtain(time, time + 4, MotionEvent.ACTION_MOVE, 81f, y + 1f, 0))
            val loop = MotionEvent.obtain(time, time + 8, MotionEvent.ACTION_MOVE, 85f, y + 5f, 0)
            loop.addBatch(time + 10, 82f, y - 2f, 1f, 1f, 0)
            loop.addBatch(time + 12, 81f, y + 1f, 1f, 1f, 0)
            dispatch(loop)
            dispatch(MotionEvent.obtain(time, time + 16, MotionEvent.ACTION_UP, 90f, y, 0))
        }
        rule.runOnIdle {
            val points = strokes.single()
            assertEquals(listOf(80f, 81f, 85f, 82f, 81f, 90f), points.map { it.x })
        }
    }

    @Test fun batchedLoopAndReleaseOnlyTailSurviveTheRealDrawingScreen() {
        val saved = AtomicReference<ImageData?>()
        rule.setContent { MaterialTheme {
            CompatPostDrawingScreen(onSaved = { saved.set(it) }, onBack = {}, forceLandscape = false)
        } }
        val canvas = rule.onNodeWithTag("compat-drawing-canvas")
        val bounds = canvas.fetchSemanticsNode().boundsInWindow
        val time = SystemClock.uptimeMillis()
        fun windowPoint(x: Float, y: Float) = Offset(bounds.left + x * bounds.width / 344, bounds.top + y * bounds.height / 135)
        fun event(action: Int, t: Long, x: Float, y: Float): MotionEvent {
            val p = windowPoint(x, y)
            return MotionEvent.obtain(time, t, action, p.x, p.y, 0)
        }
        fun dispatch(e: MotionEvent) { try { rule.activity.dispatchTouchEvent(e) } finally { e.recycle() } }
        fun MotionEvent.sample(t: Long, x: Float, y: Float) {
            val p = windowPoint(x, y)
            addBatch(t, p.x, p.y, 1f, 1f, 0)
        }
        val bends = listOf(60 to 90, 100 to 20, 140 to 85)
        rule.runOnUiThread {
            dispatch(event(MotionEvent.ACTION_DOWN, time, 20f, 40f))
            dispatch(event(MotionEvent.ACTION_MOVE, time + 4, 30f, 40f))
            val loop = event(MotionEvent.ACTION_MOVE, time + 8, 60f, 90f)
            loop.sample(time + 10, 100f, 20f)
            loop.sample(time + 12, 140f, 85f)
            loop.sample(time + 16, 30f, 40f) // Ends exactly at the previous event's position.
            dispatch(loop)
        }
        fun checkPreview(points: List<Pair<Int, Int>>) {
            val pixels = canvas.captureToImage().toPixelMap()
            points.forEach { (x, y) ->
                assertEquals("Preview lost $x,$y", 0xFF800000.toInt(),
                    pixels[(x * pixels.width / 344f).toInt(), (y * pixels.height / 135f).toInt()].toArgb())
            }
        }
        checkPreview(bends) // Check while still pressed, not just after committing the stroke.
        rule.runOnUiThread {
            val endTime = SystemClock.uptimeMillis() + 4
            val up = event(MotionEvent.ACTION_MOVE, endTime, 180f, 30f)
            up.sample(endTime + 2, 220f, 90f)
            up.sample(endTime + 4, 300f, 110f)
            up.action = MotionEvent.ACTION_UP // Preserve the history on the release itself.
            dispatch(up)
        }
        val all = bends + listOf(180 to 30, 220 to 90, 300 to 110)
        checkPreview(all)
        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("保存する").performClick()
        rule.onNodeWithText("保存する").performClick()
        rule.waitUntil(5_000) { saved.get() != null }
        val data = saved.get()!!
        val bitmap = BitmapFactory.decodeByteArray(data.bytes, 0, data.bytes.size)!!
        try {
            all.forEach { (x, y) -> assertEquals("PNG lost $x,$y", 0xFF800000.toInt(), bitmap.getPixel(x, y)) }
            assertEquals(0xFFF0E0D6.toInt(), bitmap.getPixel(300, 15))
        } finally { bitmap.recycle() }
    }

    @Test fun shortStrokesSurvivePngExportUndoRedoAndClear() {
        val saved = AtomicReference<ImageData?>()
        rule.setContent { MaterialTheme {
            CompatPostDrawingScreen(onSaved = { saved.set(it) }, onBack = {}, forceLandscape = false)
        } }
        val centers = mutableListOf<Offset>()
        var canvasWidth = 0
        var canvasHeight = 0
        rule.onNodeWithTag("compat-drawing-canvas").performTouchInput {
            canvasWidth = width; canvasHeight = height
            repeat(24) { i ->
                val p = Offset(width * (0.12f + (i % 6) * 0.15f), height * (0.2f + (i / 6) * 0.2f))
                centers.add(p)
                down(p)
                if (i % 2 == 1) moveTo(p + Offset(2f, 1f), delayMillis = 8)
                advanceEventTime(8); up(); advanceEventTime(8)
            }
        }
        fun export(): IntArray {
            saved.set(null)
            rule.onNodeWithContentDescription("その他").performClick()
            rule.onNodeWithText("保存する").performClick()
            rule.onNodeWithText("保存する").performClick()
            rule.waitUntil(5_000) { saved.get() != null }
            val data = saved.get()!!
            assertTrue(data.isHandwriting)
            val bitmap = BitmapFactory.decodeByteArray(data.bytes, 0, data.bytes.size)!!
            assertEquals(344, bitmap.width); assertEquals(135, bitmap.height)
            return try { IntArray(344 * 135).also { bitmap.getPixels(it, 0, 344, 0, 0, 344, 135) } }
            finally { bitmap.recycle() }
        }
        val original = export()
        centers.forEach { p ->
            val x = (p.x * 344 / canvasWidth).toInt()
            val y = (p.y * 135 / canvasHeight).toInt()
            assertEquals("Missing stroke at $p", 0xFF800000.toInt(), original[y * 344 + x])
        }
        repeat(24) { rule.onNodeWithContentDescription("元に戻す").assertIsEnabled().performClick() }
        rule.onNodeWithContentDescription("元に戻す").assertIsNotEnabled()
        assertTrue(export().all { it == 0xFFF0E0D6.toInt() })
        repeat(24) { rule.onNodeWithContentDescription("やり直す").assertIsEnabled().performClick() }
        assertArrayEquals(original, export())
        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("クリアー").performClick()
        rule.onNodeWithText("クリアー").performClick()
        assertTrue(export().all { it == 0xFFF0E0D6.toInt() })
    }
}
