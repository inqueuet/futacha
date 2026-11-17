package com.valoser.futacha.shared.analytics

actual object PlatformAnalytics {
    actual fun configure(platformContext: Any?) = Unit

    actual fun setAnalyticsCollectionEnabled(enabled: Boolean) = Unit

    actual fun logEvent(name: String, params: Map<String, String>) = Unit
}
