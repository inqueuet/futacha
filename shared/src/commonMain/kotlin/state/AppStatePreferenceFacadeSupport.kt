package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.CatalogDisplayStyle
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuItemConfig
import com.valoser.futacha.shared.model.ThreadSettingsMenuItemConfig
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.SaveDirectorySelection

internal class AppStatePreferenceFacade(
    private val setBackgroundRefreshEnabledImpl: suspend (Boolean) -> Unit,
    private val setHasShownPostingNoticeImpl: suspend (Boolean) -> Unit,
    private val setLastUsedDeleteKeyImpl: suspend (String) -> Unit,
    private val setLightweightModeEnabledImpl: suspend (Boolean) -> Unit,
    private val setManualSaveDirectoryImpl: suspend (String) -> Unit,
    private val setManualSaveLocationImpl: suspend (SaveLocation) -> Unit,
    private val setAttachmentPickerPreferenceImpl: suspend (AttachmentPickerPreference) -> Unit,
    private val setSaveDirectorySelectionImpl: suspend (SaveDirectorySelection) -> Unit,
    private val setPreferredFileManagerImpl: suspend (String?, String?) -> Unit,
    private val setPrivacyFilterEnabledImpl: suspend (Boolean) -> Unit,
    private val setCatalogDisplayStyleImpl: suspend (CatalogDisplayStyle) -> Unit,
    private val setCatalogGridColumnsImpl: suspend (Int) -> Unit,
    private val setCatalogModeImpl: suspend (String, CatalogMode) -> Unit,
    private val setNgHeadersImpl: suspend (List<String>) -> Unit,
    private val setNgWordsImpl: suspend (List<String>) -> Unit,
    private val setCatalogNgWordsImpl: suspend (List<String>) -> Unit,
    private val setWatchWordsImpl: suspend (List<String>) -> Unit,
    private val setThreadMenuConfigImpl: suspend (List<ThreadMenuItemConfig>) -> Unit,
    private val setThreadSettingsMenuConfigImpl: suspend (List<ThreadSettingsMenuItemConfig>) -> Unit,
    private val setThreadMenuEntriesImpl: suspend (List<ThreadMenuEntryConfig>) -> Unit,
    private val setCatalogNavEntriesImpl: suspend (List<CatalogNavEntryConfig>) -> Unit,
    private val addSelfPostIdentifierImpl: suspend (String, String, String?) -> Unit,
    private val removeSelfPostIdentifiersForThreadImpl: suspend (String, String?) -> Unit,
    private val clearSelfPostIdentifiersImpl: suspend () -> Unit
) {
    suspend fun setBackgroundRefreshEnabled(enabled: Boolean) = setBackgroundRefreshEnabledImpl(enabled)
    suspend fun setHasShownPostingNotice(shown: Boolean) = setHasShownPostingNoticeImpl(shown)
    suspend fun setLastUsedDeleteKey(deleteKey: String) = setLastUsedDeleteKeyImpl(deleteKey)
    suspend fun setLightweightModeEnabled(enabled: Boolean) = setLightweightModeEnabledImpl(enabled)
    suspend fun setManualSaveDirectory(directory: String) = setManualSaveDirectoryImpl(directory)
    suspend fun setManualSaveLocation(location: SaveLocation) = setManualSaveLocationImpl(location)
    suspend fun setAttachmentPickerPreference(preference: AttachmentPickerPreference) = setAttachmentPickerPreferenceImpl(preference)
    suspend fun setSaveDirectorySelection(selection: SaveDirectorySelection) = setSaveDirectorySelectionImpl(selection)
    suspend fun setPreferredFileManager(packageName: String?, label: String?) = setPreferredFileManagerImpl(packageName, label)
    suspend fun setPrivacyFilterEnabled(enabled: Boolean) = setPrivacyFilterEnabledImpl(enabled)
    suspend fun setCatalogDisplayStyle(style: CatalogDisplayStyle) = setCatalogDisplayStyleImpl(style)
    suspend fun setCatalogGridColumns(columns: Int) = setCatalogGridColumnsImpl(columns)
    suspend fun setCatalogMode(boardId: String, mode: CatalogMode) = setCatalogModeImpl(boardId, mode)
    suspend fun setNgHeaders(headers: List<String>) = setNgHeadersImpl(headers)
    suspend fun setNgWords(words: List<String>) = setNgWordsImpl(words)
    suspend fun setCatalogNgWords(words: List<String>) = setCatalogNgWordsImpl(words)
    suspend fun setWatchWords(words: List<String>) = setWatchWordsImpl(words)
    suspend fun setThreadMenuConfig(config: List<ThreadMenuItemConfig>) = setThreadMenuConfigImpl(config)
    suspend fun setThreadSettingsMenuConfig(config: List<ThreadSettingsMenuItemConfig>) = setThreadSettingsMenuConfigImpl(config)
    suspend fun setThreadMenuEntries(config: List<ThreadMenuEntryConfig>) = setThreadMenuEntriesImpl(config)
    suspend fun setCatalogNavEntries(config: List<CatalogNavEntryConfig>) = setCatalogNavEntriesImpl(config)
    suspend fun addSelfPostIdentifier(threadId: String, identifier: String, boardId: String?) =
        addSelfPostIdentifierImpl(threadId, identifier, boardId)
    suspend fun removeSelfPostIdentifiersForThread(threadId: String, boardId: String?) =
        removeSelfPostIdentifiersForThreadImpl(threadId, boardId)
    suspend fun clearSelfPostIdentifiers() = clearSelfPostIdentifiersImpl()
}

