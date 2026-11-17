package com.valoser.futacha.shared.media.edit

import com.valoser.futacha.shared.media.analysis.Detection

/** Commit all candidates atomically. A zero-candidate analysis still needs the user's review. */
internal fun ImageEditDocument.withDetections(detections: List<Detection>): ImageEditDocument {
    check(regions.size + detections.size <= 16) { "配置済みの枠と候補が合計16か所を超えます。不要な枠を削除して再実行してください" }
    val used = regions.map { it.id }.toMutableSet()
    val added = detections.map { detection ->
        val id = (1..17).first { it !in used }; used += id
        val bounds = detection.bounds.constrained()
        EditRegion(id, EditBounds(bounds.centerX - bounds.width / 2, bounds.centerY - bounds.height / 2, bounds.width, bounds.height).constrained(),
            label = detection.label, confidence = detection.score)
    }
    return copy(regions = regions + added, analysed = true, reviewed = false).validated()
}
