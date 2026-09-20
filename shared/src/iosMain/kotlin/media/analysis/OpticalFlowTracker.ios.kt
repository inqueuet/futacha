@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.valoser.futacha.shared.media.analysis

import com.valoser.futacha.shared.media.video.model.MosaicBounds
import com.valoser.futacha.shared.tracking.*
import kotlinx.cinterop.*

internal actual class OpticalFlowTracker actual constructor() : AutoCloseable {
    private var tracker = checkNotNull(futacha_tracker_create()) { "追尾ライブラリを読み込めません" }
    private var closed = false

    actual fun seed(frame: AnalysisFrame, region: MosaicBounds) {
        check(!closed); validateTrackingFrame(frame)
        require(region == region.constrained() && listOf(region.centerX, region.centerY, region.width, region.height).all(Float::isFinite))
        frame.gray.usePinned {
            check(futacha_tracker_seed(tracker, it.addressOf(0), frame.width, frame.height,
                region.centerX, region.centerY, region.width, region.height) == 0) { "追尾の開始位置を読み取れません" }
        }
    }

    actual fun step(frame: AnalysisFrame, sceneCut: Boolean): TrackingStep {
        check(!closed); validateTrackingFrame(frame)
        val result = FloatArray(5)
        frame.gray.usePinned { pixels -> result.usePinned { output ->
            check(futacha_tracker_step(tracker, pixels.addressOf(0), frame.width, frame.height,
                if (sceneCut) 1 else 0, output.addressOf(0)) == 0) { "動画の追尾処理に失敗しました" }
        } }
        return TrackingStep(MosaicBounds(result[0], result[1], result[2], result[3]).constrained(), result[4] != 0f)
    }

    actual override fun close() { if (!closed) { closed = true; futacha_tracker_destroy(tracker) } }
}
