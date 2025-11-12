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
    private val displayStyleKey = stringPreferencesKey("catalog_display_style")

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

    override val catalogDisplayStyle: Flow<String?> =
        context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> prefs[displayStyleKey] }

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

    override suspend fun seedIfEmpty(defaultBoardsJson: String, defaultHistoryJson: String) {
        try {
            context.dataStore.edit { prefs ->
                if (!prefs.contains(boardsKey)) {
                    prefs[boardsKey] = defaultBoardsJson
                }
                if (!prefs.contains(historyKey)) {
                    prefs[historyKey] = defaultHistoryJson
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
