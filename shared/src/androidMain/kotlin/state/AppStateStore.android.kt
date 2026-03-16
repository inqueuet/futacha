package com.valoser.futacha.shared.state

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

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
    private val adsEnabledKey = booleanPreferencesKey("ads_enabled")
    private val postingNoticeKey = booleanPreferencesKey("has_shown_posting_notice")
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
    private val lastUsedDeleteKeyPreferencesKey = stringPreferencesKey("last_used_delete_key")
    private val lastReadablePreferences = AtomicReference<Preferences?>(null)
    private val safeData: Flow<Preferences> =
        context.dataStore.data
            .onEach { prefs ->
                lastReadablePreferences.set(prefs)
            }
            .retryWhen { cause, attempt ->
                when (cause) {
                    is CancellationException -> throw cause
                    is IOException -> {
                        val cached = lastReadablePreferences.get()
                        if (cached != null) {
                            Logger.w(
                                "AndroidPlatformStateStorage",
                                "DataStore read failure detected; using last known preferences snapshot and retrying: ${cause.message}"
                            )
                            emit(cached)
                            val backoffMillis = (250L shl attempt.toInt().coerceAtMost(4)).coerceAtMost(4_000L)
                            delay(backoffMillis)
                            true
                        } else {
                            Logger.e(
                                "AndroidPlatformStateStorage",
                                "DataStore read failure with no cached snapshot",
                                cause
                            )
                            false
                        }
                    }
                    else -> false
                }
            }
            .catch { e ->
                when (e) {
                    is CancellationException -> throw e
                    else -> throw StorageException("Failed to read DataStore preferences", e)
                }
            }

    override val boardsJson: Flow<String?> =
        safeData.map { prefs -> prefs[boardsKey] }

    override val historyJson: Flow<String?> =
        safeData.map { prefs -> prefs[historyKey] }

    override val privacyFilterEnabled: Flow<Boolean> =
        safeData.map { prefs -> prefs[privacyFilterKey] ?: false }

    override val backgroundRefreshEnabled: Flow<Boolean> =
        safeData.map { prefs -> prefs[backgroundRefreshKey] ?: false }

    override val adsEnabled: Flow<Boolean> =
        safeData.map { prefs -> prefs[adsEnabledKey] ?: false }
    override val hasShownPostingNotice: Flow<Boolean> =
        safeData.map { prefs -> prefs[postingNoticeKey] ?: false }

    override val lightweightModeEnabled: Flow<Boolean> =
        safeData.map { prefs -> prefs[lightweightModeKey] ?: false }

    override val manualSaveDirectory: Flow<String> =
        safeData.map { prefs -> sanitizeManualSaveDirectoryValue(prefs[manualSaveDirectoryKey]) }

    override val attachmentPickerPreference: Flow<String?> =
        safeData.map { prefs -> prefs[attachmentPickerPreferenceKey] }

    override val saveDirectorySelection: Flow<String?> =
        safeData.map { prefs -> prefs[saveDirectorySelectionKey] }

    override val catalogModeMapJson: Flow<String?> =
        safeData.map { prefs -> prefs[catalogModeMapKey] }

    override val catalogDisplayStyle: Flow<String?> =
        safeData.map { prefs -> prefs[displayStyleKey] }
    override val catalogGridColumns: Flow<String?> =
        safeData.map { prefs -> prefs[gridColumnsKey] }

    override val ngHeadersJson: Flow<String?> =
        safeData.map { prefs -> prefs[ngHeadersKey] }

    override val ngWordsJson: Flow<String?> =
        safeData.map { prefs -> prefs[ngWordsKey] }

    override val catalogNgWordsJson: Flow<String?> =
        safeData.map { prefs -> prefs[catalogNgWordsKey] }

    override val watchWordsJson: Flow<String?> =
        safeData.map { prefs -> prefs[watchWordsKey] }

    override val selfPostIdentifiersJson: Flow<String?> =
        safeData.map { prefs -> prefs[selfPostIdentifiersKey] }

    override val threadMenuConfigJson: Flow<String?> =
        safeData.map { prefs -> prefs[threadMenuConfigKey] }

    override val threadMenuEntriesConfigJson: Flow<String?> =
        safeData.map { prefs -> prefs[threadMenuEntriesKey] }

    override val catalogNavEntriesConfigJson: Flow<String?> =
        safeData.map { prefs -> prefs[catalogNavEntriesKey] }

    override val threadSettingsMenuConfigJson: Flow<String?> =
        safeData.map { prefs -> prefs[threadSettingsMenuConfigKey] }

    override val preferredFileManagerPackage: Flow<String> =
        safeData.map { prefs -> prefs[preferredFileManagerPackageKey] ?: "" }

    override val preferredFileManagerLabel: Flow<String> =
        safeData.map { prefs -> prefs[preferredFileManagerLabelKey] ?: "" }

    override val lastUsedDeleteKey: Flow<String?> =
        safeData.map { prefs -> prefs[lastUsedDeleteKeyPreferencesKey] }

    private suspend fun updateStringPreference(
        key: Preferences.Key<String>,
        value: String,
        logLabel: String,
        failureMessage: String
    ) {
        try {
            context.dataStore.edit { prefs -> prefs[key] = value }
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            Logger.e("AndroidPlatformStateStorage", "Failed to update $logLabel: ${e.message}")
            throw StorageException(failureMessage, e)
        }
    }

    private suspend fun updateBooleanPreference(
        key: Preferences.Key<Boolean>,
        value: Boolean,
        logLabel: String,
        failureMessage: String
    ) {
        try {
            context.dataStore.edit { prefs -> prefs[key] = value }
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            Logger.e("AndroidPlatformStateStorage", "Failed to update $logLabel: ${e.message}")
            throw StorageException(failureMessage, e)
        }
    }

    private suspend fun updateStringPreferencePair(
        firstKey: Preferences.Key<String>,
        firstValue: String,
        secondKey: Preferences.Key<String>,
        secondValue: String,
        logLabel: String,
        failureMessage: String
    ) {
        try {
            context.dataStore.edit { prefs ->
                prefs[firstKey] = firstValue
                prefs[secondKey] = secondValue
            }
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            Logger.e("AndroidPlatformStateStorage", "Failed to update $logLabel: ${e.message}")
            throw StorageException(failureMessage, e)
        }
    }

    private fun MutablePreferences.seedRequiredStringPreference(
        key: Preferences.Key<String>,
        value: String
    ) {
        if (!contains(key)) {
            this[key] = value
        }
    }

    private fun MutablePreferences.seedRequiredBooleanPreference(
        key: Preferences.Key<Boolean>,
        value: Boolean
    ) {
        if (!contains(key)) {
            this[key] = value
        }
    }

    private fun MutablePreferences.seedOptionalStringPreference(
        key: Preferences.Key<String>,
        value: String?
    ) {
        if (value != null && !contains(key)) {
            this[key] = value
        }
    }

    private fun MutablePreferences.seedFrom(seedBundles: AppStateSeedBundles) {
        seedRequiredStringPreference(boardsKey, seedBundles.boards.boardsJson)
        seedRequiredStringPreference(historyKey, seedBundles.history.historyJson)
        seedRequiredStringPreference(manualSaveDirectoryKey, DEFAULT_MANUAL_SAVE_ROOT)
        seedRequiredBooleanPreference(adsEnabledKey, false)
        seedRequiredBooleanPreference(postingNoticeKey, false)
        seedOptionalStringPreference(ngHeadersKey, seedBundles.preferences.ngHeadersJson)
        seedOptionalStringPreference(ngWordsKey, seedBundles.preferences.ngWordsJson)
        seedOptionalStringPreference(catalogNgWordsKey, seedBundles.preferences.catalogNgWordsJson)
        seedOptionalStringPreference(watchWordsKey, seedBundles.preferences.watchWordsJson)
        seedOptionalStringPreference(
            selfPostIdentifiersKey,
            seedBundles.preferences.selfPostIdentifiersJson
        )
        seedOptionalStringPreference(catalogModeMapKey, seedBundles.preferences.catalogModeMapJson)
        seedOptionalStringPreference(
            attachmentPickerPreferenceKey,
            seedBundles.preferences.attachmentPickerPreference
        )
        seedOptionalStringPreference(
            saveDirectorySelectionKey,
            seedBundles.preferences.saveDirectorySelection
        )
        seedOptionalStringPreference(
            lastUsedDeleteKeyPreferencesKey,
            seedBundles.preferences.lastUsedDeleteKey
        )
        seedOptionalStringPreference(
            threadMenuConfigKey,
            seedBundles.preferences.threadMenuConfigJson
        )
        seedOptionalStringPreference(
            threadSettingsMenuConfigKey,
            seedBundles.preferences.threadSettingsMenuConfigJson
        )
        seedOptionalStringPreference(
            threadMenuEntriesKey,
            seedBundles.preferences.threadMenuEntriesConfigJson
        )
        seedOptionalStringPreference(
            catalogNavEntriesKey,
            seedBundles.preferences.catalogNavEntriesJson
        )
    }

    override suspend fun updateBoardsJson(value: String) {
        updateStringPreference(boardsKey, value, "boards", "Failed to save boards data")
    }

    override suspend fun updateHistoryJson(value: String) {
        updateStringPreference(historyKey, value, "history", "Failed to save history data")
    }

    override suspend fun updateBackgroundRefreshEnabled(enabled: Boolean) {
        updateBooleanPreference(
            backgroundRefreshKey,
            enabled,
            "background refresh",
            "Failed to save background refresh state"
        )
    }

    override suspend fun updateAdsEnabled(enabled: Boolean) {
        updateBooleanPreference(
            adsEnabledKey,
            enabled,
            "ads enabled",
            "Failed to save ads enabled state"
        )
    }

    override suspend fun updateHasShownPostingNotice(shown: Boolean) {
        updateBooleanPreference(
            postingNoticeKey,
            shown,
            "posting notice",
            "Failed to save posting notice state"
        )
    }

    override suspend fun updateLightweightModeEnabled(enabled: Boolean) {
        updateBooleanPreference(
            lightweightModeKey,
            enabled,
            "lightweight mode",
            "Failed to save lightweight mode state"
        )
    }

    override suspend fun updateManualSaveDirectory(directory: String) {
        updateStringPreference(
            manualSaveDirectoryKey,
            directory,
            "manual save directory",
            "Failed to save manual save directory"
        )
    }

    override suspend fun updateAttachmentPickerPreference(preference: String) {
        updateStringPreference(
            attachmentPickerPreferenceKey,
            preference,
            "attachment picker preference",
            "Failed to save attachment picker preference"
        )
    }

    override suspend fun updateSaveDirectorySelection(selection: String) {
        updateStringPreference(
            saveDirectorySelectionKey,
            selection,
            "save directory selection",
            "Failed to save save directory selection"
        )
    }

    override suspend fun updateCatalogModeMapJson(value: String) {
        updateStringPreference(
            catalogModeMapKey,
            value,
            "catalog mode map",
            "Failed to save catalog mode map"
        )
    }

    override suspend fun updatePrivacyFilterEnabled(enabled: Boolean) {
        updateBooleanPreference(
            privacyFilterKey,
            enabled,
            "privacy filter",
            "Failed to save privacy filter state"
        )
    }

    override suspend fun updateCatalogDisplayStyle(style: String) {
        updateStringPreference(
            displayStyleKey,
            style,
            "catalog display style",
            "Failed to save catalog display style"
        )
    }

    override suspend fun updateCatalogGridColumns(columns: String) {
        updateStringPreference(
            gridColumnsKey,
            columns,
            "catalog grid columns",
            "Failed to save catalog grid columns"
        )
    }

    override suspend fun updateNgHeadersJson(value: String) {
        updateStringPreference(ngHeadersKey, value, "NG headers", "Failed to save NG headers")
    }

    override suspend fun updateNgWordsJson(value: String) {
        updateStringPreference(ngWordsKey, value, "NG words", "Failed to save NG words")
    }

    override suspend fun updateCatalogNgWordsJson(value: String) {
        updateStringPreference(
            catalogNgWordsKey,
            value,
            "catalog NG words",
            "Failed to save catalog NG words"
        )
    }

    override suspend fun updateWatchWordsJson(value: String) {
        updateStringPreference(watchWordsKey, value, "watch words", "Failed to save watch words")
    }

    override suspend fun updateSelfPostIdentifiersJson(value: String) {
        updateStringPreference(
            selfPostIdentifiersKey,
            value,
            "self post identifiers",
            "Failed to save self post identifiers"
        )
    }

    override suspend fun updateThreadMenuConfigJson(value: String) {
        updateStringPreference(
            threadMenuConfigKey,
            value,
            "thread menu config",
            "Failed to save thread menu config"
        )
    }

    override suspend fun updateThreadMenuEntriesConfigJson(value: String) {
        updateStringPreference(
            threadMenuEntriesKey,
            value,
            "thread menu entries",
            "Failed to save thread menu entries"
        )
    }

    override suspend fun updateCatalogNavEntriesConfigJson(value: String) {
        updateStringPreference(
            catalogNavEntriesKey,
            value,
            "catalog nav entries",
            "Failed to save catalog nav entries"
        )
    }

    override suspend fun updateThreadSettingsMenuConfigJson(value: String) {
        updateStringPreference(
            threadSettingsMenuConfigKey,
            value,
            "thread settings menu config",
            "Failed to save thread settings menu config"
        )
    }

    override suspend fun updatePreferredFileManagerPackage(packageName: String) {
        updateStringPreference(
            preferredFileManagerPackageKey,
            packageName,
            "preferred file manager package",
            "Failed to save preferred file manager package"
        )
    }

    override suspend fun updatePreferredFileManagerLabel(label: String) {
        updateStringPreference(
            preferredFileManagerLabelKey,
            label,
            "preferred file manager label",
            "Failed to save preferred file manager label"
        )
    }

    override suspend fun updatePreferredFileManager(packageName: String, label: String) {
        updateStringPreferencePair(
            preferredFileManagerPackageKey,
            packageName,
            preferredFileManagerLabelKey,
            label,
            "preferred file manager pair",
            "Failed to save preferred file manager pair"
        )
    }

    override suspend fun updateLastUsedDeleteKey(value: String) {
        updateStringPreference(
            lastUsedDeleteKeyPreferencesKey,
            value,
            "last used delete key",
            "Failed to save last used delete key"
        )
    }

    override suspend fun seedIfEmpty(seedBundles: AppStateSeedBundles) {
        try {
            context.dataStore.edit { prefs -> prefs.seedFrom(seedBundles) }
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            Logger.e("AndroidPlatformStateStorage", "Failed to seed data: ${e.message}")
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

    private fun rethrowIfCancellation(error: Throwable) {
        if (error is CancellationException) throw error
    }
}

/**
 * Exception thrown when storage operations fail
 */
class StorageException(message: String, cause: Throwable? = null) : Exception(message, cause)
