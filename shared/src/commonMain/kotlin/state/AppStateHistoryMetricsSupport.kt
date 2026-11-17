package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.util.Logger
import kotlin.time.Duration

private const val HISTORY_JSON_LOG_BYTE_THRESHOLD = 512_000
private const val HISTORY_LOG_ENTRY_THRESHOLD = 100

internal fun historyJsonByteSize(value: String): Long {
    var byteCount = 0L
    var index = 0
    while (index < value.length) {
        val char = value[index]
        byteCount += when {
            char.code <= 0x7f -> 1L
            char.code <= 0x7ff -> 2L
            char.isHighSurrogate() &&
                index + 1 < value.length &&
                value[index + 1].isLowSurrogate() -> {
                index += 1
                4L
            }
            else -> 3L
        }
        index += 1
    }
    return byteCount
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
