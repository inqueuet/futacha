package com.valoser.futacha.shared.state

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import platform.Foundation.NSUserDefaults

private const val BOARDS_KEY = "boards_json"
private const val HISTORY_KEY = "history_json"

internal actual fun createPlatformStateStorage(platformContext: Any?): PlatformStateStorage {
    return IosPlatformStateStorage()
}

private class IosPlatformStateStorage : PlatformStateStorage {
    private val defaults = NSUserDefaults.standardUserDefaults()
    private val boardsState = MutableStateFlow(defaults.stringForKey(BOARDS_KEY))
    private val historyState = MutableStateFlow(defaults.stringForKey(HISTORY_KEY))

    override val boardsJson: Flow<String?> = boardsState
    override val historyJson: Flow<String?> = historyState

    override suspend fun updateBoardsJson(value: String) {
        defaults.setObject(value, forKey = BOARDS_KEY)
        defaults.synchronize()
        boardsState.value = value
    }

    override suspend fun updateHistoryJson(value: String) {
        defaults.setObject(value, forKey = HISTORY_KEY)
        defaults.synchronize()
        historyState.value = value
    }

    override suspend fun seedIfEmpty(defaultBoardsJson: String, defaultHistoryJson: String) {
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
        if (updated) {
            defaults.synchronize()
        }
    }
}
