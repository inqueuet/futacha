package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.CatalogDisplayStyle
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SaveLocation.Companion.toRawString
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuItemConfig
import com.valoser.futacha.shared.model.ThreadSettingsMenuItemConfig
import com.valoser.futacha.shared.model.normalizeCatalogNavEntries
import com.valoser.futacha.shared.model.normalizeThreadMenuConfig
import com.valoser.futacha.shared.model.normalizeThreadMenuEntries
import com.valoser.futacha.shared.model.normalizeThreadSettingsMenuConfig
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.SaveDirectorySelection
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

internal suspend fun setAppStateBackgroundRefreshEnabled(
    storage: PlatformStateStorage,
    enabled: Boolean,
    tag: String,
    rethrowIfCancellation: (Throwable) -> Unit
) {
    persistAppStatePreference(
        value = enabled,
        update = storage::updateBackgroundRefreshEnabled,
        onFailure = { error ->
            Logger.e(tag, "Failed to save background refresh state: $enabled", error)
        },
        rethrowIfCancellation = rethrowIfCancellation
    )
}

internal suspend fun setAppStateAdsEnabled(
    storage: PlatformStateStorage,
    enabled: Boolean,
    tag: String,
    rethrowIfCancellation: (Throwable) -> Unit
) {
    persistAppStatePreference(
        value = enabled,
        update = storage::updateAdsEnabled,
        onFailure = { error ->
            Logger.e(tag, "Failed to save ads enabled state: $enabled", error)
        },
        rethrowIfCancellation = rethrowIfCancellation
    )
}

internal suspend fun setAppStateHasShownPostingNotice(
    storage: PlatformStateStorage,
    shown: Boolean,
    tag: String,
    rethrowIfCancellation: (Throwable) -> Unit
) {
    persistAppStatePreference(
        value = shown,
        update = storage::updateHasShownPostingNotice,
        onFailure = { error ->
            Logger.e(tag, "Failed to save posting notice state: $shown", error)
        },
        rethrowIfCancellation = rethrowIfCancellation
    )
}

internal suspend fun setAppStateLastUsedDeleteKey(
    storage: PlatformStateStorage,
    deleteKey: String,
    tag: String,
    rethrowIfCancellation: (Throwable) -> Unit
) {
    val sanitized = deleteKey.trim().take(8)
    persistAppStatePreference(
        value = sanitized,
        update = storage::updateLastUsedDeleteKey,
        onFailure = { error ->
            Logger.e(tag, "Failed to save last used delete key", error)
        },
        rethrowIfCancellation = rethrowIfCancellation
    )
}

internal suspend fun setAppStateLightweightModeEnabled(
    storage: PlatformStateStorage,
    enabled: Boolean,
    tag: String,
    rethrowIfCancellation: (Throwable) -> Unit
) {
    persistAppStatePreference(
        value = enabled,
        update = storage::updateLightweightModeEnabled,
        onFailure = { error ->
            Logger.e(tag, "Failed to save lightweight mode state: $enabled", error)
        },
        rethrowIfCancellation = rethrowIfCancellation
    )
}

internal suspend fun setAppStateManualSaveDirectory(
    storage: PlatformStateStorage,
    directory: String,
    tag: String,
    rethrowIfCancellation: (Throwable) -> Unit
) {
    val sanitized = sanitizeManualSaveDirectoryValue(directory)
    persistAppStatePreference(
        value = sanitized,
        update = storage::updateManualSaveDirectory,
        onFailure = { error ->
            Logger.e(tag, "Failed to save manual save directory: $sanitized", error)
        },
        rethrowIfCancellation = rethrowIfCancellation
    )
}

internal suspend fun setAppStateManualSaveLocation(
    storage: PlatformStateStorage,
    location: SaveLocation,
    tag: String,
    rethrowIfCancellation: (Throwable) -> Unit
) {
    val rawString = location.toRawString()
    persistAppStatePreference(
        value = rawString,
        update = storage::updateManualSaveDirectory,
        onFailure = { error ->
            Logger.e(tag, "Failed to save manual save location: $rawString", error)
        },
        rethrowIfCancellation = rethrowIfCancellation
    )
}

