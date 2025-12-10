package com.valoser.futacha.shared.state

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private const val DATASTORE_NAME = "futacha_state"
private val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)

internal actual fun createPlatformStateStorage(platformContext: Any?): PlatformStateStorage {
    val context = (platformContext as? Context)?.applicationContext
        ?: throw IllegalArgumentException("Android Context is required to create AppStateStore")
    return AndroidPlatformStateStorage(context)
}

private class AndroidPlatformStateStorage(
    private val context: Context
) : PlatformStateStorage {
    private val boardsKey = stringPreferencesKey("boards_json")
    private val historyKey = stringPreferencesKey("history_json")
    private val privacyFilterKey = booleanPreferencesKey("privacy_filter_enabled")
    private val backgroundRefreshKey = booleanPreferencesKey("background_refresh_enabled")
    private val lightweightModeKey = booleanPreferencesKey("lightweight_mode_enabled")
    private val displayStyleKey = stringPreferencesKey("catalog_display_style")
    private val gridColumnsKey = stringPreferencesKey("catalog_grid_columns")
    private val manualSaveDirectoryKey = stringPreferencesKey("manual_save_directory")
    private val attachmentPickerPreferenceKey = stringPreferencesKey("attachment_picker_preference")
    private val saveDirectorySelectionKey = stringPreferencesKey("save_directory_selection")
    private val catalogModeMapKey = stringPreferencesKey("catalog_mode_map_json")
    private val ngHeadersKey = stringPreferencesKey("ng_headers_json")
    private val ngWordsKey = stringPreferencesKey("ng_words_json")
    private val catalogNgWordsKey = stringPreferencesKey("catalog_ng_words_json")
    private val watchWordsKey = stringPreferencesKey("watch_words_json")
    private val selfPostIdentifiersKey = stringPreferencesKey("self_post_identifiers_json")
    private val threadMenuConfigKey = stringPreferencesKey("thread_menu_config_json")
    private val threadMenuEntriesKey = stringPreferencesKey("thread_menu_entries_json")
    private val catalogNavEntriesKey = stringPreferencesKey("catalog_nav_entries_json")
    private val preferredFileManagerPackageKey = stringPreferencesKey("preferred_file_manager_package")
    private val preferredFileManagerLabelKey = stringPreferencesKey("preferred_file_manager_label")
    private val threadSettingsMenuConfigKey = stringPreferencesKey("thread_settings_menu_config_json")

    override val boardsJson: Flow<String?> =
        context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[boardsKey] }

    override val historyJson: Flow<String?> =
        context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[historyKey] }

    override val privacyFilterEnabled: Flow<Boolean> =
        context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[privacyFilterKey] ?: false }

    override val backgroundRefreshEnabled: Flow<Boolean> =
        context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[backgroundRefreshKey] ?: false }

    override val lightweightModeEnabled: Flow<Boolean> =
        context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[lightweightModeKey] ?: false }

    override val manualSaveDirectory: Flow<String> =
        context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> sanitizeManualSaveDirectoryValue(prefs[manualSaveDirectoryKey]) }

    override val attachmentPickerPreference: Flow<String?> =
        context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[attachmentPickerPreferenceKey] }

    override val saveDirectorySelection: Flow<String?> =
        context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[saveDirectorySelectionKey] }

    override val catalogModeMapJson: Flow<String?> =
        context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[catalogModeMapKey] }

    override val catalogDisplayStyle: Flow<String?> =
        context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[displayStyleKey] }
    override val catalogGridColumns: Flow<String?> =
        context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[gridColumnsKey] }

    override val ngHeadersJson: Flow<String?> =
        context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[ngHeadersKey] }

    override val ngWordsJson: Flow<String?> =
        context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[ngWordsKey] }

    override val catalogNgWordsJson: Flow<String?> =
        context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[catalogNgWordsKey] }

    override val watchWordsJson: Flow<String?> =
        context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[watchWordsKey] }

    override val selfPostIdentifiersJson: Flow<String?> =
        context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[selfPostIdentifiersKey] }

    override val threadMenuConfigJson: Flow<String?> =
        context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[threadMenuConfigKey] }

    override val threadMenuEntriesConfigJson: Flow<String?> =
        context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[threadMenuEntriesKey] }

    override val catalogNavEntriesConfigJson: Flow<String?> =
        context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[catalogNavEntriesKey] }

    override val threadSettingsMenuConfigJson: Flow<String?> =
        context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[threadSettingsMenuConfigKey] }

    override val preferredFileManagerPackage: Flow<String> =
        context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[preferredFileManagerPackageKey] ?: "" }

    override val preferredFileManagerLabel: Flow<String> =
        context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[preferredFileManagerLabelKey] ?: "" }

    override suspend fun updateBoardsJson(value: String) {
        try {
            context.dataStore.edit { prefs -> prefs[boardsKey] = value }
        } catch (e: Exception) {
            println("AndroidPlatformStateStorage: Failed to update boards: ${e.message}")
            // Re-throw as a more specific exception for caller to handle
            throw StorageException("Failed to save boards data", e)
        }
    }

    override suspend fun updateHistoryJson(value: String) {
        try {
            context.dataStore.edit { prefs -> prefs[historyKey] = value }
        } catch (e: Exception) {
            println("AndroidPlatformStateStorage: Failed to update history: ${e.message}")
            // Re-throw as a more specific exception for caller to handle
            throw StorageException("Failed to save history data", e)
        }
    }

    override suspend fun updateBackgroundRefreshEnabled(enabled: Boolean) {
        try {
            context.dataStore.edit { prefs -> prefs[backgroundRefreshKey] = enabled }
        } catch (e: Exception) {
            println("AndroidPlatformStateStorage: Failed to update background refresh: ${e.message}")
            throw StorageException("Failed to save background refresh state", e)
        }
    }

    override suspend fun updateLightweightModeEnabled(enabled: Boolean) {
        try {
            context.dataStore.edit { prefs -> prefs[lightweightModeKey] = enabled }
        } catch (e: Exception) {
            println("AndroidPlatformStateStorage: Failed to update lightweight mode: ${e.message}")
            throw StorageException("Failed to save lightweight mode state", e)
        }
    }

    override suspend fun updateManualSaveDirectory(directory: String) {
        try {
            context.dataStore.edit { prefs -> prefs[manualSaveDirectoryKey] = directory }
        } catch (e: Exception) {
            println("AndroidPlatformStateStorage: Failed to update manual save directory: ${e.message}")
            throw StorageException("Failed to save manual save directory", e)
        }
    }

    override suspend fun updateAttachmentPickerPreference(preference: String) {
        try {
            context.dataStore.edit { prefs -> prefs[attachmentPickerPreferenceKey] = preference }
        } catch (e: Exception) {
            println("AndroidPlatformStateStorage: Failed to update attachment picker preference: ${e.message}")
            throw StorageException("Failed to save attachment picker preference", e)
        }
    }

    override suspend fun updateSaveDirectorySelection(selection: String) {
        try {
            context.dataStore.edit { prefs -> prefs[saveDirectorySelectionKey] = selection }
        } catch (e: Exception) {
            println("AndroidPlatformStateStorage: Failed to update save directory selection: ${e.message}")
            throw StorageException("Failed to save save directory selection", e)
        }
    }

    override suspend fun updateCatalogModeMapJson(value: String) {
        try {
            context.dataStore.edit { prefs -> prefs[catalogModeMapKey] = value }
        } catch (e: Exception) {
            println("AndroidPlatformStateStorage: Failed to update catalog mode map: ${e.message}")
            throw StorageException("Failed to save catalog mode map", e)
        }
    }

    override suspend fun updatePrivacyFilterEnabled(enabled: Boolean) {
        try {
            context.dataStore.edit { prefs -> prefs[privacyFilterKey] = enabled }
        } catch (e: Exception) {
            println("AndroidPlatformStateStorage: Failed to update privacy filter: ${e.message}")
            // Re-throw as a more specific exception for caller to handle
            throw StorageException("Failed to save privacy filter state", e)
        }
    }

    override suspend fun updateCatalogDisplayStyle(style: String) {
        try {
            context.dataStore.edit { prefs -> prefs[displayStyleKey] = style }
        } catch (e: Exception) {
            println("AndroidPlatformStateStorage: Failed to update catalog display style: ${e.message}")
            throw StorageException("Failed to save catalog display style", e)
        }
    }

    override suspend fun updateCatalogGridColumns(columns: String) {
        try {
            context.dataStore.edit { prefs -> prefs[gridColumnsKey] = columns }
        } catch (e: Exception) {
            println("AndroidPlatformStateStorage: Failed to update catalog grid columns: ${e.message}")
            throw StorageException("Failed to save catalog grid columns", e)
        }
    }

    override suspend fun updateNgHeadersJson(value: String) {
        try {
            context.dataStore.edit { prefs -> prefs[ngHeadersKey] = value }
        } catch (e: Exception) {
            println("AndroidPlatformStateStorage: Failed to update NG headers: ${e.message}")
            throw StorageException("Failed to save NG headers", e)
        }
    }

    override suspend fun updateNgWordsJson(value: String) {
        try {
            context.dataStore.edit { prefs -> prefs[ngWordsKey] = value }
        } catch (e: Exception) {
            println("AndroidPlatformStateStorage: Failed to update NG words: ${e.message}")
            throw StorageException("Failed to save NG words", e)
        }
    }

    override suspend fun updateCatalogNgWordsJson(value: String) {
        try {
            context.dataStore.edit { prefs -> prefs[catalogNgWordsKey] = value }
        } catch (e: Exception) {
            println("AndroidPlatformStateStorage: Failed to update catalog NG words: ${e.message}")
            throw StorageException("Failed to save catalog NG words", e)
        }
    }

    override suspend fun updateWatchWordsJson(value: String) {
        try {
            context.dataStore.edit { prefs -> prefs[watchWordsKey] = value }
        } catch (e: Exception) {
            println("AndroidPlatformStateStorage: Failed to update watch words: ${e.message}")
            throw StorageException("Failed to save watch words", e)
        }
    }

    override suspend fun updateSelfPostIdentifiersJson(value: String) {
        try {
            context.dataStore.edit { prefs -> prefs[selfPostIdentifiersKey] = value }
        } catch (e: Exception) {
            println("AndroidPlatformStateStorage: Failed to update self post identifiers: ${e.message}")
            throw StorageException("Failed to save self post identifiers", e)
        }
    }

    override suspend fun updateThreadMenuConfigJson(value: String) {
        try {
            context.dataStore.edit { prefs -> prefs[threadMenuConfigKey] = value }
        } catch (e: Exception) {
            println("AndroidPlatformStateStorage: Failed to update thread menu config: ${e.message}")
            throw StorageException("Failed to save thread menu config", e)
        }
    }

    override suspend fun updateThreadMenuEntriesConfigJson(value: String) {
        try {
            context.dataStore.edit { prefs -> prefs[threadMenuEntriesKey] = value }
        } catch (e: Exception) {
            println("AndroidPlatformStateStorage: Failed to update thread menu entries: ${e.message}")
            throw StorageException("Failed to save thread menu entries", e)
        }
    }

    override suspend fun updateCatalogNavEntriesConfigJson(value: String) {
        try {
            context.dataStore.edit { prefs -> prefs[catalogNavEntriesKey] = value }
        } catch (e: Exception) {
            println("AndroidPlatformStateStorage: Failed to update catalog nav entries: ${e.message}")
            throw StorageException("Failed to save catalog nav entries", e)
        }
    }

    override suspend fun updateThreadSettingsMenuConfigJson(value: String) {
        try {
            context.dataStore.edit { prefs -> prefs[threadSettingsMenuConfigKey] = value }
        } catch (e: Exception) {
            println("AndroidPlatformStateStorage: Failed to update thread settings menu config: ${e.message}")
            throw StorageException("Failed to save thread settings menu config", e)
        }
    }

    override suspend fun updatePreferredFileManagerPackage(packageName: String) {
        try {
            context.dataStore.edit { prefs -> prefs[preferredFileManagerPackageKey] = packageName }
        } catch (e: Exception) {
            println("AndroidPlatformStateStorage: Failed to update preferred file manager package: ${e.message}")
            throw StorageException("Failed to save preferred file manager package", e)
        }
    }

    override suspend fun updatePreferredFileManagerLabel(label: String) {
        try {
            context.dataStore.edit { prefs -> prefs[preferredFileManagerLabelKey] = label }
        } catch (e: Exception) {
            println("AndroidPlatformStateStorage: Failed to update preferred file manager label: ${e.message}")
            throw StorageException("Failed to save preferred file manager label", e)
        }
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
        defaultThreadMenuConfigJson: String?,
        defaultThreadSettingsMenuConfigJson: String?,
        defaultThreadMenuEntriesConfigJson: String?,
        defaultCatalogNavEntriesJson: String?
    ) {
        try {
            context.dataStore.edit { prefs ->
                if (!prefs.contains(boardsKey)) {
                    prefs[boardsKey] = defaultBoardsJson
                }
                if (!prefs.contains(historyKey)) {
                    prefs[historyKey] = defaultHistoryJson
                }
                if (defaultNgHeadersJson != null && !prefs.contains(ngHeadersKey)) {
                    prefs[ngHeadersKey] = defaultNgHeadersJson
                }
                if (defaultNgWordsJson != null && !prefs.contains(ngWordsKey)) {
                    prefs[ngWordsKey] = defaultNgWordsJson
                }
                if (defaultCatalogNgWordsJson != null && !prefs.contains(catalogNgWordsKey)) {
                    prefs[catalogNgWordsKey] = defaultCatalogNgWordsJson
                }
                if (defaultWatchWordsJson != null && !prefs.contains(watchWordsKey)) {
                    prefs[watchWordsKey] = defaultWatchWordsJson
                }
                if (defaultSelfPostIdentifiersJson != null && !prefs.contains(selfPostIdentifiersKey)) {
                    prefs[selfPostIdentifiersKey] = defaultSelfPostIdentifiersJson
                }
                if (!prefs.contains(manualSaveDirectoryKey)) {
                    prefs[manualSaveDirectoryKey] = DEFAULT_MANUAL_SAVE_ROOT
                }
                if (defaultCatalogModeMapJson != null && !prefs.contains(catalogModeMapKey)) {
                    prefs[catalogModeMapKey] = defaultCatalogModeMapJson
                }
                if (defaultAttachmentPickerPreference != null && !prefs.contains(attachmentPickerPreferenceKey)) {
                    prefs[attachmentPickerPreferenceKey] = defaultAttachmentPickerPreference
                }
                if (defaultSaveDirectorySelection != null && !prefs.contains(saveDirectorySelectionKey)) {
                    prefs[saveDirectorySelectionKey] = defaultSaveDirectorySelection
                }
                if (defaultThreadMenuConfigJson != null && !prefs.contains(threadMenuConfigKey)) {
                    prefs[threadMenuConfigKey] = defaultThreadMenuConfigJson
                }
                if (defaultThreadSettingsMenuConfigJson != null && !prefs.contains(threadSettingsMenuConfigKey)) {
                    prefs[threadSettingsMenuConfigKey] = defaultThreadSettingsMenuConfigJson
                }
                if (defaultThreadMenuEntriesConfigJson != null && !prefs.contains(threadMenuEntriesKey)) {
                    prefs[threadMenuEntriesKey] = defaultThreadMenuEntriesConfigJson
                }
                if (defaultCatalogNavEntriesJson != null && !prefs.contains(catalogNavEntriesKey)) {
                    prefs[catalogNavEntriesKey] = defaultCatalogNavEntriesJson
                }
            }
        } catch (e: Exception) {
            println("AndroidPlatformStateStorage: Failed to seed data: ${e.message}")
            // Re-throw as a more specific exception for caller to handle
            throw StorageException("Failed to initialize default data", e)
        }
    }

    private fun sanitizeManualSaveDirectoryValue(value: String?): String {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank()) return DEFAULT_MANUAL_SAVE_ROOT
        if (trimmed == com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY) return DEFAULT_MANUAL_SAVE_ROOT
        return trimmed
    }
}

/**
 * Exception thrown when storage operations fail
 */
class StorageException(message: String, cause: Throwable? = null) : Exception(message, cause)
