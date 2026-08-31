package com.valoser.futacha.shared.compat

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Portable, deliberately bounded representation of the compatibility profile's
 * user settings.  Thread bodies and media are not included: those are caches,
 * not settings, and must never be overwritten by a settings restore.
 */
@Serializable
data class CompatToolbarBackup(
    val surface: String,
    val items: List<CompatToolbarBackupItem>
)

@Serializable
data class CompatToolbarBackupItem(
    val key: String,
    val position: Int,
    val active: Boolean
)

@Serializable
data class CompatSettingsBackup(
    val schemaVersion: Int = CURRENT_COMPAT_SETTINGS_BACKUP_VERSION,
    val exportedAtEpochMillis: Long,
    val boards: List<CompatBoard> = emptyList(),
    val tabs: List<CompatTab> = emptyList(),
    val history: List<CompatHistoryEntry> = emptyList(),
    val catalogPreferences: List<CompatCatalogPreference> = emptyList(),
    val preferences: Map<String, String> = emptyMap(),
    val ngRules: List<CompatNgRule> = emptyList(),
    val workspace: CompatWorkspaceRecord = CompatWorkspaceRecord(),
    val toolbars: List<CompatToolbarBackup> = emptyList()
)

@Serializable
data class CompatSettingsBackupImportReport(
    val boardsImported: Int,
    val tabsImported: Int,
    val historyImported: Int,
    val preferencesImported: Int,
    val ngRulesImported: Int,
    val toolbarsImported: Int
)

/** Human-editable companion file for watch words and NG rules only. */
@Serializable
data class CompatWatchNgBackup(
    val schemaVersion: Int = CURRENT_COMPAT_SETTINGS_BACKUP_VERSION,
    val exportedAtEpochMillis: Long,
    val watchWords: List<String> = emptyList(),
    val ngRules: List<CompatNgRule> = emptyList()
)

const val CURRENT_COMPAT_SETTINGS_BACKUP_VERSION = 1
const val MAX_COMPAT_SETTINGS_BACKUP_BYTES = 2 * 1024 * 1024
const val COMPAT_SETTINGS_BACKUP_FILE_NAME = "futacha-compat-settings.json"
const val COMPAT_WATCH_NG_BACKUP_FILE_NAME = "futacha-compat-watch-ng.json"
const val COMPAT_WATCH_WORDS_PREFERENCE_KEY = "compat.catalog.監視ワード"

private val compatSettingsBackupJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = false
    explicitNulls = true
}

fun encodeCompatSettingsBackup(backup: CompatSettingsBackup): String {
    validateCompatSettingsBackup(backup)
    val encoded = compatSettingsBackupJson.encodeToString(CompatSettingsBackup.serializer(), backup)
    require(encoded.encodeToByteArray().size <= MAX_COMPAT_SETTINGS_BACKUP_BYTES) {
        "バックアップファイルが大きすぎます"
    }
    return encoded
}

fun decodeCompatSettingsBackup(raw: String): CompatSettingsBackup {
    require(raw.encodeToByteArray().size <= MAX_COMPAT_SETTINGS_BACKUP_BYTES) {
        "バックアップファイルが大きすぎます"
    }
    val decoded = compatSettingsBackupJson.decodeFromString(CompatSettingsBackup.serializer(), raw)
    validateCompatSettingsBackup(decoded)
    return decoded
}

fun encodeCompatWatchNgBackup(backup: CompatSettingsBackup): String {
    validateCompatSettingsBackup(backup)
    val words = backup.preferences[COMPAT_WATCH_WORDS_PREFERENCE_KEY]
        .orEmpty()
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .toList()
    require(words.size <= 10_000) { "監視ワードが多すぎます" }
    val encoded = compatSettingsBackupJson.encodeToString(
        CompatWatchNgBackup.serializer(),
        CompatWatchNgBackup(
            exportedAtEpochMillis = backup.exportedAtEpochMillis,
            watchWords = words,
            ngRules = backup.ngRules
        )
    )
    require(encoded.encodeToByteArray().size <= MAX_COMPAT_SETTINGS_BACKUP_BYTES) {
        "バックアップファイルが大きすぎます"
    }
    return encoded
}

