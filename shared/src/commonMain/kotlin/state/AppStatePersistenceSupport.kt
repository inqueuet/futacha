package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuItemConfig
import com.valoser.futacha.shared.model.ThreadSettingsMenuItemConfig
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.SaveDirectorySelection
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

internal suspend fun encodeAppStateBoards(
    boards: List<BoardSummary>,
    json: Json
): String {
    return withContext(AppDispatchers.parsing) {
        json.encodeToString(ListSerializer(BoardSummary.serializer()), boards)
    }
}

internal suspend fun encodeAppStateHistory(
    history: List<ThreadHistoryEntry>,
    json: Json
): String {
    return withContext(AppDispatchers.parsing) {
        json.encodeToString(ListSerializer(ThreadHistoryEntry.serializer()), history)
    }
}

internal fun decodeAppStateBoards(
    raw: String,
    json: Json,
    tag: String
): List<BoardSummary> = runCatching {
    json.decodeFromString(ListSerializer(BoardSummary.serializer()), raw)
}.getOrElse { error ->
    Logger.e(tag, "Failed to decode boards from JSON", error)
    emptyList()
}

internal fun decodeAppStateHistory(
    raw: String,
    json: Json,
    tag: String
): List<ThreadHistoryEntry> = runCatching {
    json.decodeFromString(ListSerializer(ThreadHistoryEntry.serializer()), raw)
}.getOrElse { error ->
    Logger.e(tag, "Failed to decode history from JSON", error)
    emptyList()
}

internal suspend fun <T> persistAppStateListPreference(
    values: List<T>,
    serializer: KSerializer<List<T>>,
    json: Json,
    update: suspend (String) -> Unit,
    onFailure: (Throwable) -> Unit,
    rethrowIfCancellation: (Throwable) -> Unit
) {
    try {
        val encoded = withContext(AppDispatchers.parsing) {
            json.encodeToString(serializer, values)
        }
        update(encoded)
    } catch (error: Exception) {
        rethrowIfCancellation(error)
        onFailure(error)
    }
}

internal suspend fun <T> persistAppStatePreference(
    value: T,
    update: suspend (T) -> Unit,
    onFailure: (Throwable) -> Unit,
    rethrowIfCancellation: (Throwable) -> Unit,
    rethrowOnFailure: Boolean = false
) {
    try {
        update(value)
    } catch (error: Exception) {
        rethrowIfCancellation(error)
        onFailure(error)
        if (rethrowOnFailure) {
            throw error
        }
    }
}

internal suspend fun persistAppStateSelfPostIdentifierMap(
    map: Map<String, List<String>>,
    json: Json,
    update: suspend (String) -> Unit
) {
    val encoded = withContext(AppDispatchers.parsing) {
        json.encodeToString(
            MapSerializer(String.serializer(), ListSerializer(String.serializer())),
            map
        )
    }
    update(encoded)
}

internal suspend fun persistAppStateCatalogModeMap(
    map: Map<String, CatalogMode>,
    json: Json,
    update: suspend (String) -> Unit
) {
    val encoded = withContext(AppDispatchers.parsing) {
        json.encodeToString(
            MapSerializer(String.serializer(), String.serializer()),
            encodeCatalogModeMapValue(map)
        )
    }
    update(encoded)
}

internal data class AppStateSeedPayload(
    val defaultBoardsJson: String,
    val defaultHistoryJson: String,
    val defaultNgHeadersJson: String?,
    val defaultNgWordsJson: String?,
    val defaultCatalogNgWordsJson: String?,
    val defaultWatchWordsJson: String?,
    val defaultSelfPostIdentifiersJson: String?,
    val defaultCatalogModeMapJson: String?,
    val defaultAttachmentPickerPreference: String?,
    val defaultSaveDirectorySelection: String?,
    val defaultLastUsedDeleteKey: String?,
    val defaultThreadMenuConfigJson: String?,
    val defaultThreadSettingsMenuConfigJson: String?,
    val defaultThreadMenuEntriesConfigJson: String?,
    val defaultCatalogNavEntriesJson: String?
)

internal data class AppStateBoardsSeedBundle(
    val boardsJson: String
)

internal data class AppStateHistorySeedBundle(
    val historyJson: String
)

internal data class AppStatePreferencesSeedBundle(
    val ngHeadersJson: String?,
    val ngWordsJson: String?,
    val catalogNgWordsJson: String?,
    val watchWordsJson: String?,
    val selfPostIdentifiersJson: String?,
    val catalogModeMapJson: String?,
    val attachmentPickerPreference: String?,
    val saveDirectorySelection: String?,
    val lastUsedDeleteKey: String?,
    val threadMenuConfigJson: String?,
    val threadSettingsMenuConfigJson: String?,
    val threadMenuEntriesConfigJson: String?,
    val catalogNavEntriesJson: String?
)

