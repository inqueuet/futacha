package com.valoser.futacha.shared.service

data class WatchAlertNotificationLedgerEntry(
    val key: String,
    val notifiedAtEpochMillis: Long
)

object WatchAlertNotificationLedger {
    const val DEFAULT_MAX_ENTRIES: Int = 5_000
    private const val MAX_PARSED_ENTRIES: Int = 20_000
    private const val MAX_SERIALIZED_CHARS: Int = 2 * 1024 * 1024
    private const val MAX_KEY_CHARS: Int = 8_192

    fun filterNewMatches(
        serializedEntries: String?,
        legacyKeys: Set<String> = emptySet(),
        matches: List<CatalogWatchAlertMatch>
    ): List<CatalogWatchAlertMatch> {
        if (matches.isEmpty()) return emptyList()
        val notifiedKeys = readEntries(serializedEntries, legacyKeys).mapTo(mutableSetOf()) { it.key }
        return matches.filterNot { match -> match.identityKey in notifiedKeys }
    }

    fun markMatches(
        serializedEntries: String?,
        legacyKeys: Set<String> = emptySet(),
        matches: List<CatalogWatchAlertMatch>,
        nowMillis: Long,
        maxEntries: Int = DEFAULT_MAX_ENTRIES
    ): String {
        if (matches.isEmpty()) {
            return serialize(readEntries(serializedEntries, legacyKeys))
        }
        val orderedEntries = readEntries(serializedEntries, legacyKeys).toMutableList()
        val existingKeys = orderedEntries.mapTo(mutableSetOf()) { it.key }
        matches.forEach { match ->
            if (existingKeys.add(match.identityKey)) {
                orderedEntries += WatchAlertNotificationLedgerEntry(match.identityKey, nowMillis)
            }
        }
        val preserveKeys = matches.mapTo(mutableSetOf()) { it.identityKey }
        return serialize(capEntries(orderedEntries, preserveKeys, maxEntries))
    }

    fun readEntries(
        serializedEntries: String?,
        legacyKeys: Set<String> = emptySet()
    ): List<WatchAlertNotificationLedgerEntry> {
        if (!serializedEntries.isNullOrBlank()) {
            return serializedEntries
                .take(MAX_SERIALIZED_CHARS)
                .lineSequence()
                .take(MAX_PARSED_ENTRIES)
                .mapNotNull { line ->
                    val timestamp = line.substringBefore('\t').toLongOrNull() ?: return@mapNotNull null
                    val key = line.substringAfter('\t', missingDelimiterValue = "")
                        .take(MAX_KEY_CHARS)
                        .takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    WatchAlertNotificationLedgerEntry(key, timestamp)
                }
                .distinctBy { it.key }
                .toList()
        }
        return legacyKeys
            .asSequence()
            .take(MAX_PARSED_ENTRIES)
            .filter(String::isNotBlank)
            .distinct()
            .map { key -> WatchAlertNotificationLedgerEntry(key.take(MAX_KEY_CHARS), 0L) }
            .toList()
    }

    private fun capEntries(
        entries: List<WatchAlertNotificationLedgerEntry>,
        preserveKeys: Set<String>,
        maxEntries: Int
    ): List<WatchAlertNotificationLedgerEntry> {
        val sorted = entries
            .distinctBy { it.key }
            .sortedBy { it.notifiedAtEpochMillis }
        val retained = sorted.takeLast(maxEntries.coerceAtLeast(0)).toMutableList()
        val retainedKeys = retained.mapTo(mutableSetOf()) { it.key }
        sorted
            .asSequence()
            .filter { it.key in preserveKeys && it.key !in retainedKeys }
            .forEach { entry ->
                retained += entry
                retainedKeys += entry.key
            }
        return retained.sortedBy { it.notifiedAtEpochMillis }
    }

    private fun serialize(entries: List<WatchAlertNotificationLedgerEntry>): String {
        var retainedChars = 0
        val retainedNewestFirst = buildList {
            for (entry in entries.asReversed()) {
                val line = "${entry.notifiedAtEpochMillis}\t${entry.key.take(MAX_KEY_CHARS)}"
                val separatorChars = if (isEmpty()) 0 else 1
                if (line.length + separatorChars > MAX_SERIALIZED_CHARS - retainedChars) continue
                add(line)
                retainedChars += line.length + separatorChars
            }
        }
        return retainedNewestFirst.asReversed().joinToString("\n")
    }
}
