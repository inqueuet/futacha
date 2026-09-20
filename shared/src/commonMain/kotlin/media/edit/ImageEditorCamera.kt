package com.valoser.futacha.shared.media.edit

/** View-only state. Editing and export always use the same normalized image coordinates. */
internal data class ImageEditorCamera(
    val zoom: Float = 1f,
    val centerX: Float = .5f,
    val centerY: Float = .5f
) {
    init {
        require(zoom.isFinite() && zoom in 1f..8f)
        require(centerX.isFinite() && centerY.isFinite())
    }

    fun viewport(imageWidth: Int, imageHeight: Int, canvasWidth: Int, canvasHeight: Int): EditorViewport {
        val fit = EditorViewport.fit(imageWidth, imageHeight, canvasWidth, canvasHeight)
        val w = fit.width * zoom; val h = fit.height * zoom
        fun origin(length: Float, canvas: Int, center: Float): Float =
            if (length <= canvas) (canvas - length) / 2f
            else (canvas / 2f - center * length).coerceIn(canvas - length, 0f)
        return EditorViewport(origin(w, canvasWidth, centerX), origin(h, canvasHeight, centerY), w, h)
    }

    /** Keep the image under the pinch centroid stationary, then apply the drag in screen pixels. */
    fun transform(imageWidth: Int, imageHeight: Int, canvasWidth: Int, canvasHeight: Int,
        scale: Float, anchorX: Float, anchorY: Float, panX: Float = 0f, panY: Float = 0f): ImageEditorCamera {
        require(listOf(scale, anchorX, anchorY, panX, panY).all(Float::isFinite) && scale > 0f)
        if (canvasWidth <= 0 || canvasHeight <= 0) return this
        val before = viewport(imageWidth, imageHeight, canvasWidth, canvasHeight)
        val nextZoom = (zoom * scale).coerceIn(1f, 8f)
        val w = before.width * nextZoom / zoom; val h = before.height * nextZoom / zoom
        val left = anchorX + panX - (anchorX - before.left) / before.width * w
        val top = anchorY + panY - (anchorY - before.top) / before.height * h
        val proposed = ImageEditorCamera(nextZoom, (canvasWidth / 2f - left) / w, (canvasHeight / 2f - top) / h)
        val bounded = proposed.viewport(imageWidth, imageHeight, canvasWidth, canvasHeight)
        return proposed.copy(centerX = (canvasWidth / 2f - bounded.left) / w, centerY = (canvasHeight / 2f - bounded.top) / h)
    }
}
