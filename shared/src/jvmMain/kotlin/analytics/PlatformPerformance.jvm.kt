package com.valoser.futacha.shared.analytics

actual object PlatformPerformance {
    actual fun configure(platformContext: Any?) = Unit
    actual fun setPerformanceCollectionEnabled(enabled: Boolean) = Unit
    actual fun startTrace(name: String): PlatformPerformanceTrace? = PlatformPerformanceTrace()
}

actual class PlatformPerformanceTrace {
    actual fun putAttribute(name: String, value: String) = Unit
    actual fun putMetric(name: String, value: Long) = Unit
    actual fun stop() = Unit
}
