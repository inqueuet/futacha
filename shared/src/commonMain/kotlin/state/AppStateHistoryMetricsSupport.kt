package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.util.Logger
import kotlin.time.Duration

private const val HISTORY_JSON_LOG_BYTE_THRESHOLD = 512_000
private const val HISTORY_LOG_ENTRY_THRESHOLD = 100

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
