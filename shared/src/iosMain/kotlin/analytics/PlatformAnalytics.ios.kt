package com.valoser.futacha.shared.analytics

actual object PlatformAnalytics {
    actual fun configure(platformContext: Any?) = Unit

    actual fun setAnalyticsCollectionEnabled(enabled: Boolean) {
        IosFirebaseTelemetryBridge.setAnalyticsCollectionEnabled(enabled)
    }

    actual fun logEvent(name: String, params: Map<String, String>) {
        IosFirebaseTelemetryBridge.logEvent(name, params)
    }
}
