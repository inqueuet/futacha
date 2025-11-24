package com.valoser.futacha.shared.state

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
    private val displayStyleKey = stringPreferencesKey("catalog_display_style")
    private val ngHeadersKey = stringPreferencesKey("ng_headers_json")
    private val ngWordsKey = stringPreferencesKey("ng_words_json")
    private val catalogNgWordsKey = stringPreferencesKey("catalog_ng_words_json")
    private val watchWordsKey = stringPreferencesKey("watch_words_json")
    private val selfPostIdentifiersKey = stringPreferencesKey("self_post_identifiers_json")

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

    override val catalogDisplayStyle: Flow<String?> =
        context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[displayStyleKey] }

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

    override suspend fun seedIfEmpty(
        defaultBoardsJson: String,
        defaultHistoryJson: String,
        defaultNgHeadersJson: String?,
        defaultNgWordsJson: String?,
        defaultCatalogNgWordsJson: String?,
        defaultWatchWordsJson: String?,
        defaultSelfPostIdentifiersJson: String?
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
            }
        } catch (e: Exception) {
            println("AndroidPlatformStateStorage: Failed to seed data: ${e.message}")
            // Re-throw as a more specific exception for caller to handle
            throw StorageException("Failed to initialize default data", e)
        }
    }
}

/**
 * Exception thrown when storage operations fail
 */
class StorageException(message: String, cause: Throwable? = null) : Exception(message, cause)
