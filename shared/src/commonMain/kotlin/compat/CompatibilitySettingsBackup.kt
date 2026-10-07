package com.valoser.futacha.shared.compat

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

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
    /**
     * Null for partial (watch/NG-only) restores: those must keep the current
     * tabs, active tab and selector state instead of resetting the workspace.
     */
    val workspace: CompatWorkspaceRecord? = CompatWorkspaceRecord(),
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

/**
 * How many restored tabs or history entries ([restoredKeys]) are still stored
 * ([keptKeys]) after the normal limits ran. The tab trim keeps the newest by
 * the time each tab was opened, so restored tabs older than the device's own
 * may be trimmed first (P4-4); the report counts what was actually kept.
 */
fun countCompatRestoredKept(restoredKeys: Collection<String>, keptKeys: Set<String>): Int =
    restoredKeys.toSet().count { it in keptKeys }

/** Human-editable companion file for watch words and NG rules only. */
@Serializable
data class CompatWatchNgBackup(
    val schemaVersion: Int = CURRENT_COMPAT_SETTINGS_BACKUP_VERSION,
    val exportedAtEpochMillis: Long,
    val watchWords: List<String> = emptyList(),
    val ngRules: List<CompatNgRule> = emptyList(),
    val watchRules: List<CompatWatchRule>? = null
)

/**
 * The tab trim never removes favourites or the active tab, so a profile can
 * legitimately hold more than [COMPAT_TAB_LIMIT_TRIGGER] tabs (P4-3). A backup
 * accepts as many as Android reads back (MAX_COMPAT_TAB_READ_ROWS); the byte
 * limit below still bounds the file.
 */
const val MAX_COMPAT_BACKUP_TABS = 1_000
const val CURRENT_COMPAT_SETTINGS_BACKUP_VERSION = 1
const val MAX_COMPAT_SETTINGS_BACKUP_BYTES = 2 * 1024 * 1024
const val COMPAT_SETTINGS_BACKUP_FILE_NAME = "futacha-compat-settings.json"
const val COMPAT_WATCH_NG_BACKUP_FILE_NAME = "futacha-compat-watch-ng.json"
const val COMPAT_WATCH_WORDS_PREFERENCE_KEY = "compat.catalog.監視ワード"

/**
 * Settings that only make sense on the device that wrote them: save folders (a document
 * tree URI, a bookmark or an absolute path) and cache locations. Restoring them on another
 * device or OS overwrote the folder chosen there with one that cannot be opened, so saving,
 * MHT and the saved box failed. Exports leave them out and a restore never applies them,
 * even from an older backup that still carries them.
 */
val COMPAT_BACKUP_DEVICE_PREFERENCE_KEYS = setOf(
    "compat.storage.dummyDownloadDir",
    "compat.storage.dummyDrawingDir",
    "compat.storage.dummyImageCacheLocation",
    "compat.storage.dummyCatalogImageCacheLocation"
)

/**
 * Post delete keys are passwords for deleting the user's own posts. A backup
 * file can be copied or shared, so exports leave them out; restoring an older
 * backup that still contains one keeps working.
 *
 * Unsent Futaber drafts are the user's unfinished post text; a settings file
 * that gets shared must not carry them, and a restore must not bring back
 * stale ones.
 *
 * The start lock's failed-attempt counter and remaining cool-down belong to
 * this device and must not be restored from (or leak into) a backup: restoring
 * one would lock the app, and leaving it out of an export would be no loss.
 */
val COMPAT_BACKUP_EXCLUDED_PREFERENCE_KEYS = setOf(
    "compat.common.commonPostDeleteKey",
    "compat.lastDeleteKey",
    "compat.futaber.drafts",
    "compat.appLock.attempts"
) + COMPAT_BACKUP_DEVICE_PREFERENCE_KEYS

fun compatBackupExportPreferences(preferences: Map<String, String>): Map<String, String> =
    preferences.filterKeys { it !in COMPAT_BACKUP_EXCLUDED_PREFERENCE_KEYS }

/** Keys a restore must not apply even when an (older) backup file contains them. */
private val COMPAT_BACKUP_UNRESTORABLE_PREFERENCE_KEYS =
    COMPAT_BACKUP_DEVICE_PREFERENCE_KEYS + "compat.futaber.drafts" + "compat.appLock.attempts"

fun compatBackupRestorePreferences(preferences: Map<String, String>): Map<String, String> =
    preferences.filterKeys { it !in COMPAT_BACKUP_UNRESTORABLE_PREFERENCE_KEYS }

private val compatSettingsBackupJson = Json {
    encodeDefaults = true
    // Backups written by a newer app version may carry fields this version
    // does not know yet; the bounded validation below still applies.
    ignoreUnknownKeys = true
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
    require("watchWords" !in compatSettingsBackupJson.parseToJsonElement(raw).jsonObject) {
        "監視ワード・NGのファイルです。対応する復元メニューを利用してください"
    }
    // Backups written by older versions carry the image hash cache as
    // preferences, often thousands of them; drop it before the size check so
    // such files can still be restored.
    val decoded = compatSettingsBackupJson.decodeFromString(CompatSettingsBackup.serializer(), raw)
        .let { backup ->
            backup.copy(
                preferences = compatBackupRestorePreferences(backup.preferences)
                    .filterKeys { !isCompatImagePhashCacheKey(it) }
            )
        }
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
            ngRules = backup.ngRules,
            watchRules = if (COMPAT_WATCH_RULES_KEY in backup.preferences) compatWatchRules(backup.preferences) else null
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
    require("watchWords" in compatSettingsBackupJson.parseToJsonElement(raw).jsonObject) {
        "監視ワード・NGのバックアップではありません"
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
        ) + (decoded.watchRules?.let { rules ->
            // Same compact encoding and limits as saving: the backup file's
            // verbose form (defaults and nulls written out) must not count.
            mapOf(COMPAT_WATCH_RULES_KEY to encodeValidCompatWatchRules(rules))
        } ?: emptyMap()),
        ngRules = decoded.ngRules,
        workspace = null
    )
}

/**
 * Keep the two user-facing backup rows physically independent.  The settings
 * file deliberately excludes watch/NG data so it can be restored without
 * replacing a hand-maintained keyword list.
 */
fun CompatSettingsBackup.settingsOnly(): CompatSettingsBackup = copy(
    preferences = preferences.filterKeys {
        it != COMPAT_WATCH_WORDS_PREFERENCE_KEY && it != COMPAT_WATCH_RULES_KEY &&
            !it.startsWith("compat.watcher.result.") && !isCompatImagePhashCacheKey(it)
    },
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
    preferences = preferences.filterKeys { it == COMPAT_WATCH_WORDS_PREFERENCE_KEY || it == COMPAT_WATCH_RULES_KEY },
    ngRules = ngRules,
    workspace = null
)

fun validateCompatSettingsBackup(backup: CompatSettingsBackup) {
    require(backup.schemaVersion == CURRENT_COMPAT_SETTINGS_BACKUP_VERSION) {
        "対応していないバックアップ形式です"
    }
    require(backup.boards.size <= 100) { "板の登録数が上限を超えています" }
    require(backup.tabs.size <= MAX_COMPAT_BACKUP_TABS) { "タブの登録数が上限を超えています" }
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
