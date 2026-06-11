package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.onStart
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import platform.Foundation.NSLock
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
private const val THREAD_SUMMARY_MODE_KEY = "thread_summary_mode_enabled"
private const val AI_POST_FILTER_KEY = "ai_post_filter_enabled"
private const val AI_COMMAND_KEY = "ai_command_enabled"
private const val APP_LOCK_PASSWORD_HASH_KEY = "app_lock_password_hash"
private const val MANUAL_SAVE_DIRECTORY_KEY = "manual_save_directory"
private const val ATTACHMENT_PICKER_PREF_KEY = "attachment_picker_preference"
private const val SAVE_DIRECTORY_SELECTION_KEY = "save_directory_selection"
private const val THREAD_GALLERY_TAP_ACTION_KEY = "thread_gallery_tap_action"
private const val THEME_MODE_KEY = "theme_mode"
private const val THEME_PALETTE_KEY = "theme_palette"
private const val APP_ICON_VARIANT_KEY = "app_icon_variant"
private const val THREAD_DISPLAY_MODE_KEY = "thread_display_mode"
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
private const val IOS_STATE_READ_MAX_ATTEMPTS = 3

internal actual fun createPlatformStateStorage(platformContext: Any?): PlatformStateStorage {
    return IosPlatformStateStorage()
}

private class IosPlatformStateStorage : PlatformStateStorage {
    private val defaults = NSUserDefaults.standardUserDefaults()
    private val updateMutex = Mutex()
    private val cacheLock = NSLock()
    private val stringReadCache = mutableMapOf<String, String?>()
    private val booleanReadCache = mutableMapOf<String, Boolean>()
    private val locallyUpdatedKeys = mutableSetOf<String>()
    private val deferredLoadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val boardsState = MutableStateFlow<String?>(null)
    private val historyState = MutableStateFlow<String?>(null)
    private val displayStyleState = MutableStateFlow<String?>(null)
    private val gridColumnsState = MutableStateFlow<String?>(null)
    private val privacyFilterState = MutableStateFlow(false)
    private val backgroundRefreshState = MutableStateFlow(false)
    private val adsEnabledState = MutableStateFlow(true)
    private val postingNoticeState = MutableStateFlow(false)
    private val lightweightModeState = MutableStateFlow(false)
    private val threadSummaryModeState = MutableStateFlow(false)
    private val aiPostFilterState = MutableStateFlow(false)
    private val aiCommandState = MutableStateFlow(readBooleanState(AI_COMMAND_KEY))
    private val appLockPasswordHashState = MutableStateFlow<String?>(null)
    private val manualSaveDirectoryState = MutableStateFlow(
        DEFAULT_MANUAL_SAVE_ROOT
    )
    private val attachmentPickerPreferenceState = MutableStateFlow<String?>(null)
    private val saveDirectorySelectionState = MutableStateFlow<String?>(null)
    private val threadGalleryTapActionState = MutableStateFlow<String?>(null)
    private val themeModeState = MutableStateFlow<String?>(null)
    private val themePaletteState = MutableStateFlow<String?>(null)
    private val appIconVariantState = MutableStateFlow<String?>(null)
    private val threadDisplayModeState = MutableStateFlow<String?>(null)
    private val catalogModeMapState = MutableStateFlow<String?>(null)
    private val ngHeadersState = MutableStateFlow<String?>(null)
    private val ngWordsState = MutableStateFlow<String?>(null)
    private val catalogNgWordsState = MutableStateFlow<String?>(null)
    private val watchWordsState = MutableStateFlow<String?>(null)
    private val selfPostIdentifiersState = MutableStateFlow<String?>(null)
    private val threadMenuConfigState = MutableStateFlow<String?>(null)
    private val threadSettingsMenuConfigState = MutableStateFlow<String?>(null)
    private val threadMenuEntriesState = MutableStateFlow<String?>(null)
    private val catalogNavEntriesState = MutableStateFlow<String?>(null)
    private val preferredFileManagerPackageState = MutableStateFlow("")
    private val preferredFileManagerLabelState = MutableStateFlow("")
    private val lastUsedDeleteKeyState = MutableStateFlow<String?>(null)

