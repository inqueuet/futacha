package com.valoser.futacha.shared.media.edit

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.valoser.futacha.shared.media.video.model.MosaicMask
import kotlin.math.*

internal data class EditPoint(val x: Float, val y: Float) {
    init { require(x.isFinite() && y.isFinite()) }
    fun constrained() = EditPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}
internal data class EditBounds(val left: Float, val top: Float, val width: Float, val height: Float) {
    init { require(listOf(left, top, width, height).all { it.isFinite() }) }
    fun constrained(): EditBounds {
        val w = width.coerceIn(.01f, 1f); val h = height.coerceIn(.01f, 1f)
        return EditBounds(left.coerceIn(0f, 1f - w), top.coerceIn(0f, 1f - h), w, h)
    }
    fun contains(p: EditPoint) = p.x in left..left + width && p.y in top..top + height
}
internal enum class EditStyle { MOSAIC, BLACK }
internal data class EditRegion(
    val id: Int,
    val bounds: EditBounds = EditBounds(.25f, .25f, .5f, .5f),
    val style: EditStyle = EditStyle.MOSAIC,
    val blockFraction: Float = .04f,
    val darkness: Float = 0f,
    val label: String? = null,
    val confidence: Float? = null,
    val contour: MosaicMask? = null,
    val contourMargin: Int = 2,
    val contourUncertain: Boolean = false
)
internal data class EditStroke(val points: List<EditPoint>, val diameter: Float, val erase: Boolean)
internal data class ImageEditDocument(
    val strokes: List<EditStroke> = emptyList(),
    val regions: List<EditRegion> = emptyList(),
    val brushBlockFraction: Float = .04f,
    val brushOpacity: Float = 1f,
    val analysed: Boolean = false,
    val reviewed: Boolean = false
) {
    val needsReview: Boolean get() = analysed && !reviewed
    val hasEmptyContour: Boolean get() = regions.any { it.contour?.isEmpty == true }
    fun requireExportReady() {
        check(!hasEmptyContour) { "空の輪郭があります。塗り足すか、枠全体に戻してから保存してください" }
        check(!needsReview) { "画像全体を確認し、「確認・修正済み」にしてから保存してください" }
    }
    fun validated(): ImageEditDocument = apply {
        require(strokes.size <= 128 && strokes.sumOf { it.points.size } <= 32_768) { "手描きの上限です。画像を保存して編集を続けてください" }
        require(regions.size <= 16 && regions.map { it.id }.distinct().size == regions.size) { "枠は16個までです" }
        require(brushBlockFraction.isFinite() && brushBlockFraction in .005f.. .2f && brushOpacity.isFinite() && brushOpacity in 0f..1f)
        strokes.forEach { s ->
            require(s.points.isNotEmpty() && s.diameter.isFinite() && s.diameter in .005f.. .3f)
            require(s.points.all { it.x in 0f..1f && it.y in 0f..1f })
        }
        regions.forEach { r ->
            require(r.contourMargin in 0..MosaicMask.MAX_MARGIN)
            require(r.bounds == r.bounds.constrained())
            require(r.blockFraction.isFinite() && r.blockFraction in .005f.. .2f && r.darkness.isFinite() && r.darkness in 0f..1f)
            require(r.confidence == null || (r.confidence.isFinite() && r.confidence in 0f..1f))
            require(r.label == null || r.label.length <= 80)
        }
    }
}

/** History contains immutable vector edits, never full bitmap copies. One drag is one undo. */
internal class ImageEditHistory {
    private val current = MutableStateFlow(ImageEditDocument())
    val document = current.asStateFlow()
    val availability = MutableStateFlow(false to false)
    private fun notifyHistory() { availability.value = canUndo to canRedo }
    private var committed = current.value
    private val undo = ArrayDeque<ImageEditDocument>()
    private val redo = ArrayDeque<ImageEditDocument>()
    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()
    fun preview(transform: (ImageEditDocument) -> ImageEditDocument) {
        val before = current.value
        val after = transform(before).validated()
        // All edits, including hand strokes, are vectors in this history. Undo can therefore
        // restore approval only together with the exact image that was approved.
        val pixelsChanged = before.strokes != after.strokes || before.regions != after.regions ||
            before.brushBlockFraction != after.brushBlockFraction || before.brushOpacity != after.brushOpacity
        current.value = if (pixelsChanged) after.copy(reviewed = false) else after
    }
    fun commit() {
        if (current.value == committed) return
        undo.addLast(committed)
        if (undo.size > 50) undo.removeFirst()
        committed = current.value; redo.clear(); notifyHistory()
    }
    fun change(transform: (ImageEditDocument) -> ImageEditDocument) { preview(transform); commit() }
    fun cancelGesture() { current.value = committed }
    fun undo() { cancelGesture(); if (undo.isNotEmpty()) { redo.addLast(committed); committed = undo.removeLast(); current.value = committed; notifyHistory() } }
    fun redo() { cancelGesture(); if (redo.isNotEmpty()) { undo.addLast(committed); committed = redo.removeLast(); current.value = committed; notifyHistory() } }
}

internal data class EditorViewport(val left: Float, val top: Float, val width: Float, val height: Float) {
    fun point(x: Float, y: Float, clamp: Boolean = false): EditPoint? {
        if (width <= 0 || height <= 0 || !x.isFinite() || !y.isFinite()) return null
        val p = EditPoint((x - left) / width, (y - top) / height)
        return if (clamp) p.constrained() else p.takeIf { it.x in 0f..1f && it.y in 0f..1f }
    }
    companion object {
        fun fit(imageWidth: Int, imageHeight: Int, canvasWidth: Int, canvasHeight: Int): EditorViewport {
            require(imageWidth > 0 && imageHeight > 0)
            val scale = min(canvasWidth.toFloat() / imageWidth, canvasHeight.toFloat() / imageHeight).coerceAtLeast(0f)
            val w = imageWidth * scale; val h = imageHeight * scale
            return EditorViewport((canvasWidth - w) / 2, (canvasHeight - h) / 2, w, h)
        }
    }
}