internal suspend fun setAppStateAttachmentPickerPreference(
    storage: PlatformStateStorage,
    preference: AttachmentPickerPreference,
    tag: String,
    rethrowIfCancellation: (Throwable) -> Unit
) {
    persistAppStatePreference(
        value = preference.name,
        update = storage::updateAttachmentPickerPreference,
        onFailure = { error ->
            Logger.e(tag, "Failed to save attachment picker preference: $preference", error)
        },
        rethrowIfCancellation = rethrowIfCancellation
    )
}

internal suspend fun setAppStateSaveDirectorySelection(
    storage: PlatformStateStorage,
    selection: SaveDirectorySelection,
    tag: String,
    rethrowIfCancellation: (Throwable) -> Unit
) {
    persistAppStatePreference(
        value = selection.name,
        update = storage::updateSaveDirectorySelection,
        onFailure = { error ->
            Logger.e(tag, "Failed to save save directory selection: $selection", error)
        },
        rethrowIfCancellation = rethrowIfCancellation
    )
}

internal suspend fun setAppStatePreferredFileManager(
    storage: PlatformStateStorage,
    packageName: String?,
    label: String?,
    tag: String,
    rethrowIfCancellation: (Throwable) -> Unit
) {
    persistAppStatePreference(
        value = packageName.orEmpty() to label.orEmpty(),
        update = { (pkg, currentLabel) -> storage.updatePreferredFileManager(pkg, currentLabel) },
        onFailure = { error ->
            Logger.e(tag, "Failed to save preferred file manager: ${packageName.orEmpty()}", error)
        },
        rethrowIfCancellation = rethrowIfCancellation,
        rethrowOnFailure = true
    )
}

internal suspend fun setAppStatePrivacyFilterEnabled(
    storage: PlatformStateStorage,
    enabled: Boolean,
    tag: String,
    rethrowIfCancellation: (Throwable) -> Unit
) {
    persistAppStatePreference(
        value = enabled,
        update = storage::updatePrivacyFilterEnabled,
        onFailure = { error ->
            Logger.e(tag, "Failed to save privacy filter state: $enabled", error)
        },
        rethrowIfCancellation = rethrowIfCancellation
    )
}

internal suspend fun setAppStateCatalogDisplayStyle(
    storage: PlatformStateStorage,
    style: CatalogDisplayStyle,
    tag: String,
    rethrowIfCancellation: (Throwable) -> Unit
) {
    persistAppStatePreference(
        value = style.name,
        update = storage::updateCatalogDisplayStyle,
        onFailure = { error ->
            Logger.e(tag, "Failed to save catalog display style: ${style.name}", error)
        },
        rethrowIfCancellation = rethrowIfCancellation
    )
}

internal suspend fun setAppStateCatalogGridColumns(
    storage: PlatformStateStorage,
    columns: Int,
    tag: String,
    rethrowIfCancellation: (Throwable) -> Unit
) {
    val clamped = columns.coerceIn(MIN_CATALOG_GRID_COLUMNS_VALUE, MAX_CATALOG_GRID_COLUMNS_VALUE)
    persistAppStatePreference(
        value = clamped.toString(),
        update = storage::updateCatalogGridColumns,
        onFailure = { error ->
            Logger.e(tag, "Failed to save catalog grid columns: $clamped", error)
        },
        rethrowIfCancellation = rethrowIfCancellation
    )
}

