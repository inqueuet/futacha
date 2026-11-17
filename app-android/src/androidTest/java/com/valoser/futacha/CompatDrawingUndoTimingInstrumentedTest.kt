@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.ui.compat.CompatDrawingPoint
import com.valoser.futacha.shared.ui.compat.CompatDrawingStroke
import com.valoser.futacha.shared.ui.compat.CompatDrawingStrokeCache
import org.junit.Test

/**
 * L68 (measurement): an Undo redraws every finished stroke into the canvas-sized bitmap on the main thread. Times
 * one Undo for drawings of growing size on the canvas the screen gives a 1080px-wide phone (90% of its width, at
 * 344:135). Times go to logcat (tag L68) and the instrumentation status.
 */
class CompatDrawingUndoTimingInstrumentedTest {
    private val width = 972
    private val height = (972 * 135f / 344f).toInt()

    private fun strokes(count: Int, pointsEach: Int) = List(count) { s ->
        CompatDrawingStroke(colorArgb = 0xFF000000.toInt() or (s * 2654435761L).toInt(), widthPx = 3f + s % 20,
            points = List(pointsEach) { p -> CompatDrawingPoint((s * 37 + p * 3) % 344f, (s * 11 + p * 2) % 135f) })
    }

    private fun report(what: String, value: Long) {
        Log.i("L68", "$what: $value ms")
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply { putString("stream", "L68 $what: $value ms\n") })
    }

    @Test fun measureUndoOfLargeDrawings() {
        val bitmap = ImageBitmap(width, height)
        val scope = CanvasDrawScope()
        listOf(50 to 100, 200 to 100, 500 to 200, 1_000 to 200, 2_000 to 300).forEach { (count, points) ->
            val drawing = strokes(count, points)
            val cache = CompatDrawingStrokeCache()
            fun render(list: List<CompatDrawingStroke>): Long {
                val start = SystemClock.elapsedRealtime()
                scope.draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
                    cache.draw(this, list, Size(344f, 135f))
                }
                return SystemClock.elapsedRealtime() - start
            }
            render(drawing)
            val undo = render(drawing.dropLast(1))
            report("undo with $count strokes x $points points", undo)
        }
    }
}
