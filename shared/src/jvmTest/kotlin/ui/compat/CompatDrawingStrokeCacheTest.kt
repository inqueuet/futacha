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
import kotlin.test.assertNotEquals

class CompatDrawingStrokeCacheTest {
    private val width = 120
    private val height = 48
    private val background = Color(COMPAT_DRAWING_CANVAS_COLOR_ARGB)

    private fun render(block: DrawScope.() -> Unit): IntArray {
        return renderSized(width, height, block)
    }

    private fun renderSized(width: Int, height: Int, block: DrawScope.() -> Unit): IntArray {
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

    @Test
    fun logicalStrokesRerasteriseAtTheNewPhysicalSizeAndKeepTheAppendCache() {
        val cache = CompatDrawingStrokeCache()
        val logicalSize = Size(344f, 135f)
        val strokes = mutableListOf(stroke(Color.Red, 280f to 30f), stroke(Color.Blue, 280f to 100f))
        val wide = renderSized(688, 270) { cache.draw(this, strokes, logicalSize) }
        assertEquals(Color.Red.toArgb(), wide[60 * 688 + 560])
        assertEquals(Color.Blue.toArgb(), wide[200 * 688 + 560])
        val small = renderSized(344, 135) { cache.draw(this, strokes, logicalSize) }
        assertEquals(Color.Red.toArgb(), small[30 * 344 + 280])
        assertEquals(Color.Blue.toArgb(), small[100 * 344 + 280])
        assertEquals(4, cache.rasterisedStrokeCount)
        strokes += stroke(Color.Black, 80f to 70f)
        val appended = renderSized(344, 135) { cache.draw(this, strokes, logicalSize) }
        assertEquals(Color.Black.toArgb(), appended[70 * 344 + 80])
        renderSized(344, 135) { cache.draw(this, strokes, logicalSize) }
        assertEquals(5, cache.rasterisedStrokeCount)
        val grown = renderSized(688, 270) { cache.draw(this, strokes, logicalSize) }
        assertEquals(Color.Red.toArgb(), grown[60 * 688 + 560])
        assertEquals(Color.Black.toArgb(), grown[140 * 688 + 160])
        assertEquals(8, cache.rasterisedStrokeCount)
    }

    @Test
    fun aChangedLogicalSpaceInvalidatesTheBitmapEvenWhenItsPhysicalSizeStaysTheSame() {
        val cache = CompatDrawingStrokeCache()
        val strokes = listOf(stroke(Color.Red, 30f to 30f))
        val first = render { cache.draw(this, strokes, Size(120f, 48f)) }
        val second = render { cache.draw(this, strokes, Size(240f, 96f)) }
        assertEquals(Color.Red.toArgb(), first[30 * width + 30])
        assertEquals(background.toArgb(), second[30 * width + 30])
        assertNotEquals(background.toArgb(), second[15 * width + 15])
        assertEquals(2, cache.rasterisedStrokeCount)
    }

    /** L68: an Undo of a large drawing redraws from the nearest kept copy, not every stroke. */
    @Test
    fun anUndoRedrawsFromTheNearestCopyNotEveryStroke() {
        val cache = CompatDrawingStrokeCache()
        val colors = listOf(Color.Black, Color.Red, Color.Blue)
        val strokes = MutableList(100) { stroke(colors[it % 3], (it % 110).toFloat() + 5f to 5f + it % 40, 60f to (it % 44).toFloat()) }
        render { cache.draw(this, strokes) }
        assertEquals(100, cache.rasterisedStrokeCount)

        strokes.removeAt(strokes.lastIndex) // Undo
        val afterUndo = render { cache.draw(this, strokes) }
        kotlin.test.assertTrue(cache.rasterisedStrokeCount - 100 < COMPAT_DRAWING_CHECKPOINT_STROKES,
            "one Undo redrew ${cache.rasterisedStrokeCount - 100} strokes")
        assertPixelsClose(render { strokes.forEach { drawCompatDrawingStroke(it) } }, afterUndo)

        repeat(40) { strokes.removeAt(strokes.lastIndex) } // Undo, Undo, ...
        val deeper = render { cache.draw(this, strokes) }
        assertPixelsClose(render { strokes.forEach { drawCompatDrawingStroke(it) } }, deeper)

        strokes += stroke(Color.Red, 2f to 2f, 118f to 46f) // a new stroke after the Undos
        val redrawn = render { cache.draw(this, strokes) }
        assertPixelsClose(render { strokes.forEach { drawCompatDrawingStroke(it) } }, redrawn)

        strokes[0] = stroke(Color.Blue, 100f to 4f) // an early stroke changed: from the start
        val changed = render { cache.draw(this, strokes) }
        assertPixelsClose(render { strokes.forEach { drawCompatDrawingStroke(it) } }, changed)
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
