package com.valoser.futacha.shared.state

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import platform.Foundation.NSUserDefaults

private const val BOARDS_KEY = "boards_json"
private const val HISTORY_KEY = "history_json"
private const val CATALOG_DISPLAY_STYLE_KEY = "catalog_display_style"
private const val CATALOG_GRID_COLUMNS_KEY = "catalog_grid_columns"
private const val PRIVACY_FILTER_KEY = "privacy_filter_enabled"
private const val BACKGROUND_REFRESH_KEY = "background_refresh_enabled"
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
    private val boardsState = MutableStateFlow(defaults.stringForKey(BOARDS_KEY))
    private val historyState = MutableStateFlow(defaults.stringForKey(HISTORY_KEY))
    private val displayStyleState = MutableStateFlow(defaults.stringForKey(CATALOG_DISPLAY_STYLE_KEY))
    private val gridColumnsState = MutableStateFlow(defaults.stringForKey(CATALOG_GRID_COLUMNS_KEY))
    private val privacyFilterState = MutableStateFlow(defaults.boolForKey(PRIVACY_FILTER_KEY))
    private val backgroundRefreshState = MutableStateFlow(defaults.boolForKey(BACKGROUND_REFRESH_KEY))
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

    override suspend fun updateBoardsJson(value: String) {
        defaults.setObject(value, forKey = BOARDS_KEY)
        boardsState.value = value
    }

    override suspend fun updateHistoryJson(value: String) {
        defaults.setObject(value, forKey = HISTORY_KEY)
        historyState.value = value
    }

    override suspend fun updatePrivacyFilterEnabled(enabled: Boolean) {
        defaults.setBool(enabled, forKey = PRIVACY_FILTER_KEY)
        privacyFilterState.value = enabled
    }

    override suspend fun updateBackgroundRefreshEnabled(enabled: Boolean) {
        defaults.setBool(enabled, forKey = BACKGROUND_REFRESH_KEY)
        backgroundRefreshState.value = enabled
    }

    override suspend fun updateLightweightModeEnabled(enabled: Boolean) {
        defaults.setBool(enabled, forKey = LIGHTWEIGHT_MODE_KEY)
        lightweightModeState.value = enabled
    }

    override suspend fun updateManualSaveDirectory(directory: String) {
        defaults.setObject(directory, forKey = MANUAL_SAVE_DIRECTORY_KEY)
        manualSaveDirectoryState.value = directory
    }

    override suspend fun updateAttachmentPickerPreference(preference: String) {
        defaults.setObject(preference, forKey = ATTACHMENT_PICKER_PREF_KEY)
        attachmentPickerPreferenceState.value = preference
    }

    override suspend fun updateSaveDirectorySelection(selection: String) {
        defaults.setObject(selection, forKey = SAVE_DIRECTORY_SELECTION_KEY)
        saveDirectorySelectionState.value = selection
    }

    override suspend fun updateCatalogModeMapJson(value: String) {
        defaults.setObject(value, forKey = CATALOG_MODE_MAP_KEY)
        catalogModeMapState.value = value
    }

    override suspend fun updateCatalogDisplayStyle(style: String) {
        defaults.setObject(style, forKey = CATALOG_DISPLAY_STYLE_KEY)
        displayStyleState.value = style
    }

    override suspend fun updateCatalogGridColumns(columns: String) {
        defaults.setObject(columns, forKey = CATALOG_GRID_COLUMNS_KEY)
        gridColumnsState.value = columns
    }

    override suspend fun updateNgHeadersJson(value: String) {
        defaults.setObject(value, forKey = NG_HEADERS_KEY)
        ngHeadersState.value = value
    }

    override suspend fun updateNgWordsJson(value: String) {
        defaults.setObject(value, forKey = NG_WORDS_KEY)
        ngWordsState.value = value
    }

    override suspend fun updateCatalogNgWordsJson(value: String) {
        defaults.setObject(value, forKey = CATALOG_NG_WORDS_KEY)
        catalogNgWordsState.value = value
    }

    override suspend fun updateWatchWordsJson(value: String) {
        defaults.setObject(value, forKey = WATCH_WORDS_KEY)
        watchWordsState.value = value
    }

    override suspend fun updateSelfPostIdentifiersJson(value: String) {
        defaults.setObject(value, forKey = SELF_POST_IDENTIFIERS_KEY)
        selfPostIdentifiersState.value = value
    }

    override suspend fun updateThreadMenuConfigJson(value: String) {
        defaults.setObject(value, forKey = THREAD_MENU_CONFIG_KEY)
        threadMenuConfigState.value = value
    }

    override suspend fun updateThreadSettingsMenuConfigJson(value: String) {
        defaults.setObject(value, forKey = THREAD_SETTINGS_MENU_CONFIG_KEY)
        threadSettingsMenuConfigState.value = value
    }

    override suspend fun updateThreadMenuEntriesConfigJson(value: String) {
        defaults.setObject(value, forKey = THREAD_MENU_ENTRIES_KEY)
        threadMenuEntriesState.value = value
    }

    override suspend fun updateCatalogNavEntriesConfigJson(value: String) {
        defaults.setObject(value, forKey = CATALOG_NAV_ENTRIES_KEY)
        catalogNavEntriesState.value = value
    }

    override suspend fun updatePreferredFileManagerPackage(packageName: String) {
        defaults.setObject(packageName, forKey = PREFERRED_FILE_MANAGER_PACKAGE_KEY)
        preferredFileManagerPackageState.value = packageName
    }

    override suspend fun updatePreferredFileManagerLabel(label: String) {
        defaults.setObject(label, forKey = PREFERRED_FILE_MANAGER_LABEL_KEY)
        preferredFileManagerLabelState.value = label
    }

    override suspend fun updateLastUsedDeleteKey(value: String) {
        defaults.setObject(value, forKey = LAST_USED_DELETE_KEY)
        lastUsedDeleteKeyState.value = value
    }

    override suspend fun seedIfEmpty(
        defaultBoardsJson: String,
        defaultHistoryJson: String,
        defaultNgHeadersJson: String?,
        defaultNgWordsJson: String?,
        defaultCatalogNgWordsJson: String?,
        defaultWatchWordsJson: String?,
        defaultSelfPostIdentifiersJson: String?,
        defaultCatalogModeMapJson: String?,
        defaultAttachmentPickerPreference: String?,
        defaultSaveDirectorySelection: String?,
        defaultLastUsedDeleteKey: String?,
        defaultThreadMenuConfigJson: String?,
        defaultThreadSettingsMenuConfigJson: String?,
        defaultThreadMenuEntriesConfigJson: String?,
        defaultCatalogNavEntriesJson: String?
    ) {
        var updated = false
        if (defaults.stringForKey(BOARDS_KEY) == null) {
            defaults.setObject(defaultBoardsJson, forKey = BOARDS_KEY)
            boardsState.value = defaultBoardsJson
            updated = true
        }
        if (defaults.stringForKey(HISTORY_KEY) == null) {
            defaults.setObject(defaultHistoryJson, forKey = HISTORY_KEY)
            historyState.value = defaultHistoryJson
            updated = true
        }
        if (defaults.stringForKey(MANUAL_SAVE_DIRECTORY_KEY) == null) {
            defaults.setObject(DEFAULT_MANUAL_SAVE_ROOT, forKey = MANUAL_SAVE_DIRECTORY_KEY)
            manualSaveDirectoryState.value = DEFAULT_MANUAL_SAVE_ROOT
            updated = true
        }
        if (defaultLastUsedDeleteKey != null && defaults.stringForKey(LAST_USED_DELETE_KEY) == null) {
            defaults.setObject(defaultLastUsedDeleteKey, forKey = LAST_USED_DELETE_KEY)
            lastUsedDeleteKeyState.value = defaultLastUsedDeleteKey
            updated = true
        }
        if (defaultNgHeadersJson != null && defaults.stringForKey(NG_HEADERS_KEY) == null) {
            defaults.setObject(defaultNgHeadersJson, forKey = NG_HEADERS_KEY)
            ngHeadersState.value = defaultNgHeadersJson
            updated = true
        }
        if (defaultNgWordsJson != null && defaults.stringForKey(NG_WORDS_KEY) == null) {
            defaults.setObject(defaultNgWordsJson, forKey = NG_WORDS_KEY)
            ngWordsState.value = defaultNgWordsJson
            updated = true
        }
        if (defaultCatalogNgWordsJson != null && defaults.stringForKey(CATALOG_NG_WORDS_KEY) == null) {
            defaults.setObject(defaultCatalogNgWordsJson, forKey = CATALOG_NG_WORDS_KEY)
            catalogNgWordsState.value = defaultCatalogNgWordsJson
            updated = true
        }
        if (defaultWatchWordsJson != null && defaults.stringForKey(WATCH_WORDS_KEY) == null) {
            defaults.setObject(defaultWatchWordsJson, forKey = WATCH_WORDS_KEY)
            watchWordsState.value = defaultWatchWordsJson
            updated = true
        }
        if (defaultSelfPostIdentifiersJson != null && defaults.stringForKey(SELF_POST_IDENTIFIERS_KEY) == null) {
            defaults.setObject(defaultSelfPostIdentifiersJson, forKey = SELF_POST_IDENTIFIERS_KEY)
            selfPostIdentifiersState.value = defaultSelfPostIdentifiersJson
            updated = true
        }
        if (defaultAttachmentPickerPreference != null && defaults.stringForKey(ATTACHMENT_PICKER_PREF_KEY) == null) {
            defaults.setObject(defaultAttachmentPickerPreference, forKey = ATTACHMENT_PICKER_PREF_KEY)
            attachmentPickerPreferenceState.value = defaultAttachmentPickerPreference
            updated = true
        }
        if (defaultSaveDirectorySelection != null && defaults.stringForKey(SAVE_DIRECTORY_SELECTION_KEY) == null) {
            defaults.setObject(defaultSaveDirectorySelection, forKey = SAVE_DIRECTORY_SELECTION_KEY)
            saveDirectorySelectionState.value = defaultSaveDirectorySelection
            updated = true
        }
        if (defaultThreadMenuConfigJson != null && defaults.stringForKey(THREAD_MENU_CONFIG_KEY) == null) {
            defaults.setObject(defaultThreadMenuConfigJson, forKey = THREAD_MENU_CONFIG_KEY)
            threadMenuConfigState.value = defaultThreadMenuConfigJson
            updated = true
        }
        if (defaultThreadSettingsMenuConfigJson != null && defaults.stringForKey(THREAD_SETTINGS_MENU_CONFIG_KEY) == null) {
            defaults.setObject(defaultThreadSettingsMenuConfigJson, forKey = THREAD_SETTINGS_MENU_CONFIG_KEY)
            threadSettingsMenuConfigState.value = defaultThreadSettingsMenuConfigJson
            updated = true
        }
        if (defaultThreadMenuEntriesConfigJson != null && defaults.stringForKey(THREAD_MENU_ENTRIES_KEY) == null) {
            defaults.setObject(defaultThreadMenuEntriesConfigJson, forKey = THREAD_MENU_ENTRIES_KEY)
            threadMenuEntriesState.value = defaultThreadMenuEntriesConfigJson
            updated = true
        }
        if (defaultCatalogNavEntriesJson != null && defaults.stringForKey(CATALOG_NAV_ENTRIES_KEY) == null) {
            defaults.setObject(defaultCatalogNavEntriesJson, forKey = CATALOG_NAV_ENTRIES_KEY)
            catalogNavEntriesState.value = defaultCatalogNavEntriesJson
            updated = true
        }
        if (defaultCatalogModeMapJson != null && defaults.stringForKey(CATALOG_MODE_MAP_KEY) == null) {
            defaults.setObject(defaultCatalogModeMapJson, forKey = CATALOG_MODE_MAP_KEY)
            catalogModeMapState.value = defaultCatalogModeMapJson
            updated = true
        }
        // NSUserDefaults writes are automatically persisted; explicit synchronize is unnecessary.
    }

    private fun sanitizeManualSaveDirectoryValue(value: String?): String {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank()) return DEFAULT_MANUAL_SAVE_ROOT
        if (trimmed == com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY) return DEFAULT_MANUAL_SAVE_ROOT
        return trimmed
    }
}
