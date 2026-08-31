package com.valoser.futacha.shared.analytics

actual object PlatformCrashReporter {
    actual fun configure(platformContext: Any?) = Unit

    actual fun setCrashlyticsCollectionEnabled(enabled: Boolean) {
        IosFirebaseTelemetryBridge.setCrashlyticsCollectionEnabled(enabled)
    }

    actual fun setCustomKey(name: String, value: String) {
        IosFirebaseTelemetryBridge.setCrashlyticsCustomKey(name, value)
    }

    actual fun log(message: String) {
        IosFirebaseTelemetryBridge.logCrashlyticsMessage(message)
    }

    actual fun recordException(error: Throwable, sanitizedMessage: String) {
        IosFirebaseTelemetryBridge.recordCrashlyticsException(
            name = error::class.simpleName ?: "Throwable",
            message = sanitizedMessage
        )
    }
}
