package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal open class BaseInMemoryPlatformStateStorage : PlatformStateStorage {
    val boardsState = MutableStateFlow<String?>(null)
    val historyState = MutableStateFlow<String?>(null)
    val privacyFilterState = MutableStateFlow(false)
    val backgroundRefreshState = MutableStateFlow(false)
    val adsEnabledState = MutableStateFlow(false)
    val lightweightModeState = MutableStateFlow(false)
    val manualSaveDirectoryState = MutableStateFlow(DEFAULT_MANUAL_SAVE_ROOT)
    val attachmentPickerPreferenceState = MutableStateFlow<String?>(null)
    val saveDirectorySelectionState = MutableStateFlow<String?>(null)
    val lastUsedDeleteKeyState = MutableStateFlow<String?>(null)
    val catalogModeMapState = MutableStateFlow<String?>(null)
    val catalogDisplayStyleState = MutableStateFlow<String?>(null)
    val catalogGridColumnsState = MutableStateFlow<String?>(null)
    val ngHeadersState = MutableStateFlow<String?>(null)
    val ngWordsState = MutableStateFlow<String?>(null)
    val catalogNgWordsState = MutableStateFlow<String?>(null)
    val watchWordsState = MutableStateFlow<String?>(null)
    val selfPostIdentifiersState = MutableStateFlow<String?>(null)
    val preferredFileManagerPackageState = MutableStateFlow("")
    val preferredFileManagerLabelState = MutableStateFlow("")
    val threadMenuConfigState = MutableStateFlow<String?>(null)
    val threadSettingsMenuConfigState = MutableStateFlow<String?>(null)
    val threadMenuEntriesConfigState = MutableStateFlow<String?>(null)
    val catalogNavEntriesConfigState = MutableStateFlow<String?>(null)

    override val boardsJson: Flow<String?> = boardsState
    override val historyJson: Flow<String?> = historyState
    override val privacyFilterEnabled: Flow<Boolean> = privacyFilterState
    override val backgroundRefreshEnabled: Flow<Boolean> = backgroundRefreshState
    override val adsEnabled: Flow<Boolean> = adsEnabledState
    override val lightweightModeEnabled: Flow<Boolean> = lightweightModeState
    override val manualSaveDirectory: Flow<String> = manualSaveDirectoryState
    override val attachmentPickerPreference: Flow<String?> = attachmentPickerPreferenceState
    override val saveDirectorySelection: Flow<String?> = saveDirectorySelectionState
    override val lastUsedDeleteKey: Flow<String?> = lastUsedDeleteKeyState
    override val catalogModeMapJson: Flow<String?> = catalogModeMapState
    override val catalogDisplayStyle: Flow<String?> = catalogDisplayStyleState
    override val catalogGridColumns: Flow<String?> = catalogGridColumnsState
    override val ngHeadersJson: Flow<String?> = ngHeadersState
    override val ngWordsJson: Flow<String?> = ngWordsState
    override val catalogNgWordsJson: Flow<String?> = catalogNgWordsState
    override val watchWordsJson: Flow<String?> = watchWordsState
    override val selfPostIdentifiersJson: Flow<String?> = selfPostIdentifiersState
    override val preferredFileManagerPackage: Flow<String> = preferredFileManagerPackageState
    override val preferredFileManagerLabel: Flow<String> = preferredFileManagerLabelState
    override val threadMenuConfigJson: Flow<String?> = threadMenuConfigState
    override val threadSettingsMenuConfigJson: Flow<String?> = threadSettingsMenuConfigState
    override val threadMenuEntriesConfigJson: Flow<String?> = threadMenuEntriesConfigState
    override val catalogNavEntriesConfigJson: Flow<String?> = catalogNavEntriesConfigState

    override suspend fun updateBoardsJson(value: String) { boardsState.value = value }
    override suspend fun updateHistoryJson(value: String) { historyState.value = value }
    override suspend fun updatePrivacyFilterEnabled(enabled: Boolean) { privacyFilterState.value = enabled }
    override suspend fun updateBackgroundRefreshEnabled(enabled: Boolean) { backgroundRefreshState.value = enabled }
    override suspend fun updateAdsEnabled(enabled: Boolean) { adsEnabledState.value = enabled }
    override suspend fun updateLightweightModeEnabled(enabled: Boolean) { lightweightModeState.value = enabled }
    override suspend fun updateManualSaveDirectory(directory: String) { manualSaveDirectoryState.value = directory }
    override suspend fun updateAttachmentPickerPreference(preference: String) { attachmentPickerPreferenceState.value = preference }
    override suspend fun updateSaveDirectorySelection(selection: String) { saveDirectorySelectionState.value = selection }
    override suspend fun updateLastUsedDeleteKey(value: String) { lastUsedDeleteKeyState.value = value }
    override suspend fun updateCatalogModeMapJson(value: String) { catalogModeMapState.value = value }
    override suspend fun updateCatalogDisplayStyle(style: String) { catalogDisplayStyleState.value = style }
    override suspend fun updateCatalogGridColumns(columns: String) { catalogGridColumnsState.value = columns }
    override suspend fun updateNgHeadersJson(value: String) { ngHeadersState.value = value }
    override suspend fun updateNgWordsJson(value: String) { ngWordsState.value = value }
    override suspend fun updateCatalogNgWordsJson(value: String) { catalogNgWordsState.value = value }
    override suspend fun updateWatchWordsJson(value: String) { watchWordsState.value = value }
    override suspend fun updateSelfPostIdentifiersJson(value: String) { selfPostIdentifiersState.value = value }

    override suspend fun updatePreferredFileManager(packageName: String, label: String) {
        preferredFileManagerPackageState.value = packageName
        preferredFileManagerLabelState.value = label
    }

    override suspend fun updatePreferredFileManagerPackage(packageName: String) {
        preferredFileManagerPackageState.value = packageName
    }

    override suspend fun updatePreferredFileManagerLabel(label: String) {
        preferredFileManagerLabelState.value = label
    }

    override suspend fun updateThreadMenuConfigJson(value: String) { threadMenuConfigState.value = value }
    override suspend fun updateThreadSettingsMenuConfigJson(value: String) { threadSettingsMenuConfigState.value = value }
    override suspend fun updateThreadMenuEntriesConfigJson(value: String) { threadMenuEntriesConfigState.value = value }
    override suspend fun updateCatalogNavEntriesConfigJson(value: String) { catalogNavEntriesConfigState.value = value }

    override suspend fun seedIfEmpty(seedBundles: AppStateSeedBundles) {
        if (boardsState.value == null) boardsState.value = seedBundles.boards.boardsJson
        if (historyState.value == null) historyState.value = seedBundles.history.historyJson
        if (manualSaveDirectoryState.value.isBlank()) manualSaveDirectoryState.value = DEFAULT_MANUAL_SAVE_ROOT
        if (ngHeadersState.value == null) ngHeadersState.value = seedBundles.preferences.ngHeadersJson
        if (ngWordsState.value == null) ngWordsState.value = seedBundles.preferences.ngWordsJson
        if (catalogNgWordsState.value == null) catalogNgWordsState.value = seedBundles.preferences.catalogNgWordsJson
        if (watchWordsState.value == null) watchWordsState.value = seedBundles.preferences.watchWordsJson
        if (selfPostIdentifiersState.value == null) selfPostIdentifiersState.value = seedBundles.preferences.selfPostIdentifiersJson
        if (catalogModeMapState.value == null) catalogModeMapState.value = seedBundles.preferences.catalogModeMapJson
        if (attachmentPickerPreferenceState.value == null) {
            attachmentPickerPreferenceState.value = seedBundles.preferences.attachmentPickerPreference
        }
        if (saveDirectorySelectionState.value == null) {
            saveDirectorySelectionState.value = seedBundles.preferences.saveDirectorySelection
        }
        if (lastUsedDeleteKeyState.value == null) {
            lastUsedDeleteKeyState.value = seedBundles.preferences.lastUsedDeleteKey
        }
        if (threadMenuConfigState.value == null) {
            threadMenuConfigState.value = seedBundles.preferences.threadMenuConfigJson
        }
        if (threadSettingsMenuConfigState.value == null) {
            threadSettingsMenuConfigState.value = seedBundles.preferences.threadSettingsMenuConfigJson
        }
        if (threadMenuEntriesConfigState.value == null) {
            threadMenuEntriesConfigState.value = seedBundles.preferences.threadMenuEntriesConfigJson
        }
        if (catalogNavEntriesConfigState.value == null) {
            catalogNavEntriesConfigState.value = seedBundles.preferences.catalogNavEntriesJson
        }
    }
}
