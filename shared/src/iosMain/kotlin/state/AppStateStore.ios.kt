package com.valoser.futacha.shared.state

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import platform.Foundation.NSUserDefaults

private const val BOARDS_KEY = "boards_json"
private const val HISTORY_KEY = "history_json"
private const val CATALOG_DISPLAY_STYLE_KEY = "catalog_display_style"
private const val CATALOG_GRID_COLUMNS_KEY = "catalog_grid_columns"
private const val PRIVACY_FILTER_KEY = "privacy_filter_enabled"
private const val BACKGROUND_REFRESH_KEY = "background_refresh_enabled"
private const val ADS_ENABLED_KEY = "ads_enabled"
private const val POSTING_NOTICE_KEY = "has_shown_posting_notice"
private const val LIGHTWEIGHT_MODE_KEY = "lightweight_mode_enabled"
private const val MANUAL_SAVE_DIRECTORY_KEY = "manual_save_directory"
private const val ATTACHMENT_PICKER_PREF_KEY = "attachment_picker_preference"
private const val SAVE_DIRECTORY_SELECTION_KEY = "save_directory_selection"
private const val CATALOG_MODE_MAP_KEY = "catalog_mode_map_json"
private const val NG_HEADERS_KEY = "ng_headers_json"
private const val NG_WORDS_KEY = "ng_words_json"
private const val CATALOG_NG_WORDS_KEY = "catalog_ng_words_json"
private const val WATCH_WORDS_KEY = "watch_words_json"
private const val SELF_POST_IDENTIFIERS_KEY = "self_post_identifiers_json"
private const val THREAD_MENU_CONFIG_KEY = "thread_menu_config_json"
private const val THREAD_SETTINGS_MENU_CONFIG_KEY = "thread_settings_menu_config_json"
private const val THREAD_MENU_ENTRIES_KEY = "thread_menu_entries_config_json"
private const val CATALOG_NAV_ENTRIES_KEY = "catalog_nav_entries_config_json"
private const val PREFERRED_FILE_MANAGER_PACKAGE_KEY = "preferred_file_manager_package"
private const val PREFERRED_FILE_MANAGER_LABEL_KEY = "preferred_file_manager_label"
private const val LAST_USED_DELETE_KEY = "last_used_delete_key"

internal actual fun createPlatformStateStorage(platformContext: Any?): PlatformStateStorage {
    return IosPlatformStateStorage()
}

private class IosPlatformStateStorage : PlatformStateStorage {
    private val defaults = NSUserDefaults.standardUserDefaults()
    private val updateMutex = Mutex()
    private val boardsState = MutableStateFlow(defaults.stringForKey(BOARDS_KEY))
    private val historyState = MutableStateFlow(defaults.stringForKey(HISTORY_KEY))
    private val displayStyleState = MutableStateFlow(defaults.stringForKey(CATALOG_DISPLAY_STYLE_KEY))
    private val gridColumnsState = MutableStateFlow(defaults.stringForKey(CATALOG_GRID_COLUMNS_KEY))
    private val privacyFilterState = MutableStateFlow(defaults.boolForKey(PRIVACY_FILTER_KEY))
    private val backgroundRefreshState = MutableStateFlow(defaults.boolForKey(BACKGROUND_REFRESH_KEY))
    private val adsEnabledState = MutableStateFlow(
        if (defaults.objectForKey(ADS_ENABLED_KEY) == null) false else defaults.boolForKey(ADS_ENABLED_KEY)
    )
    private val postingNoticeState = MutableStateFlow(defaults.boolForKey(POSTING_NOTICE_KEY))
    private val lightweightModeState = MutableStateFlow(defaults.boolForKey(LIGHTWEIGHT_MODE_KEY))
    private val manualSaveDirectoryState = MutableStateFlow(
        sanitizeManualSaveDirectoryValue(defaults.stringForKey(MANUAL_SAVE_DIRECTORY_KEY))
    )
    private val attachmentPickerPreferenceState = MutableStateFlow(defaults.stringForKey(ATTACHMENT_PICKER_PREF_KEY))
    private val saveDirectorySelectionState = MutableStateFlow(defaults.stringForKey(SAVE_DIRECTORY_SELECTION_KEY))
    private val catalogModeMapState = MutableStateFlow(defaults.stringForKey(CATALOG_MODE_MAP_KEY))
    private val ngHeadersState = MutableStateFlow(defaults.stringForKey(NG_HEADERS_KEY))
    private val ngWordsState = MutableStateFlow(defaults.stringForKey(NG_WORDS_KEY))
    private val catalogNgWordsState = MutableStateFlow(defaults.stringForKey(CATALOG_NG_WORDS_KEY))
    private val watchWordsState = MutableStateFlow(defaults.stringForKey(WATCH_WORDS_KEY))
    private val selfPostIdentifiersState = MutableStateFlow(defaults.stringForKey(SELF_POST_IDENTIFIERS_KEY))
    private val threadMenuConfigState = MutableStateFlow(defaults.stringForKey(THREAD_MENU_CONFIG_KEY))
    private val threadSettingsMenuConfigState = MutableStateFlow(defaults.stringForKey(THREAD_SETTINGS_MENU_CONFIG_KEY))
    private val threadMenuEntriesState = MutableStateFlow(defaults.stringForKey(THREAD_MENU_ENTRIES_KEY))
    private val catalogNavEntriesState = MutableStateFlow(defaults.stringForKey(CATALOG_NAV_ENTRIES_KEY))
    private val preferredFileManagerPackageState = MutableStateFlow(defaults.stringForKey(PREFERRED_FILE_MANAGER_PACKAGE_KEY) ?: "")
    private val preferredFileManagerLabelState = MutableStateFlow(defaults.stringForKey(PREFERRED_FILE_MANAGER_LABEL_KEY) ?: "")
    private val lastUsedDeleteKeyState = MutableStateFlow(defaults.stringForKey(LAST_USED_DELETE_KEY))