internal fun buildAppStatePreferenceFacade(
    setBackgroundRefreshEnabledImpl: suspend (Boolean) -> Unit,
    setHasShownPostingNoticeImpl: suspend (Boolean) -> Unit,
    setLastUsedDeleteKeyImpl: suspend (String) -> Unit,
    setLightweightModeEnabledImpl: suspend (Boolean) -> Unit,
    setManualSaveDirectoryImpl: suspend (String) -> Unit,
    setManualSaveLocationImpl: suspend (SaveLocation) -> Unit,
    setAttachmentPickerPreferenceImpl: suspend (AttachmentPickerPreference) -> Unit,
    setSaveDirectorySelectionImpl: suspend (SaveDirectorySelection) -> Unit,
    setPreferredFileManagerImpl: suspend (String?, String?) -> Unit,
    setPrivacyFilterEnabledImpl: suspend (Boolean) -> Unit,
    setCatalogDisplayStyleImpl: suspend (CatalogDisplayStyle) -> Unit,
    setCatalogGridColumnsImpl: suspend (Int) -> Unit,
    setCatalogModeImpl: suspend (String, CatalogMode) -> Unit,
    setNgHeadersImpl: suspend (List<String>) -> Unit,
    setNgWordsImpl: suspend (List<String>) -> Unit,
    setCatalogNgWordsImpl: suspend (List<String>) -> Unit,
    setWatchWordsImpl: suspend (List<String>) -> Unit,
    setThreadMenuConfigImpl: suspend (List<ThreadMenuItemConfig>) -> Unit,
    setThreadSettingsMenuConfigImpl: suspend (List<ThreadSettingsMenuItemConfig>) -> Unit,
    setThreadMenuEntriesImpl: suspend (List<ThreadMenuEntryConfig>) -> Unit,
    setCatalogNavEntriesImpl: suspend (List<CatalogNavEntryConfig>) -> Unit,
    addSelfPostIdentifierImpl: suspend (String, String, String?) -> Unit,
    removeSelfPostIdentifiersForThreadImpl: suspend (String, String?) -> Unit,
    clearSelfPostIdentifiersImpl: suspend () -> Unit
): AppStatePreferenceFacade {
    return AppStatePreferenceFacade(
        setBackgroundRefreshEnabledImpl = setBackgroundRefreshEnabledImpl,
        setHasShownPostingNoticeImpl = setHasShownPostingNoticeImpl,
        setLastUsedDeleteKeyImpl = setLastUsedDeleteKeyImpl,
        setLightweightModeEnabledImpl = setLightweightModeEnabledImpl,
        setManualSaveDirectoryImpl = setManualSaveDirectoryImpl,
        setManualSaveLocationImpl = setManualSaveLocationImpl,
        setAttachmentPickerPreferenceImpl = setAttachmentPickerPreferenceImpl,
        setSaveDirectorySelectionImpl = setSaveDirectorySelectionImpl,
        setPreferredFileManagerImpl = setPreferredFileManagerImpl,
        setPrivacyFilterEnabledImpl = setPrivacyFilterEnabledImpl,
        setCatalogDisplayStyleImpl = setCatalogDisplayStyleImpl,
        setCatalogGridColumnsImpl = setCatalogGridColumnsImpl,
        setCatalogModeImpl = setCatalogModeImpl,
        setNgHeadersImpl = setNgHeadersImpl,
        setNgWordsImpl = setNgWordsImpl,
        setCatalogNgWordsImpl = setCatalogNgWordsImpl,
        setWatchWordsImpl = setWatchWordsImpl,
        setThreadMenuConfigImpl = setThreadMenuConfigImpl,
        setThreadSettingsMenuConfigImpl = setThreadSettingsMenuConfigImpl,
        setThreadMenuEntriesImpl = setThreadMenuEntriesImpl,
        setCatalogNavEntriesImpl = setCatalogNavEntriesImpl,
        addSelfPostIdentifierImpl = addSelfPostIdentifierImpl,
        removeSelfPostIdentifiersForThreadImpl = removeSelfPostIdentifiersForThreadImpl,
        clearSelfPostIdentifiersImpl = clearSelfPostIdentifiersImpl
    )
}
