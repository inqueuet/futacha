package com.valoser.futacha.shared.media.edit

import com.valoser.futacha.shared.media.analysis.ContourResult
import com.valoser.futacha.shared.media.video.model.MosaicBounds
import com.valoser.futacha.shared.media.video.model.MosaicMask

// Image boxes can be 1% wide; applying the video's 2.5% minimum would misalign their masks.
internal fun EditBounds.asMosaicBounds() = MosaicBounds(left + width / 2, top + height / 2, width, height)

internal fun EditRegion.paintContour(from: EditPoint, to: EditPoint, imageWidth: Int, imageHeight: Int,
    brush: Float, erase: Boolean): EditRegion {
    require(imageWidth > 0 && imageHeight > 0 && brush.isFinite() && brush in .01f.. .2f)
    val initial = contour ?: if (erase) MosaicMask.FULL else MosaicMask.EMPTY
    val painted = initial.paintWithin(bounds.asMosaicBounds(), imageWidth, imageHeight, from.x, from.y, to.x, to.y, brush, erase)
    return if (painted === initial && contour == null) this
    else copy(contour = painted, contourUncertain = false)
}

/** Automatic results are committed together, with review required even when all boxes fall back. */
internal fun ImageEditDocument.withContours(ids: List<Int>, results: List<ContourResult>): ImageEditDocument {
    require(ids.isNotEmpty() && ids.size == ids.distinct().size && ids.size == results.size)
    require(ids.all { id -> regions.any { it.id == id } })
    val byId = ids.zip(results).toMap()
    return copy(regions = regions.map { region ->
        byId[region.id]?.let { result -> region.copy(contour = result.mask?.takeUnless { it.isEmpty },
            contourUncertain = result.uncertain || result.mask?.isEmpty == true) } ?: region
    }, analysed = true, reviewed = false).validated()
}