    private val deferredLoadJob = deferredLoadScope.launch {
        loadDeferredInitialState()
    }

    override val boardsJson: Flow<String?> = awaitDeferredInitialLoad(boardsState)
    override val historyJson: Flow<String?> = awaitDeferredInitialLoad(historyState)
    override val privacyFilterEnabled: Flow<Boolean> = awaitDeferredInitialLoad(privacyFilterState)
    override val backgroundRefreshEnabled: Flow<Boolean> = awaitDeferredInitialLoad(backgroundRefreshState)
    override val adsEnabled: Flow<Boolean> = awaitDeferredInitialLoad(adsEnabledState)
    override val hasShownPostingNotice: Flow<Boolean> = awaitDeferredInitialLoad(postingNoticeState)
    override val lightweightModeEnabled: Flow<Boolean> = awaitDeferredInitialLoad(lightweightModeState)
    override val threadSummaryModeEnabled: Flow<Boolean> = awaitDeferredInitialLoad(threadSummaryModeState)
    override val aiPostFilterEnabled: Flow<Boolean> = awaitDeferredInitialLoad(aiPostFilterState)
    override val aiCommandEnabled: Flow<Boolean> = awaitDeferredInitialLoad(aiCommandState)
    override val appLockPasswordHash: Flow<String?> = awaitDeferredInitialLoad(appLockPasswordHashState)
    override val manualSaveDirectory: Flow<String> = awaitDeferredInitialLoad(manualSaveDirectoryState)
    override val attachmentPickerPreference: Flow<String?> = awaitDeferredInitialLoad(attachmentPickerPreferenceState)
    override val saveDirectorySelection: Flow<String?> = awaitDeferredInitialLoad(saveDirectorySelectionState)
    override val threadGalleryTapAction: Flow<String?> = awaitDeferredInitialLoad(threadGalleryTapActionState)
    override val themeMode: Flow<String?> = awaitDeferredInitialLoad(themeModeState)
    override val themePalette: Flow<String?> = awaitDeferredInitialLoad(themePaletteState)
    override val appIconVariant: Flow<String?> = awaitDeferredInitialLoad(appIconVariantState)
    override val threadDisplayMode: Flow<String?> = awaitDeferredInitialLoad(threadDisplayModeState)
    override val catalogModeMapJson: Flow<String?> = awaitDeferredInitialLoad(catalogModeMapState)
    override val catalogDisplayStyle: Flow<String?> = awaitDeferredInitialLoad(displayStyleState)
    override val catalogGridColumns: Flow<String?> = awaitDeferredInitialLoad(gridColumnsState)
    override val ngHeadersJson: Flow<String?> = awaitDeferredInitialLoad(ngHeadersState)
    override val ngWordsJson: Flow<String?> = awaitDeferredInitialLoad(ngWordsState)
    override val catalogNgWordsJson: Flow<String?> = awaitDeferredInitialLoad(catalogNgWordsState)
    override val watchWordsJson: Flow<String?> = awaitDeferredInitialLoad(watchWordsState)
    override val selfPostIdentifiersJson: Flow<String?> = awaitDeferredInitialLoad(selfPostIdentifiersState)
    override val threadMenuConfigJson: Flow<String?> = awaitDeferredInitialLoad(threadMenuConfigState)
    override val threadSettingsMenuConfigJson: Flow<String?> = awaitDeferredInitialLoad(threadSettingsMenuConfigState)
    override val threadMenuEntriesConfigJson: Flow<String?> = awaitDeferredInitialLoad(threadMenuEntriesState)
    override val catalogNavEntriesConfigJson: Flow<String?> = awaitDeferredInitialLoad(catalogNavEntriesState)
    override val preferredFileManagerPackage: Flow<String> = awaitDeferredInitialLoad(preferredFileManagerPackageState)
    override val preferredFileManagerLabel: Flow<String> = awaitDeferredInitialLoad(preferredFileManagerLabelState)
    override val lastUsedDeleteKey: Flow<String?> = awaitDeferredInitialLoad(lastUsedDeleteKeyState)

