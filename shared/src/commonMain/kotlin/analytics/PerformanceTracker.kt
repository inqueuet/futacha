package com.valoser.futacha.shared.analytics

import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.describeFailureForLog
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.MutableStateFlow

private const val PERFORMANCE_TAG = "PerformanceTracker"
private const val MAX_TRACE_NAME_LENGTH = 64
private const val MAX_TRACE_ATTRIBUTE_COUNT = 5
private const val MAX_PERFORMANCE_SOURCE_CHARS = 8 * 1024

object PerformanceTracker {
    private val collectionEnabled = MutableStateFlow(false)

    fun configure(platformContext: Any?) {
        runAnalyticsSdkCatching {
            PlatformPerformance.configure(platformContext)
            PlatformPerformance.setPerformanceCollectionEnabled(collectionEnabled.value)
        }.onFailure {
            Logger.w(PERFORMANCE_TAG, "Failed to configure performance monitoring: ${describeFailureForLog(it)}")
        }
    }

    fun setCollectionEnabled(enabled: Boolean) {
        collectionEnabled.value = enabled
        runAnalyticsSdkCatching {
            PlatformPerformance.setPerformanceCollectionEnabled(enabled)
        }.onFailure {
            Logger.w(PERFORMANCE_TAG, "Failed to update performance collection state: ${describeFailureForLog(it)}")
        }
    }

    @OptIn(ExperimentalTime::class)
    suspend fun <T> measureSuspend(
        traceName: String,
        attributes: Map<String, String> = emptyMap(),
        block: suspend () -> T
    ): T {
        val sanitizedTraceName = sanitizeTraceName(traceName)
        val sanitizedAttributes = sanitizeTraceAttributes(attributes)
        val trace = startTrace(sanitizedTraceName, sanitizedAttributes)
        val started = TimeSource.Monotonic.markNow()
        var outcome = "failure"
        try {
            val result = block()
            outcome = "success"
            return result
        } catch (error: CancellationException) {
            outcome = "cancelled"
            trace.safePutAttribute("result", outcome)
            throw error
        } catch (error: Throwable) {
            trace.safePutAttribute("result", outcome)
            trace.safePutAttribute("error_type", error::class.simpleName ?: "unknown")
            throw error
        } finally {
            val elapsedMillis = started.elapsedNow().inWholeMilliseconds.coerceAtLeast(0L)
            trace.safePutMetric("duration_ms", elapsedMillis)
            trace.safePutAttribute("result", if (trace == null) "unavailable" else outcome)
            trace.safeStop()
            AnalyticsTracker.event(
                "performance_trace",
                mapOf(
                    "trace" to sanitizedTraceName,
                    "result" to outcome,
                    "duration_bucket" to analyticsDurationBucket(elapsedMillis)
                ) + sanitizedAttributes
            )
        }
    }

    @OptIn(ExperimentalTime::class)
    fun <T> measure(
        traceName: String,
        attributes: Map<String, String> = emptyMap(),
        block: () -> T
    ): T {
        val sanitizedTraceName = sanitizeTraceName(traceName)
        val sanitizedAttributes = sanitizeTraceAttributes(attributes)
        val trace = startTrace(sanitizedTraceName, sanitizedAttributes)
        val started = TimeSource.Monotonic.markNow()
        var outcome = "failure"
        try {
            val result = block()
            outcome = "success"
            return result
        } catch (error: Throwable) {
            trace.safePutAttribute("result", outcome)
            trace.safePutAttribute("error_type", error::class.simpleName ?: "unknown")
            throw error
        } finally {
            val elapsedMillis = started.elapsedNow().inWholeMilliseconds.coerceAtLeast(0L)
            trace.safePutMetric("duration_ms", elapsedMillis)
            trace.safePutAttribute("result", if (trace == null) "unavailable" else outcome)
            trace.safeStop()
            AnalyticsTracker.event(
                "performance_trace",
                mapOf(
                    "trace" to sanitizedTraceName,
                    "result" to outcome,
                    "duration_bucket" to analyticsDurationBucket(elapsedMillis)
                ) + sanitizedAttributes
            )
        }
    }

    fun startTrace(name: String, attributes: Map<String, String> = emptyMap()): PlatformPerformanceTrace? {
        if (!collectionEnabled.value) return null
        val sanitizedName = sanitizeTraceName(name)
        val trace = runAnalyticsSdkCatching { PlatformPerformance.startTrace(sanitizedName) }
            .onFailure {
                Logger.w(
                    PERFORMANCE_TAG,
                    "Failed to start trace $sanitizedName: ${describeFailureForLog(it)}"
                )
            }
            .getOrNull()
        sanitizeTraceAttributes(attributes).forEach { (key, value) ->
            trace.safePutAttribute(key, value)
        }
        return trace
    }
}

expect object PlatformPerformance {
    fun configure(platformContext: Any?)
    fun setPerformanceCollectionEnabled(enabled: Boolean)
    fun startTrace(name: String): PlatformPerformanceTrace?
}

expect class PlatformPerformanceTrace {
    fun putAttribute(name: String, value: String)
    fun putMetric(name: String, value: Long)
    fun stop()
}

private fun PlatformPerformanceTrace?.safePutAttribute(name: String, value: String) {
    if (this == null) return
    runAnalyticsSdkCatching { putAttribute(name, value) }
        .onFailure { Logger.w(PERFORMANCE_TAG, "Failed to update trace attribute $name") }
}

private fun PlatformPerformanceTrace?.safePutMetric(name: String, value: Long) {
    if (this == null) return
    runAnalyticsSdkCatching { putMetric(name, value) }
        .onFailure { Logger.w(PERFORMANCE_TAG, "Failed to update trace metric $name") }
}

private fun PlatformPerformanceTrace?.safeStop() {
    if (this == null) return
    runAnalyticsSdkCatching { stop() }
        .onFailure { Logger.w(PERFORMANCE_TAG, "Failed to stop performance trace") }
}

private fun sanitizeTraceName(raw: String): String {
    val normalized = raw
        .take(MAX_PERFORMANCE_SOURCE_CHARS)
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
        .ifBlank { "app_trace" }
    return normalized.take(MAX_TRACE_NAME_LENGTH).trimEnd('_').ifBlank { "app_trace" }
}

private fun sanitizeTraceAttributes(params: Map<String, String>): Map<String, String> {
    return params
        .asSequence()
        .take(MAX_TRACE_ATTRIBUTE_COUNT)
        .map { (key, value) ->
            key.take(128) to value.take(400)
        }
        .filter { it.first.isNotBlank() && it.second.isNotBlank() }
        .associate { (key, value) ->
            key.take(32).trim('_').ifBlank { "attr" } to value.take(100)
        }
}