internal suspend fun setAppStateCatalogMode(
    boardId: String,
    mode: CatalogMode,
    tag: String,
    mutateCatalogModeMap: suspend (
        onReadFailure: (Throwable) -> Unit,
        onWriteFailure: (Throwable) -> Unit,
        transform: (Map<String, CatalogMode>) -> Map<String, CatalogMode>
    ) -> Unit
) {
    val normalizedBoardId = boardId.trim()
    if (normalizedBoardId.isBlank()) {
        Logger.w(tag, "Ignoring catalog mode update with blank boardId")
        return
    }
    mutateCatalogModeMap(
        { error ->
            Logger.e(tag, "Failed to read catalog mode map snapshot", error)
        },
        { error ->
            Logger.e(tag, "Failed to save catalog mode for $normalizedBoardId: ${mode.name}", error)
        }
    ) { current ->
        if (current[normalizedBoardId] == mode) {
            current
        } else {
            current + (normalizedBoardId to mode)
        }
    }
}

internal suspend fun <T> persistAppStateNamedListPreference(
    values: List<T>,
    serializer: KSerializer<List<T>>,
    json: Json,
    update: suspend (String) -> Unit,
    tag: String,
    failureMessage: String,
    rethrowIfCancellation: (Throwable) -> Unit
) {
    persistAppStateListPreference(
        values = values,
        serializer = serializer,
        json = json,
        update = update,
        onFailure = { error ->
            Logger.e(tag, failureMessage, error)
        },
        rethrowIfCancellation = rethrowIfCancellation
    )
}

internal suspend fun setAppStateNgHeaders(
    storage: PlatformStateStorage,
    headers: List<String>,
    serializer: KSerializer<List<String>>,
    json: Json,
    tag: String,
    rethrowIfCancellation: (Throwable) -> Unit
) {
    persistAppStateNamedListPreference(
        values = headers,
        serializer = serializer,
        json = json,
        update = storage::updateNgHeadersJson,
        tag = tag,
        failureMessage = "Failed to save NG headers (${headers.size})",
        rethrowIfCancellation = rethrowIfCancellation
    )
}

internal suspend fun setAppStateNgWords(
    storage: PlatformStateStorage,
    words: List<String>,
    serializer: KSerializer<List<String>>,
    json: Json,
    tag: String,
    rethrowIfCancellation: (Throwable) -> Unit
) {
    persistAppStateNamedListPreference(
        values = words,
        serializer = serializer,
        json = json,
        update = storage::updateNgWordsJson,
        tag = tag,
        failureMessage = "Failed to save NG words (${words.size})",
        rethrowIfCancellation = rethrowIfCancellation
    )
}

internal suspend fun setAppStateCatalogNgWords(
    storage: PlatformStateStorage,
    words: List<String>,
    serializer: KSerializer<List<String>>,
    json: Json,
    tag: String,
    rethrowIfCancellation: (Throwable) -> Unit
) {
    persistAppStateNamedListPreference(
        values = words,
        serializer = serializer,
        json = json,
        update = storage::updateCatalogNgWordsJson,
        tag = tag,
        failureMessage = "Failed to save catalog NG words (${words.size})",
        rethrowIfCancellation = rethrowIfCancellation
    )
}

internal suspend fun setAppStateWatchWords(
    storage: PlatformStateStorage,
    words: List<String>,
    serializer: KSerializer<List<String>>,
    json: Json,
    tag: String,
    rethrowIfCancellation: (Throwable) -> Unit
) {
    persistAppStateNamedListPreference(
        values = words,
        serializer = serializer,
        json = json,
        update = storage::updateWatchWordsJson,
        tag = tag,
        failureMessage = "Failed to save watch words (${words.size})",
        rethrowIfCancellation = rethrowIfCancellation
    )
}

internal suspend fun setAppStateThreadMenuConfig(
    storage: PlatformStateStorage,
    config: List<ThreadMenuItemConfig>,
    serializer: KSerializer<List<ThreadMenuItemConfig>>,
    json: Json,
    tag: String,
    rethrowIfCancellation: (Throwable) -> Unit
) {
    val normalized = normalizeThreadMenuConfig(config)
    persistAppStateNamedListPreference(
        values = normalized,
        serializer = serializer,
        json = json,
        update = storage::updateThreadMenuConfigJson,
        tag = tag,
        failureMessage = "Failed to save thread menu config (${normalized.size} items)",
        rethrowIfCancellation = rethrowIfCancellation
    )
}

