package com.valoser.futacha.shared.analytics

actual object PlatformPerformance {
    actual fun configure(platformContext: Any?) = Unit

    actual fun setPerformanceCollectionEnabled(enabled: Boolean) {
        IosFirebaseTelemetryBridge.setPerformanceCollectionEnabled(enabled)
    }

    actual fun startTrace(name: String): PlatformPerformanceTrace? {
        val traceId = IosFirebaseTelemetryBridge.startTrace(name) ?: return null
        return PlatformPerformanceTrace(traceId)
    }
}

actual class PlatformPerformanceTrace internal constructor(
    private val traceId: String
) {
    actual fun putAttribute(name: String, value: String) {
        IosFirebaseTelemetryBridge.putTraceAttribute(traceId, name, value)
    }

    actual fun putMetric(name: String, value: Long) {
        IosFirebaseTelemetryBridge.putTraceMetric(traceId, name, value)
    }

    actual fun stop() {
        IosFirebaseTelemetryBridge.stopTrace(traceId)
    }
}