    private fun <T> awaitDeferredInitialLoad(flow: Flow<T>): Flow<T> =
        flow.onStart { deferredLoadJob.join() }

    private suspend fun loadDeferredInitialState() {
        val stringValues = mapOf(
            BOARDS_KEY to readStringState(BOARDS_KEY),
            HISTORY_KEY to readStringState(HISTORY_KEY),
            CATALOG_DISPLAY_STYLE_KEY to readStringState(CATALOG_DISPLAY_STYLE_KEY),
            CATALOG_GRID_COLUMNS_KEY to readStringState(CATALOG_GRID_COLUMNS_KEY),
            APP_LOCK_PASSWORD_HASH_KEY to readStringState(APP_LOCK_PASSWORD_HASH_KEY),
            MANUAL_SAVE_DIRECTORY_KEY to readStringState(MANUAL_SAVE_DIRECTORY_KEY),
            ATTACHMENT_PICKER_PREF_KEY to readStringState(ATTACHMENT_PICKER_PREF_KEY),
            SAVE_DIRECTORY_SELECTION_KEY to readStringState(SAVE_DIRECTORY_SELECTION_KEY),
            THREAD_GALLERY_TAP_ACTION_KEY to readStringState(THREAD_GALLERY_TAP_ACTION_KEY),
            THEME_MODE_KEY to readStringState(THEME_MODE_KEY),
            THEME_PALETTE_KEY to readStringState(THEME_PALETTE_KEY),
            APP_ICON_VARIANT_KEY to readStringState(APP_ICON_VARIANT_KEY),
            THREAD_DISPLAY_MODE_KEY to readStringState(THREAD_DISPLAY_MODE_KEY),
            CATALOG_MODE_MAP_KEY to readStringState(CATALOG_MODE_MAP_KEY),
            NG_HEADERS_KEY to readStringState(NG_HEADERS_KEY),
            NG_WORDS_KEY to readStringState(NG_WORDS_KEY),
            CATALOG_NG_WORDS_KEY to readStringState(CATALOG_NG_WORDS_KEY),
            WATCH_WORDS_KEY to readStringState(WATCH_WORDS_KEY),
            SELF_POST_IDENTIFIERS_KEY to readStringState(SELF_POST_IDENTIFIERS_KEY),
            THREAD_MENU_CONFIG_KEY to readStringState(THREAD_MENU_CONFIG_KEY),
            THREAD_SETTINGS_MENU_CONFIG_KEY to readStringState(THREAD_SETTINGS_MENU_CONFIG_KEY),
            THREAD_MENU_ENTRIES_KEY to readStringState(THREAD_MENU_ENTRIES_KEY),
            CATALOG_NAV_ENTRIES_KEY to readStringState(CATALOG_NAV_ENTRIES_KEY),
            PREFERRED_FILE_MANAGER_PACKAGE_KEY to readStringState(PREFERRED_FILE_MANAGER_PACKAGE_KEY),
            PREFERRED_FILE_MANAGER_LABEL_KEY to readStringState(PREFERRED_FILE_MANAGER_LABEL_KEY),
            LAST_USED_DELETE_KEY to readStringState(LAST_USED_DELETE_KEY)
        )
        val booleanValues = mapOf(
            PRIVACY_FILTER_KEY to readBooleanState(PRIVACY_FILTER_KEY),
            BACKGROUND_REFRESH_KEY to readBooleanState(BACKGROUND_REFRESH_KEY),
            ADS_ENABLED_KEY to readBooleanState(ADS_ENABLED_KEY, defaultValue = true),
            POSTING_NOTICE_KEY to readBooleanState(POSTING_NOTICE_KEY),
            LIGHTWEIGHT_MODE_KEY to readBooleanState(LIGHTWEIGHT_MODE_KEY),
            THREAD_SUMMARY_MODE_KEY to readBooleanState(THREAD_SUMMARY_MODE_KEY),
            AI_POST_FILTER_KEY to readBooleanState(AI_POST_FILTER_KEY),
            AI_COMMAND_KEY to readBooleanState(AI_COMMAND_KEY)
        )

        updateMutex.withLock {
            applyDeferredStringState(BOARDS_KEY, stringValues, boardsState)
            applyDeferredStringState(HISTORY_KEY, stringValues, historyState)
            applyDeferredStringState(CATALOG_DISPLAY_STYLE_KEY, stringValues, displayStyleState)
            applyDeferredStringState(CATALOG_GRID_COLUMNS_KEY, stringValues, gridColumnsState)
            applyDeferredBooleanState(PRIVACY_FILTER_KEY, booleanValues, privacyFilterState)
            applyDeferredBooleanState(BACKGROUND_REFRESH_KEY, booleanValues, backgroundRefreshState)
            applyDeferredBooleanState(ADS_ENABLED_KEY, booleanValues, adsEnabledState)
            applyDeferredBooleanState(POSTING_NOTICE_KEY, booleanValues, postingNoticeState)
            applyDeferredBooleanState(LIGHTWEIGHT_MODE_KEY, booleanValues, lightweightModeState)
            applyDeferredBooleanState(THREAD_SUMMARY_MODE_KEY, booleanValues, threadSummaryModeState)
            applyDeferredBooleanState(AI_POST_FILTER_KEY, booleanValues, aiPostFilterState)
            applyDeferredBooleanState(AI_COMMAND_KEY, booleanValues, aiCommandState)
            applyDeferredStringState(APP_LOCK_PASSWORD_HASH_KEY, stringValues, appLockPasswordHashState)
            applyDeferredStringState(
                key = MANUAL_SAVE_DIRECTORY_KEY,
                values = stringValues,
                state = manualSaveDirectoryState,
                transform = ::sanitizeManualSaveDirectoryValue
            )
            applyDeferredStringState(ATTACHMENT_PICKER_PREF_KEY, stringValues, attachmentPickerPreferenceState)
            applyDeferredStringState(SAVE_DIRECTORY_SELECTION_KEY, stringValues, saveDirectorySelectionState)
            applyDeferredStringState(THREAD_GALLERY_TAP_ACTION_KEY, stringValues, threadGalleryTapActionState)
            applyDeferredStringState(THEME_MODE_KEY, stringValues, themeModeState)
            applyDeferredStringState(THEME_PALETTE_KEY, stringValues, themePaletteState)
            applyDeferredStringState(APP_ICON_VARIANT_KEY, stringValues, appIconVariantState)
            applyDeferredStringState(THREAD_DISPLAY_MODE_KEY, stringValues, threadDisplayModeState)
            applyDeferredStringState(CATALOG_MODE_MAP_KEY, stringValues, catalogModeMapState)
            applyDeferredStringState(NG_HEADERS_KEY, stringValues, ngHeadersState)
            applyDeferredStringState(NG_WORDS_KEY, stringValues, ngWordsState)
            applyDeferredStringState(CATALOG_NG_WORDS_KEY, stringValues, catalogNgWordsState)
            applyDeferredStringState(WATCH_WORDS_KEY, stringValues, watchWordsState)
            applyDeferredStringState(SELF_POST_IDENTIFIERS_KEY, stringValues, selfPostIdentifiersState)
            applyDeferredStringState(THREAD_MENU_CONFIG_KEY, stringValues, threadMenuConfigState)
            applyDeferredStringState(THREAD_SETTINGS_MENU_CONFIG_KEY, stringValues, threadSettingsMenuConfigState)
            applyDeferredStringState(THREAD_MENU_ENTRIES_KEY, stringValues, threadMenuEntriesState)
            applyDeferredStringState(CATALOG_NAV_ENTRIES_KEY, stringValues, catalogNavEntriesState)
            applyDeferredStringState(
                key = PREFERRED_FILE_MANAGER_PACKAGE_KEY,
                values = stringValues,
                state = preferredFileManagerPackageState,
                transform = { it.orEmpty() }
            )
            applyDeferredStringState(
                key = PREFERRED_FILE_MANAGER_LABEL_KEY,
                values = stringValues,
                state = preferredFileManagerLabelState,
                transform = { it.orEmpty() }
            )
            applyDeferredStringState(LAST_USED_DELETE_KEY, stringValues, lastUsedDeleteKeyState)
        }
    }

