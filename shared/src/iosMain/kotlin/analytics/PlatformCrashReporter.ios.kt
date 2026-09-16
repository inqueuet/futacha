package com.valoser.futacha.shared.analytics

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.getStackTraceAddresses

actual object PlatformCrashReporter {
    actual fun configure(platformContext: Any?) {
        IosFirebaseTelemetryBridge.installKotlinExceptionHook()
    }

    actual fun setCrashlyticsCollectionEnabled(enabled: Boolean) {
        IosFirebaseTelemetryBridge.setCrashlyticsCollectionEnabled(enabled)
    }

    actual fun setCustomKey(name: String, value: String) {
        IosFirebaseTelemetryBridge.setCrashlyticsCustomKey(name, value)
    }

    actual fun log(message: String) {
        IosFirebaseTelemetryBridge.logCrashlyticsMessage(message)
    }

    @OptIn(ExperimentalNativeApi::class)
    actual fun recordException(error: Throwable, sanitizedMessage: String) {
        IosFirebaseTelemetryBridge.recordCrashlyticsException(
            name = error::class.simpleName ?: "Throwable",
            message = sanitizedMessage,
            stackTraceAddresses = error.getStackTraceAddresses().take(64)
        )
    }
}