    override val boardsJson: Flow<String?> = boardsState
    override val historyJson: Flow<String?> = historyState
    override val privacyFilterEnabled: Flow<Boolean> = privacyFilterState
    override val backgroundRefreshEnabled: Flow<Boolean> = backgroundRefreshState
    override val adsEnabled: Flow<Boolean> = adsEnabledState
    override val hasShownPostingNotice: Flow<Boolean> = postingNoticeState
    override val lightweightModeEnabled: Flow<Boolean> = lightweightModeState
    override val manualSaveDirectory: Flow<String> = manualSaveDirectoryState
    override val attachmentPickerPreference: Flow<String?> = attachmentPickerPreferenceState
    override val saveDirectorySelection: Flow<String?> = saveDirectorySelectionState
    override val catalogModeMapJson: Flow<String?> = catalogModeMapState
    override val catalogDisplayStyle: Flow<String?> = displayStyleState
    override val catalogGridColumns: Flow<String?> = gridColumnsState
    override val ngHeadersJson: Flow<String?> = ngHeadersState
    override val ngWordsJson: Flow<String?> = ngWordsState
    override val catalogNgWordsJson: Flow<String?> = catalogNgWordsState
    override val watchWordsJson: Flow<String?> = watchWordsState
    override val selfPostIdentifiersJson: Flow<String?> = selfPostIdentifiersState
    override val threadMenuConfigJson: Flow<String?> = threadMenuConfigState
    override val threadSettingsMenuConfigJson: Flow<String?> = threadSettingsMenuConfigState
    override val threadMenuEntriesConfigJson: Flow<String?> = threadMenuEntriesState
    override val catalogNavEntriesConfigJson: Flow<String?> = catalogNavEntriesState
    override val preferredFileManagerPackage: Flow<String> = preferredFileManagerPackageState
    override val preferredFileManagerLabel: Flow<String> = preferredFileManagerLabelState
    override val lastUsedDeleteKey: Flow<String?> = lastUsedDeleteKeyState

    private suspend fun update(block: () -> Unit) {
        updateMutex.withLock {
            withContext(Dispatchers.Default) {
                block()
            }
        }
    }

    private suspend fun updateStringState(
        key: String,
        value: String,
        state: MutableStateFlow<String?>
    ) {
        update {
            defaults.setObject(value, forKey = key)
            state.value = value
        }
    }

    private suspend fun updateStringState(
        key: String,
        value: String,
        state: MutableStateFlow<String>
    ) {
        update {
            defaults.setObject(value, forKey = key)
            state.value = value
        }
    }

    private suspend fun updateBooleanState(
        key: String,
        value: Boolean,
        state: MutableStateFlow<Boolean>
    ) {
        update {
            defaults.setBool(value, forKey = key)
            state.value = value
        }
    }

    private suspend fun updateStringStatePair(
        firstKey: String,
        firstValue: String,
        firstState: MutableStateFlow<String>,
        secondKey: String,
        secondValue: String,
        secondState: MutableStateFlow<String>
    ) {
        update {
            defaults.setObject(firstValue, forKey = firstKey)
            defaults.setObject(secondValue, forKey = secondKey)
            firstState.value = firstValue
            secondState.value = secondValue
        }
    }

