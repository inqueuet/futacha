package com.valoser.futacha.shared.media.analysis

import com.valoser.futacha.shared.media.video.model.MosaicBounds
import kotlin.math.abs

internal data class TrackingStep(val bounds: MosaicBounds, val uncertain: Boolean)

/** Single analysis-worker ownership. Close only after the synchronous native call returns.
 * Uses toshikari's bidirectional LK/RANSAC tracker; loss freezes the last box until reseeded. */
internal expect class OpticalFlowTracker() : AutoCloseable {
    fun seed(frame: AnalysisFrame, region: MosaicBounds)
    fun step(frame: AnalysisFrame, sceneCut: Boolean = false): TrackingStep
    override fun close()
}

internal fun sceneChanged(a: AnalysisFrame?, b: AnalysisFrame): Boolean {
    require(b.gray.size == b.width * b.height)
    if (a == null) return false
    require(a.gray.size == a.width * a.height)
    if (a.width != b.width || a.height != b.height || abs(b.timeUs - a.timeUs) > 500_000) return true
    var difference = 0L; var count = 0
    for (i in b.gray.indices step 32) {
        difference += abs((a.gray[i].toInt() and 255) - (b.gray[i].toInt() and 255))
        count++
    }
    return count > 0 && difference.toDouble() / count > 48
}

internal fun validateTrackingFrame(frame: AnalysisFrame) {
    require(frame.gray.size == frame.width * frame.height) { "追尾用の輝度画像がありません" }
}
