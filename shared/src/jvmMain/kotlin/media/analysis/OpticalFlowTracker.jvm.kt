package com.valoser.futacha.shared.media.analysis

import com.valoser.futacha.shared.media.video.model.MosaicBounds
import com.valoser.futacha.shared.desktop.desktopResource
import com.valoser.futacha.shared.desktop.DesktopPlatform
import com.sun.jna.*

private interface TrackingNative : Library {
    fun futacha_tracker_create(): Pointer?
    fun futacha_tracker_destroy(tracker: Pointer)
    fun futacha_tracker_seed(tracker: Pointer, gray: ByteArray, width: Int, height: Int, x: Float, y: Float, w: Float, h: Float): Int
    fun futacha_tracker_step(tracker: Pointer, gray: ByteArray, width: Int, height: Int, sceneCut: Int, output: FloatArray): Int
}
private val trackingNative: TrackingNative by lazy {
    val name = if (DesktopPlatform.isWindows) "futacha_tracking_bridge.dll" else "libfutacha_tracking_bridge.dylib"
    // Resolve dependent DLLs from the bridge's own directory, including libwinpthread.
    val options = if (DesktopPlatform.isWindows) mapOf(Library.OPTION_OPEN_FLAGS to 0x00000008) else emptyMap()
    Native.load(desktopResource("native/$name").absolutePath, TrackingNative::class.java, options)
}

internal actual class OpticalFlowTracker actual constructor() : AutoCloseable {
    private val native = trackingNative
    private var pointer: Pointer? = checkNotNull(native.futacha_tracker_create()) { "追尾ライブラリを読み込めません" }
    actual fun seed(frame: AnalysisFrame, region: MosaicBounds) {
        validateTrackingFrame(frame)
        require(region == region.constrained())
        check(native.futacha_tracker_seed(checkNotNull(pointer), frame.gray, frame.width, frame.height,
            region.centerX, region.centerY, region.width, region.height) == 0) { "追尾の開始位置を読み取れません" }
    }
    actual fun step(frame: AnalysisFrame, sceneCut: Boolean): TrackingStep {
        validateTrackingFrame(frame)
        val output = FloatArray(5)
        check(native.futacha_tracker_step(checkNotNull(pointer), frame.gray, frame.width, frame.height, if (sceneCut) 1 else 0, output) == 0) { "動画の追尾処理に失敗しました" }
        return TrackingStep(MosaicBounds(output[0], output[1], output[2], output[3]).constrained(), output[4] != 0f)
    }
    actual override fun close() { pointer?.let { native.futacha_tracker_destroy(it) }; pointer = null }
}