    private fun seedRequiredStringState(
        key: String,
        value: String,
        state: MutableStateFlow<String?>
    ) {
        if (defaults.stringForKey(key) == null) {
            defaults.setObject(value, forKey = key)
            state.value = value
        }
    }

    private fun seedRequiredBooleanState(
        key: String,
        value: Boolean,
        state: MutableStateFlow<Boolean>
    ) {
        if (defaults.objectForKey(key) == null) {
            defaults.setBool(value, forKey = key)
            state.value = value
        }
    }

    private fun seedRequiredStringState(
        key: String,
        value: String,
        state: MutableStateFlow<String>
    ) {
        if (defaults.stringForKey(key) == null) {
            defaults.setObject(value, forKey = key)
            state.value = value
        }
    }

    private fun seedOptionalStringState(
        key: String,
        value: String?,
        state: MutableStateFlow<String?>
    ) {
        if (value != null && defaults.stringForKey(key) == null) {
            defaults.setObject(value, forKey = key)
            state.value = value
        }
    }

    private fun seedFrom(seedBundles: AppStateSeedBundles) {
        seedRequiredStringState(BOARDS_KEY, seedBundles.boards.boardsJson, boardsState)
        seedRequiredStringState(HISTORY_KEY, seedBundles.history.historyJson, historyState)
        seedRequiredBooleanState(ADS_ENABLED_KEY, false, adsEnabledState)
        seedRequiredBooleanState(POSTING_NOTICE_KEY, false, postingNoticeState)
        seedRequiredStringState(
            MANUAL_SAVE_DIRECTORY_KEY,
            DEFAULT_MANUAL_SAVE_ROOT,
            manualSaveDirectoryState
        )
        seedOptionalStringState(
            LAST_USED_DELETE_KEY,
            seedBundles.preferences.lastUsedDeleteKey,
            lastUsedDeleteKeyState
        )
        seedOptionalStringState(
            NG_HEADERS_KEY,
            seedBundles.preferences.ngHeadersJson,
            ngHeadersState
        )
        seedOptionalStringState(
            NG_WORDS_KEY,
            seedBundles.preferences.ngWordsJson,
            ngWordsState
        )
        seedOptionalStringState(
            CATALOG_NG_WORDS_KEY,
            seedBundles.preferences.catalogNgWordsJson,
            catalogNgWordsState
        )
        seedOptionalStringState(
            WATCH_WORDS_KEY,
            seedBundles.preferences.watchWordsJson,
            watchWordsState
        )
        seedOptionalStringState(
            SELF_POST_IDENTIFIERS_KEY,
            seedBundles.preferences.selfPostIdentifiersJson,
            selfPostIdentifiersState
        )
        seedOptionalStringState(
            ATTACHMENT_PICKER_PREF_KEY,
            seedBundles.preferences.attachmentPickerPreference,
            attachmentPickerPreferenceState
        )
        seedOptionalStringState(
            SAVE_DIRECTORY_SELECTION_KEY,
            seedBundles.preferences.saveDirectorySelection,
            saveDirectorySelectionState
        )
        seedOptionalStringState(
            THREAD_MENU_CONFIG_KEY,
            seedBundles.preferences.threadMenuConfigJson,
            threadMenuConfigState
        )
        seedOptionalStringState(
            THREAD_SETTINGS_MENU_CONFIG_KEY,
            seedBundles.preferences.threadSettingsMenuConfigJson,
            threadSettingsMenuConfigState
        )
        seedOptionalStringState(
            THREAD_MENU_ENTRIES_KEY,
            seedBundles.preferences.threadMenuEntriesConfigJson,
            threadMenuEntriesState
        )
        seedOptionalStringState(
            CATALOG_NAV_ENTRIES_KEY,
            seedBundles.preferences.catalogNavEntriesJson,
            catalogNavEntriesState
        )
        seedOptionalStringState(
            CATALOG_MODE_MAP_KEY,
            seedBundles.preferences.catalogModeMapJson,
            catalogModeMapState
        )
    }

    override suspend fun updateBoardsJson(value: String) {
        updateStringState(BOARDS_KEY, value, boardsState)
    }

    override suspend fun updateHistoryJson(value: String) {
        updateStringState(HISTORY_KEY, value, historyState)
    }

    override suspend fun updatePrivacyFilterEnabled(enabled: Boolean) {
        updateBooleanState(PRIVACY_FILTER_KEY, enabled, privacyFilterState)
    }

    override suspend fun updateBackgroundRefreshEnabled(enabled: Boolean) {
        updateBooleanState(BACKGROUND_REFRESH_KEY, enabled, backgroundRefreshState)
    }

