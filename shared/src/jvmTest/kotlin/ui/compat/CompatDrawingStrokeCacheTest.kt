package com.valoser.futacha.shared.ui.compat

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

class CompatDrawingStrokeCacheTest {
    private val width = 120
    private val height = 48
    private val background = Color(COMPAT_DRAWING_CANVAS_COLOR_ARGB)

    private fun render(block: DrawScope.() -> Unit): IntArray {
        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            drawRect(background)
            block()
        }
        val map = bitmap.toPixelMap()
        return IntArray(width * height) { map[it % width, it / width].toArgb() }
    }

    private fun stroke(color: Color, vararg points: Pair<Float, Float>) = CompatDrawingStroke(
        colorArgb = color.toArgb(),
        widthPx = 6f,
        points = points.map { CompatDrawingPoint(it.first, it.second) }
    )

    @Test
    fun appendedStrokesAreRasterisedOnceAndMatchDirectDrawing() {
        val cache = CompatDrawingStrokeCache()
        val strokes = mutableListOf(stroke(Color.Black, 5f to 5f, 60f to 30f))
        render { cache.draw(this, strokes) }
        strokes += stroke(Color.Red, 10f to 40f, 110f to 8f, 90f to 44f)
        strokes += stroke(Color.Blue, 30f to 20f)
        render { cache.draw(this, strokes) }
        val cached = render { cache.draw(this, strokes) }

        // Each stroke drawn once in total, not once per frame.
        assertEquals(3, cache.rasterisedStrokeCount)
        val direct = render { strokes.forEach { drawCompatDrawingStroke(it) } }
        assertPixelsClose(direct, cached)
    }

    @Test
    fun undoRedoAndClearRenderTheCurrentListOnly() {
        val cache = CompatDrawingStrokeCache()
        val first = stroke(Color.Black, 5f to 5f, 60f to 30f)
        val second = stroke(Color.Red, 10f to 40f, 110f to 8f)
        val strokes = mutableListOf(first, second)
        render { cache.draw(this, strokes) }

        strokes.removeAt(strokes.lastIndex) // Undo
        val afterUndo = render { cache.draw(this, strokes) }
        assertPixelsClose(render { drawCompatDrawingStroke(first) }, afterUndo)

        strokes += second // Redo
        val afterRedo = render { cache.draw(this, strokes) }
        assertPixelsClose(render { drawCompatDrawingStroke(first); drawCompatDrawingStroke(second) }, afterRedo)

        // Undo followed by a new stroke within one frame must not reuse the old bitmap.
        strokes.removeAt(strokes.lastIndex)
        val replacement = stroke(Color.Blue, 100f to 40f, 20f to 10f)
        strokes += replacement
        val afterReplace = render { cache.draw(this, strokes) }
        assertPixelsClose(render { drawCompatDrawingStroke(first); drawCompatDrawingStroke(replacement) }, afterReplace)

        strokes.clear()
        assertPixelsClose(render { }, render { cache.draw(this, strokes) })
    }

    private fun assertPixelsClose(expected: IntArray, actual: IntArray) {
        var maxDelta = 0
        for (index in expected.indices) {
            for (shift in intArrayOf(0, 8, 16, 24)) {
                val delta = kotlin.math.abs((expected[index] ushr shift and 0xFF) - (actual[index] ushr shift and 0xFF))
                if (delta > maxDelta) maxDelta = delta
            }
        }
        // Compositing a premultiplied layer can differ by rounding only.
        kotlin.test.assertTrue(maxDelta <= 2, "max channel delta $maxDelta")
    }
}
