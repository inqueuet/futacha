package com.valoser.futacha.shared.analytics

import android.content.Context
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace

private var performance: FirebasePerformance? = null

actual object PlatformPerformance {
    actual fun configure(platformContext: Any?) {
        val context = platformContext as? Context ?: return
        ensureFirebaseInitialized(context) ?: return
        performance = FirebasePerformance.getInstance()
    }

    actual fun setPerformanceCollectionEnabled(enabled: Boolean) {
        performance?.isPerformanceCollectionEnabled = enabled
    }

    actual fun startTrace(name: String): PlatformPerformanceTrace? {
        val trace = performance?.newTrace(name) ?: return null
        trace.start()
        return PlatformPerformanceTrace(trace)
    }
}

actual class PlatformPerformanceTrace internal constructor(
    private val trace: Trace
) {
    actual fun putAttribute(name: String, value: String) {
        trace.putAttribute(name, value)
    }

    actual fun putMetric(name: String, value: Long) {
        trace.putMetric(name, value)
    }

    actual fun stop() {
        trace.stop()
    }
}
