package com.valoser.futacha.shared.media.analysis

import com.valoser.futacha.shared.media.video.model.MosaicBounds

/** JNI entry points are kept by the Android release rules. */
internal object TrackingNative {
    init { System.loadLibrary("futacha_tracking_bridge") }
    external fun create(): Long
    external fun destroy(tracker: Long)
    external fun seed(tracker: Long, gray: ByteArray, width: Int, height: Int,
                      x: Float, y: Float, w: Float, h: Float): Int
    external fun step(tracker: Long, gray: ByteArray, width: Int, height: Int,
                      sceneCut: Boolean, output: FloatArray): Int
}

/** The same sparse LK tracker, thresholds and cumulative polygon as iOS/desktop. */
internal actual class OpticalFlowTracker actual constructor() : AutoCloseable {
    private var pointer = TrackingNative.create().also { check(it != 0L) { "追尾ライブラリを読み込めません" } }

    actual fun seed(frame: AnalysisFrame, region: MosaicBounds) {
        check(pointer != 0L); validateTrackingFrame(frame)
        require(region == region.constrained() && listOf(region.centerX, region.centerY, region.width, region.height).all(Float::isFinite))
        check(TrackingNative.seed(pointer, frame.gray, frame.width, frame.height,
            region.centerX, region.centerY, region.width, region.height) == 0) { "追尾の開始位置を読み取れません" }
    }

    actual fun step(frame: AnalysisFrame, sceneCut: Boolean): TrackingStep {
        check(pointer != 0L); validateTrackingFrame(frame)
        val output = FloatArray(5)
        check(TrackingNative.step(pointer, frame.gray, frame.width, frame.height, sceneCut, output) == 0) { "動画の追尾処理に失敗しました" }
        return TrackingStep(MosaicBounds(output[0], output[1], output[2], output[3]).constrained(), output[4] != 0f)
    }

    actual override fun close() {
        if (pointer != 0L) { TrackingNative.destroy(pointer); pointer = 0L }
    }
}
