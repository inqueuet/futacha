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
    suspend fun setBackgroundRefreshEnabled(enabled: Boolean) {
        setAppStateBackgroundRefreshEnabled(
            storage = storage,
            enabled = enabled,
            tag = tag,
            rethrowIfCancellation = rethrowIfCancellation
        )
    }

    suspend fun setAdsEnabled(enabled: Boolean) {
        setAppStateAdsEnabled(
            storage = storage,
            enabled = enabled,
            tag = tag,
            rethrowIfCancellation = rethrowIfCancellation
        )
    }

    suspend fun setLastUsedDeleteKey(deleteKey: String) {
        setAppStateLastUsedDeleteKey(
            storage = storage,
            deleteKey = deleteKey,
            tag = tag,
            rethrowIfCancellation = rethrowIfCancellation
        )
    }

    suspend fun setLightweightModeEnabled(enabled: Boolean) {
        setAppStateLightweightModeEnabled(
            storage = storage,
            enabled = enabled,
            tag = tag,
            rethrowIfCancellation = rethrowIfCancellation
        )
    }

    suspend fun setManualSaveDirectory(directory: String) {
        setAppStateManualSaveDirectory(
            storage = storage,
            directory = directory,
            tag = tag,
            rethrowIfCancellation = rethrowIfCancellation
        )
    }

    suspend fun setManualSaveLocation(location: SaveLocation) {
        setAppStateManualSaveLocation(
            storage = storage,
            location = location,
            tag = tag,
            rethrowIfCancellation = rethrowIfCancellation
        )
    }

    suspend fun setAttachmentPickerPreference(preference: AttachmentPickerPreference) {
        setAppStateAttachmentPickerPreference(
            storage = storage,
            preference = preference,
            tag = tag,
            rethrowIfCancellation = rethrowIfCancellation
        )
    }

    suspend fun setSaveDirectorySelection(selection: SaveDirectorySelection) {
        setAppStateSaveDirectorySelection(
            storage = storage,
            selection = selection,
            tag = tag,
            rethrowIfCancellation = rethrowIfCancellation
        )
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
        setAppStatePrivacyFilterEnabled(
            storage = storage,
            enabled = enabled,
            tag = tag,
            rethrowIfCancellation = rethrowIfCancellation
        )
    }

    suspend fun setCatalogDisplayStyle(style: CatalogDisplayStyle) {
        setAppStateCatalogDisplayStyle(
            storage = storage,
            style = style,
            tag = tag,
            rethrowIfCancellation = rethrowIfCancellation
        )
    }

    suspend fun setCatalogGridColumns(columns: Int) {
        setAppStateCatalogGridColumns(
            storage = storage,
            columns = columns,
            tag = tag,
            rethrowIfCancellation = rethrowIfCancellation
        )
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
        setAppStateNgHeaders(
            storage = storage,
            headers = headers,
            serializer = stringListSerializer,
            json = json,
            tag = tag,
            rethrowIfCancellation = rethrowIfCancellation
        )
    }

    suspend fun setNgWords(words: List<String>) {
        setAppStateNgWords(
            storage = storage,
            words = words,
            serializer = stringListSerializer,
            json = json,
            tag = tag,
            rethrowIfCancellation = rethrowIfCancellation
        )
    }

    suspend fun setCatalogNgWords(words: List<String>) {
        setAppStateCatalogNgWords(
            storage = storage,
            words = words,
            serializer = stringListSerializer,
            json = json,
            tag = tag,
            rethrowIfCancellation = rethrowIfCancellation
        )
    }

    suspend fun setWatchWords(words: List<String>) {
        setAppStateWatchWords(
            storage = storage,
            words = words,
            serializer = stringListSerializer,
            json = json,
            tag = tag,
            rethrowIfCancellation = rethrowIfCancellation
        )
    }

    suspend fun setThreadMenuConfig(config: List<ThreadMenuItemConfig>) {
        setAppStateThreadMenuConfig(
            storage = storage,
            config = config,
            serializer = threadMenuConfigSerializer,
            json = json,
            tag = tag,
            rethrowIfCancellation = rethrowIfCancellation
        )
    }

    suspend fun setThreadSettingsMenuConfig(config: List<ThreadSettingsMenuItemConfig>) {
        setAppStateThreadSettingsMenuConfig(
            storage = storage,
            config = config,
            serializer = threadSettingsMenuConfigSerializer,
            json = json,
            tag = tag,
            rethrowIfCancellation = rethrowIfCancellation
        )
    }

    suspend fun setThreadMenuEntries(config: List<ThreadMenuEntryConfig>) {
        setAppStateThreadMenuEntries(
            storage = storage,
            config = config,
            serializer = threadMenuEntriesSerializer,
            json = json,
            tag = tag,
            rethrowIfCancellation = rethrowIfCancellation
        )
    }

    suspend fun setCatalogNavEntries(config: List<CatalogNavEntryConfig>) {
        setAppStateCatalogNavEntries(
            storage = storage,
            config = config,
            serializer = catalogNavEntriesSerializer,
            json = json,
            tag = tag,
            rethrowIfCancellation = rethrowIfCancellation
        )
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