internal data class AppStateSeedBundles(
    val boards: AppStateBoardsSeedBundle,
    val history: AppStateHistorySeedBundle,
    val preferences: AppStatePreferencesSeedBundle
)

internal fun AppStateSeedPayload.toSeedBundles(): AppStateSeedBundles {
    return AppStateSeedBundles(
        boards = AppStateBoardsSeedBundle(
            boardsJson = defaultBoardsJson
        ),
        history = AppStateHistorySeedBundle(
            historyJson = defaultHistoryJson
        ),
        preferences = AppStatePreferencesSeedBundle(
            ngHeadersJson = defaultNgHeadersJson,
            ngWordsJson = defaultNgWordsJson,
            catalogNgWordsJson = defaultCatalogNgWordsJson,
            watchWordsJson = defaultWatchWordsJson,
            selfPostIdentifiersJson = defaultSelfPostIdentifiersJson,
            catalogModeMapJson = defaultCatalogModeMapJson,
            attachmentPickerPreference = defaultAttachmentPickerPreference,
            saveDirectorySelection = defaultSaveDirectorySelection,
            lastUsedDeleteKey = defaultLastUsedDeleteKey,
            threadMenuConfigJson = defaultThreadMenuConfigJson,
            threadSettingsMenuConfigJson = defaultThreadSettingsMenuConfigJson,
            threadMenuEntriesConfigJson = defaultThreadMenuEntriesConfigJson,
            catalogNavEntriesJson = defaultCatalogNavEntriesJson
        )
    )
}

internal suspend fun buildAppStateSeedPayload(
    defaultBoards: List<BoardSummary>,
    defaultHistory: List<ThreadHistoryEntry>,
    defaultNgHeaders: List<String>,
    defaultNgWords: List<String>,
    defaultCatalogNgWords: List<String>,
    defaultWatchWords: List<String>,
    defaultSelfPostIdentifierMap: Map<String, List<String>>,
    defaultCatalogModeMap: Map<String, CatalogMode>,
    defaultThreadMenuConfig: List<ThreadMenuItemConfig>,
    defaultThreadSettingsMenuConfig: List<ThreadSettingsMenuItemConfig>,
    defaultThreadMenuEntries: List<ThreadMenuEntryConfig>,
    defaultCatalogNavEntries: List<CatalogNavEntryConfig>,
    defaultLastUsedDeleteKey: String,
    json: Json,
    threadMenuConfig: List<ThreadMenuItemConfig>,
    threadSettingsMenuConfig: List<ThreadSettingsMenuItemConfig>,
    threadMenuEntries: List<ThreadMenuEntryConfig>,
    catalogNavEntries: List<CatalogNavEntryConfig>
): AppStateSeedPayload {
    return withContext(AppDispatchers.parsing) {
        AppStateSeedPayload(
            defaultBoardsJson = json.encodeToString(ListSerializer(BoardSummary.serializer()), defaultBoards),
            defaultHistoryJson = json.encodeToString(ListSerializer(ThreadHistoryEntry.serializer()), defaultHistory),
            defaultNgHeadersJson = json.encodeToString(ListSerializer(String.serializer()), defaultNgHeaders),
            defaultNgWordsJson = json.encodeToString(ListSerializer(String.serializer()), defaultNgWords),
            defaultCatalogNgWordsJson = json.encodeToString(ListSerializer(String.serializer()), defaultCatalogNgWords),
            defaultWatchWordsJson = json.encodeToString(ListSerializer(String.serializer()), defaultWatchWords),
            defaultSelfPostIdentifiersJson = json.encodeToString(
                MapSerializer(String.serializer(), ListSerializer(String.serializer())),
                defaultSelfPostIdentifierMap
            ),
            defaultCatalogModeMapJson = json.encodeToString(
                MapSerializer(String.serializer(), String.serializer()),
                encodeCatalogModeMapValue(defaultCatalogModeMap)
            ),
            defaultAttachmentPickerPreference = AttachmentPickerPreference.MEDIA.name,
            defaultSaveDirectorySelection = SaveDirectorySelection.MANUAL_INPUT.name,
            defaultLastUsedDeleteKey = defaultLastUsedDeleteKey.take(8),
            defaultThreadMenuConfigJson = json.encodeToString(
                ListSerializer(ThreadMenuItemConfig.serializer()),
                threadMenuConfig
            ),
            defaultThreadSettingsMenuConfigJson = json.encodeToString(
                ListSerializer(ThreadSettingsMenuItemConfig.serializer()),
                threadSettingsMenuConfig
            ),
            defaultThreadMenuEntriesConfigJson = json.encodeToString(
                ListSerializer(ThreadMenuEntryConfig.serializer()),
                threadMenuEntries
            ),
            defaultCatalogNavEntriesJson = json.encodeToString(
                ListSerializer(CatalogNavEntryConfig.serializer()),
                catalogNavEntries
            )
        )
    }
}