    override suspend fun updateAdsEnabled(enabled: Boolean) {
        updateBooleanState(ADS_ENABLED_KEY, enabled, adsEnabledState)
    }

    override suspend fun updateHasShownPostingNotice(shown: Boolean) {
        updateBooleanState(POSTING_NOTICE_KEY, shown, postingNoticeState)
    }

    override suspend fun updateLightweightModeEnabled(enabled: Boolean) {
        updateBooleanState(LIGHTWEIGHT_MODE_KEY, enabled, lightweightModeState)
    }

    override suspend fun updateManualSaveDirectory(directory: String) {
        updateStringState(MANUAL_SAVE_DIRECTORY_KEY, directory, manualSaveDirectoryState)
    }

    override suspend fun updateAttachmentPickerPreference(preference: String) {
        updateStringState(
            ATTACHMENT_PICKER_PREF_KEY,
            preference,
            attachmentPickerPreferenceState
        )
    }

    override suspend fun updateSaveDirectorySelection(selection: String) {
        updateStringState(SAVE_DIRECTORY_SELECTION_KEY, selection, saveDirectorySelectionState)
    }

    override suspend fun updateCatalogModeMapJson(value: String) {
        updateStringState(CATALOG_MODE_MAP_KEY, value, catalogModeMapState)
    }

    override suspend fun updateCatalogDisplayStyle(style: String) {
        updateStringState(CATALOG_DISPLAY_STYLE_KEY, style, displayStyleState)
    }

    override suspend fun updateCatalogGridColumns(columns: String) {
        updateStringState(CATALOG_GRID_COLUMNS_KEY, columns, gridColumnsState)
    }

    override suspend fun updateNgHeadersJson(value: String) {
        updateStringState(NG_HEADERS_KEY, value, ngHeadersState)
    }

    override suspend fun updateNgWordsJson(value: String) {
        updateStringState(NG_WORDS_KEY, value, ngWordsState)
    }

    override suspend fun updateCatalogNgWordsJson(value: String) {
        updateStringState(CATALOG_NG_WORDS_KEY, value, catalogNgWordsState)
    }

    override suspend fun updateWatchWordsJson(value: String) {
        updateStringState(WATCH_WORDS_KEY, value, watchWordsState)
    }

    override suspend fun updateSelfPostIdentifiersJson(value: String) {
        updateStringState(SELF_POST_IDENTIFIERS_KEY, value, selfPostIdentifiersState)
    }

    override suspend fun updateThreadMenuConfigJson(value: String) {
        updateStringState(THREAD_MENU_CONFIG_KEY, value, threadMenuConfigState)
    }

    override suspend fun updateThreadSettingsMenuConfigJson(value: String) {
        updateStringState(
            THREAD_SETTINGS_MENU_CONFIG_KEY,
            value,
            threadSettingsMenuConfigState
        )
    }

    override suspend fun updateThreadMenuEntriesConfigJson(value: String) {
        updateStringState(THREAD_MENU_ENTRIES_KEY, value, threadMenuEntriesState)
    }

    override suspend fun updateCatalogNavEntriesConfigJson(value: String) {
        updateStringState(CATALOG_NAV_ENTRIES_KEY, value, catalogNavEntriesState)
    }

    override suspend fun updatePreferredFileManagerPackage(packageName: String) {
        updateStringState(
            PREFERRED_FILE_MANAGER_PACKAGE_KEY,
            packageName,
            preferredFileManagerPackageState
        )
    }

    override suspend fun updatePreferredFileManagerLabel(label: String) {
        updateStringState(PREFERRED_FILE_MANAGER_LABEL_KEY, label, preferredFileManagerLabelState)
    }

    override suspend fun updatePreferredFileManager(packageName: String, label: String) {
        updateStringStatePair(
            PREFERRED_FILE_MANAGER_PACKAGE_KEY,
            packageName,
            preferredFileManagerPackageState,
            PREFERRED_FILE_MANAGER_LABEL_KEY,
            label,
            preferredFileManagerLabelState
        )
    }

    override suspend fun updateLastUsedDeleteKey(value: String) {
        updateStringState(LAST_USED_DELETE_KEY, value, lastUsedDeleteKeyState)
    }

    override suspend fun seedIfEmpty(seedBundles: AppStateSeedBundles) {
        update { seedFrom(seedBundles) }
        // NSUserDefaults writes are automatically persisted; explicit synchronize is unnecessary.
    }

    private fun sanitizeManualSaveDirectoryValue(value: String?): String {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank()) return DEFAULT_MANUAL_SAVE_ROOT
        if (trimmed == com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY) return DEFAULT_MANUAL_SAVE_ROOT
        return trimmed
    }
}
