package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.HistoryArchiveEntry
import com.valoser.futacha.shared.model.ThreadHistoryEntry

data class HistoryGrowthWarningThresholds(
    val entryCount: Int = DEFAULT_HISTORY_WARNING_ENTRY_COUNT,
    val jsonByteSize: Long = DEFAULT_HISTORY_WARNING_JSON_BYTE_SIZE
)

data class HistoryGrowthWarning(
    val entryCount: Int,
    val jsonByteSize: Long,
    val isEntryCountWarning: Boolean,
    val isJsonSizeWarning: Boolean
)

data class HistoryArchiveImportMergeResult(
    val updatedHistory: List<ThreadHistoryEntry>,
    val addedCount: Int,
    val updatedCount: Int,
    val unchangedCount: Int,
    val skippedCount: Int
)

const val DEFAULT_HISTORY_WARNING_ENTRY_COUNT = 500
const val DEFAULT_HISTORY_WARNING_JSON_BYTE_SIZE = 2_000_000L

fun evaluateHistoryGrowthWarning(
    entryCount: Int,
    jsonByteSize: Long,
    thresholds: HistoryGrowthWarningThresholds = HistoryGrowthWarningThresholds()
): HistoryGrowthWarning? {
    val normalizedEntryCount = entryCount.coerceAtLeast(0)
    val normalizedJsonByteSize = jsonByteSize.coerceAtLeast(0L)
    val entryThreshold = thresholds.entryCount.coerceAtLeast(1)
    val jsonThreshold = thresholds.jsonByteSize.coerceAtLeast(1L)
    val entryWarning = normalizedEntryCount >= entryThreshold
    val jsonWarning = normalizedJsonByteSize >= jsonThreshold
    if (!entryWarning && !jsonWarning) {
        return null
    }
    return HistoryGrowthWarning(
        entryCount = normalizedEntryCount,
        jsonByteSize = normalizedJsonByteSize,
        isEntryCountWarning = entryWarning,
        isJsonSizeWarning = jsonWarning
    )
}

fun resolveHistoryArchiveImportMerge(
    currentHistory: List<ThreadHistoryEntry>,
    importedEntries: Collection<HistoryArchiveEntry>
): HistoryArchiveImportMergeResult {
    return resolveHistoryArchiveImportMergeEntries(
        currentHistory = currentHistory,
        importedHistory = importedEntries.map { it.historyEntry }
    )
}

fun resolveHistoryArchiveImportMergeEntries(
    currentHistory: List<ThreadHistoryEntry>,
    importedHistory: Collection<ThreadHistoryEntry>
): HistoryArchiveImportMergeResult {
    val importedByKey = linkedMapOf<String, ThreadHistoryEntry>()
    var skippedCount = 0
    importedHistory.forEach { entry ->
        val key = historyEntryIdentity(entry)
        if (key.isBlank()) {
            skippedCount += 1
            return@forEach
        }
        val existing = importedByKey[key]
        importedByKey[key] = if (existing == null) {
            entry
        } else {
            mergeAppStateHistoryEntry(existing, entry)
        }
    }

    if (importedByKey.isEmpty()) {
        return HistoryArchiveImportMergeResult(
            updatedHistory = currentHistory,
            addedCount = 0,
            updatedCount = 0,
            unchangedCount = 0,
            skippedCount = skippedCount
        )
    }

    var updatedCount = 0
    var unchangedCount = 0
    val remainingImports = importedByKey.toMutableMap()
    val mergedCurrent = currentHistory.map { existing ->
        val key = historyEntryIdentity(existing)
        val imported = remainingImports.remove(key)
        if (imported == null) {
            existing
        } else {
            val merged = mergeAppStateHistoryEntry(existing, imported)
            if (merged == existing) {
                unchangedCount += 1
            } else {
                updatedCount += 1
            }
            merged
        }
    }
    val added = remainingImports.values
        .sortedByDescending { it.lastVisitedEpochMillis }
    return HistoryArchiveImportMergeResult(
        updatedHistory = mergedCurrent + added,
        addedCount = added.size,
        updatedCount = updatedCount,
        unchangedCount = unchangedCount,
        skippedCount = skippedCount
    )
}
