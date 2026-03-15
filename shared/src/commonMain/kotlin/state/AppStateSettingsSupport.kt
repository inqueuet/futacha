package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.CatalogDisplayStyle
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuItemConfig
import com.valoser.futacha.shared.model.ThreadSettingsMenuItemConfig
import com.valoser.futacha.shared.model.defaultCatalogNavEntries
import com.valoser.futacha.shared.model.defaultThreadMenuConfig
import com.valoser.futacha.shared.model.defaultThreadMenuEntries
import com.valoser.futacha.shared.model.defaultThreadSettingsMenuConfig
import com.valoser.futacha.shared.model.normalizeCatalogNavEntries
import com.valoser.futacha.shared.model.normalizeThreadMenuConfig
import com.valoser.futacha.shared.model.normalizeThreadMenuEntries
import com.valoser.futacha.shared.model.normalizeThreadSettingsMenuConfig
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.SaveDirectorySelection
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

internal const val DEFAULT_CATALOG_GRID_COLUMNS_VALUE = 5
internal const val MIN_CATALOG_GRID_COLUMNS_VALUE = 2
internal const val MAX_CATALOG_GRID_COLUMNS_VALUE = 8
internal const val SELF_POST_IDENTIFIER_MAX_ENTRIES_VALUE = 20
internal const val SELF_POST_IDENTIFIER_DELIMITER = "::"

private val catalogModeMapJson = Json { ignoreUnknownKeys = true }
private val catalogModeMapSerializer = MapSerializer(String.serializer(), String.serializer())

internal fun decodeCatalogDisplayStyleValue(raw: String?): CatalogDisplayStyle {
    return raw?.let { value ->
        CatalogDisplayStyle.entries.firstOrNull { it.name == value }
    } ?: CatalogDisplayStyle.Grid
}

internal fun decodeCatalogGridColumnsValue(raw: String?): Int {
    val parsed = raw?.toIntOrNull() ?: DEFAULT_CATALOG_GRID_COLUMNS_VALUE
    return parsed.coerceIn(MIN_CATALOG_GRID_COLUMNS_VALUE, MAX_CATALOG_GRID_COLUMNS_VALUE)
}

internal fun decodeCatalogModeMapValue(raw: String?): Map<String, CatalogMode> {
    if (raw.isNullOrBlank()) return emptyMap()
    return runCatching {
        val decoded = catalogModeMapJson.decodeFromString(catalogModeMapSerializer, raw)
        decoded.mapNotNull { (boardId, modeName) ->
            val mode = CatalogMode.entries.firstOrNull { it.name == modeName }
            mode?.let { boardId to it }
        }.toMap()
    }.getOrDefault(emptyMap())
}

internal fun encodeCatalogModeMapValue(map: Map<String, CatalogMode>): Map<String, String> {
    return map.mapValues { it.value.name }
}

internal fun decodeStringListValue(
    raw: String?,
    json: Json,
    serializer: KSerializer<List<String>>
): List<String> {
    if (raw == null) return emptyList()
    return runCatching {
        json.decodeFromString(serializer, raw)
    }.getOrDefault(emptyList())
}

internal fun decodeSelfPostIdentifierMapValue(
    raw: String?,
    json: Json,
    serializer: KSerializer<Map<String, List<String>>>
): Map<String, List<String>> {
    if (raw == null) return emptyMap()
    return runCatching {
        json.decodeFromString(serializer, raw)
    }.getOrDefault(emptyMap())
}

internal fun aggregateSelfPostIdentifiers(
    map: Map<String, List<String>>,
    maxEntries: Int = SELF_POST_IDENTIFIER_MAX_ENTRIES_VALUE
): List<String> {
    val seenKeys = mutableSetOf<String>()
    val aggregated = mutableListOf<String>()
    map.values.forEach { identifiers ->
        identifiers.forEach { identifier ->
            val trimmed = identifier.trim()
            if (trimmed.isBlank()) return@forEach
            val key = trimmed.lowercase()
            if (key in seenKeys) return@forEach
            aggregated.add(trimmed)
            seenKeys.add(key)
            if (aggregated.size >= maxEntries) {
                return aggregated
            }
        }
    }
    return aggregated
}

internal fun buildSelfPostStorageKey(threadId: String, boardId: String?): String {
    val cleanThreadId = threadId.trim()
    val cleanBoardId = boardId?.trim().orEmpty()
    return if (cleanBoardId.isBlank()) {
        cleanThreadId
    } else {
        "$cleanBoardId$SELF_POST_IDENTIFIER_DELIMITER$cleanThreadId"
    }
}

