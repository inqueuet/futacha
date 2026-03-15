package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.CatalogDisplayStyle
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuItemConfig
import com.valoser.futacha.shared.model.ThreadSettingsMenuItemConfig
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.PreferredFileManager
import com.valoser.futacha.shared.util.SaveDirectorySelection
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

internal class AppStatePreferenceOperations(
    private val storage: PlatformStateStorage,
    private val json: Json,
    private val stringListSerializer: KSerializer<List<String>>,
    private val threadMenuConfigSerializer: KSerializer<List<ThreadMenuItemConfig>>,
    private val threadSettingsMenuConfigSerializer: KSerializer<List<ThreadSettingsMenuItemConfig>>,
    private val threadMenuEntriesSerializer: KSerializer<List<ThreadMenuEntryConfig>>,
    private val catalogNavEntriesSerializer: KSerializer<List<CatalogNavEntryConfig>>,
    private val preferredFileManagerFlow: Flow<PreferredFileManager?>,
    private val selfIdentifierMaxEntries: Int,
    private val tag: String,
    private val rethrowIfCancellation: (Throwable) -> Unit,
    private val mutateCatalogModeMap: suspend (
        onReadFailure: (Throwable) -> Unit,
        onWriteFailure: (Throwable) -> Unit,
        transform: (Map<String, CatalogMode>) -> Map<String, CatalogMode>
    ) -> Unit,
    private val mutateSelfPostIdentifierMap: suspend (
        transform: (Map<String, List<String>>) -> Map<String, List<String>>
    ) -> Unit
) {
    private suspend fun <T> mutateSimplePreference(
        value: T,
        mutation: suspend (
            storage: PlatformStateStorage,
            value: T,
            tag: String,
            rethrowIfCancellation: (Throwable) -> Unit
        ) -> Unit
    ) {
        mutation(storage, value, tag, rethrowIfCancellation)
    }

    private suspend fun mutateStringListPreference(
        values: List<String>,
        mutation: suspend (
            storage: PlatformStateStorage,
            values: List<String>,
            serializer: KSerializer<List<String>>,
            json: Json,
            tag: String,
            rethrowIfCancellation: (Throwable) -> Unit
        ) -> Unit
    ) {
        mutation(storage, values, stringListSerializer, json, tag, rethrowIfCancellation)
    }

    private suspend fun mutateThreadMenuConfigPreference(
        config: List<ThreadMenuItemConfig>,
        mutation: suspend (
            storage: PlatformStateStorage,
            config: List<ThreadMenuItemConfig>,
            serializer: KSerializer<List<ThreadMenuItemConfig>>,
            json: Json,
            tag: String,
            rethrowIfCancellation: (Throwable) -> Unit
        ) -> Unit
    ) {
        mutation(storage, config, threadMenuConfigSerializer, json, tag, rethrowIfCancellation)
    }

    private suspend fun mutateThreadSettingsMenuConfigPreference(
        config: List<ThreadSettingsMenuItemConfig>,
        mutation: suspend (
            storage: PlatformStateStorage,
            config: List<ThreadSettingsMenuItemConfig>,
            serializer: KSerializer<List<ThreadSettingsMenuItemConfig>>,
            json: Json,
            tag: String,
            rethrowIfCancellation: (Throwable) -> Unit
        ) -> Unit
    ) {
        mutation(storage, config, threadSettingsMenuConfigSerializer, json, tag, rethrowIfCancellation)
    }

    private suspend fun mutateThreadMenuEntriesPreference(
        config: List<ThreadMenuEntryConfig>,
        mutation: suspend (
            storage: PlatformStateStorage,
            config: List<ThreadMenuEntryConfig>,
            serializer: KSerializer<List<ThreadMenuEntryConfig>>,
            json: Json,
            tag: String,
            rethrowIfCancellation: (Throwable) -> Unit
        ) -> Unit
    ) {
        mutation(storage, config, threadMenuEntriesSerializer, json, tag, rethrowIfCancellation)
    }

    private suspend fun mutateCatalogNavEntriesPreference(
        config: List<CatalogNavEntryConfig>,
        mutation: suspend (
            storage: PlatformStateStorage,
            config: List<CatalogNavEntryConfig>,
            serializer: KSerializer<List<CatalogNavEntryConfig>>,
            json: Json,
            tag: String,
            rethrowIfCancellation: (Throwable) -> Unit
        ) -> Unit
    ) {
        mutation(storage, config, catalogNavEntriesSerializer, json, tag, rethrowIfCancellation)
    }

    suspend fun setBackgroundRefreshEnabled(enabled: Boolean) {
        mutateSimplePreference(enabled, ::setAppStateBackgroundRefreshEnabled)
    }

    suspend fun setAdsEnabled(enabled: Boolean) {
        mutateSimplePreference(enabled, ::setAppStateAdsEnabled)
    }

    suspend fun setLastUsedDeleteKey(deleteKey: String) {
        mutateSimplePreference(deleteKey, ::setAppStateLastUsedDeleteKey)
    }

    suspend fun setLightweightModeEnabled(enabled: Boolean) {
        mutateSimplePreference(enabled, ::setAppStateLightweightModeEnabled)
    }

    suspend fun setManualSaveDirectory(directory: String) {
        mutateSimplePreference(directory, ::setAppStateManualSaveDirectory)
    }

    suspend fun setManualSaveLocation(location: SaveLocation) {
        mutateSimplePreference(location, ::setAppStateManualSaveLocation)
    }

    suspend fun setAttachmentPickerPreference(preference: AttachmentPickerPreference) {
        mutateSimplePreference(preference, ::setAppStateAttachmentPickerPreference)
    }

    suspend fun setSaveDirectorySelection(selection: SaveDirectorySelection) {
        mutateSimplePreference(selection, ::setAppStateSaveDirectorySelection)
    }

    suspend fun setPreferredFileManager(packageName: String?, label: String?) {
        setAppStatePreferredFileManager(
            storage = storage,
            packageName = packageName,
            label = label,
            tag = tag,
            rethrowIfCancellation = rethrowIfCancellation
        )
    }

    fun getPreferredFileManager(): Flow<PreferredFileManager?> = preferredFileManagerFlow

    suspend fun setPrivacyFilterEnabled(enabled: Boolean) {
        mutateSimplePreference(enabled, ::setAppStatePrivacyFilterEnabled)
    }

    suspend fun setCatalogDisplayStyle(style: CatalogDisplayStyle) {
        mutateSimplePreference(style, ::setAppStateCatalogDisplayStyle)
    }

    suspend fun setCatalogGridColumns(columns: Int) {
        mutateSimplePreference(columns, ::setAppStateCatalogGridColumns)
    }

    suspend fun setCatalogMode(boardId: String, mode: CatalogMode) {
        setAppStateCatalogMode(
            boardId = boardId,
            mode = mode,
            tag = tag,
            mutateCatalogModeMap = mutateCatalogModeMap
        )
    }

    suspend fun setNgHeaders(headers: List<String>) {
        mutateStringListPreference(headers, ::setAppStateNgHeaders)
    }

    suspend fun setNgWords(words: List<String>) {
        mutateStringListPreference(words, ::setAppStateNgWords)
    }

    suspend fun setCatalogNgWords(words: List<String>) {
        mutateStringListPreference(words, ::setAppStateCatalogNgWords)
    }

    suspend fun setWatchWords(words: List<String>) {
        mutateStringListPreference(words, ::setAppStateWatchWords)
    }

    suspend fun setThreadMenuConfig(config: List<ThreadMenuItemConfig>) {
        mutateThreadMenuConfigPreference(config, ::setAppStateThreadMenuConfig)
    }

    suspend fun setThreadSettingsMenuConfig(config: List<ThreadSettingsMenuItemConfig>) {
        mutateThreadSettingsMenuConfigPreference(config, ::setAppStateThreadSettingsMenuConfig)
    }

    suspend fun setThreadMenuEntries(config: List<ThreadMenuEntryConfig>) {
        mutateThreadMenuEntriesPreference(config, ::setAppStateThreadMenuEntries)
    }

    suspend fun setCatalogNavEntries(config: List<CatalogNavEntryConfig>) {
        mutateCatalogNavEntriesPreference(config, ::setAppStateCatalogNavEntries)
    }

    suspend fun addSelfPostIdentifier(threadId: String, identifier: String, boardId: String? = null) {
        addAppStateSelfPostIdentifier(
            threadId = threadId,
            identifier = identifier,
            boardId = boardId,
            maxEntries = selfIdentifierMaxEntries,
            mutateSelfPostIdentifierMap = mutateSelfPostIdentifierMap
        )
    }

    suspend fun removeSelfPostIdentifiersForThread(threadId: String, boardId: String? = null) {
        removeAppStateSelfPostIdentifiersForThread(
            threadId = threadId,
            boardId = boardId,
            mutateSelfPostIdentifierMap = mutateSelfPostIdentifierMap
        )
    }

    suspend fun clearSelfPostIdentifiers() {
        clearAppStateSelfPostIdentifiers(mutateSelfPostIdentifierMap)
    }
}
