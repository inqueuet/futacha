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
    private suspend fun <T> setSimple(
        value: T,
        update: suspend (T) -> Unit,
        label: String,
        rethrowOnFailure: Boolean = false
    ) {
        setAppStateSimplePreference(
            value = value,
            update = update,
            label = label,
            tag = tag,
            rethrowIfCancellation = rethrowIfCancellation,
            rethrowOnFailure = rethrowOnFailure
        )
    }

    private suspend fun <T> setList(
        values: List<T>,
        serializer: KSerializer<List<T>>,
        update: suspend (String) -> Unit,
        label: String
    ) {
        setAppStateListPreference(
            values = values,
            serializer = serializer,
            json = json,
            update = update,
            label = label,
            tag = tag,
            rethrowIfCancellation = rethrowIfCancellation
        )
    }

    suspend fun setBackgroundRefreshEnabled(enabled: Boolean) {
        setSimple(enabled, storage::updateBackgroundRefreshEnabled, "background refresh state")
    }

    suspend fun setAdsEnabled(enabled: Boolean) {
        setSimple(enabled, storage::updateAdsEnabled, "ads enabled state")
    }

    suspend fun setHasShownPostingNotice(shown: Boolean) {
        setSimple(shown, storage::updateHasShownPostingNotice, "posting notice state")
    }

    suspend fun setLastUsedDeleteKey(deleteKey: String) {
        val sanitized = deleteKey.trim().take(8)
        setSimple(sanitized, storage::updateLastUsedDeleteKey, "last used delete key")
    }

    suspend fun setLightweightModeEnabled(enabled: Boolean) {
        setSimple(enabled, storage::updateLightweightModeEnabled, "lightweight mode state")
    }

    suspend fun setManualSaveDirectory(directory: String) {
        val sanitized = sanitizeManualSaveDirectoryValue(directory)
        setSimple(sanitized, storage::updateManualSaveDirectory, "manual save directory")
    }

    suspend fun setManualSaveLocation(location: SaveLocation) {
        val rawString = location.toRawString()
        setSimple(rawString, storage::updateManualSaveDirectory, "manual save location")
    }

    suspend fun setAttachmentPickerPreference(preference: AttachmentPickerPreference) {
        setSimple(preference.name, storage::updateAttachmentPickerPreference, "attachment picker preference")
    }

    suspend fun setSaveDirectorySelection(selection: SaveDirectorySelection) {
        setSimple(selection.name, storage::updateSaveDirectorySelection, "save directory selection")
    }

    suspend fun setPreferredFileManager(packageName: String?, label: String?) {
        setSimple(
            value = packageName.orEmpty() to label.orEmpty(),
            update = { (pkg, currentLabel) -> storage.updatePreferredFileManager(pkg, currentLabel) },
            label = "preferred file manager",
            rethrowOnFailure = true
        )
    }

    fun getPreferredFileManager(): Flow<PreferredFileManager?> = preferredFileManagerFlow

    suspend fun setPrivacyFilterEnabled(enabled: Boolean) {
        setSimple(enabled, storage::updatePrivacyFilterEnabled, "privacy filter state")
    }

    suspend fun setCatalogDisplayStyle(style: CatalogDisplayStyle) {
        setSimple(style.name, storage::updateCatalogDisplayStyle, "catalog display style")
    }

    suspend fun setCatalogGridColumns(columns: Int) {
        val clamped = columns.coerceIn(MIN_CATALOG_GRID_COLUMNS_VALUE, MAX_CATALOG_GRID_COLUMNS_VALUE)
        setSimple(clamped.toString(), storage::updateCatalogGridColumns, "catalog grid columns")
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
        setList(headers, stringListSerializer, storage::updateNgHeadersJson, "NG headers")
    }

    suspend fun setNgWords(words: List<String>) {
        setList(words, stringListSerializer, storage::updateNgWordsJson, "NG words")
    }

    suspend fun setCatalogNgWords(words: List<String>) {
        setList(words, stringListSerializer, storage::updateCatalogNgWordsJson, "catalog NG words")
    }

    suspend fun setWatchWords(words: List<String>) {
        setList(words, stringListSerializer, storage::updateWatchWordsJson, "watch words")
    }

    suspend fun setThreadMenuConfig(config: List<ThreadMenuItemConfig>) {
        val normalized = normalizeThreadMenuConfig(config)
        setList(normalized, threadMenuConfigSerializer, storage::updateThreadMenuConfigJson, "thread menu config")
    }

    suspend fun setThreadSettingsMenuConfig(config: List<ThreadSettingsMenuItemConfig>) {
        val normalized = normalizeThreadSettingsMenuConfig(config)
        setList(normalized, threadSettingsMenuConfigSerializer, storage::updateThreadSettingsMenuConfigJson, "thread settings menu config")
    }

    suspend fun setThreadMenuEntries(config: List<ThreadMenuEntryConfig>) {
        val normalized = normalizeThreadMenuEntries(config)
        setList(normalized, threadMenuEntriesSerializer, storage::updateThreadMenuEntriesConfigJson, "thread menu entries")
    }

    suspend fun setCatalogNavEntries(config: List<CatalogNavEntryConfig>) {
        val normalized = normalizeCatalogNavEntries(config)
        setList(normalized, catalogNavEntriesSerializer, storage::updateCatalogNavEntriesConfigJson, "catalog nav entries")
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
