package com.valoser.futacha.shared.analytics

actual object PlatformCrashReporter {
    actual fun configure(platformContext: Any?) = Unit
    actual fun setCrashlyticsCollectionEnabled(enabled: Boolean) = Unit
    actual fun setCustomKey(name: String, value: String) = Unit
    actual fun log(message: String) = Unit
    actual fun recordException(error: Throwable, sanitizedMessage: String) = Unit
}