    private fun applyDeferredStringState(
        key: String,
        values: Map<String, String?>,
        state: MutableStateFlow<String?>
    ) {
        if (key !in locallyUpdatedKeys) {
            state.value = values[key]
        }
    }

    private fun applyDeferredStringState(
        key: String,
        values: Map<String, String?>,
        state: MutableStateFlow<String>,
        transform: (String?) -> String
    ) {
        if (key !in locallyUpdatedKeys) {
            state.value = transform(values[key])
        }
    }

    private fun applyDeferredBooleanState(
        key: String,
        values: Map<String, Boolean>,
        state: MutableStateFlow<Boolean>
    ) {
        if (key !in locallyUpdatedKeys) {
            state.value = values[key] ?: state.value
        }
    }

    private inline fun <T> withCacheLock(block: () -> T): T {
        cacheLock.lock()
        return try {
            block()
        } finally {
            cacheLock.unlock()
        }
    }

    private fun cacheStringState(key: String, value: String?) {
        withCacheLock {
            stringReadCache[key] = value
        }
    }

    private fun cachedStringState(key: String): String? {
        return withCacheLock {
            stringReadCache[key]
        }
    }

    private fun cacheBooleanState(key: String, value: Boolean) {
        withCacheLock {
            booleanReadCache[key] = value
        }
    }

