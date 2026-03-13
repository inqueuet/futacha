package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.CatalogDisplayStyle
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuItemConfig
import com.valoser.futacha.shared.model.ThreadSettingsMenuItemConfig
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.PreferredFileManager
import com.valoser.futacha.shared.util.SaveDirectorySelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

internal data class AppStatePreferenceFlows(
    val manualSaveDirectory: Flow<String>,
    val manualSaveLocation: Flow<SaveLocation>,
    val attachmentPickerPreference: Flow<AttachmentPickerPreference>,
    val saveDirectorySelection: Flow<SaveDirectorySelection>,
    val lastUsedDeleteKey: Flow<String>,
    val catalogModes: Flow<Map<String, CatalogMode>>,
    val catalogDisplayStyle: Flow<CatalogDisplayStyle>,
    val catalogGridColumns: Flow<Int>,
    val ngHeaders: Flow<List<String>>,
    val ngWords: Flow<List<String>>,
    val catalogNgWords: Flow<List<String>>,
    val watchWords: Flow<List<String>>,
    val selfPostIdentifierMapFlow: Flow<Map<String, List<String>>>,
    val selfPostIdentifiers: Flow<List<String>>,
    val preferredFileManagerFlow: Flow<PreferredFileManager?>,
    val threadMenuConfig: Flow<List<ThreadMenuItemConfig>>,
    val threadSettingsMenuConfig: Flow<List<ThreadSettingsMenuItemConfig>>,
    val threadMenuEntries: Flow<List<ThreadMenuEntryConfig>>,
    val catalogNavEntries: Flow<List<CatalogNavEntryConfig>>
)

internal fun buildAppStatePreferenceFlows(
    storage: PlatformStateStorage,
    json: Json,
    stringListSerializer: KSerializer<List<String>>,
    selfPostIdentifierMapSerializer: KSerializer<Map<String, List<String>>>,
    threadMenuConfigSerializer: KSerializer<List<ThreadMenuItemConfig>>,
    threadSettingsMenuConfigSerializer: KSerializer<List<ThreadSettingsMenuItemConfig>>,
    threadMenuEntriesSerializer: KSerializer<List<ThreadMenuEntryConfig>>,
    catalogNavEntriesSerializer: KSerializer<List<CatalogNavEntryConfig>>,
    selfIdentifierMaxEntries: Int = SELF_POST_IDENTIFIER_MAX_ENTRIES_VALUE
): AppStatePreferenceFlows {
    val manualSaveDirectory = storage.manualSaveDirectory
        .map { manualPath ->
            sanitizeManualSaveDirectoryValue(manualPath)
        }
        .distinctUntilChanged()

    val selfPostIdentifierMapFlow = storage.selfPostIdentifiersJson
        .distinctUntilChanged()
        .map { raw ->
            withContext(AppDispatchers.parsing) {
                decodeSelfPostIdentifierMapValue(raw, json, selfPostIdentifierMapSerializer)
            }
        }

    return AppStatePreferenceFlows(
        manualSaveDirectory = manualSaveDirectory,
        manualSaveLocation = manualSaveDirectory
            .map(SaveLocation::fromString)
            .distinctUntilChanged(),
        attachmentPickerPreference = storage.attachmentPickerPreference
            .map { raw -> decodeAttachmentPickerPreferenceValue(raw) }
            .distinctUntilChanged(),
        saveDirectorySelection = storage.saveDirectorySelection
            .map { raw -> decodeSaveDirectorySelectionValue(raw) }
            .distinctUntilChanged(),
        lastUsedDeleteKey = storage.lastUsedDeleteKey
            .map { raw -> raw?.take(8).orEmpty() }
            .distinctUntilChanged(),
        catalogModes = storage.catalogModeMapJson
            .distinctUntilChanged()
            .map { raw ->
                withContext(AppDispatchers.parsing) {
                    decodeCatalogModeMapValue(raw)
                }
            },
        catalogDisplayStyle = storage.catalogDisplayStyle
            .map { raw -> decodeCatalogDisplayStyleValue(raw) }
            .distinctUntilChanged(),
        catalogGridColumns = storage.catalogGridColumns
            .map { raw -> decodeCatalogGridColumnsValue(raw) }
            .distinctUntilChanged(),
        ngHeaders = storage.ngHeadersJson
            .distinctUntilChanged()
            .map { raw ->
                withContext(AppDispatchers.parsing) {
                    decodeStringListValue(raw, json, stringListSerializer)
                }
            },
        ngWords = storage.ngWordsJson
            .distinctUntilChanged()
            .map { raw ->
                withContext(AppDispatchers.parsing) {
                    decodeStringListValue(raw, json, stringListSerializer)
                }
            },
        catalogNgWords = storage.catalogNgWordsJson
            .distinctUntilChanged()
            .map { raw ->
                withContext(AppDispatchers.parsing) {
                    decodeStringListValue(raw, json, stringListSerializer)
                }
            },
        watchWords = storage.watchWordsJson
            .distinctUntilChanged()
            .map { raw ->
                withContext(AppDispatchers.parsing) {
                    decodeStringListValue(raw, json, stringListSerializer)
                }
            },
        selfPostIdentifierMapFlow = selfPostIdentifierMapFlow,
        selfPostIdentifiers = selfPostIdentifierMapFlow
            .map { decoded -> aggregateSelfPostIdentifiers(decoded, selfIdentifierMaxEntries) }
            .distinctUntilChanged(),
        preferredFileManagerFlow = storage.preferredFileManagerPackage
            .combine(storage.preferredFileManagerLabel) { pkg, label ->
                if (pkg.isBlank()) {
                    null
                } else {
                    PreferredFileManager(pkg, label)
                }
            }
            .distinctUntilChanged(),
        threadMenuConfig = storage.threadMenuConfigJson
            .distinctUntilChanged()
            .map { raw ->
                withContext(AppDispatchers.parsing) {
                    decodeThreadMenuConfigValue(raw, json, threadMenuConfigSerializer)
                }
            },
        threadSettingsMenuConfig = storage.threadSettingsMenuConfigJson
            .distinctUntilChanged()
            .map { raw ->
                withContext(AppDispatchers.parsing) {
                    decodeThreadSettingsMenuConfigValue(raw, json, threadSettingsMenuConfigSerializer)
                }
            },
        threadMenuEntries = storage.threadMenuEntriesConfigJson
            .distinctUntilChanged()
            .map { raw ->
                withContext(AppDispatchers.parsing) {
                    decodeThreadMenuEntriesValue(raw, json, threadMenuEntriesSerializer)
                }
            },
        catalogNavEntries = storage.catalogNavEntriesConfigJson
            .distinctUntilChanged()
            .map { raw ->
                withContext(AppDispatchers.parsing) {
                    decodeCatalogNavEntriesValue(raw, json, catalogNavEntriesSerializer)
                }
            }
    )
}