internal suspend fun setAppStateThreadSettingsMenuConfig(
    storage: PlatformStateStorage,
    config: List<ThreadSettingsMenuItemConfig>,
    serializer: KSerializer<List<ThreadSettingsMenuItemConfig>>,
    json: Json,
    tag: String,
    rethrowIfCancellation: (Throwable) -> Unit
) {
    val normalized = normalizeThreadSettingsMenuConfig(config)
    persistAppStateNamedListPreference(
        values = normalized,
        serializer = serializer,
        json = json,
        update = storage::updateThreadSettingsMenuConfigJson,
        tag = tag,
        failureMessage = "Failed to save thread settings menu config (${normalized.size} items)",
        rethrowIfCancellation = rethrowIfCancellation
    )
}

internal suspend fun setAppStateThreadMenuEntries(
    storage: PlatformStateStorage,
    config: List<ThreadMenuEntryConfig>,
    serializer: KSerializer<List<ThreadMenuEntryConfig>>,
    json: Json,
    tag: String,
    rethrowIfCancellation: (Throwable) -> Unit
) {
    val normalized = normalizeThreadMenuEntries(config)
    persistAppStateNamedListPreference(
        values = normalized,
        serializer = serializer,
        json = json,
        update = storage::updateThreadMenuEntriesConfigJson,
        tag = tag,
        failureMessage = "Failed to save thread menu entries (${normalized.size} items)",
        rethrowIfCancellation = rethrowIfCancellation
    )
}

internal suspend fun setAppStateCatalogNavEntries(
    storage: PlatformStateStorage,
    config: List<CatalogNavEntryConfig>,
    serializer: KSerializer<List<CatalogNavEntryConfig>>,
    json: Json,
    tag: String,
    rethrowIfCancellation: (Throwable) -> Unit
) {
    val normalized = normalizeCatalogNavEntries(config)
    persistAppStateNamedListPreference(
        values = normalized,
        serializer = serializer,
        json = json,
        update = storage::updateCatalogNavEntriesConfigJson,
        tag = tag,
        failureMessage = "Failed to save catalog nav entries (${normalized.size} items)",
        rethrowIfCancellation = rethrowIfCancellation
    )
}

internal suspend fun addAppStateSelfPostIdentifier(
    threadId: String,
    identifier: String,
    boardId: String? = null,
    maxEntries: Int = SELF_POST_IDENTIFIER_MAX_ENTRIES_VALUE,
    mutateSelfPostIdentifierMap: suspend (
        transform: (Map<String, List<String>>) -> Map<String, List<String>>
    ) -> Unit
) {
    val trimmed = identifier.trim().takeIf { it.isNotBlank() } ?: return
    mutateSelfPostIdentifierMap { currentMap ->
        mergeSelfPostIdentifierMap(
            currentMap = currentMap,
            threadId = threadId,
            identifier = trimmed,
            boardId = boardId,
            maxEntries = maxEntries
        )
    }
}

internal suspend fun removeAppStateSelfPostIdentifiersForThread(
    threadId: String,
    boardId: String? = null,
    mutateSelfPostIdentifierMap: suspend (
        transform: (Map<String, List<String>>) -> Map<String, List<String>>
    ) -> Unit
) {
    mutateSelfPostIdentifierMap { currentMap ->
        removeSelfPostIdentifiersFromMap(currentMap, threadId, boardId)
    }
}

internal suspend fun clearAppStateSelfPostIdentifiers(
    mutateSelfPostIdentifierMap: suspend (
        transform: (Map<String, List<String>>) -> Map<String, List<String>>
    ) -> Unit
) {
    mutateSelfPostIdentifierMap { currentMap ->
        if (currentMap.isEmpty()) currentMap else emptyMap()
    }
}
