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
import androidx.compose.ui.graphics.drawscope.withTransform
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

/** (L68) A copy of the picture is kept every this many strokes, so an Undo redraws from the nearest one. */
internal const val COMPAT_DRAWING_CHECKPOINT_STROKES = 32

/** (L68) The copies kept at most, in bytes (the newest are kept; at least the cost of one is spent only if it fits). */
internal const val COMPAT_DRAWING_CHECKPOINT_BYTES = 16L * 1024 * 1024

/**
 * Keeps finished strokes rasterised in one bitmap. Redrawing every finished
 * stroke for every frame of the active stroke made drawing slower as the
 * picture grew (E-12). Appended strokes are drawn into the bitmap once; a
 * resize re-renders the list.
 *
 * An Undo (or a change of an earlier stroke) used to redraw every stroke on the main thread, a second or more for
 * a large drawing (L68). Copies of the picture are now kept every [checkpointEvery] strokes (within
 * [checkpointBytes]); a change re-renders from the newest copy still matching the list, so an Undo redraws at most
 * that many strokes.
 */
internal class CompatDrawingStrokeCache(
    private val checkpointEvery: Int = COMPAT_DRAWING_CHECKPOINT_STROKES,
    private val checkpointBytes: Long = COMPAT_DRAWING_CHECKPOINT_BYTES
) {
    private class Checkpoint(val strokeCount: Int, val bitmap: ImageBitmap)

    private var bitmap: ImageBitmap? = null
    private val renderedStrokes = ArrayList<CompatDrawingStroke>()
    private val checkpoints = ArrayDeque<Checkpoint>()
    private val bitmapScope = CanvasDrawScope()
    private var renderedLogicalSize: Size? = null

    /** Number of strokes rasterised into the bitmap so far (for tests). */
    internal var rasterisedStrokeCount: Int = 0
        private set

    fun draw(scope: DrawScope, strokes: List<CompatDrawingStroke>, logicalSize: Size = scope.size) {
        val width = ceil(scope.size.width).toInt()
        val height = ceil(scope.size.height).toInt()
        // Read the whole list so the draw scope observes every change.
        val count = strokes.size
        if (width <= 0 || height <= 0 || logicalSize.width <= 0 || logicalSize.height <= 0) return
        var target = bitmap
        val resized = target == null || target.width != width || target.height != height ||
            logicalSize != renderedLogicalSize
        if (target == null || target.width != width || target.height != height) {
            target = ImageBitmap(width, height)
            bitmap = target
        }
        if (resized) {
            checkpoints.clear()
            renderedStrokes.clear()
        }
        val matching = if (resized) 0 else matchingPrefix(strokes, count)
        val appendOnly = !resized && matching == renderedStrokes.size
        var start = renderedStrokes.size
        var from: Checkpoint? = null
        if (!appendOnly) {
            while (checkpoints.isNotEmpty() && checkpoints.last().strokeCount > minOf(matching, count)) checkpoints.removeLast()
            from = checkpoints.lastOrNull()
            start = from?.strokeCount ?: 0
            while (renderedStrokes.size > start) renderedStrokes.removeAt(renderedStrokes.lastIndex)
        }
        val canvasSize = Size(width.toFloat(), height.toFloat())
        if (!appendOnly) {
            bitmapScope.draw(scope, scope.layoutDirection, Canvas(target), canvasSize) {
                drawRect(Color.Transparent, blendMode = BlendMode.Clear)
                from?.let { drawImage(it.bitmap) }
            }
        }
        var next = start
        while (next < count) {
            val end = minOf(count, (next / checkpointEvery + 1) * checkpointEvery)
            val segmentStart = next
            bitmapScope.draw(scope, scope.layoutDirection, Canvas(target), canvasSize) {
                withTransform({ scale(width / logicalSize.width, height / logicalSize.height, Offset.Zero) }) {
                    for (index in segmentStart until end) drawCompatDrawingStroke(strokes[index])
                }
            }
            for (index in segmentStart until end) renderedStrokes += strokes[index]
            rasterisedStrokeCount += end - segmentStart
            next = end
            if (end % checkpointEvery == 0 && end > (checkpoints.lastOrNull()?.strokeCount ?: 0)) keepCheckpoint(end, target)
        }
        renderedLogicalSize = logicalSize
        scope.drawImage(target)
    }

    private fun keepCheckpoint(strokeCount: Int, source: ImageBitmap) {
        val bytes = source.width.toLong() * source.height * 4
        if (bytes > checkpointBytes) return
        while (checkpoints.isNotEmpty() && (checkpoints.size + 1) * bytes > checkpointBytes) checkpoints.removeFirst()
        val copy = ImageBitmap(source.width, source.height)
        bitmapScope.draw(androidx.compose.ui.unit.Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr, Canvas(copy),
            Size(source.width.toFloat(), source.height.toFloat())) { drawImage(source) }
        checkpoints.addLast(Checkpoint(strokeCount, copy))
    }

    /** How many leading strokes are the same objects as those drawn. */
    private fun matchingPrefix(strokes: List<CompatDrawingStroke>, count: Int): Int {
        val limit = minOf(renderedStrokes.size, count)
        for (index in 0 until limit) {
            if (renderedStrokes[index] !== strokes[index]) return index
        }
        return limit
    }
}
