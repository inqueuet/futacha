package com.valoser.futacha.shared.state

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import platform.Foundation.NSUserDefaults

private const val BOARDS_KEY = "boards_json"
private const val HISTORY_KEY = "history_json"
private const val CATALOG_DISPLAY_STYLE_KEY = "catalog_display_style"
private const val PRIVACY_FILTER_KEY = "privacy_filter_enabled"
private const val BACKGROUND_REFRESH_KEY = "background_refresh_enabled"
private const val MANUAL_SAVE_DIRECTORY_KEY = "manual_save_directory"
private const val NG_HEADERS_KEY = "ng_headers_json"
private const val NG_WORDS_KEY = "ng_words_json"
private const val CATALOG_NG_WORDS_KEY = "catalog_ng_words_json"
private const val WATCH_WORDS_KEY = "watch_words_json"
private const val SELF_POST_IDENTIFIERS_KEY = "self_post_identifiers_json"

internal actual fun createPlatformStateStorage(platformContext: Any?): PlatformStateStorage {
    return IosPlatformStateStorage()
}

private class IosPlatformStateStorage : PlatformStateStorage {
    private val defaults = NSUserDefaults.standardUserDefaults()
    private val boardsState = MutableStateFlow(defaults.stringForKey(BOARDS_KEY))
    private val historyState = MutableStateFlow(defaults.stringForKey(HISTORY_KEY))
    private val displayStyleState = MutableStateFlow(defaults.stringForKey(CATALOG_DISPLAY_STYLE_KEY))
    private val privacyFilterState = MutableStateFlow(defaults.boolForKey(PRIVACY_FILTER_KEY))
    private val backgroundRefreshState = MutableStateFlow(defaults.boolForKey(BACKGROUND_REFRESH_KEY))
    private val manualSaveDirectoryState = MutableStateFlow(
        sanitizeManualSaveDirectoryValue(defaults.stringForKey(MANUAL_SAVE_DIRECTORY_KEY))
    )
    private val ngHeadersState = MutableStateFlow(defaults.stringForKey(NG_HEADERS_KEY))
    private val ngWordsState = MutableStateFlow(defaults.stringForKey(NG_WORDS_KEY))
    private val catalogNgWordsState = MutableStateFlow(defaults.stringForKey(CATALOG_NG_WORDS_KEY))
    private val watchWordsState = MutableStateFlow(defaults.stringForKey(WATCH_WORDS_KEY))
    private val selfPostIdentifiersState = MutableStateFlow(defaults.stringForKey(SELF_POST_IDENTIFIERS_KEY))

    override val boardsJson: Flow<String?> = boardsState
    override val historyJson: Flow<String?> = historyState
    override val privacyFilterEnabled: Flow<Boolean> = privacyFilterState
    override val backgroundRefreshEnabled: Flow<Boolean> = backgroundRefreshState
    override val manualSaveDirectory: Flow<String> = manualSaveDirectoryState
    override val catalogDisplayStyle: Flow<String?> = displayStyleState
    override val ngHeadersJson: Flow<String?> = ngHeadersState
    override val ngWordsJson: Flow<String?> = ngWordsState
    override val catalogNgWordsJson: Flow<String?> = catalogNgWordsState
    override val watchWordsJson: Flow<String?> = watchWordsState
    override val selfPostIdentifiersJson: Flow<String?> = selfPostIdentifiersState

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

    override suspend fun updatePrivacyFilterEnabled(enabled: Boolean) {
        defaults.setBool(enabled, forKey = PRIVACY_FILTER_KEY)
        defaults.synchronize()
        privacyFilterState.value = enabled
    }

    override suspend fun updateBackgroundRefreshEnabled(enabled: Boolean) {
        defaults.setBool(enabled, forKey = BACKGROUND_REFRESH_KEY)
        defaults.synchronize()
        backgroundRefreshState.value = enabled
    }

    override suspend fun updateManualSaveDirectory(directory: String) {
        defaults.setObject(directory, forKey = MANUAL_SAVE_DIRECTORY_KEY)
        defaults.synchronize()
        manualSaveDirectoryState.value = directory
    }

    override suspend fun updateCatalogDisplayStyle(style: String) {
        defaults.setObject(style, forKey = CATALOG_DISPLAY_STYLE_KEY)
        defaults.synchronize()
        displayStyleState.value = style
    }

    override suspend fun updateNgHeadersJson(value: String) {
        defaults.setObject(value, forKey = NG_HEADERS_KEY)
        defaults.synchronize()
        ngHeadersState.value = value
    }

    override suspend fun updateNgWordsJson(value: String) {
        defaults.setObject(value, forKey = NG_WORDS_KEY)
        defaults.synchronize()
        ngWordsState.value = value
    }

    override suspend fun updateCatalogNgWordsJson(value: String) {
        defaults.setObject(value, forKey = CATALOG_NG_WORDS_KEY)
        defaults.synchronize()
        catalogNgWordsState.value = value
    }

    override suspend fun updateWatchWordsJson(value: String) {
        defaults.setObject(value, forKey = WATCH_WORDS_KEY)
        defaults.synchronize()
        watchWordsState.value = value
    }

    override suspend fun updateSelfPostIdentifiersJson(value: String) {
        defaults.setObject(value, forKey = SELF_POST_IDENTIFIERS_KEY)
        defaults.synchronize()
        selfPostIdentifiersState.value = value
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
        if (updated) {
            defaults.synchronize()
        }
    }

    private fun sanitizeManualSaveDirectoryValue(value: String?): String {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank()) return DEFAULT_MANUAL_SAVE_ROOT
        if (trimmed == com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY) return DEFAULT_MANUAL_SAVE_ROOT
        return trimmed
    }
}
