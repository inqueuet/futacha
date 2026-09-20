package com.valoser.futacha.shared.desktop

/** Used by the packaged application's explicit --check-installation diagnostic. */
fun verifyDesktopNativeLibraries() {
    check(java.nio.charset.Charset.forName("Shift_JIS").encode("ふたば").remaining() == 6)
    org.bytedeco.javacv.FFmpegFrameGrabber.tryLoad()
    // Match CpuInference's production setup; ORT's telemetry worker can race JVM shutdown.
    ai.onnxruntime.OrtEnvironment.getEnvironment("futacha-offline-editing").setTelemetry(false)
    com.valoser.futacha.shared.media.analysis.OpticalFlowTracker().close()
    com.valoser.futacha.shared.ui.board.DesktopVideoSession { _, _, _ -> }.close()
}