    private fun cachedBooleanState(key: String): Boolean? {
        return withCacheLock {
            booleanReadCache[key]
        }
    }

    private fun readStringState(key: String): String? {
        var lastError: Throwable? = null
        repeat(IOS_STATE_READ_MAX_ATTEMPTS) { attempt ->
            runCatching { defaults.stringForKey(key) }
                .onSuccess { value ->
                    cacheStringState(key, value)
                    return value
                }
                .onFailure { error ->
                    lastError = error
                    Logger.w(
                        "IosPlatformStateStorage",
                        "NSUserDefaults string read failed for '$key' (attempt=${attempt + 1}/$IOS_STATE_READ_MAX_ATTEMPTS): ${error.message}"
                    )
                }
        }
        return cachedStringState(key).also {
            if (lastError != null) {
                Logger.e(
                    "IosPlatformStateStorage",
                    "Falling back to cached string state for '$key' after repeated read failures",
                    lastError
                )
            }
        }
    }

    private fun readBooleanState(key: String, defaultValue: Boolean = false): Boolean {
        var lastError: Throwable? = null
        repeat(IOS_STATE_READ_MAX_ATTEMPTS) { attempt ->
            runCatching {
                if (defaults.objectForKey(key) == null) {
                    defaultValue
                } else {
                    defaults.boolForKey(key)
                }
            }.onSuccess { value ->
                cacheBooleanState(key, value)
                return value
            }.onFailure { error ->
                lastError = error
                Logger.w(
                    "IosPlatformStateStorage",
                    "NSUserDefaults boolean read failed for '$key' (attempt=${attempt + 1}/$IOS_STATE_READ_MAX_ATTEMPTS): ${error.message}"
                )
            }
        }
        return (cachedBooleanState(key) ?: defaultValue).also {
            if (lastError != null) {
                Logger.e(
                    "IosPlatformStateStorage",
                    "Falling back to cached boolean state for '$key' after repeated read failures",
                    lastError
                )
            }
        }
    }

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
            cacheStringState(key, value)
            locallyUpdatedKeys += key
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
            cacheStringState(key, value)
            locallyUpdatedKeys += key
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
            cacheBooleanState(key, value)
            locallyUpdatedKeys += key
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
            cacheStringState(firstKey, firstValue)
            cacheStringState(secondKey, secondValue)
            locallyUpdatedKeys += firstKey
            locallyUpdatedKeys += secondKey
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
            cacheStringState(key, value)
            locallyUpdatedKeys += key
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
            cacheBooleanState(key, value)
            locallyUpdatedKeys += key
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
            cacheStringState(key, value)
            locallyUpdatedKeys += key
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
            cacheStringState(key, value)
            locallyUpdatedKeys += key
        }
    }

    private fun seedFrom(seedBundles: AppStateSeedBundles) {
        seedRequiredStringState(BOARDS_KEY, seedBundles.boards.boardsJson, boardsState)
        seedRequiredStringState(HISTORY_KEY, seedBundles.history.historyJson, historyState)
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
            THREAD_GALLERY_TAP_ACTION_KEY,
            seedBundles.preferences.threadGalleryTapAction,
            threadGalleryTapActionState
        )
        seedOptionalStringState(
            THEME_MODE_KEY,
            seedBundles.preferences.themeMode,
            themeModeState
        )
        seedOptionalStringState(
            THEME_PALETTE_KEY,
            seedBundles.preferences.themePalette,
            themePaletteState
        )
        seedOptionalStringState(
            APP_ICON_VARIANT_KEY,
            seedBundles.preferences.appIconVariant,
            appIconVariantState
        )
        seedOptionalStringState(
            THREAD_DISPLAY_MODE_KEY,
            seedBundles.preferences.threadDisplayMode,
            threadDisplayModeState
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
        update {
            if (enabled) {
                defaults.removeObjectForKey(ADS_ENABLED_KEY)
            } else {
                defaults.setBool(false, forKey = ADS_ENABLED_KEY)
            }
            adsEnabledState.value = enabled
            cacheBooleanState(ADS_ENABLED_KEY, enabled)
            locallyUpdatedKeys += ADS_ENABLED_KEY
        }
    }

    override suspend fun updateHasShownPostingNotice(shown: Boolean) {
        updateBooleanState(POSTING_NOTICE_KEY, shown, postingNoticeState)
    }

    override suspend fun updateLightweightModeEnabled(enabled: Boolean) {
        updateBooleanState(LIGHTWEIGHT_MODE_KEY, enabled, lightweightModeState)
    }

    override suspend fun updateThreadSummaryModeEnabled(enabled: Boolean) {
        updateBooleanState(THREAD_SUMMARY_MODE_KEY, enabled, threadSummaryModeState)
    }

    override suspend fun updateAiPostFilterEnabled(enabled: Boolean) {
        updateBooleanState(AI_POST_FILTER_KEY, enabled, aiPostFilterState)
    }

    override suspend fun updateAiCommandEnabled(enabled: Boolean) {
        updateBooleanState(AI_COMMAND_KEY, enabled, aiCommandState)
    }

    override suspend fun updateAppLockPasswordHash(value: String) {
        updateStringState(APP_LOCK_PASSWORD_HASH_KEY, value, appLockPasswordHashState)
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

    override suspend fun updateThreadGalleryTapAction(action: String) {
        updateStringState(
            THREAD_GALLERY_TAP_ACTION_KEY,
            action,
            threadGalleryTapActionState
        )
    }

    override suspend fun updateThemeMode(mode: String) {
        updateStringState(THEME_MODE_KEY, mode, themeModeState)
    }

    override suspend fun updateThemePalette(palette: String) {
        updateStringState(THEME_PALETTE_KEY, palette, themePaletteState)
    }

    override suspend fun updateAppIconVariant(variant: String) {
        updateStringState(APP_ICON_VARIANT_KEY, variant, appIconVariantState)
    }

    override suspend fun updateThreadDisplayMode(mode: String) {
        updateStringState(THREAD_DISPLAY_MODE_KEY, mode, threadDisplayModeState)
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
        deferredLoadJob.join()
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
