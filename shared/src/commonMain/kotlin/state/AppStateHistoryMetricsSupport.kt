package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.util.Logger
import kotlin.time.Duration

private const val HISTORY_JSON_LOG_BYTE_THRESHOLD = 512_000
private const val HISTORY_LOG_ENTRY_THRESHOLD = 100

internal data class AppStateHistoryPersistenceMetrics(
    val persistCount: Long,
    val revision: Long,
    val pass: Int,
    val entryCount: Int,
    val jsonByteSize: Long,
    val encodeDuration: Duration,
    val writeDuration: Duration
)

internal fun historyJsonByteSize(value: String): Long {
    return value.encodeToByteArray().size.toLong()
}

internal fun shouldLogAppStateHistoryMetrics(entryCount: Int, jsonByteSize: Long): Boolean {
    return entryCount >= HISTORY_LOG_ENTRY_THRESHOLD || jsonByteSize >= HISTORY_JSON_LOG_BYTE_THRESHOLD
}

internal fun logAppStateHistoryDecodeMetrics(
    tag: String,
    entryCount: Int,
    jsonByteSize: Long,
    duration: Duration
) {
    if (!shouldLogAppStateHistoryMetrics(entryCount, jsonByteSize)) {
        return
    }
    Logger.d(
        tag,
        "history decode: entries=$entryCount jsonBytes=$jsonByteSize durationMs=${duration.inWholeMilliseconds}"
    )
}

internal fun logAppStateHistoryPersistenceMetrics(
    tag: String,
    metrics: AppStateHistoryPersistenceMetrics
) {
    if (!shouldLogAppStateHistoryMetrics(metrics.entryCount, metrics.jsonByteSize)) {
        return
    }
    Logger.d(
        tag,
        "history persist: count=${metrics.persistCount} revision=${metrics.revision} " +
            "pass=${metrics.pass} entries=${metrics.entryCount} jsonBytes=${metrics.jsonByteSize} " +
            "encodeMs=${metrics.encodeDuration.inWholeMilliseconds} writeMs=${metrics.writeDuration.inWholeMilliseconds}"
    )
}

internal fun countHistoryEntries(history: List<ThreadHistoryEntry>): Int {
    return history.size
}
