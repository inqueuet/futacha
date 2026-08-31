package com.valoser.futacha.shared.analytics

object IosFirebaseTelemetryBridge {
    var logEventHandler: ((name: String, keys: List<String>, values: List<String>) -> Unit)? = null
    var setAnalyticsCollectionEnabledHandler: ((Boolean) -> Unit)? = null
    var setPerformanceCollectionEnabledHandler: ((Boolean) -> Unit)? = null
    var setCrashlyticsCollectionEnabledHandler: ((Boolean) -> Unit)? = null
    var setCrashlyticsCustomKeyHandler: ((name: String, value: String) -> Unit)? = null
    var logCrashlyticsMessageHandler: ((String) -> Unit)? = null
    var recordCrashlyticsExceptionHandler: ((name: String, message: String) -> Unit)? = null
    var startTraceHandler: ((name: String, keys: List<String>, values: List<String>) -> String?)? = null
    var putTraceAttributeHandler: ((traceId: String, name: String, value: String) -> Unit)? = null
    var putTraceMetricHandler: ((traceId: String, name: String, value: Long) -> Unit)? = null
    var stopTraceHandler: ((traceId: String) -> Unit)? = null

    fun logEvent(name: String, params: Map<String, String>) {
        logEventHandler?.invoke(name, params.keys.toList(), params.values.toList())
    }

    fun setAnalyticsCollectionEnabled(enabled: Boolean) {
        setAnalyticsCollectionEnabledHandler?.invoke(enabled)
    }

    fun setPerformanceCollectionEnabled(enabled: Boolean) {
        setPerformanceCollectionEnabledHandler?.invoke(enabled)
    }

    fun setCrashlyticsCollectionEnabled(enabled: Boolean) {
        setCrashlyticsCollectionEnabledHandler?.invoke(enabled)
    }

    fun setCrashlyticsCustomKey(name: String, value: String) {
        setCrashlyticsCustomKeyHandler?.invoke(name, value)
    }

    fun logCrashlyticsMessage(message: String) {
        logCrashlyticsMessageHandler?.invoke(message)
    }

    fun recordCrashlyticsException(name: String, message: String) {
        recordCrashlyticsExceptionHandler?.invoke(name, message)
    }

    fun startTrace(name: String, attributes: Map<String, String> = emptyMap()): String? {
        return startTraceHandler?.invoke(name, attributes.keys.toList(), attributes.values.toList())
    }

    fun putTraceAttribute(traceId: String, name: String, value: String) {
        putTraceAttributeHandler?.invoke(traceId, name, value)
    }

    fun putTraceMetric(traceId: String, name: String, value: Long) {
        putTraceMetricHandler?.invoke(traceId, name, value)
    }

    fun stopTrace(traceId: String) {
        stopTraceHandler?.invoke(traceId)
    }
}