fun decodeCompatWatchNgBackup(raw: String): CompatSettingsBackup {
    require(raw.encodeToByteArray().size <= MAX_COMPAT_SETTINGS_BACKUP_BYTES) {
        "バックアップファイルが大きすぎます"
    }
    val decoded = compatSettingsBackupJson.decodeFromString(CompatWatchNgBackup.serializer(), raw)
    require(decoded.schemaVersion == CURRENT_COMPAT_SETTINGS_BACKUP_VERSION) {
        "対応していないバックアップ形式です"
    }
    require(decoded.watchWords.size <= 10_000) { "監視ワードが多すぎます" }
    decoded.watchWords.forEach { word ->
        require(word.length <= MAX_COMPAT_PREFERENCE_VALUE_CHARS) { "監視ワードが長すぎます" }
    }
    require(decoded.ngRules.size <= MAX_COMPAT_NG_RULES) { "NG項目が多すぎます" }
    decoded.ngRules.forEach(::requireValidCompatNgRule)
    val normalizedWatchWords = decoded.watchWords
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .joinToString("\n")
    require(normalizedWatchWords.length <= MAX_COMPAT_PREFERENCE_VALUE_CHARS) {
        "監視ワードが多すぎます"
    }
    return CompatSettingsBackup(
        exportedAtEpochMillis = decoded.exportedAtEpochMillis,
        preferences = mapOf(
            COMPAT_WATCH_WORDS_PREFERENCE_KEY to normalizedWatchWords
        ),
        ngRules = decoded.ngRules
    )
}

/**
 * Keep the two user-facing backup rows physically independent.  The settings
 * file deliberately excludes watch/NG data so it can be restored without
 * replacing a hand-maintained keyword list.
 */
fun CompatSettingsBackup.settingsOnly(): CompatSettingsBackup = copy(
    preferences = preferences - COMPAT_WATCH_WORDS_PREFERENCE_KEY,
    ngRules = emptyList()
)

/**
 * A compact, editable keyword file. Board/tab records are intentionally not
 * included: importing words must never add or overwrite navigation state.
 * Board-scoped rules are accepted when the same board already exists on the
 * destination device, just like the legacy keyword.cfg import.
 */
fun CompatSettingsBackup.watchAndNgOnly(): CompatSettingsBackup = CompatSettingsBackup(
    schemaVersion = schemaVersion,
    exportedAtEpochMillis = exportedAtEpochMillis,
    preferences = preferences.filterKeys { it == COMPAT_WATCH_WORDS_PREFERENCE_KEY },
    ngRules = ngRules
)

fun validateCompatSettingsBackup(backup: CompatSettingsBackup) {
    require(backup.schemaVersion == CURRENT_COMPAT_SETTINGS_BACKUP_VERSION) {
        "対応していないバックアップ形式です"
    }
    require(backup.boards.size <= 100) { "板の登録数が上限を超えています" }
    require(backup.tabs.size <= 100) { "タブの登録数が上限を超えています" }
    require(backup.history.size <= 200) { "履歴の登録数が上限を超えています" }
    require(backup.preferences.size <= 4096) { "設定項目が多すぎます" }
    require(backup.ngRules.size <= MAX_COMPAT_NG_RULES) { "NG項目が多すぎます" }
    require(backup.toolbars.size <= CompatToolbarSurface.entries.size) { "ツールバー項目が多すぎます" }
    require(backup.boards.map { it.key }.distinct().size == backup.boards.size) { "板キーが重複しています" }
    require(backup.boards.map { it.canonicalUrl }.distinct().size == backup.boards.size) { "板URLが重複しています" }
    require(backup.tabs.map { it.key }.distinct().size == backup.tabs.size) { "タブキーが重複しています" }
    require(backup.history.map { it.canonicalUrl }.distinct().size == backup.history.size) { "履歴URLが重複しています" }
    backup.boards.forEach { board ->
        require(board.key.length in 1..200)
        require(board.name.length <= 200)
        require(board.canonicalUrl.length <= 500)
        require(board.originalUrl.length <= 500)
    }
    backup.preferences.forEach { (key, value) ->
        requireValidCompatPreference(key, value)
    }
    backup.ngRules.forEach(::requireValidCompatNgRule)
    backup.toolbars.forEach { toolbar ->
        require(toolbar.surface in CompatToolbarSurface.entries.map { it.name })
        require(toolbar.items.size <= 64)
        require(toolbar.items.map { it.key }.distinct().size == toolbar.items.size)
    }
}
