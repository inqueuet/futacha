package com.valoser.futacha.shared.state

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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

    override val boardsJson: Flow<String?> =
        context.dataStore.data.map { prefs -> prefs[boardsKey] }

    override val historyJson: Flow<String?> =
        context.dataStore.data.map { prefs -> prefs[historyKey] }

    override suspend fun updateBoardsJson(value: String) {
        context.dataStore.edit { prefs -> prefs[boardsKey] = value }
    }

    override suspend fun updateHistoryJson(value: String) {
        context.dataStore.edit { prefs -> prefs[historyKey] = value }
    }

    override suspend fun seedIfEmpty(defaultBoardsJson: String, defaultHistoryJson: String) {
        context.dataStore.edit { prefs ->
            if (!prefs.contains(boardsKey)) {
                prefs[boardsKey] = defaultBoardsJson
            }
            if (!prefs.contains(historyKey)) {
                prefs[historyKey] = defaultHistoryJson
            }
        }
    }
}
