package com.valoser.futacha.shared.analytics

import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.describeFailureForLog
import kotlinx.coroutines.flow.MutableStateFlow

private const val CRASH_REPORTER_TAG = "CrashReporter"
private const val MAX_KEY_LENGTH = 40
private const val MAX_VALUE_LENGTH = 100
private const val MAX_LOG_LENGTH = 300
private const val MAX_CRASH_SOURCE_CHARS = 8 * 1024
private const val MAX_NON_FATAL_KEYS = 16

object CrashReporter {
    private val collectionEnabled = MutableStateFlow(false)

    fun configure(platformContext: Any?) {
        runAnalyticsSdkCatching {
            PlatformCrashReporter.configure(platformContext)
            PlatformCrashReporter.setCrashlyticsCollectionEnabled(collectionEnabled.value)
        }.onFailure {
            Logger.w(CRASH_REPORTER_TAG, "Failed to configure crash reporting: ${describeFailureForLog(it)}")
        }
    }

    fun setCollectionEnabled(enabled: Boolean) {
        collectionEnabled.value = enabled
        runAnalyticsSdkCatching {
            PlatformCrashReporter.setCrashlyticsCollectionEnabled(enabled)
        }.onFailure {
            Logger.w(CRASH_REPORTER_TAG, "Failed to update crash reporting state: ${describeFailureForLog(it)}")
        }
    }

    fun setKey(name: String, value: String) {
        if (!collectionEnabled.value) return
        val key = sanitizeCrashKey(name)
        val sanitizedValue = sanitizeCrashValue(value)
        if (sanitizedValue.isBlank()) return
        runAnalyticsSdkCatching {
            PlatformCrashReporter.setCustomKey(key, sanitizedValue)
        }.onFailure {
            Logger.w(CRASH_REPORTER_TAG, "Failed to set crash key $key: ${describeFailureForLog(it)}")
        }
    }

    fun log(message: String) {
        if (!collectionEnabled.value) return
        val sanitized = message.take(MAX_CRASH_SOURCE_CHARS).trim().take(MAX_LOG_LENGTH)
        if (sanitized.isBlank()) return
        runAnalyticsSdkCatching {
            PlatformCrashReporter.log(sanitized)
        }
    }

    fun recordNonFatal(
        error: Throwable,
        keys: Map<String, String> = emptyMap()
    ) {
        if (!collectionEnabled.value) return
        keys.asSequence().take(MAX_NON_FATAL_KEYS).forEach { (key, value) -> setKey(key, value) }
        runAnalyticsSdkCatching {
            PlatformCrashReporter.recordException(
                error = error,
                sanitizedMessage = buildSanitizedCrashExceptionMessage(error)
            )
        }.onFailure {
            Logger.w(CRASH_REPORTER_TAG, "Failed to record non-fatal exception: ${describeFailureForLog(it)}")
        }
    }
}

expect object PlatformCrashReporter {
    fun configure(platformContext: Any?)
    fun setCrashlyticsCollectionEnabled(enabled: Boolean)
    fun setCustomKey(name: String, value: String)
    fun log(message: String)
    fun recordException(error: Throwable, sanitizedMessage: String)
}

internal fun buildSanitizedCrashExceptionMessage(error: Throwable): String {
    val type = error::class.simpleName
        ?.take(96)
        ?.takeIf { it.isNotBlank() }
        ?: "Throwable"
    return "type=$type category=${analyticsFailureCategory(error)}"
}

private fun sanitizeCrashKey(raw: String): String {
    val normalized = raw
        .take(MAX_CRASH_SOURCE_CHARS)
        .trim()
        .lowercase()
        .map { char ->
            when {
                char in 'a'..'z' -> char
                char in '0'..'9' -> char
                char == '_' -> char
                else -> '_'
            }
        }
        .joinToString("")
        .replace(Regex("_+"), "_")
        .trim('_')
        .ifBlank { "key" }
    return normalized.take(MAX_KEY_LENGTH).trimEnd('_').ifBlank { "key" }
}

private fun sanitizeCrashValue(raw: String): String {
    return raw
        .take(MAX_CRASH_SOURCE_CHARS)
        .trim()
        .replace(Regex("\\s+"), "_")
        .take(MAX_VALUE_LENGTH)
}
