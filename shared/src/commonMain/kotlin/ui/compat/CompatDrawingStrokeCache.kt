package com.valoser.futacha.shared.ui.compat

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.ceil

internal fun DrawScope.drawCompatDrawingStroke(stroke: CompatDrawingStroke) {
    val points = stroke.points
    if (points.isEmpty()) return
    if (points.size == 1) {
        drawCircle(
            color = Color(stroke.colorArgb),
            radius = stroke.widthPx / 2f,
            center = Offset(points[0].x, points[0].y)
        )
    } else {
        for (index in 1 until points.size) {
            val from = points[index - 1]
            val to = points[index]
            drawLine(
                color = Color(stroke.colorArgb),
                start = Offset(from.x, from.y),
                end = Offset(to.x, to.y),
                strokeWidth = stroke.widthPx,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Keeps finished strokes rasterised in one bitmap. Redrawing every finished
 * stroke for every frame of the active stroke made drawing slower as the
 * picture grew (E-12). Appended strokes are drawn into the bitmap once; any
 * other change (Undo, clear, resize) re-renders the list.
 */
internal class CompatDrawingStrokeCache {
    private var bitmap: ImageBitmap? = null
    private val renderedStrokes = ArrayList<CompatDrawingStroke>()
    private val bitmapScope = CanvasDrawScope()

    /** Number of strokes rasterised into the bitmap so far (for tests). */
    internal var rasterisedStrokeCount: Int = 0
        private set

    fun draw(scope: DrawScope, strokes: List<CompatDrawingStroke>) {
        val width = ceil(scope.size.width).toInt()
        val height = ceil(scope.size.height).toInt()
        // Read the whole list so the draw scope observes every change.
        val count = strokes.size
        if (width <= 0 || height <= 0) return
        var target = bitmap
        val fullRender = target == null || target.width != width || target.height != height ||
            !isRenderedPrefixOf(strokes, count)
        if (target == null || target.width != width || target.height != height) {
            target = ImageBitmap(width, height)
            bitmap = target
        }
        val start = if (fullRender) 0 else renderedStrokes.size
        if (fullRender || start < count) {
            bitmapScope.draw(scope, scope.layoutDirection, Canvas(target), Size(width.toFloat(), height.toFloat())) {
                if (fullRender) drawRect(Color.Transparent, blendMode = BlendMode.Clear)
                for (index in start until count) drawCompatDrawingStroke(strokes[index])
            }
            rasterisedStrokeCount += count - start
            if (fullRender) renderedStrokes.clear()
            for (index in start until count) renderedStrokes += strokes[index]
        }
        scope.drawImage(target)
    }

    private fun isRenderedPrefixOf(strokes: List<CompatDrawingStroke>, count: Int): Boolean {
        if (renderedStrokes.size > count) return false
        for (index in renderedStrokes.indices) {
            if (renderedStrokes[index] !== strokes[index]) return false
        }
        return true
    }
}