internal fun sanitizeManualSaveDirectoryValue(input: String?): String {
    val trimmed = input?.trim().orEmpty()
    if (trimmed.isBlank()) return DEFAULT_MANUAL_SAVE_ROOT
    val withoutCurrentDirPrefix = if (trimmed.startsWith("./")) {
        trimmed.removePrefix("./")
    } else {
        trimmed
    }
    if (withoutCurrentDirPrefix == MANUAL_SAVE_DIRECTORY) return DEFAULT_MANUAL_SAVE_ROOT
    return withoutCurrentDirPrefix.ifBlank { DEFAULT_MANUAL_SAVE_ROOT }
}

internal fun decodeAttachmentPickerPreferenceValue(raw: String?): AttachmentPickerPreference {
    return runCatching {
        raw?.let { AttachmentPickerPreference.valueOf(it) }
    }.getOrNull() ?: AttachmentPickerPreference.MEDIA
}

internal fun decodeSaveDirectorySelectionValue(raw: String?): SaveDirectorySelection {
    return runCatching {
        raw?.let { SaveDirectorySelection.valueOf(it) }
    }.getOrNull() ?: SaveDirectorySelection.MANUAL_INPUT
}

internal fun decodeThreadMenuConfigValue(
    raw: String?,
    json: Json,
    serializer: KSerializer<List<ThreadMenuItemConfig>>
): List<ThreadMenuItemConfig> {
    if (raw.isNullOrBlank()) return defaultThreadMenuConfig()
    return runCatching {
        json.decodeFromString(serializer, raw)
    }.map(::normalizeThreadMenuConfig)
        .getOrDefault(defaultThreadMenuConfig())
}

internal fun decodeThreadSettingsMenuConfigValue(
    raw: String?,
    json: Json,
    serializer: KSerializer<List<ThreadSettingsMenuItemConfig>>
): List<ThreadSettingsMenuItemConfig> {
    if (raw.isNullOrBlank()) return defaultThreadSettingsMenuConfig()
    return runCatching {
        json.decodeFromString(serializer, raw)
    }.map(::normalizeThreadSettingsMenuConfig)
        .getOrDefault(defaultThreadSettingsMenuConfig())
}

internal fun decodeThreadMenuEntriesValue(
    raw: String?,
    json: Json,
    serializer: KSerializer<List<ThreadMenuEntryConfig>>
): List<ThreadMenuEntryConfig> {
    if (raw.isNullOrBlank()) return defaultThreadMenuEntries()
    return runCatching {
        json.decodeFromString(serializer, raw)
    }.map(::normalizeThreadMenuEntries)
        .getOrDefault(defaultThreadMenuEntries())
}

internal fun decodeCatalogNavEntriesValue(
    raw: String?,
    json: Json,
    serializer: KSerializer<List<CatalogNavEntryConfig>>
): List<CatalogNavEntryConfig> {
    if (raw.isNullOrBlank()) return defaultCatalogNavEntries()
    return runCatching {
        json.decodeFromString(serializer, raw)
    }.map(::normalizeCatalogNavEntries)
        .getOrDefault(defaultCatalogNavEntries())
}

internal fun mergeSelfPostIdentifierMap(
    currentMap: Map<String, List<String>>,
    threadId: String,
    identifier: String,
    boardId: String? = null,
    maxEntries: Int = SELF_POST_IDENTIFIER_MAX_ENTRIES_VALUE
): Map<String, List<String>> {
    val trimmed = identifier.trim().takeIf { it.isNotBlank() } ?: return currentMap
    val scopedKey = buildSelfPostStorageKey(threadId, boardId)
    val legacyKey = threadId.trim()
    val existingForThread = buildList {
        addAll(currentMap[scopedKey].orEmpty())
        if (scopedKey != legacyKey) {
            addAll(currentMap[legacyKey].orEmpty())
        }
    }
    val normalized = existingForThread
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toMutableList()
    if (normalized.none { it.equals(trimmed, ignoreCase = true) }) {
        normalized.add(trimmed)
    }
    val updatedThreadList = normalized
        .asSequence()
        .distinctBy { it.lowercase() }
        .take(maxEntries)
        .toList()
    val nextMap = currentMap.toMutableMap()
    nextMap[scopedKey] = updatedThreadList
    return nextMap.toMap()
}

internal fun removeSelfPostIdentifiersFromMap(
    currentMap: Map<String, List<String>>,
    threadId: String,
    boardId: String? = null
): Map<String, List<String>> {
    val scopedKey = buildSelfPostStorageKey(threadId, boardId)
    val mutable = currentMap.toMutableMap()
    val removedScoped = mutable.remove(scopedKey) != null
    if (boardId.isNullOrBlank()) {
        mutable.remove(threadId.trim())
        val scopedSuffix = "$SELF_POST_IDENTIFIER_DELIMITER${threadId.trim()}"
        mutable.keys
            .filter { it.endsWith(scopedSuffix) }
            .forEach { key -> mutable.remove(key) }
    } else if (!removedScoped) {
        return currentMap
    }
    return mutable.toMap()
}
